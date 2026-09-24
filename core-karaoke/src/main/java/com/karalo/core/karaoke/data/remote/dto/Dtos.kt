package com.karalo.core.karaoke.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's exact JSON shapes (see `backend/.../domain/model/Models.kt`) field-for-field. */
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
data class NowPlayingPayloadDto(
    val nowPlaying: NowPlayingDto? = null,
    val playbackState: String? = null,
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
data class WsEnvelopeDto(
    val type: String,
    val sessionId: String,
    val ts: String,
    val data: kotlinx.serialization.json.JsonElement,
)

@Serializable
data class HistoryPlayDto(
    val id: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val playedAt: String,
    val nightStartedAt: String,
)

@Serializable
data class HistoryByDateDto(
    val items: List<HistoryPlayDto>,
    val nextCursor: String? = null,
    val paused: Boolean = false,
)

@Serializable
data class MostPlayedSongDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val playCount: Int,
    val lastPlayedAt: String,
)

@Serializable
data class MostPlayedDto(
    val items: List<MostPlayedSongDto>,
    val nextOffset: Int? = null,
    val paused: Boolean = false,
)

@Serializable
data class HistoryPausedDto(
    val paused: Boolean,
)
