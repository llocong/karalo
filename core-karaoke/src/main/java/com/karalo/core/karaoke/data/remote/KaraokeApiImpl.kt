package com.karalo.core.karaoke.data.remote

import com.karalo.core.common.di.IoDispatcher
import com.karalo.core.common.error.AppError
import com.karalo.core.common.result.AppResult
import com.karalo.core.common.result.map
import com.karalo.core.karaoke.data.remote.dto.ErrorEnvelopeDto
import com.karalo.core.karaoke.data.remote.dto.HistoryByDateDto
import com.karalo.core.karaoke.data.remote.dto.HistoryPausedDto
import com.karalo.core.karaoke.data.remote.dto.MostPlayedDto
import com.karalo.core.karaoke.data.remote.dto.NowPlayingPayloadDto
import com.karalo.core.karaoke.data.remote.dto.QueueSnapshotDto
import com.karalo.core.karaoke.data.remote.dto.SessionEnsureResponseDto
import com.karalo.core.karaoke.data.remote.dto.ThemeDto
import com.karalo.core.karaoke.di.KaraokeHttpClient
import com.karalo.core.karaoke.domain.PlayNowSong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409

private const val TV_REGISTRATION_KEY_HEADER = "X-Karalo-Registration-Key"

@Suppress("TooManyFunctions") // one method per backend endpoint, plus the shared request plumbing
class KaraokeApiImpl
    internal constructor(
        private val client: OkHttpClient,
        private val json: Json,
        private val ioDispatcher: CoroutineDispatcher,
        private val restBaseUrl: String,
        private val tvRegistrationKey: String,
    ) : KaraokeApi {
        @Inject
        constructor(
            @KaraokeHttpClient client: OkHttpClient,
            json: Json,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ) : this(client, json, ioDispatcher, NetworkConfig.restBaseUrl, NetworkConfig.tvRegistrationKey)

        override suspend fun ensureSession(
            tvInstallationId: String,
            tvSecret: String?,
        ): AppResult<SessionEnsureResponseDto> =
            execute(
                path = "/api/tvs/$tvInstallationId/session/ensure",
                method = "POST",
                bearer = tvSecret,
                // Lets a new TV register with an internet-exposed backend (see the backend's
                // AppConfig.tvRegistrationKey); ignored once the TV is registered.
                headers = registrationHeaders(),
            )

        private fun registrationHeaders(): Map<String, String> =
            if (tvRegistrationKey.isEmpty()) emptyMap() else mapOf(TV_REGISTRATION_KEY_HEADER to tvRegistrationKey)

        override suspend fun fetchQueue(
            sessionId: String,
            tvSecret: String,
        ): AppResult<QueueSnapshotDto> =
            execute(
                path = "/api/sessions/$sessionId/queue",
                method = "GET",
                bearer = tvSecret,
            )

        override suspend fun consumeNext(
            sessionId: String,
            tvSecret: String,
        ): AppResult<NowPlayingPayloadDto> =
            execute(
                path = "/api/sessions/$sessionId/queue/consume-next",
                method = "POST",
                bearer = tvSecret,
                body = "{}",
            )

        override suspend fun playNowStart(
            sessionId: String,
            tvSecret: String,
            song: PlayNowSong,
        ): AppResult<NowPlayingPayloadDto> =
            execute(
                path = "/api/sessions/$sessionId/play-now/start",
                method = "POST",
                bearer = tvSecret,
                body =
                    json.encodeToString(
                        PlayNowStartBody.serializer(),
                        PlayNowStartBody(
                            song.videoId,
                            song.title,
                            song.channelName,
                            song.thumbnailUrl,
                            song.durationSeconds,
                        ),
                    ),
            )

        override suspend fun playNowEnd(
            sessionId: String,
            tvSecret: String,
        ): AppResult<NowPlayingPayloadDto> =
            execute(
                path = "/api/sessions/$sessionId/play-now/end",
                method = "POST",
                bearer = tvSecret,
                body = "{}",
            )

        override suspend fun reportPlaybackState(
            sessionId: String,
            tvSecret: String,
            isPlaying: Boolean,
        ): AppResult<Unit> =
            execute<AckDto>(
                path = "/api/sessions/$sessionId/playback-state",
                method = "POST",
                bearer = tvSecret,
                body =
                    json.encodeToString(
                        PlaybackStateBody.serializer(),
                        PlaybackStateBody(if (isPlaying) "PLAYING" else "PAUSED"),
                    ),
            ).map { }

        override suspend fun fetchHistoryByDate(
            sessionId: String,
            tvSecret: String,
            before: String?,
            limit: Int,
        ): AppResult<HistoryByDateDto> =
            execute(
                path = "/api/sessions/$sessionId/history",
                method = "GET",
                bearer = tvSecret,
                query = mapOf("sort" to "date", "before" to before, "limit" to limit.toString()),
            )

        override suspend fun fetchMostPlayed(
            sessionId: String,
            tvSecret: String,
            offset: Int,
            limit: Int,
        ): AppResult<MostPlayedDto> =
            execute(
                path = "/api/sessions/$sessionId/history",
                method = "GET",
                bearer = tvSecret,
                query = mapOf("sort" to "most_played", "offset" to offset.toString(), "limit" to limit.toString()),
            )

        override suspend fun setHistoryPaused(
            sessionId: String,
            tvSecret: String,
            paused: Boolean,
        ): AppResult<Boolean> =
            execute<HistoryPausedDto>(
                path = "/api/sessions/$sessionId/history/paused",
                method = "PUT",
                bearer = tvSecret,
                body = json.encodeToString(HistoryPausedDto.serializer(), HistoryPausedDto(paused)),
            ).map { it.paused }

        override suspend fun setTheme(
            sessionId: String,
            tvSecret: String,
            theme: String,
        ): AppResult<String> =
            execute<ThemeDto>(
                path = "/api/sessions/$sessionId/theme",
                method = "PUT",
                bearer = tvSecret,
                body = json.encodeToString(ThemeDto.serializer(), ThemeDto(theme)),
            ).map { it.theme }

        override suspend fun clearHistory(
            sessionId: String,
            tvSecret: String,
        ): AppResult<Unit> =
            execute(
                path = "/api/sessions/$sessionId/history",
                method = "DELETE",
                bearer = tvSecret,
            )

        private suspend inline fun <reified T> execute(
            path: String,
            method: String,
            bearer: String?,
            body: String? = null,
            headers: Map<String, String> = emptyMap(),
            query: Map<String, String?> = emptyMap(),
        ): AppResult<T> =
            withContext(ioDispatcher) {
                runCatching {
                    val request = buildRequest(path, method, bearer, body, headers, query)
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw ApiCallException(mapErrorCode(response.code, responseBody))
                        }
                        // A bodiless success (DELETE's 204) is only ever asked for as Unit.
                        if (T::class == Unit::class) Unit as T else json.decodeFromString<T>(responseBody)
                    }
                }.fold(
                    onSuccess = { AppResult.Success(it) },
                    onFailure = { throwable -> AppResult.Failure(toAppError(throwable)) },
                )
            }

        @Suppress("LongParameterList") // mirrors execute's own parameters
        private fun buildRequest(
            path: String,
            method: String,
            bearer: String?,
            body: String?,
            headers: Map<String, String>,
            query: Map<String, String?>,
        ): Request {
            val url =
                (restBaseUrl + path)
                    .toHttpUrl()
                    .newBuilder()
                    .apply { query.forEach { (name, value) -> value?.let { addQueryParameter(name, it) } } }
                    .build()
            val builder = Request.Builder().url(url)
            bearer?.let { builder.header("Authorization", "Bearer $it") }
            headers.forEach { (name, value) -> builder.header(name, value) }
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post((body ?: "{}").toRequestBody(JSON_MEDIA_TYPE))
                "PUT" -> builder.put((body ?: "{}").toRequestBody(JSON_MEDIA_TYPE))
                "DELETE" -> builder.delete()
                else -> error("Unsupported method $method")
            }
            return builder.build()
        }

        private fun mapErrorCode(
            httpCode: Int,
            body: String,
        ): AppError {
            val serverCode =
                runCatching { json.decodeFromString(ErrorEnvelopeDto.serializer(), body).error.code }.getOrNull()
            return when {
                httpCode == HTTP_UNAUTHORIZED || serverCode == "UNAUTHORIZED" -> AppError.Unauthorized
                httpCode == HTTP_NOT_FOUND || serverCode == "NOT_FOUND" -> AppError.NotFound
                httpCode == HTTP_CONFLICT || serverCode == "CONFLICT" -> AppError.Conflict(serverCode ?: "CONFLICT")
                else -> AppError.Network()
            }
        }

        private fun toAppError(throwable: Throwable): AppError =
            when (throwable) {
                is ApiCallException -> throwable.appError
                is IOException -> AppError.Network(throwable)
                is SerializationException -> AppError.Unknown(throwable)
                else -> AppError.Unknown(throwable)
            }

        private class ApiCallException(
            val appError: AppError,
        ) : Exception()
    }

@kotlinx.serialization.Serializable
private data class PlayNowStartBody(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
)

@kotlinx.serialization.Serializable
private data class PlaybackStateBody(
    val playbackState: String,
)

@kotlinx.serialization.Serializable
private data class AckDto(
    val ok: Boolean = true,
)
