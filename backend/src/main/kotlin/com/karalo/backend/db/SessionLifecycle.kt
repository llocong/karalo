package com.karalo.backend.db

import com.karalo.backend.db.tables.Participants
import com.karalo.backend.db.tables.QueueItems
import com.karalo.backend.db.tables.Sessions
import com.karalo.backend.db.tables.TvInstallations
import com.karalo.backend.domain.SESSION_ENDED
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.Instant

/**
 * How long the TV can go without a sign of life (its periodic session/ensure, or any other
 * TV-authenticated call) before its session is considered over. The TV sends one every
 * 5 minutes while the app is in the foreground, so this only trips when it's asleep, closed or
 * on another app.
 */
internal val TV_INACTIVITY_TIMEOUT: Duration = Duration.ofMinutes(30)

/**
 * How long the TV's live connection can stay down, with no other call from the TV, before its
 * session is considered over: closing Karalo or turning the TV off drops that connection, so
 * phones lose access within minutes instead of after [TV_INACTIVITY_TIMEOUT]. Long enough to ride
 * out a Wi-Fi blip, a backend restart or a quick app restart, which all reconnect in seconds.
 */
internal val TV_DISCONNECT_GRACE: Duration = Duration.ofMinutes(2)

/**
 * Told about every session that ends, so open phone sockets can be closed right away (see
 * AppDependencies). A plain hook rather than a constructor dependency because the end is
 * realized lazily from both repositories, inside whatever transaction happens to notice it.
 */
@Volatile
internal var onSessionEnded: (sessionId: String) -> Unit = {}

/**
 * Lazily ends [sessionId] if its TV has been quiet for [TV_INACTIVITY_TIMEOUT], or its live
 * connection has been gone for [TV_DISCONNECT_GRACE] with no TV call since: the pending queue
 * is dropped, now-playing is cleared and every guest becomes a SESSION_ENDED tombstone, so no old
 * phone can keep using it. Play history stays. Returns true if it ended the session just now.
 * Must be called inside a transaction.
 */
internal fun endSessionIfTvInactive(
    sessionId: String,
    now: Instant = Instant.now(),
): Boolean {
    val session = Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull() ?: return false
    if (session[Sessions.endedAt] != null) return false
    val tvLastSeen =
        TvInstallations
            .selectAll()
            .where { TvInstallations.id eq session[Sessions.tvInstallationId] }
            .single()[TvInstallations.lastSeenAt]
    val disconnectedAt = session[Sessions.tvDisconnectedAt]
    val quietTooLong = tvLastSeen.isBefore(now.minus(TV_INACTIVITY_TIMEOUT))
    val goneTooLong =
        disconnectedAt != null && disconnectedAt.isBefore(now.minus(TV_DISCONNECT_GRACE)) && !tvLastSeen.isAfter(disconnectedAt)
    if (!quietTooLong && !goneTooLong) return false

    QueueItems.deleteWhere { QueueItems.sessionId eq sessionId }
    Participants.update({ (Participants.sessionId eq sessionId) and Participants.removedAt.isNull() }) {
        it[removedAt] = now
        it[removedReason] = SESSION_ENDED
    }
    Sessions.update({ Sessions.id eq sessionId }) {
        it[nowPlayingSource] = null
        it[nowPlayingQueueItemId] = null
        it[playNowVideoId] = null
        it[playNowTitle] = null
        it[playNowChannelName] = null
        it[playNowThumbnailUrl] = null
        it[playNowDurationSeconds] = null
        it[playbackState] = "IDLE"
        it[endedAt] = now
        it[updatedAt] = now
    }
    onSessionEnded(sessionId)
    return true
}

/** Whether [sessionId] has ended and its TV hasn't come back yet. Must be called inside a transaction. */
internal fun isSessionEnded(sessionId: String): Boolean =
    Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull()?.get(Sessions.endedAt) != null

/**
 * Records a sign of life from the TV that owns [sessionId]: ends the session first if the TV had
 * been quiet too long (so yesterday's guests never carry over), starts it fresh if it had ended,
 * then bumps the TV's lastSeenAt. Returns true if the session was (re)started by this call.
 * Must be called inside a transaction, after the TV secret has been checked.
 */
internal fun touchTv(
    sessionId: String,
    tvId: String,
    now: Instant = Instant.now(),
): Boolean {
    endSessionIfTvInactive(sessionId, now)
    val restarted = isSessionEnded(sessionId)
    if (restarted) {
        Sessions.update({ Sessions.id eq sessionId }) {
            it[endedAt] = null
            it[updatedAt] = now
        }
    }
    TvInstallations.update({ TvInstallations.id eq tvId }) { it[lastSeenAt] = now }
    return restarted
}

/**
 * Records the TV's live connection opening ([connected] true) or closing, which
 * [endSessionIfTvInactive] reads. Must be called inside a transaction.
 */
internal fun setTvConnected(
    sessionId: String,
    connected: Boolean,
    now: Instant = Instant.now(),
) = Sessions.update({ Sessions.id eq sessionId }) { it[tvDisconnectedAt] = if (connected) null else now }
