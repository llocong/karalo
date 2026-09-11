package com.karalo.karalo.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.karalo.core.ui.R
import com.karalo.core.ui.theme.KaraloLogoTextStyle

const val NAV_TAG_HOME = "nav_home"
const val NAV_TAG_SEARCH = "nav_search"
const val NAV_TAG_SETTINGS = "nav_settings"

// Generous gap below the logo header so the nav items sit well clear of it once the drawer is
// collapsed to icons-only, per the nav-drawer guide's three-section layout (logo header / nav
// items / bottom actions) -- developer.android.com/design/ui/tv/guides/components/navigation-drawer.
private val HEADER_TO_ITEMS_SPACING = 48.dp

// Bigger than a regular nav icon -- this is the brand mark, not just another rail item.
private val LOGO_SIZE = 40.dp

private val ITEM_COLLAPSED_WIDTH = NavigationDrawerItemDefaults.CollapsedDrawerItemWidth
private val ITEM_EXPANDED_WIDTH = NavigationDrawerItemDefaults.ExpandedDrawerItemWidth
private val ITEM_HEIGHT = NavigationDrawerItemDefaults.ContainerHeightOneLine
private val ITEM_HORIZONTAL_INSET = 16.dp
private val ITEM_ICON_SIZE = NavigationDrawerItemDefaults.IconSize

// Centers the (larger) logo on the same vertical line as the nav icons below it: item icons sit
// at ITEM_HORIZONTAL_INSET + half their own size; solving for the same center with LOGO_SIZE
// gives this inset instead of reusing ITEM_HORIZONTAL_INSET directly.
private val HEADER_HORIZONTAL_INSET = ITEM_HORIZONTAL_INSET + (ITEM_ICON_SIZE - LOGO_SIZE) / 2

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
 * focus on into that destination's own content, via [onHomeSelect]/[onSearchSelect].
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
 */
@Composable
internal fun NavigationDrawerScope.KaraloNavRailContent(
    currentRoute: String?,
    drawerState: DrawerState,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHomeSelect: () -> Unit,
    onSearchSelect: () -> Unit,
) {
    val searchInteractionSource = remember { MutableInteractionSource() }
    val homeInteractionSource = remember { MutableInteractionSource() }
    val settingsInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
    val isHomeFocused by homeInteractionSource.collectIsFocusedAsState()
    val isSettingsFocused by settingsInteractionSource.collectIsFocusedAsState()

    LaunchedEffect(isSearchFocused, isHomeFocused, isSettingsFocused) {
        val anyFocused = isSearchFocused || isHomeFocused || isSettingsFocused
        drawerState.setValue(if (anyFocused) DrawerValue.Open else DrawerValue.Closed)
    }

    // Focusing an item -- without clicking it -- immediately navigates to and previews that
    // destination, matching a common TV "focus to preview" pattern.
    LaunchedEffect(isSearchFocused) { if (isSearchFocused) onSearchClick() }
    LaunchedEffect(isHomeFocused) { if (isHomeFocused) onHomeClick() }
    LaunchedEffect(isSettingsFocused) { if (isSettingsFocused) onSettingsClick() }

    Column(
        modifier =
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface),
                    ),
                ).fillMaxHeight()
                .padding(12.dp)
                .selectableGroup(),
    ) {
        KaraloNavHeader()
        Spacer(modifier = Modifier.height(HEADER_TO_ITEMS_SPACING))

        KaraloNavItem(
            selected = currentRoute == NavDestination.Search.route,
            onClick = onSearchSelect,
            icon = Icons.Filled.Search,
            label = "Search",
            interactionSource = searchInteractionSource,
            modifier = Modifier.testTag(NAV_TAG_SEARCH),
        )

        KaraloNavItem(
            selected = currentRoute == NavDestination.Home.route,
            onClick = onHomeSelect,
            icon = Icons.Filled.Home,
            label = "Home",
            interactionSource = homeInteractionSource,
            modifier = Modifier.testTag(NAV_TAG_HOME).padding(top = 8.dp),
        )

        Box(modifier = Modifier.weight(1f))

        KaraloNavItem(
            selected = currentRoute == NavDestination.Settings.route,
            onClick = onSettingsClick,
            icon = Icons.Filled.Settings,
            label = "Settings",
            interactionSource = settingsInteractionSource,
            modifier = Modifier.testTag(NAV_TAG_SETTINGS),
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
    modifier: Modifier = Modifier,
) {
    val width by
        animateDpAsState(
            targetValue = if (hasFocus) ITEM_EXPANDED_WIDTH else ITEM_COLLAPSED_WIDTH,
            label = "navItemWidth",
        )
    // How far along the width tween currently is (0 = fully collapsed, 1 = fully expanded) --
    // used to fade the label in lockstep with the width itself, see the KDoc above.
    val revealFraction = revealFractionOf(width)

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.width(width).height(ITEM_HEIGHT),
        shape = SelectableSurfaceDefaults.shape(shape = RoundedCornerShape(percent = 50)),
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
                    .padding(start = ITEM_HORIZONTAL_INSET),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(ITEM_ICON_SIZE)) {
                Icon(imageVector = icon, contentDescription = null)
            }
            if (revealFraction > 0f) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier =
                        Modifier
                            .padding(start = 12.dp)
                            .graphicsLayer { alpha = revealFraction },
                )
            }
        }
    }
}

/** App logo + wordmark, centered on the same vertical line as the nav item icons below it. */
@Composable
private fun NavigationDrawerScope.KaraloNavHeader() {
    val width by
        animateDpAsState(
            targetValue = if (hasFocus) ITEM_EXPANDED_WIDTH else ITEM_COLLAPSED_WIDTH,
            label = "headerWidth",
        )
    val revealFraction = revealFractionOf(width)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .width(width)
                .padding(start = HEADER_HORIZONTAL_INSET, top = 12.dp, bottom = 12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_karalo_logo),
            contentDescription = null,
            modifier = Modifier.size(LOGO_SIZE),
        )
        if (revealFraction > 0f) {
            Text(
                text = "Karalo",
                color = MaterialTheme.colorScheme.onBackground,
                style = KaraloLogoTextStyle,
                // At in-between widths during the collapse/expand tween, this would otherwise
                // wrap onto a second line -- taller than the logo itself -- which grew the
                // header's own height for that instant and pushed every item below it down (and
                // made the header's own icon look like it was moving too).
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .graphicsLayer { alpha = revealFraction },
            )
        }
    }
}

/** How far along the collapsed-to-expanded width range [width] currently is, from 0f to 1f. */
private fun revealFractionOf(width: Dp): Float =
    ((width - ITEM_COLLAPSED_WIDTH) / (ITEM_EXPANDED_WIDTH - ITEM_COLLAPSED_WIDTH)).coerceIn(0f, 1f)

@Composable
private fun karaloNavItemColors() =
    SelectableSurfaceDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        focusedContentColor = MaterialTheme.colorScheme.onBackground,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        selectedContentColor = MaterialTheme.colorScheme.onBackground,
        // The current route's own item starts out both selected *and* focused (e.g. Home at
        // launch) -- without these, that combined state falls back to SelectableSurfaceDefaults'
        // own muted default instead of matching our plain focused look.
        focusedSelectedContainerColor = MaterialTheme.colorScheme.primary,
        focusedSelectedContentColor = MaterialTheme.colorScheme.onBackground,
    )
