package com.karalo.karalo.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
        modifier =
            modifier
                .width(NAV_RAIL_WIDTH_DP.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 24.dp, horizontal = 8.dp),
    ) {
        NavRailItem(
            label = "Home",
            selected = currentRoute == NavDestination.Home.route,
            onClick = onHomeClick,
            modifier = Modifier.testTag(NAV_TAG_HOME),
        )
        NavRailItem(
            label = "Search",
            selected = currentRoute == NavDestination.Search.route,
            onClick = onSearchClick,
            modifier = Modifier.testTag(NAV_TAG_SEARCH),
        )
    }
}

@Composable
private fun NavRailItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val background =
        when {
            isFocused -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        }

    Text(
        text = label,
        color = if (isFocused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
        modifier =
            modifier
                .fillMaxWidth()
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
                .background(background, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
