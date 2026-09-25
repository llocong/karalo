package com.karalo.karalo.nav

import android.app.Activity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.karalo.core.ui.components.KaraloLogoLockup
import com.karalo.core.ui.components.KaraloLogoMark
import com.karalo.core.ui.icons.KaraloIcons
import com.karalo.core.ui.theme.KaraloRailItemActive
import com.karalo.core.ui.theme.KaraloTextSecondary
import com.karalo.core.ui.theme.LocalKaraloTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

const val NAV_TAG_HOME = "nav_home"
const val NAV_TAG_SEARCH = "nav_search"
const val NAV_TAG_HISTORY = "nav_history"
const val NAV_TAG_SETTINGS = "nav_settings"

// How long focus has to stay on one rail item before its "focus to preview" navigation actually
// fires -- see the LaunchedEffects below for why this matters.
private const val FOCUS_PREVIEW_DEBOUNCE_MS = 150L

// How long after an explicit select (RIGHT/Center on a rail item) a *different* item's own
// focus-to-preview debounce is refused -- see markExplicitSelect's own doc for the real-device bug
// this guards against. Comfortably longer than FOCUS_PREVIEW_DEBOUNCE_MS itself so it always covers
// that whole window, not just close to it.
private const val EXPLICIT_SELECT_GRACE_MS = 500L

// How long after an explicit select the *immediate* stray-focus correction below is armed -- much
// shorter than EXPLICIT_SELECT_GRACE_MS on purpose: the stray focus this corrects is a same-frame-
// ish side effect of the select itself (observed on a real device landing within ~20ms), whereas a
// person's own deliberate arrow-key press takes real reaction time to happen -- keeping this window
// short means a genuine "select Home, then actually arrow up to Search" within the same second still
// works, rather than being silently snapped back.
private const val STRAY_FOCUS_CORRECTION_WINDOW_MS = 120L

// Gap below the logo header, per the "Karalo Themes" design (4cqw on a 16:9 screen).
private val HEADER_TO_ITEMS_SPACING = 38.dp

// Bigger than a regular nav icon -- this is the brand mark, not just another rail item.
private val LOGO_SIZE = 35.dp

// The rail's share of the screen width, expanded (icons + labels) and collapsed (icons only), per
// the design. Content tiles keep their own fixed size either way -- collapsing just reveals more.
private const val RAIL_EXPANDED_SCREEN_FRACTION = 0.22f
private const val RAIL_COLLAPSED_SCREEN_FRACTION = 0.09f
private val RAIL_HORIZONTAL_PADDING = 16.dp
private val RAIL_TOP_PADDING = 35.dp

private val ITEM_HEIGHT = 46.dp
private val ITEM_SPACING = 12.dp
private val ITEM_ICON_SIZE = 22.dp
private val ITEM_LABEL_SPACING = 13.dp
private val ITEM_SHAPE = RoundedCornerShape(10.dp)
private val ITEM_LABEL_FONT_SIZE = 16.sp

/**
 * The rail item widths (the rail itself is these plus [RAIL_HORIZONTAL_PADDING] on each side), and
 * the icon inset that keeps each icon centered in the collapsed width -- so it stays put while the
 * rail expands around it.
 */
private class RailMetrics(
    screenWidth: Dp,
) {
    val collapsedWidth = screenWidth * RAIL_COLLAPSED_SCREEN_FRACTION - RAIL_HORIZONTAL_PADDING * 2
    val expandedWidth = screenWidth * RAIL_EXPANDED_SCREEN_FRACTION - RAIL_HORIZONTAL_PADDING * 2
    val itemHorizontalInset = (collapsedWidth - ITEM_ICON_SIZE) / 2

    // Centers the (larger) logo on the same vertical line as the nav icons below it.
    val headerHorizontalInset = itemHorizontalInset + (ITEM_ICON_SIZE - LOGO_SIZE) / 2

    /** How far along the collapsed-to-expanded width range [width] currently is, from 0f to 1f. */
    fun revealFractionOf(width: Dp): Float =
        ((width - collapsedWidth) / (expandedWidth - collapsedWidth)).coerceIn(0f, 1f)
}

/**
 * The drawer's contents: a logo header, the primary destinations, and a settings action pinned to
 * the bottom -- standard TV Material navigation-drawer pattern (developer.android.com/design/ui/tv/
 * guides/components/navigation-drawer) -- collapsed to icons-only until an item gains focus, then
 * it animates open to show icon + label for every item.
 *
 * The menu starts collapsed with no item focused: initial D-pad focus goes to the Home screen's
 * own content instead (see [com.karalo.feature.home.HomeScreen]), matching a common TV "browse in
 * content, dip into the rail only when needed" pattern. Merely *focusing* an item here (e.g. while
 * arrowing through the rail) immediately navigates to and previews that destination via
 * [onHomeClick]/[onSearchClick]/[onSettingsClick] -- *clicking* (selecting) an item instead moves
 * focus on into that destination's own content, via [onHomeSelect]/[onSearchSelect]. Pressing
 * RIGHT while an item is focused (i.e. the drawer is open) behaves the same as clicking it -- see
 * [KaraloNavItem]'s own key handling -- since RIGHT is the natural "go into the content" direction
 * once the drawer has expanded to reveal it there.
 *
 * [homeFocusRequester]/[searchFocusRequester]/[settingsFocusRequester] are attached to their
 * respective items here so each destination's own content (BACK, or LEFT from Home/Search's first
 * item/column) can move focus straight onto the *correct* rail item deterministically, rather than
 * relying on Compose's default two-dimensional focus search -- which, spatially, can land on a
 * different item than intended (e.g. Settings, if it happens to sit closer to whichever shelf/row
 * currently has focus).
 *
 * Items are hand-rolled on top of [Surface] rather than using the library's own
 * [androidx.tv.material3.NavigationDrawerItem]: that component's width animation and its label's
 * fade animation are two independent, un-synchronizable animations (no parameter exposes either
 * one), and in practice the label finishes fading out well before the width tween catches up --
 * leaving a wide, empty pill that then visibly snaps to its final (icon-only) width. Deriving both
 * the width *and* the label's opacity from the same single animated value here guarantees they can
 * never drift apart.
 *
 * [drawerState] is driven explicitly from each item's own `interactionSource` here rather than
 * left to [NavigationDrawer]'s built-in focus detection: in practice (tv-material 1.1.0) the
 * drawer's own [NavigationDrawerScope.hasFocus] never flips to true even though individual items'
 * focused styling updates correctly, so the drawer never auto-expands. Tracking per-item focus via
 * `collectIsFocusedAsState()` is unaffected by that and reliably opens/closes the drawer.
 *
 * Exempt from detekt's CyclomaticComplexMethod: most of this function's branching is the
 * stray-focus guards and per-item focus-preview effects below, each one a fix for focus behavior
 * only reproduced on real TV hardware. Splitting them out just to satisfy the complexity threshold
 * isn't worth the risk of regressing one of those without re-verifying every case on a device.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun NavigationDrawerScope.KaraloNavRailContent(
    currentRoute: String?,
    drawerState: DrawerState,
    homeFocusRequester: FocusRequester,
    searchFocusRequester: FocusRequester,
    historyFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHomeSelect: () -> Unit,
    onSearchSelect: () -> Unit,
    onHistorySelect: () -> Unit,
    onSettingsSelect: () -> Unit,
) {
    val searchInteractionSource = remember { MutableInteractionSource() }
    val homeInteractionSource = remember { MutableInteractionSource() }
    val historyInteractionSource = remember { MutableInteractionSource() }
    val settingsInteractionSource = remember { MutableInteractionSource() }
    // Per-item focus is only read in effects (and by each item itself), never directly here: this
    // whole rail used to recompose on every focus move from one item to the next. Only
    // "any item focused" -- which changes when entering/leaving the rail -- is read in composition.
    val searchFocused = searchInteractionSource.collectIsFocusedAsState()
    val homeFocused = homeInteractionSource.collectIsFocusedAsState()
    val historyFocused = historyInteractionSource.collectIsFocusedAsState()
    val settingsFocused = settingsInteractionSource.collectIsFocusedAsState()

    val anyFocused by remember {
        derivedStateOf { searchFocused.value || homeFocused.value || historyFocused.value || settingsFocused.value }
    }
    LaunchedEffect(anyFocused) {
        drawerState.setValue(if (anyFocused) DrawerValue.Open else DrawerValue.Closed)
    }

    // Guards the focus-preview debounce below against a real, reproduced-on-device bug: selecting
    // an item (RIGHT/Center, i.e. onXSelect) can leave the rail itself with focus briefly (and
    // persistently, not just a one-frame flicker) landing on a *different* rail item afterwards --
    // most likely NavigationDrawer's own focus handling as it hands focus off to `content`, though
    // the exact mechanism inside that library component isn't visible from here. Left unguarded,
    // that stray focus starts *this* item's own preview debounce below, which then fires and calls
    // activateTopLevel on the wrong destination -- by the time the real selection's own
    // (inherently slower, multi-hop: NavHost -> MainTabsHost -> the screen's own trigger effect)
    // request to focus its content finally runs, the destination has already flipped away from
    // under it and that screen's tab is no longer active, so the request silently does nothing.
    // Recording *which* destination was just explicitly selected, and refusing to let a preview
    // fire for any other destination within a short window afterwards, closes that race without
    // depending on precisely why the stray focus happens.
    var recentExplicitSelectRoute by remember { mutableStateOf<String?>(null) }
    var recentExplicitSelectAtMs by remember { mutableLongStateOf(0L) }

    fun markExplicitSelect(route: String) {
        recentExplicitSelectRoute = route
        recentExplicitSelectAtMs = System.currentTimeMillis()
    }

    fun isRecentExplicitSelectElsewhere(route: String): Boolean {
        val selected = recentExplicitSelectRoute ?: return false
        if (selected == route) return false
        return System.currentTimeMillis() - recentExplicitSelectAtMs < EXPLICIT_SELECT_GRACE_MS
    }

    fun focusRequesterFor(route: String): FocusRequester? =
        when (route) {
            NavDestination.Home.route -> homeFocusRequester
            NavDestination.Search.route -> searchFocusRequester
            NavDestination.History.route -> historyFocusRequester
            NavDestination.Settings.route -> settingsFocusRequester
            else -> null
        }

    // Reacts to the stray focus itself, immediately, rather than only guarding its downstream
    // consequence below: left to just that guard, the stray item still visibly renders its own
    // focused style for however long it takes this item's own FOCUS_PREVIEW_DEBOUNCE_MS timer to
    // fire and get refused -- a real, visible flicker onto the wrong rail item, confirmed on a real
    // device, even though the wrong destination itself never ends up active. Sending focus straight
    // back to the item that was actually just selected, the moment the stray item is seen to gain
    // it, closes that visible gap almost entirely instead of just waiting it out.
    fun correctStrayFocus(strayRoute: String) {
        val selected = recentExplicitSelectRoute ?: return
        if (selected == strayRoute) return
        if (System.currentTimeMillis() - recentExplicitSelectAtMs >= STRAY_FOCUS_CORRECTION_WINDOW_MS) return
        focusRequesterFor(selected)?.requestFocus()
    }

    val trackedOnHomeSelect: () -> Unit = {
        markExplicitSelect(NavDestination.Home.route)
        onHomeSelect()
    }
    val trackedOnSearchSelect: () -> Unit = {
        markExplicitSelect(NavDestination.Search.route)
        onSearchSelect()
    }
    val trackedOnHistorySelect: () -> Unit = {
        markExplicitSelect(NavDestination.History.route)
        onHistorySelect()
    }
    val trackedOnSettingsSelect: () -> Unit = {
        markExplicitSelect(NavDestination.Settings.route)
        onSettingsSelect()
    }

    // Focusing an item -- without clicking it -- navigates to and previews that destination,
    // matching a common TV "focus to preview" pattern. Debounced by a short delay rather than
    // firing the instant focus lands: previewing a destination means a real NavHost navigate()
    // call, and NavHost only ever keeps one destination's composable subtree alive -- so it fully
    // disposes the screen being left and rebuilds the one being entered (shelves, lazy rows, cards,
    // images, focus nodes) from scratch. Scanning across several rail items quickly (holding
    // DOWN/UP, or just arrowing through in succession) would otherwise pay that full rebuild cost
    // once per item passed over, not just once for wherever the user actually stops -- each item's
    // FocusPreviewEffect cancels its still-pending delay the instant focus moves off that item
    // again, meaning only the item the user actually settles on for a moment ever triggers the
    // expensive navigation.
    val currentOnSearchClick by rememberUpdatedState(onSearchClick)
    val currentOnHomeClick by rememberUpdatedState(onHomeClick)
    val currentOnHistoryClick by rememberUpdatedState(onHistoryClick)
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    FocusPreviewEffect(
        searchFocused,
        NavDestination.Search.route,
        ::correctStrayFocus,
        ::isRecentExplicitSelectElsewhere,
    ) {
        currentOnSearchClick()
    }
    FocusPreviewEffect(homeFocused, NavDestination.Home.route, ::correctStrayFocus, ::isRecentExplicitSelectElsewhere) {
        currentOnHomeClick()
    }
    FocusPreviewEffect(
        historyFocused,
        NavDestination.History.route,
        ::correctStrayFocus,
        ::isRecentExplicitSelectElsewhere,
    ) {
        currentOnHistoryClick()
    }
    FocusPreviewEffect(
        settingsFocused,
        NavDestination.Settings.route,
        ::correctStrayFocus,
        ::isRecentExplicitSelectElsewhere,
    ) {
        currentOnSettingsClick()
    }

    val activity = LocalContext.current as? Activity

    // Hoisted here and passed down, rather than each item (and the header) independently calling
    // animateDpAsState(targetValue = if (hasFocus) ...) itself: hasFocus flips for all of them at
    // once (it's "is any rail item focused", not per-item), so four independent Animatable-backed
    // tweens were previously restarting in lockstep on every focus move onto/off the rail -- four
    // times the animation bookkeeping and four separate graphicsLayer recompositions per frame for
    // what is, visually, one single collapse/expand. A single shared value read by all four keeps
    // the exact same look for a quarter of the per-frame cost.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val metrics = remember(screenWidth) { RailMetrics(screenWidth) }
    // Read only from layout/draw lambdas (never during composition), so the collapse/expand tween
    // re-lays out and redraws the rail each frame without recomposing it -- confirmed in a Perfetto
    // trace on the reference TV, where per-frame recomposition of all four items was a major part
    // of the rail's frame time.
    val widthState =
        animateDpAsState(
            targetValue = if (hasFocus) metrics.expandedWidth else metrics.collapsedWidth,
            label = "navRailWidth",
        )
    val width = remember(widthState) { { widthState.value } }
    val revealFraction = remember(widthState, metrics) { { metrics.revealFractionOf(widthState.value) } }

    // While the rail has focus, only the focused item looks active. The current destination only
    // follows focus after FOCUS_PREVIEW_DEBOUNCE_MS (and a frame or two for the switch itself), so
    // styling it as "selected" too showed the item just left still highlighted next to the new one.
    fun isShownSelected(route: String) = currentRoute == route && !anyFocused

    Column(
        modifier =
            Modifier
                .background(LocalKaraloTokens.current.sidebarBackground)
                .fillMaxHeight()
                .padding(horizontal = RAIL_HORIZONTAL_PADDING)
                .padding(top = RAIL_TOP_PADDING)
                .selectableGroup()
                // BACK while any rail item is focused (i.e. the drawer is open) exits the app --
                // consumed here, on an ancestor of every item, rather than left to fall through to
                // the platform default: NavHost installs its own internal back handling that would
                // otherwise intercept it first and silently no-op (there's nothing to actually pop
                // from Home or Search's own top-level back stack), leaving BACK looking like it
                // does nothing at all instead of exiting.
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                        activity?.finish()
                        true
                    } else {
                        false
                    }
                },
    ) {
        KaraloNavHeader(width = width, revealFraction = revealFraction, horizontalInset = metrics.headerHorizontalInset)
        Spacer(modifier = Modifier.height(HEADER_TO_ITEMS_SPACING))

        KaraloNavItem(
            selected = isShownSelected(NavDestination.Search.route),
            onClick = trackedOnSearchSelect,
            icon = KaraloIcons.Search,
            label = "Search",
            interactionSource = searchInteractionSource,
            width = width,
            revealFraction = revealFraction,
            iconInset = metrics.itemHorizontalInset,
            modifier =
                Modifier
                    .testTag(NAV_TAG_SEARCH)
                    .focusRequester(searchFocusRequester),
            blockDirectionUp = true,
        )

        KaraloNavItem(
            selected = isShownSelected(NavDestination.Home.route),
            onClick = trackedOnHomeSelect,
            icon = KaraloIcons.Home,
            label = "Home",
            interactionSource = homeInteractionSource,
            width = width,
            revealFraction = revealFraction,
            iconInset = metrics.itemHorizontalInset,
            modifier =
                Modifier
                    .testTag(NAV_TAG_HOME)
                    .focusRequester(homeFocusRequester)
                    .padding(top = ITEM_SPACING),
        )

        KaraloNavItem(
            selected = isShownSelected(NavDestination.History.route),
            onClick = trackedOnHistorySelect,
            icon = KaraloIcons.History,
            label = "History",
            interactionSource = historyInteractionSource,
            width = width,
            revealFraction = revealFraction,
            iconInset = metrics.itemHorizontalInset,
            modifier =
                Modifier
                    .testTag(NAV_TAG_HISTORY)
                    .focusRequester(historyFocusRequester)
                    .padding(top = ITEM_SPACING),
        )

        KaraloNavItem(
            selected = isShownSelected(NavDestination.Settings.route),
            onClick = trackedOnSettingsSelect,
            icon = KaraloIcons.Settings,
            label = "Settings",
            interactionSource = settingsInteractionSource,
            width = width,
            revealFraction = revealFraction,
            iconInset = metrics.itemHorizontalInset,
            modifier =
                Modifier
                    .testTag(NAV_TAG_SETTINGS)
                    .focusRequester(settingsFocusRequester)
                    .padding(top = ITEM_SPACING),
        )
    }
}

@Composable
private fun NavigationDrawerScope.KaraloNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    interactionSource: MutableInteractionSource,
    width: () -> Dp,
    revealFraction: () -> Float,
    iconInset: Dp,
    modifier: Modifier = Modifier,
    blockDirectionUp: Boolean = false,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    Surface(
        selected = selected,
        onClick = onClick,
        modifier =
            modifier
                .animatedWidth(width)
                .height(ITEM_HEIGHT)
                // While the drawer is open, RIGHT dives into the destination's content just like
                // pressing the OK/Center button does -- a focused rail item is, by construction,
                // only reachable while the drawer is open (see the drawerState effect above), so
                // no extra "is the drawer open" check is needed here.
                //
                // Center/Enter is handled explicitly here too, exactly like RIGHT, rather than
                // left to Surface's own default click dispatch for those keys: confirmed on a real
                // device that leaving it to Surface's own path can select the *wrong* destination
                // (landing back on Search instead of Home) -- Surface's own click dispatch fires
                // later, in the bubble-up phase rather than this preview phase, which apparently
                // lands `onClick()` in a different recomposition window than RIGHT's immediate,
                // synchronous call does, racing this rail's own focus-preview debounce (see the
                // LaunchedEffects above). Calling `onClick()` directly and consuming the event here
                // -- identically to RIGHT -- sidesteps that race entirely rather than chasing its
                // exact timing.
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else if (keyEvent.key == Key.DirectionRight ||
                        keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter
                    ) {
                        onClick()
                        true
                    } else {
                        // The topmost item (Search) has nothing above it within the rail's own
                        // Column; left unconsumed, Compose's default focus search doesn't respect
                        // that boundary and can leak UP into the underlying content instead (e.g.
                        // the search query field, which sits near the top of the content pane) --
                        // consuming it here keeps UP a no-op once you're already at the top of the
                        // rail, matching how the rail's own bottom (Settings) already has nothing
                        // below it to leak into.
                        blockDirectionUp && keyEvent.key == Key.DirectionUp
                    }
                },
        shape = SelectableSurfaceDefaults.shape(shape = ITEM_SHAPE),
        colors = karaloNavItemColors(),
        // Surface's default 1.1x focused-scale pivots around the item's own center, which sits at
        // a different absolute x at 56dp vs 256dp wide -- combined with the width tween, that
        // visibly shifted the icon sideways while focused. Focus is already communicated by the
        // width growth and color change, so scale would be redundant on top of that anyway.
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        interactionSource = interactionSource,
    ) {
        Row(
            // Pinned explicitly to the box's start rather than relying on the Row filling the
            // surface and packing its own content to the start -- Surface's content box centers
            // its child by default, which was silently shifting the icon right by several dp as
            // the surface widened (the icon "moving" during collapse/expand).
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = iconInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The active item's icon takes the theme accent; its label just turns white (see
            // karaloNavItemColors).
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused || selected) LocalKaraloTokens.current.accent else LocalContentColor.current,
                modifier = Modifier.size(ITEM_ICON_SIZE),
            )
            // Always composed (just transparent while collapsed) so expanding never has to
            // compose it mid-animation.
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = ITEM_LABEL_FONT_SIZE),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                modifier =
                    Modifier
                        .padding(start = ITEM_LABEL_SPACING)
                        .graphicsLayer { alpha = revealFraction() },
            )
        }
    }
}

/**
 * One item's focus-to-preview debounce (see the comment above its call sites): keyed on the focus
 * State object rather than its value, and collected with collectLatest, so a pending preview is
 * cancelled the moment focus moves on -- without the rail recomposing on each move.
 */
@Composable
private fun FocusPreviewEffect(
    focused: State<Boolean>,
    route: String,
    correctStrayFocus: (String) -> Unit,
    isRecentExplicitSelectElsewhere: (String) -> Boolean,
    onPreview: () -> Unit,
) {
    LaunchedEffect(focused) {
        snapshotFlow { focused.value }.collectLatest { isFocused ->
            if (isFocused) {
                correctStrayFocus(route)
                delay(FOCUS_PREVIEW_DEBOUNCE_MS)
                if (!isRecentExplicitSelectElsewhere(route)) onPreview()
            }
        }
    }
}

/** A fixed [width] read at layout time, so animating it re-lays out without recomposing. */
private fun Modifier.animatedWidth(width: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val widthPx = constraints.constrainWidth(width().roundToPx())
        val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
        layout(widthPx, placeable.height) { placeable.place(0, 0) }
    }

/**
 * The logo lockup (mark + wordmark, one vector), collapsing to the mark alone. The mark sits on the
 * same vertical line as the nav item icons below it; the full lockup fades in over it as the rail
 * expands, clipped to the header's current width so it never pushes the layout during the tween.
 */
@Composable
private fun KaraloNavHeader(
    width: () -> Dp,
    revealFraction: () -> Float,
    horizontalInset: Dp,
) {
    Box(
        modifier =
            Modifier
                .animatedWidth(width)
                .clipToBounds()
                .padding(start = horizontalInset),
    ) {
        KaraloLogoMark(size = LOGO_SIZE)
        KaraloLogoLockup(
            markSize = LOGO_SIZE,
            modifier =
                Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { alpha = revealFraction() },
        )
    }
}

@Composable
private fun karaloNavItemColors() =
    SelectableSurfaceDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = KaraloTextSecondary,
        focusedContainerColor = KaraloRailItemActive,
        focusedContentColor = MaterialTheme.colorScheme.onBackground,
        selectedContainerColor = KaraloRailItemActive,
        selectedContentColor = MaterialTheme.colorScheme.onBackground,
        // The current route's own item starts out both selected *and* focused (e.g. Home at
        // launch) -- without these, that combined state falls back to SelectableSurfaceDefaults'
        // own muted default instead of matching our plain focused look.
        focusedSelectedContainerColor = KaraloRailItemActive,
        focusedSelectedContentColor = MaterialTheme.colorScheme.onBackground,
    )
