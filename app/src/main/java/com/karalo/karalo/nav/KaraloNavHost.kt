package com.karalo.karalo.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.rememberDrawerState
import com.karalo.core.common.model.PlayableItemRef
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.karaoke.domain.KaraokeSessionHolder
import com.karalo.core.karaoke.domain.NowPlaying
import com.karalo.feature.player.presentation.PlayerScreen

@Composable
fun KaraloNavHost(
    karaokeSessionHolder: KaraokeSessionHolder,
    searchSessionHolder: SearchSessionHolder,
    sessionJoinUrl: String?,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isPlayerActive = backStackEntry?.destination?.route == NavDestination.Player.route
    // The nav rail is hidden during immersive playback so the player owns the whole screen.
    val showNavRail = !isPlayerActive

    // Which of Home/Search/Settings is actually shown -- flipped only after the rail's own
    // 150ms focus-settle debounce (see KaraloNavRailContent), never on every intermediate
    // focus move. Kept as plain local state (not a NavHost route) precisely so switching is a
    // cheap state write rather than a NavController.navigate() call -- see MainTabsHost's own
    // doc for why that distinction is the whole point of this refactor.
    var activeDestination by rememberSaveable { mutableStateOf(NavDestination.Home.route) }

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
    var previousIsPlayerActive by remember { mutableStateOf(isPlayerActive) }
    var homePlayerReturnTrigger by remember { mutableIntStateOf(0) }
    var searchPlayerReturnTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(isPlayerActive) {
        if (previousIsPlayerActive && !isPlayerActive) {
            when (activeDestination) {
                NavDestination.Home.route -> homePlayerReturnTrigger++
                NavDestination.Search.route -> searchPlayerReturnTrigger++
            }
        }
        previousIsPlayerActive = isPlayerActive
    }

    // Flow A (remote-first): a phone added the very first song, or the queue resumed after the
    // waiting screen, while nobody navigated to Player manually -- KaraokeSessionHolder emits once
    // per such transition (see its own doc). Reuses the exact existing SearchSessionHolder + nav
    // mechanism Home/Search already use for a manual click, just triggered from here instead --
    // confirmed by inspecting PlayerViewModel.buildInitialQueue: a 1-item list written into
    // SearchSessionHolder before navigating flows through it completely unchanged.
    LaunchedEffect(karaokeSessionHolder) {
        karaokeSessionHolder.autoStartRequests.collect { nowPlaying ->
            if (!isPlayerActive) {
                searchSessionHolder.setLastResults(listOf(nowPlaying.toPlayableItemRef()))
                navController.navigate(
                    NavDestination.Player.createRoute(startIndex = 0, startVideoId = nowPlaying.videoId),
                )
            }
        }
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

    // Activates a tab via a plain state write instead of a NavController.navigate() call -- see
    // MainTabsHost's own doc for why that's the entire point of this refactor. Setting it to a
    // value equal to its own current value (e.g. re-focusing a rail item already active) is a
    // no-op: Compose skips recomposition on an unchanged MutableState write.
    fun activateTopLevel(route: String) {
        activeDestination = route
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
                    startDestination = NavDestination.Main.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(NavDestination.Main.route) {
                        MainTabsHost(
                            activeDestination = activeDestination,
                            onHomeResultClick = { startIndex, videoId ->
                                navController.navigate(NavDestination.Player.createRoute(startIndex, videoId))
                            },
                            onSearchResultClick = { startIndex, videoId ->
                                navController.navigate(NavDestination.Player.createRoute(startIndex, videoId))
                            },
                            homeContentFocusTrigger = homeContentFocusTrigger,
                            homePlayerReturnTrigger = homePlayerReturnTrigger,
                            homeRailFocusRequester = homeRailFocusRequester,
                            searchContentFocusTrigger = searchContentFocusTrigger,
                            searchPlayerReturnTrigger = searchPlayerReturnTrigger,
                            searchRailFocusRequester = searchRailFocusRequester,
                            settingsContentFocusTrigger = settingsContentFocusTrigger,
                            settingsRailFocusRequester = settingsRailFocusRequester,
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
                    currentRoute = activeDestination,
                    drawerState = drawerState,
                    homeFocusRequester = homeRailFocusRequester,
                    searchFocusRequester = searchRailFocusRequester,
                    settingsFocusRequester = settingsRailFocusRequester,
                    onHomeClick = { activateTopLevel(NavDestination.Home.route) },
                    onSearchClick = { activateTopLevel(NavDestination.Search.route) },
                    onSettingsClick = { activateTopLevel(NavDestination.Settings.route) },
                    onHomeSelect = {
                        activateTopLevel(NavDestination.Home.route)
                        homeContentFocusTrigger++
                    },
                    onSearchSelect = {
                        activateTopLevel(NavDestination.Search.route)
                        searchContentFocusTrigger++
                    },
                    onSettingsSelect = {
                        activateTopLevel(NavDestination.Settings.route)
                        settingsContentFocusTrigger++
                    },
                    sessionJoinUrl = sessionJoinUrl,
                )
            },
            content = screens,
        )
    } else {
        screens()
    }
}

private const val MILLIS_PER_SECOND = 1000L

private fun NowPlaying.toPlayableItemRef() =
    PlayableItemRef(
        videoId = videoId,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationSeconds?.let { it.toLong() * MILLIS_PER_SECOND },
    )
