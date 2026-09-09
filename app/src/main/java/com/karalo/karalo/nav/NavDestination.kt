package com.karalo.karalo.nav

const val PLAYER_ARG_START_INDEX = "startIndex"
const val PLAYER_ARG_START_VIDEO_ID = "startVideoId"

sealed class NavDestination(val route: String) {
    data object Home : NavDestination("home")
    data object Search : NavDestination("search")

    data object Player :
        NavDestination("player/{$PLAYER_ARG_START_INDEX}?$PLAYER_ARG_START_VIDEO_ID={$PLAYER_ARG_START_VIDEO_ID}") {
        fun createRoute(startIndex: Int, startVideoId: String): String =
            "player/$startIndex?$PLAYER_ARG_START_VIDEO_ID=$startVideoId"
    }
}
