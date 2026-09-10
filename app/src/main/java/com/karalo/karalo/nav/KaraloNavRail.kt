package com.karalo.karalo.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text

const val NAV_TAG_HOME = "nav_home"
const val NAV_TAG_SEARCH = "nav_search"

/**
 * The drawer's contents: standard [NavigationDrawerItem]s in a plain vertical list, per Android's
 * TV navigation-drawer pattern (developer.android.com/design/ui/tv/guides/components/navigation-drawer)
 * -- collapsed to icons-only until an item gains focus, then it animates open to show icon + label
 * for every item.
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
) {
    val homeInteractionSource = remember { MutableInteractionSource() }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val isHomeFocused by homeInteractionSource.collectIsFocusedAsState()
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()

    LaunchedEffect(isHomeFocused, isSearchFocused) {
        drawerState.setValue(if (isHomeFocused || isSearchFocused) DrawerValue.Open else DrawerValue.Closed)
    }

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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavigationDrawerItem(
            selected = currentRoute == NavDestination.Home.route,
            onClick = onHomeClick,
            leadingContent = { Icon(imageVector = Icons.Filled.Home, contentDescription = null) },
            colors = karaloNavItemColors(),
            interactionSource = homeInteractionSource,
            modifier = Modifier.testTag(NAV_TAG_HOME),
        ) {
            Text("Home", style = MaterialTheme.typography.labelMedium)
        }

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
