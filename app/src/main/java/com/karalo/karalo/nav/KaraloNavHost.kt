package com.karalo.karalo.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
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
    // Still true for a frame or two after BACK pops Player: the popped entry stays composed until
    // NavHost's (instant) exit transition completes, only then leaving visibleEntries.
    val visibleEntries by navController.visibleEntries.collectAsState()
    val isPlayerComposed = visibleEntries.any { it.destination.route == NavDestination.Player.route }
    // The nav rail is hidden during immersive playback so the player owns the whole screen. Hidden
    // immediately on entering Player, but only re-shown once Player has fully left composition, not
    // as soon as the back stack pops: showing it wraps `screens` in NavigationDrawer, re-parenting
    // and shrinking the still-attached PlayerView's SurfaceView. On API 34 only, Media3's PlayerView
    // answers that resize by holding the window's next frame in a SurfaceSyncGroup until it draws
    // again (its workaround for androidx/media#1237) -- which it never does, being released right
    // after -- so the whole window froze on the last video frame, drawer already showing, until
    // that group's 1000ms timeout. Confirmed on a Chromecast with Google TV (API 34); invisible on
    // the API 30 emulator, where that workaround is skipped.
    val showNavRail = !isPlayerActive && !isPlayerComposed

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
    // Keyed on showNavRail rather than isPlayerActive so the focus restore only runs once `screens`
    // has already moved into NavigationDrawer (see showNavRail above), not while it's still sitting
    // in its rail-less position, one move away from possibly losing that focus again.
    var previousShowNavRail by remember { mutableStateOf(showNavRail) }
    var homePlayerReturnTrigger by remember { mutableIntStateOf(0) }
    var searchPlayerReturnTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(showNavRail) {
        if (!previousShowNavRail && showNavRail) {
            when (activeDestination) {
                NavDestination.Home.route -> homePlayerReturnTrigger++
                NavDestination.Search.route -> searchPlayerReturnTrigger++
            }
        }
        previousShowNavRail = showNavRail
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

    // Snapshotted only at the instant the drawer actually opens, rather than read live inside
    // onHomeSelect -- confirmed on a real device that reading activeDestination live there is
    // unreliable for this: KaraloNavRailContent's own "focus preview" behavior (its own doc) flips
    // activeDestination to Home the moment the user merely *arrows over* Home's rail item while
    // browsing the open drawer (e.g. BACK opens it with Search focused, then UP moves focus onto
    // Home, previewing it, *before* RIGHT/Center ever explicitly selects it) -- so by the time
    // onHomeSelect runs, activeDestination can already read "home" even on a genuine switch away
    // from a different destination, one that was never actually showing before this drawer
    // session opened. Freezing it once, right as the drawer opens (before any such preview
    // browsing inside it can happen), is what actually distinguishes "Home was already the
    // foreground content when the user opened the drawer" from "the user just arrowed past it."
    var homeActiveWhenDrawerOpened by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            homeActiveWhenDrawerOpened = activeDestination == NavDestination.Home.route
        }
    }
    // Each destination's own rail item -- attached to it via KaraloNavRailContent -- so both BACK
    // from each screen's own content and LEFT from its first item/column can move focus straight
    // to the *correct* item deterministically, rather than relying on Compose's default
    // two-dimensional focus search (which, spatially, can land on a completely different item --
    // e.g. Settings, if it happens to sit closer to whichever shelf/row is currently focused).
    val homeRailFocusRequester = remember { FocusRequester() }
    val searchRailFocusRequester = remember { FocusRequester() }
    val settingsRailFocusRequester = remember { FocusRequester() }

    // Hoisted here (rather than left owned locally inside HomeScreenContent) so onHomeSelect below
    // can request focus on it directly and synchronously, in the same key-event-handling call
    // stack as the select itself -- see onHomeSelect's own doc for why that directness is exactly
    // what closes the real race this app hit with NavigationDrawer's own internal focus handling.
    val homeFirstVideoFocusRequester = remember { FocusRequester() }

    // Bumped by onHomeSelect below instead of homeContentFocusTrigger when Home was already the
    // active destination -- see its own doc for why that distinction is what makes re-selecting
    // Home "stay where you were" instead of always resetting to the first video. HomeScreenContent
    // reacts to a fresh value by restoring focus onto lastFocusedVideoId, the same
    // restoreFocusItemKey mechanism it already uses to restore onto a *played* video: native
    // Modifier.focusRestorer() alone was tried first and confirmed on a real device to be
    // unreliable for this (a direct requestFocus() onto an ancestor .focusRestorer() group landed
    // on the group/fallback rather than cascading down to the actual last-focused card).
    var homeReselectTrigger by remember { mutableIntStateOf(0) }

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
    //
    // Being remembered, it would also capture sessionJoinUrl's *first* value (null, until the
    // session resolves) for good -- read through rememberUpdatedState so Home sees later updates.
    val currentSessionJoinUrl by rememberUpdatedState(sessionJoinUrl)
    val screens =
        remember {
            movableContentOf {
                NavHost(
                    navController = navController,
                    startDestination = NavDestination.Main.route,
                    modifier = Modifier.fillMaxSize(),
                    // Entering/leaving Player must be instant, with no crossfade: the nav rail
                    // toggles visible the moment isPlayerActive flips (see showNavRail above), and
                    // any animated transition leaves a window where the rail is already showing
                    // while the outgoing screen (the still-playing video, on the way back out) is
                    // still on screen underneath it -- i.e. the rail appears to flash in front of
                    // the video instead of the video instantly giving way to it.
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
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
                            homeFirstVideoFocusRequester = homeFirstVideoFocusRequester,
                            homeReselectTrigger = homeReselectTrigger,
                            searchContentFocusTrigger = searchContentFocusTrigger,
                            searchPlayerReturnTrigger = searchPlayerReturnTrigger,
                            searchRailFocusRequester = searchRailFocusRequester,
                            settingsContentFocusTrigger = settingsContentFocusTrigger,
                            settingsRailFocusRequester = settingsRailFocusRequester,
                            sessionJoinUrl = currentSessionJoinUrl,
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
                        // Closed explicitly here (a genuine select should always collapse the rail
                        // right away) rather than waiting on the focus-driven effect above
                        // (drawerState.setValue(if (anyFocused) Open else Closed)) to notice focus
                        // has moved on -- see KaraloNavRailContent's own doc on markExplicitSelect
                        // for the real race this selection also has to guard against separately.
                        val wasHomeAlreadyActive = homeActiveWhenDrawerOpened
                        drawerState.setValue(DrawerValue.Closed)
                        activateTopLevel(NavDestination.Home.route)
                        if (wasHomeAlreadyActive) {
                            // Re-selecting Home while it's already the active destination is just
                            // closing the drawer BACK opened, not a genuine switch from elsewhere --
                            // per explicit product decision, this should land back wherever the user
                            // already was, not reset to the first video. See homeReselectTrigger's
                            // own doc for why this bumps a separate trigger (HomeScreenContent's own
                            // restoreFocusItemKey-based restore) rather than requesting focus here
                            // directly the way the other branch below does.
                            homeReselectTrigger++
                        } else {
                            homeContentFocusTrigger++
                            // Requested directly here, synchronously, in addition to (not instead
                            // of) the trigger bump above: that trigger's own effect, inside
                            // HomeScreenContent, is what actually handles Top Picks not having
                            // loaded yet (deferring via a placeholder, then retrying once it has)
                            // -- but *reaching* that effect takes a few hops (state flows up
                            // through this NavHost, back down through MainTabsHost, into
                            // HomeScreen), and confirmed on a real device, that gap is exactly
                            // where NavigationDrawer's own internal "nothing has focus, grab it for
                            // the group" logic (see DrawerSheet in the tv-material sources) would
                            // otherwise win the race and land focus on a sibling rail item instead.
                            // A direct call here, in the exact same call stack as the select,
                            // closes that gap the same way homeRailFocusRequester.requestFocus()
                            // (BACK's own single-hop request) never had it to begin with. Harmless
                            // if Top Picks isn't loaded yet (nothing attached to this
                            // FocusRequester, so this is a silent no-op) or if the trigger's own
                            // effect also runs moments later and requests it again (focusing an
                            // already-focused target is a no-op too).
                            homeFirstVideoFocusRequester.requestFocus()
                        }
                    },
                    onSearchSelect = {
                        drawerState.setValue(DrawerValue.Closed)
                        activateTopLevel(NavDestination.Search.route)
                        searchContentFocusTrigger++
                    },
                    onSettingsSelect = {
                        drawerState.setValue(DrawerValue.Closed)
                        activateTopLevel(NavDestination.Settings.route)
                        settingsContentFocusTrigger++
                    },
                )
            },
            content = screens,
        )
    } else {
        // Hidden (not removed, so nothing re-lays out) for the frame or two between BACK popping
        // Player and Player actually leaving composition (see showNavRail above): by then Home is
        // already drawn here but the rail isn't back yet, so on a real TV Home visibly flashed at
        // full width and then jumped sideways as the rail reappeared. Blank instead, it reads as
        // the video simply ending and Home (rail included) appearing in one step.
        val isLeavingPlayer = !isPlayerActive && isPlayerComposed
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (isLeavingPlayer) 0f else 1f }) {
            screens()
        }
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
