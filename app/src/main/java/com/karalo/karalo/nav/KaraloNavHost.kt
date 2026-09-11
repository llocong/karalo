package com.karalo.karalo.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.rememberDrawerState
import com.karalo.feature.home.HomeScreen
import com.karalo.feature.player.presentation.PlayerScreen
import com.karalo.feature.search.presentation.SearchScreen

@Composable
fun KaraloNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The nav rail is hidden during immersive playback so the player owns the whole screen.
    val showNavRail = currentRoute != NavDestination.Player.route

    // Bumped whenever the corresponding nav item is *selected* (clicked), asking that
    // destination's own content to grab focus -- merely focusing the item (see
    // KaraloNavRailContent) only navigates/previews it, it doesn't steal focus off the rail.
    // Home starts at 1 (not 0): the app's initial launch is treated as an implicit first
    // "selection" of Home, since the menu starts collapsed with focus landing directly in Home's
    // content rather than on any rail item.
    var homeContentFocusTrigger by remember { mutableIntStateOf(1) }
    var searchContentFocusTrigger by remember { mutableIntStateOf(0) }
    // Without an explicit initial focus target, the system's own fallback focus-search lands on
    // the rail's first item instead (composed before the content, in NavigationDrawer's own Row) --
    // immediately (and wrongly) expanding the menu and navigating away from Home. True only for
    // Home's very first-ever composition (the real app launch): HomeScreen claims a neutral
    // placeholder focus target the instant it mounts, purely to keep focus off the rail until its
    // first video is ready to take over. Any later re-entry to Home (via the rail, focus or click)
    // goes through the ordinary firstVideoFocusTrigger path instead, so merely *focusing* Home to
    // preview it never steals focus away from the rail itself.
    var isFirstEverHomeEntry by remember { mutableStateOf(true) }

    // Hoisted above the show/hide branch below so both the rail (which drives it on focus) and
    // each destination's own content (which reads it to decide whether BACK should open the
    // drawer -- see HomeScreen/SearchScreen's own key handling) can see it.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // Each destination's own rail item -- attached to it via KaraloNavRailContent -- so both BACK
    // from each screen's own content and LEFT from its first item/column can move focus straight
    // to the *correct* item deterministically, rather than relying on Compose's default
    // two-dimensional focus search (which, spatially, can land on a completely different item --
    // e.g. Settings, if it happens to sit closer to whichever shelf/row is currently focused).
    val homeRailFocusRequester = remember { FocusRequester() }
    val searchRailFocusRequester = remember { FocusRequester() }

    // Wrapped in movableContentOf (rather than a plain lambda) because this same content is
    // called from two different structural positions below -- as NavigationDrawer's content slot
    // while the rail is shown, or directly when it's hidden for immersive playback. A plain lambda
    // invoked from two different call sites is, from Compose's point of view, two unrelated
    // subtrees: every navigation to or from the player would otherwise dispose and recreate the
    // whole NavHost (and hence Home/Search's own state, rememberSaveable included) from scratch.
    // movableContentOf instead relocates the existing composition node, preserving its state.
    val screens =
        remember {
            movableContentOf {
                NavHost(
                    navController = navController,
                    startDestination = NavDestination.Home.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(NavDestination.Home.route) {
                        HomeScreen(
                            onResultClick = { startIndex, videoId ->
                                navController.navigate(NavDestination.Player.createRoute(startIndex, videoId))
                            },
                            firstVideoFocusTrigger = homeContentFocusTrigger,
                            claimInitialPlaceholderFocus = isFirstEverHomeEntry,
                            railFocusRequester = homeRailFocusRequester,
                        )
                        if (isFirstEverHomeEntry) {
                            LaunchedEffect(Unit) { isFirstEverHomeEntry = false }
                        }
                    }
                    composable(NavDestination.Search.route) {
                        SearchScreen(
                            onResultClick = { startIndex, videoId ->
                                navController.navigate(NavDestination.Player.createRoute(startIndex, videoId))
                            },
                            contentFocusTrigger = searchContentFocusTrigger,
                            railFocusRequester = searchRailFocusRequester,
                        )
                    }
                    composable(NavDestination.Settings.route) {
                        SettingsScreen()
                    }
                    composable(
                        route = NavDestination.Player.route,
                        arguments =
                            listOf(
                                navArgument(PLAYER_ARG_START_INDEX) { type = NavType.IntType },
                                navArgument(PLAYER_ARG_START_VIDEO_ID) {
                                    type = NavType.StringType
                                    nullable = true
                                },
                            ),
                    ) {
                        PlayerScreen()
                    }
                }
            }
        }

    if (showNavRail) {
        NavigationDrawer(
            modifier = modifier.fillMaxSize(),
            drawerState = drawerState,
            drawerContent = {
                KaraloNavRailContent(
                    currentRoute = currentRoute,
                    drawerState = drawerState,
                    homeFocusRequester = homeRailFocusRequester,
                    searchFocusRequester = searchRailFocusRequester,
                    onHomeClick = { navController.navigateToTopLevel(NavDestination.Home.route) },
                    onSearchClick = { navController.navigateToTopLevel(NavDestination.Search.route) },
                    onSettingsClick = { navController.navigateToTopLevel(NavDestination.Settings.route) },
                    onHomeSelect = {
                        navController.navigateToTopLevel(NavDestination.Home.route)
                        homeContentFocusTrigger++
                    },
                    onSearchSelect = {
                        navController.navigateToTopLevel(NavDestination.Search.route)
                        searchContentFocusTrigger++
                    },
                )
            },
            content = screens,
        )
    } else {
        screens()
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
