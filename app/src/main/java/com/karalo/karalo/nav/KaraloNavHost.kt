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
    var settingsContentFocusTrigger by remember { mutableIntStateOf(0) }

    // Bumped specifically when the back stack pops from Player back to Home/Search (a genuine
    // system-BACK return), asking that destination to restore focus to whichever video was last
    // played there. This has to be a dedicated signal rather than reusing "no fresh select
    // pending" as a proxy for it: merely *focusing* a rail item to preview it also navigates (see
    // KaraloNavRailContent), producing a remount that looks identical to a real return from
    // Player from the content's own point of view -- without this, that mere preview would also
    // silently steal focus off the rail and onto whatever video was last played there.
    var previousRoute by remember { mutableStateOf<String?>(null) }
    var homePlayerReturnTrigger by remember { mutableIntStateOf(0) }
    var searchPlayerReturnTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentRoute) {
        if (previousRoute == NavDestination.Player.route) {
            when (currentRoute) {
                NavDestination.Home.route -> homePlayerReturnTrigger++
                NavDestination.Search.route -> searchPlayerReturnTrigger++
            }
        }
        previousRoute = currentRoute
    }

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
    val settingsRailFocusRequester = remember { FocusRequester() }

    // True only for Home's very first-ever composition (the app's initial launch). Home uses this
    // to decide whether it's safe to unconditionally claim a neutral placeholder focus target while
    // Top Picks is still loading -- doing that on every mount (including a mere rail focus-preview,
    // which also navigates here, see KaraloNavRailContent) would steal real focus off the rail item
    // the instant it's merely focused, not clicked.
    var isFirstEverHomeEntry by remember { mutableStateOf(true) }

    // Navigating to the route that's already current serves no purpose -- there's nowhere to
    // actually go -- but doing it anyway (e.g. every time a rail item is merely re-focused while
    // already on its destination) is exactly what triggers Navigation-Compose's
    // popUpTo(saveState=true)+restoreState=true dispose/restore cycle non-deterministically: this
    // is the root cause behind several hard-to-reproduce focus bugs (a rail item's own destination
    // content randomly losing/mishandling pending focus state right as the drawer opens/closes).
    // Skipping the call entirely when already there sidesteps that instability altogether, rather
    // than working around its symptoms.
    fun navigateToTopLevelIfNeeded(route: String) {
        if (currentRoute != route) {
            navController.navigateToTopLevel(route)
        }
    }

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
                        LaunchedEffect(Unit) { isFirstEverHomeEntry = false }
                        HomeScreen(
                            onResultClick = { startIndex, videoId ->
                                navController.navigate(NavDestination.Player.createRoute(startIndex, videoId))
                            },
                            firstVideoFocusTrigger = homeContentFocusTrigger,
                            playerReturnTrigger = homePlayerReturnTrigger,
                            claimInitialPlaceholderFocus = isFirstEverHomeEntry,
                            railFocusRequester = homeRailFocusRequester,
                        )
                    }
                    composable(NavDestination.Search.route) {
                        SearchScreen(
                            onResultClick = { startIndex, videoId ->
                                navController.navigate(NavDestination.Player.createRoute(startIndex, videoId))
                            },
                            contentFocusTrigger = searchContentFocusTrigger,
                            playerReturnTrigger = searchPlayerReturnTrigger,
                            railFocusRequester = searchRailFocusRequester,
                        )
                    }
                    composable(NavDestination.Settings.route) {
                        SettingsScreen(
                            contentFocusTrigger = settingsContentFocusTrigger,
                            railFocusRequester = settingsRailFocusRequester,
                        )
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
                    settingsFocusRequester = settingsRailFocusRequester,
                    onHomeClick = { navigateToTopLevelIfNeeded(NavDestination.Home.route) },
                    onSearchClick = { navigateToTopLevelIfNeeded(NavDestination.Search.route) },
                    onSettingsClick = { navigateToTopLevelIfNeeded(NavDestination.Settings.route) },
                    onHomeSelect = {
                        navigateToTopLevelIfNeeded(NavDestination.Home.route)
                        homeContentFocusTrigger++
                    },
                    onSearchSelect = {
                        navigateToTopLevelIfNeeded(NavDestination.Search.route)
                        searchContentFocusTrigger++
                    },
                    onSettingsSelect = {
                        navigateToTopLevelIfNeeded(NavDestination.Settings.route)
                        settingsContentFocusTrigger++
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
