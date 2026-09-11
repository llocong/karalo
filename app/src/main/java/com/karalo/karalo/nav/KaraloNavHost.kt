package com.karalo.karalo.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    val screens: @Composable () -> Unit = {
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

    if (showNavRail) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        NavigationDrawer(
            modifier = modifier.fillMaxSize(),
            drawerState = drawerState,
            drawerContent = {
                KaraloNavRailContent(
                    currentRoute = currentRoute,
                    drawerState = drawerState,
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
