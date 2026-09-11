package com.karalo.karalo.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                )
            }
            composable(NavDestination.Search.route) {
                SearchScreen(
                    onResultClick = { startIndex, videoId ->
                        navController.navigate(NavDestination.Player.createRoute(startIndex, videoId))
                    },
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
