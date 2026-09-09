package com.karalo.karalo.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val NAV_RAIL_WIDTH_DP = 220

const val NAV_TAG_HOME = "nav_home"
const val NAV_TAG_SEARCH = "nav_search"

@Composable
internal fun KaraloNavRail(
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(NAV_RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 24.dp, horizontal = 8.dp),
    ) {
        ListItem(
            selected = currentRoute == NavDestination.Home.route,
            onClick = onHomeClick,
            headlineContent = { Text("Home") },
            modifier = Modifier.testTag(NAV_TAG_HOME),
        )
        ListItem(
            selected = currentRoute == NavDestination.Search.route,
            onClick = onSearchClick,
            headlineContent = { Text("Search") },
            modifier = Modifier.testTag(NAV_TAG_SEARCH),
        )
    }
}
