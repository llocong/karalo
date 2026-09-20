package com.karalo.core.karaoke.domain

/** The TV's one persistent karaoke session, as returned by `session/ensure`. */
data class KaraokeSession(
    val sessionId: String,
    val sessionCode: String,
    val joinUrl: String,
)

enum class RemoteCommandType { PAUSE, RESUME, SKIP }

/** One-shot realtime signals a [KaraokeRepository] consumer reacts to (not the queue state itself). */
sealed interface KaraokeEvent {
    data class ParticipantJoined(
        val participantId: String,
        val displayName: String,
        val participantCount: Int,
    ) : KaraokeEvent

    data class ParticipantLeft(
        val participantId: String,
        val participantCount: Int,
    ) : KaraokeEvent

    data class RemoteCommand(
        val command: RemoteCommandType,
        val requestedByDisplayName: String,
    ) : KaraokeEvent
}
