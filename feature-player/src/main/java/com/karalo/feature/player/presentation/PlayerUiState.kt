package com.karalo.feature.player.presentation

import com.karalo.feature.player.domain.PlayableItem

data class PlayerUiState(
    val currentItem: PlayableItem? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val error: String? = null,
    /** Karaoke remote-control feature (see PlayerViewModel's own doc on onPlaybackEnded): true when
     *  the current video ended and the persistent remote queue had nothing next -- shows
     *  KaraokeWaitingScreen instead of a frozen/blank video frame. */
    val isWaitingForQueue: Boolean = false,
    /** The karaoke session's join URL, once known -- feeds the QR overlay/waiting-screen QR. Null
     *  until the app-launch session-ensure call resolves. */
    val sessionJoinUrl: String? = null,
)
