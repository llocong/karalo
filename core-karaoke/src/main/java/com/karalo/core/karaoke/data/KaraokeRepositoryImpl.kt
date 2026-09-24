package com.karalo.core.karaoke.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karalo.core.common.di.ApplicationScope
import com.karalo.core.common.error.AppError
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.result.map
import com.karalo.core.common.result.onSuccess
import com.karalo.core.karaoke.data.remote.KaraokeApi
import com.karalo.core.karaoke.data.remote.dto.HistoryPlayDto
import com.karalo.core.karaoke.data.remote.dto.MostPlayedSongDto
import com.karalo.core.karaoke.data.remote.dto.NowPlayingDto
import com.karalo.core.karaoke.data.remote.dto.NowPlayingPayloadDto
import com.karalo.core.karaoke.data.remote.dto.QueueItemDto
import com.karalo.core.karaoke.data.remote.dto.QueueSnapshotDto
import com.karalo.core.karaoke.data.ws.KaraokeWebSocketClient
import com.karalo.core.karaoke.di.KaraokeDataStore
import com.karalo.core.karaoke.domain.HistoryPage
import com.karalo.core.karaoke.domain.HistoryPlay
import com.karalo.core.karaoke.domain.KaraokeEvent
import com.karalo.core.karaoke.domain.KaraokeQueueSnapshot
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.core.karaoke.domain.KaraokeSession
import com.karalo.core.karaoke.domain.MostPlayedSong
import com.karalo.core.karaoke.domain.NowPlaying
import com.karalo.core.karaoke.domain.NowPlayingSource
import com.karalo.core.karaoke.domain.PlayNowSong
import com.karalo.core.karaoke.domain.QueueItem
import com.karalo.core.karaoke.domain.RemoteCommandType
import com.karalo.core.karaoke.domain.TvInstallationIdProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val RECONNECT_BASE_DELAY_SECONDS = 1L
private const val RECONNECT_MAX_DELAY_SECONDS = 30L
private const val RECONNECT_MAX_BACKOFF_SHIFT = 5
private const val RECONNECT_JITTER_MAX_MS = 400L

internal val TV_SECRET_KEY = stringPreferencesKey("karaoke_tv_secret")

/** Exponential backoff, capped: 1s, 2s, 4s, ... up to [RECONNECT_MAX_DELAY_SECONDS]. */
private fun backoffDelayFor(attempt: Int): Long {
    val shift = attempt.coerceAtMost(RECONNECT_MAX_BACKOFF_SHIFT)
    return (RECONNECT_BASE_DELAY_SECONDS shl shift).coerceAtMost(RECONNECT_MAX_DELAY_SECONDS)
}

/**
 * `@Singleton` (not `@ActivityRetainedScoped`) — the realtime connection and queue state must
 * outlive Player nav-entry disposal and rail tab switches; see [KaraokeRepository]'s own doc.
 *
 * The WS reconnect loop and every queue-affecting event handler funnel through [reconcile] — a
 * wholesale REST re-fetch that replaces [queueSnapshot] entirely, rather than patching it
 * field-by-field from partial WS payloads (`QUEUE_ITEM_ADDED`/`QUEUE_ITEM_REMOVED` only carry the
 * single changed item, not a full list). This mirrors
 * [com.karalo.core.common.session.SearchSessionHolder]'s "newest snapshot wins" spirit: simpler
 * and more robust than a client-side merge, at the cost of one extra REST round trip per event --
 * an acceptable trade for this feature's actual event cadence (human-paced queue changes, not a
 * high-frequency stream).
 */
@Singleton
@Suppress("TooManyFunctions") // one method per backend operation (see KaraokeRepository), plus the WS loop
class KaraokeRepositoryImpl
    @Inject
    constructor(
        private val api: KaraokeApi,
        private val webSocketClient: KaraokeWebSocketClient,
        private val tvInstallationIdProvider: TvInstallationIdProvider,
        private val json: Json,
        private val logger: Logger,
        @ApplicationScope private val appScope: CoroutineScope,
        @KaraokeDataStore private val dataStore: DataStore<Preferences>,
    ) : KaraokeRepository {
        private val queueSnapshotFlow = MutableStateFlow(KaraokeQueueSnapshot.EMPTY)
        override val queueSnapshot: StateFlow<KaraokeQueueSnapshot> = queueSnapshotFlow.asStateFlow()

        private val eventsFlow = MutableSharedFlow<KaraokeEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<KaraokeEvent> = eventsFlow.asSharedFlow()

        private val sessionJoinUrlFlow = MutableStateFlow<String?>(null)
        override val sessionJoinUrl: StateFlow<String?> = sessionJoinUrlFlow.asStateFlow()

        // Cached after the first successful ensure; reused for every subsequent REST call and WS
        // (re)connect for the rest of this process's lifetime. Never re-derived from anywhere
        // else, which is what guarantees the WS reconnect loop can never accidentally address a
        // different/new session.
        @Volatile private var cachedSessionId: String? = null

        // Persisted in DataStore (see [dataStore]/[TV_SECRET_KEY]), not just cached in memory: the
        // backend's `session/ensure` is trust-on-first-use -- only the very first call for a given
        // TV installation ID may omit the secret, every call after that must present the one issued
        // then. Losing this on process death/restart (a plain @Volatile var would) would otherwise
        // permanently lock this TV out of its own already-created session with no way to recover
        // short of deleting it server-side -- discovered by actually restarting the app against a
        // live backend during manual verification, not by unit tests alone.
        @Volatile private var cachedTvSecret: String? = null
        private var wsLoopStarted = false

        override suspend fun ensureSession(): AppResult<KaraokeSession> {
            val tvId = tvInstallationIdProvider.getOrCreate()
            val secret = cachedTvSecret ?: dataStore.data.first()[TV_SECRET_KEY]
            return when (val result = api.ensureSession(tvId, secret)) {
                is AppResult.Success -> {
                    val body = result.data
                    cachedSessionId = body.session.id
                    body.tvSecret?.let { fresh ->
                        cachedTvSecret = fresh
                        dataStore.edit { prefs -> prefs[TV_SECRET_KEY] = fresh }
                    } ?: run { cachedTvSecret = secret }
                    sessionJoinUrlFlow.value = body.session.joinUrl
                    reconcile()
                    startWebSocketLoopIfNeeded()
                    AppResult.Success(KaraokeSession(body.session.id, body.session.code, body.session.joinUrl))
                }
                is AppResult.Failure -> AppResult.Failure(result.error)
            }
        }

        override suspend fun consumeNext(): AppResult<NowPlaying?> =
            withSession { sessionId, secret ->
                api
                    .consumeNext(
                        sessionId,
                        secret,
                    ).onSuccess { applyNowPlayingPayload(it) }
                    .map { it.nowPlaying?.toDomain() }
            }

        override suspend fun playNowStart(song: PlayNowSong): AppResult<NowPlaying?> =
            withSession { sessionId, secret ->
                api
                    .playNowStart(sessionId, secret, song)
                    .onSuccess { applyNowPlayingPayload(it) }
                    .map { it.nowPlaying?.toDomain() }
            }

        override suspend fun playNowEnd(): AppResult<NowPlaying?> =
            withSession { sessionId, secret ->
                api
                    .playNowEnd(
                        sessionId,
                        secret,
                    ).onSuccess { applyNowPlayingPayload(it) }
                    .map { it.nowPlaying?.toDomain() }
            }

        override suspend fun reportPlaybackState(isPlaying: Boolean): AppResult<Unit> =
            withSession { sessionId, secret -> api.reportPlaybackState(sessionId, secret, isPlaying) }

        override suspend fun historyByDate(
            before: String?,
            limit: Int,
        ): AppResult<HistoryPage<HistoryPlay, String>> =
            withSession { sessionId, secret ->
                api.fetchHistoryByDate(sessionId, secret, before, limit).map { page ->
                    HistoryPage(page.items.map { it.toDomain() }, page.nextCursor, page.paused)
                }
            }

        override suspend fun mostPlayed(
            offset: Int,
            limit: Int,
        ): AppResult<HistoryPage<MostPlayedSong, Int>> =
            withSession { sessionId, secret ->
                api.fetchMostPlayed(sessionId, secret, offset, limit).map { page ->
                    HistoryPage(page.items.map { it.toDomain() }, page.nextOffset, page.paused)
                }
            }

        override suspend fun setHistoryPaused(paused: Boolean): AppResult<Boolean> =
            withSession { sessionId, secret -> api.setHistoryPaused(sessionId, secret, paused) }

        override suspend fun clearHistory(): AppResult<Unit> =
            withSession { sessionId, secret -> api.clearHistory(sessionId, secret) }

        private suspend inline fun <T> withSession(
            block: suspend (sessionId: String, secret: String) -> AppResult<T>,
        ): AppResult<T> {
            val sessionId = cachedSessionId
            val secret = cachedTvSecret
            if (sessionId == null || secret == null) {
                return AppResult.Failure(AppError.Unauthorized)
            }
            return block(sessionId, secret)
        }

        /** Wholesale REST re-fetch + replace -- see this class's own doc for why. */
        private suspend fun reconcile() {
            val sessionId = cachedSessionId ?: return
            val secret = cachedTvSecret ?: return
            when (val result = api.fetchQueue(sessionId, secret)) {
                is AppResult.Success -> queueSnapshotFlow.value = result.data.toDomain()
                is AppResult.Failure -> logger.log("Karaoke queue reconcile failed: ${result.error}")
            }
        }

        private fun applyNowPlayingPayload(payload: NowPlayingPayloadDto) {
            queueSnapshotFlow.value =
                queueSnapshotFlow.value.copy(
                    nowPlaying = payload.nowPlaying?.toDomain(),
                    playbackState = payload.playbackState ?: queueSnapshotFlow.value.playbackState,
                )
        }

        private fun startWebSocketLoopIfNeeded() {
            if (wsLoopStarted) return
            wsLoopStarted = true
            appScope.launch {
                var attempt = 0
                while (isActive) {
                    val sessionId = cachedSessionId
                    val secret = cachedTvSecret
                    if (sessionId != null && secret != null) {
                        runCatching {
                            webSocketClient.connectAndAwaitClose(
                                sessionId = sessionId,
                                tvSecret = secret,
                                // Reconcile on every (re)connect -- never assume, never create a new session.
                                onOpen = {
                                    attempt = 0
                                    reconcile()
                                },
                                onMessage = { text -> handleWsMessage(text) },
                            )
                        }.onFailure { logger.recordException(it) }
                    }
                    attempt++
                    val jitterMs = Random.nextLong(0, RECONNECT_JITTER_MAX_MS)
                    delay(backoffDelayFor(attempt).seconds + jitterMs.milliseconds)
                }
            }
        }

        private suspend fun handleWsMessage(text: String) {
            val obj = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return
            val data = obj["data"] as? JsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "QUEUE_ITEM_ADDED",
                "QUEUE_ITEM_REMOVED",
                "QUEUE_UPDATED",
                "NOW_PLAYING_CHANGED",
                "SESSION_UPDATED",
                -> reconcile()
                "PARTICIPANT_JOINED" -> data?.let(::emitParticipantJoined)
                "PARTICIPANT_LEFT" -> data?.let(::emitParticipantLeft)
                "PLAYBACK_COMMAND" -> data?.let(::emitRemoteCommand)
            }
        }

        private fun emitParticipantJoined(data: JsonObject) {
            eventsFlow.tryEmit(
                KaraokeEvent.ParticipantJoined(
                    participantId = data.stringField("participantId"),
                    displayName = data.stringField("displayName"),
                    participantCount = data.intField("participantCount"),
                ),
            )
        }

        private fun emitParticipantLeft(data: JsonObject) {
            eventsFlow.tryEmit(
                KaraokeEvent.ParticipantLeft(
                    participantId = data.stringField("participantId"),
                    participantCount = data.intField("participantCount"),
                ),
            )
        }

        private fun emitRemoteCommand(data: JsonObject) {
            val command = mapCommand(data.stringField("command")) ?: return
            eventsFlow.tryEmit(
                KaraokeEvent.RemoteCommand(
                    command = command,
                    requestedByDisplayName = data.stringField("requestedByDisplayName"),
                ),
            )
        }

        private fun mapCommand(raw: String): RemoteCommandType? =
            when (raw) {
                "PAUSE" -> RemoteCommandType.PAUSE
                "RESUME" -> RemoteCommandType.RESUME
                "SKIP" -> RemoteCommandType.SKIP
                else -> null
            }
    }

private fun JsonObject.stringField(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

private fun JsonObject.intField(key: String): Int = this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

private fun QueueSnapshotDto.toDomain() =
    KaraokeQueueSnapshot(
        playbackState = playbackState,
        nowPlaying = nowPlaying?.toDomain(),
        queue = queue.map { it.toDomain() },
    )

private fun NowPlayingDto.toDomain() =
    NowPlaying(
        source = if (source == "PLAY_NOW") NowPlayingSource.PLAY_NOW else NowPlayingSource.QUEUE,
        queueItemId = queueItemId,
        videoId = videoId,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        addedByDisplayName = addedByDisplayName,
    )

private fun QueueItemDto.toDomain() =
    QueueItem(
        id = id,
        position = position,
        videoId = videoId,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        addedByParticipantId = addedByParticipantId,
        addedByDisplayName = addedByDisplayName,
        addedAt = addedAt,
    )

private fun HistoryPlayDto.toDomain() =
    HistoryPlay(
        id = id,
        videoId = videoId,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        playedAt = Instant.parse(playedAt),
        nightStartedAt = Instant.parse(nightStartedAt),
    )

private fun MostPlayedSongDto.toDomain() =
    MostPlayedSong(
        videoId = videoId,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        playCount = playCount,
    )
