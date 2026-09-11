package com.karalo.karalo.nav

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
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

/**
 * The drawer's contents: a logo header, the primary destinations, and a settings action pinned to
 * the bottom -- standard TV Material navigation-drawer pattern (developer.android.com/design/ui/tv/
 * guides/components/navigation-drawer) -- collapsed to icons-only until an item gains focus, then
 * it animates open to show icon + label for every item.
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
) {
    val searchInteractionSource = remember { MutableInteractionSource() }
    val homeInteractionSource = remember { MutableInteractionSource() }
    val settingsInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
    val isHomeFocused by homeInteractionSource.collectIsFocusedAsState()
    val isSettingsFocused by settingsInteractionSource.collectIsFocusedAsState()
    val homeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchFocused, isHomeFocused, isSettingsFocused) {
        val anyFocused = isSearchFocused || isHomeFocused || isSettingsFocused
        drawerState.setValue(if (anyFocused) DrawerValue.Open else DrawerValue.Closed)
    }

    // Home is the app's start destination, so it should own initial D-pad focus even though
    // Search is listed first.
    LaunchedEffect(Unit) { homeFocusRequester.requestFocus() }

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

        NavigationDrawerItem(
            selected = currentRoute == NavDestination.Search.route,
            onClick = onSearchClick,
            leadingContent = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            colors = karaloNavItemColors(),
            interactionSource = searchInteractionSource,
            modifier = Modifier.testTag(NAV_TAG_SEARCH),
        ) {
            Text("Search", style = MaterialTheme.typography.labelMedium)
        }

        NavigationDrawerItem(
            selected = currentRoute == NavDestination.Home.route,
            onClick = onHomeClick,
            leadingContent = { Icon(imageVector = Icons.Filled.Home, contentDescription = null) },
            colors = karaloNavItemColors(),
            interactionSource = homeInteractionSource,
            modifier = Modifier.testTag(NAV_TAG_HOME).focusRequester(homeFocusRequester).padding(top = 8.dp),
        ) {
            Text("Home", style = MaterialTheme.typography.labelMedium)
        }

        Box(modifier = Modifier.weight(1f))

        NavigationDrawerItem(
            selected = currentRoute == NavDestination.Settings.route,
            onClick = onSettingsClick,
            leadingContent = { Icon(imageVector = Icons.Filled.Settings, contentDescription = null) },
            colors = karaloNavItemColors(),
            interactionSource = settingsInteractionSource,
            modifier = Modifier.testTag(NAV_TAG_SETTINGS),
        ) {
            Text("Settings", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** App logo + wordmark, matching [NavigationDrawerItem]'s own icon-size and expand-on-focus pattern. */
@Composable
private fun NavigationDrawerScope.KaraloNavHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_karalo_logo),
            contentDescription = null,
            modifier = Modifier.size(LOGO_SIZE),
        )
        AnimatedVisibility(
            visible = hasFocus,
            enter = NavigationDrawerItemDefaults.ContentAnimationEnter,
            exit = NavigationDrawerItemDefaults.ContentAnimationExit,
        ) {
            Text(
                text = "Karalo",
                color = MaterialTheme.colorScheme.onBackground,
                style = KaraloLogoTextStyle,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun karaloNavItemColors() =
    NavigationDrawerItemDefaults.colors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        focusedContentColor = MaterialTheme.colorScheme.onBackground,
        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        selectedContentColor = MaterialTheme.colorScheme.onBackground,
    )
