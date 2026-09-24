package com.karalo.backend.domain.model

import kotlinx.serialization.Serializable

enum class PlaybackState { PLAYING, PAUSED, IDLE }

enum class NowPlayingSource { QUEUE, PLAY_NOW }

enum class QueueItemStatus { PENDING, PLAYED, SKIPPED, REMOVED }

@Serializable
data class QueueItemDto(
    val id: String,
    val position: Long,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val addedByParticipantId: String,
    val addedByDisplayName: String,
    val addedAt: String,
)

@Serializable
data class NowPlayingDto(
    val source: String,
    val queueItemId: String?,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val addedByDisplayName: String?,
)

@Serializable
data class QueueSnapshotDto(
    val playbackState: String,
    val nowPlaying: NowPlayingDto?,
    val queue: List<QueueItemDto>,
)

@Serializable
data class SessionSummaryDto(
    val id: String,
    val code: String,
    val joinUrl: String,
    val playbackState: String,
    val nowPlaying: NowPlayingDto?,
    val participantCount: Int,
    val queueLength: Int,
)

@Serializable
data class SessionEnsureResponseDto(
    val tvSecret: String? = null,
    val session: SessionSummaryDto,
)

@Serializable
data class PublicSessionDto(
    val sessionId: String,
    val code: String,
    val participantCount: Int,
)

@Serializable
data class ParticipantJoinRequestDto(
    val displayName: String,
)

@Serializable
data class ParticipantJoinResponseDto(
    val participantId: String,
    val participantToken: String,
    val sessionId: String,
    val displayName: String,
)

@Serializable
data class MeDto(
    val participantId: String,
    val displayName: String,
    val sessionId: String,
)

@Serializable
data class AddQueueItemRequestDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
)

@Serializable
data class ReorderRequestDto(
    val orderedQueueItemIds: List<String>,
)

@Serializable
data class ConsumeNextRequestDto(
    val reason: String = "COMPLETED",
)

@Serializable
data class PlayNowStartRequestDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
)

@Serializable
data class PlaybackStateRequestDto(
    val playbackState: String,
)

@Serializable
data class SearchResultDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
)

@Serializable
data class SearchResponseDto(
    val results: List<SearchResultDto>,
)

@Serializable
data class CommandAckDto(
    val commandId: String,
    val delivered: Boolean,
)

@Serializable
data class ErrorEnvelopeDto(
    val error: ErrorBodyDto,
)

@Serializable
data class ErrorBodyDto(
    val code: String,
    val message: String,
)

@Serializable
data class HistoryPlayDto(
    val id: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val playedAt: String,
    val nightStartedAt: String,
)

@Serializable
data class HistoryByDateDto(
    val items: List<HistoryPlayDto>,
    val nextCursor: String?,
    val paused: Boolean,
)

@Serializable
data class MostPlayedSongDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val playCount: Int,
    val lastPlayedAt: String,
)

@Serializable
data class MostPlayedDto(
    val items: List<MostPlayedSongDto>,
    val nextOffset: Int?,
    val paused: Boolean,
)

@Serializable
data class HistoryPausedDto(
    val paused: Boolean,
)
