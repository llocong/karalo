package com.karalo.feature.playlists

import androidx.annotation.DrawableRes

/**
 * One of the Playlists page's fixed playlists. Its songs aren't a curated list: they're whatever
 * YouTube search returns for [query], the same way Home's Top Picks shelf and the phone remote's
 * playlists (backend web/search.js) are built -- "karaoke " followed by the playlist's theme.
 */
data class Playlist(
    val id: String,
    val name: String,
    val query: String,
    @DrawableRes val cover: Int,
)

/**
 * In the order the "Karalo TV Playlists" design lists them. Same covers in every theme. The phone
 * remote shows the same list (PLAYLISTS in backend web/search.js, covers in web/images/playlists)
 * -- keep the two in step.
 */
val PLAYLISTS: List<Playlist> =
    listOf(
        // Same query as Home's Top Picks shelf (HomeViewModel.TOP_PICKS_QUERY).
        Playlist("top-picks", "Top Picks", "karaoke", R.drawable.playlist_cover_mic),
        Playlist("duets", "Duets", "karaoke duets", R.drawable.playlist_cover_duets),
        Playlist("pop", "Pop", "karaoke pop", R.drawable.playlist_cover_disco),
        Playlist("rock", "Rock", "karaoke rock", R.drawable.playlist_cover_guitar),
        Playlist("rnb", "R&B", "karaoke r&b", R.drawable.playlist_cover_rnb),
        Playlist("latin", "Latin", "karaoke latin", R.drawable.playlist_cover_latin),
        Playlist("hip-hop", "Hip-Hop", "karaoke hip hop", R.drawable.playlist_cover_hiphop),
        Playlist("french-variety", "French Variety", "karaoke variété française", R.drawable.playlist_cover_french),
        Playlist("disney", "Disney", "karaoke disney", R.drawable.playlist_cover_disney),
        Playlist("90s", "90’s Hits", "karaoke 90s hits", R.drawable.playlist_cover_90s),
        Playlist("80s", "80’s Hits", "karaoke 80s hits", R.drawable.playlist_cover_80s),
        Playlist("70s", "70’s Hits", "karaoke 70s hits", R.drawable.playlist_cover_70s),
        Playlist("60s", "60’s Hits", "karaoke 60s hits", R.drawable.playlist_cover_60s),
        Playlist("vietnamese", "Vietnamese", "karaoke vietnamese", R.drawable.playlist_cover_vietnamese),
    )
