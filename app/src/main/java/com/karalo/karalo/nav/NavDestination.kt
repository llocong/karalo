package com.karalo.karalo.nav

const val PLAYER_ARG_START_INDEX = "startIndex"
const val PLAYER_ARG_START_VIDEO_ID = "startVideoId"

sealed class NavDestination(
    val route: String,
) {
    /** The single NavHost destination hosting the Home/Search/History/Settings tab switcher (see
     * [com.karalo.karalo.nav.MainTabsHost]) -- those tabs don't have their own NavHost
     * routes, since switching between them via a real navigate() call is what forced a full
     * dispose+rebuild of whichever tab's shelves/carousels/images on every rail landing. */
    data object Main : NavDestination("main")

    data object Home : NavDestination("home")

    data object Search : NavDestination("search")

    data object History : NavDestination("history")

    data object Settings : NavDestination("settings")

    data object Player :
        NavDestination("player/{$PLAYER_ARG_START_INDEX}?$PLAYER_ARG_START_VIDEO_ID={$PLAYER_ARG_START_VIDEO_ID}") {
        fun createRoute(
            startIndex: Int,
            startVideoId: String,
        ): String = "player/$startIndex?$PLAYER_ARG_START_VIDEO_ID=$startVideoId"
    }
}
