package com.karalo.karalo.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import com.karalo.feature.history.HistoryScreen
import com.karalo.feature.home.HomeScreen
import com.karalo.feature.search.presentation.SearchScreen

/**
 * Hosts the rail-switched top-level destinations (Home/Search/History/Settings) as permanent
 * siblings instead of routing them through separate NavHost destinations. NavHost only ever keeps
 * one destination's Composable subtree alive -- switching via a real navigate() call, as this app
 * used to for these three, fully disposes the screen being left and rebuilds the one being entered
 * (shelves, TvCarousel lazy rows, cards, Coil image prefetch, focus nodes) from scratch, which is
 * what made rapid DPAD rail scanning expensive on physical Google TV hardware even though the
 * underlying ViewModel data was already cached.
 *
 * Each tab mounts (at most) once, the first time it becomes [activeDestination], and is then never
 * removed from composition again for the rest of the app's lifetime -- only its own visibility
 * ([tabVisibility]) toggles. Player is unaffected: it keeps navigating through the real NavHost,
 * pushed on top of this composable's own "main" destination, and keeps its existing
 * dispose-on-leave behavior (releasing its ExoPlayer).
 */
@Composable
internal fun MainTabsHost(
    activeDestination: String,
    onHomeResultClick: (Int, String) -> Unit,
    onSearchResultClick: (Int, String) -> Unit,
    onHistoryResultClick: (Int, String) -> Unit,
    homeContentFocusTrigger: Int,
    homePlayerReturnTrigger: Int,
    homeRailFocusRequester: FocusRequester,
    homeFirstVideoFocusRequester: FocusRequester,
    homeReselectTrigger: Int,
    searchContentFocusTrigger: Int,
    searchPlayerReturnTrigger: Int,
    searchRailFocusRequester: FocusRequester,
    historyContentFocusTrigger: Int,
    historyPlayerReturnTrigger: Int,
    historyRailFocusRequester: FocusRequester,
    settingsContentFocusTrigger: Int,
    settingsRailFocusRequester: FocusRequester,
    sessionJoinUrl: String?,
    modifier: Modifier = Modifier,
) {
    var homeEverActive by rememberSaveable { mutableStateOf(activeDestination == NavDestination.Home.route) }
    var searchEverActive by rememberSaveable { mutableStateOf(activeDestination == NavDestination.Search.route) }
    var historyEverActive by rememberSaveable { mutableStateOf(activeDestination == NavDestination.History.route) }
    var settingsEverActive by rememberSaveable { mutableStateOf(activeDestination == NavDestination.Settings.route) }
    if (activeDestination == NavDestination.Home.route) homeEverActive = true
    if (activeDestination == NavDestination.Search.route) searchEverActive = true
    if (activeDestination == NavDestination.History.route) historyEverActive = true
    if (activeDestination == NavDestination.Settings.route) settingsEverActive = true

    // True only for Home's very first-ever composition (now a one-time event, since Home is
    // never disposed again once mounted -- see the class doc). Home uses this to decide whether
    // it's safe to unconditionally claim a neutral placeholder focus target while Top Picks is
    // still loading; claiming it on every mount would have stolen focus off the rail on every
    // rail focus-preview back when Home used to be disposed/remounted on every visit.
    var hasHomeEverMounted by rememberSaveable { mutableStateOf(false) }

    // Each tab's visibility modifier is created once and reads the current destination only at
    // draw/placement/focus time, so switching tabs (which the rail does on every focus-preview)
    // never hands the screens a new modifier -- previously a fresh one per switch made every
    // mounted screen recompose, the biggest cost in a Perfetto trace of rail scanning on the TV.
    val currentDestination by rememberUpdatedState(activeDestination)
    val homeVisibility = remember { Modifier.tabVisibility { currentDestination == NavDestination.Home.route } }
    val searchVisibility = remember { Modifier.tabVisibility { currentDestination == NavDestination.Search.route } }
    val historyVisibility = remember { Modifier.tabVisibility { currentDestination == NavDestination.History.route } }
    val settingsVisibility = remember { Modifier.tabVisibility { currentDestination == NavDestination.Settings.route } }

    Box(modifier = modifier.fillMaxSize()) {
        if (homeEverActive) {
            LaunchedEffect(Unit) { hasHomeEverMounted = true }
            HomeScreen(
                onResultClick = onHomeResultClick,
                firstVideoFocusTrigger = homeContentFocusTrigger,
                playerReturnTrigger = homePlayerReturnTrigger,
                claimInitialPlaceholderFocus = !hasHomeEverMounted,
                railFocusRequester = homeRailFocusRequester,
                firstVideoFocusRequester = homeFirstVideoFocusRequester,
                homeReselectTrigger = homeReselectTrigger,
                sessionJoinUrl = sessionJoinUrl,
                modifier = homeVisibility,
            )
        }
        if (searchEverActive) {
            SearchScreen(
                onResultClick = onSearchResultClick,
                contentFocusTrigger = searchContentFocusTrigger,
                playerReturnTrigger = searchPlayerReturnTrigger,
                railFocusRequester = searchRailFocusRequester,
                modifier = searchVisibility,
            )
        }
        if (historyEverActive) {
            val isHistoryActive = activeDestination == NavDestination.History.route
            HistoryScreen(
                onResultClick = onHistoryResultClick,
                isActive = isHistoryActive,
                contentFocusTrigger = historyContentFocusTrigger,
                playerReturnTrigger = historyPlayerReturnTrigger,
                railFocusRequester = historyRailFocusRequester,
                modifier = historyVisibility,
            )
        }
        if (settingsEverActive) {
            SettingsScreen(
                contentFocusTrigger = settingsContentFocusTrigger,
                railFocusRequester = settingsRailFocusRequester,
                modifier = settingsVisibility,
            )
        }
    }
}

/**
 * Hides an already-composed, already-loaded tab without disposing it: alpha 0 keeps it invisible
 * while its Compose subtree (and Coil's decoded images) stay resident, so switching back to it is
 * an instant visibility flip, not a rebuild. canFocus=false + onEnter cancelling the focus change
 * is what actually matters for correctness, not just alpha: without it, Compose's default
 * two-dimensional focus search can still find a focusable node sitting (invisibly) at the same
 * screen coordinates as the active tab's content, since alpha alone doesn't affect focusability.
 */
private fun Modifier.tabVisibility(isActive: () -> Boolean): Modifier =
    this
        // The active tab is placed on top of the others (zIndex at placement time).
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0, zIndex = if (isActive()) 1f else 0f)
            }
        }.graphicsLayer { alpha = if (isActive()) 1f else 0f }
        .focusProperties {
            canFocus = isActive()
            onEnter = { if (!isActive()) cancelFocusChange() }
        }
