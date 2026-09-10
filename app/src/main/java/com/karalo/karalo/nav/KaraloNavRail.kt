package com.karalo.karalo.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val NAV_RAIL_WIDTH_DP = 220
private const val SEARCH_BUTTON_SIZE_DP = 44
private const val FOCUSED_TEXT_SCALE = 1.15f
private const val UNFOCUSED_TEXT_SCALE = 1f

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
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface),
                    ),
                ).padding(vertical = 24.dp, horizontal = 8.dp),
    ) {
        SearchIconButton(
            selected = currentRoute == NavDestination.Search.route,
            onClick = onSearchClick,
            modifier = Modifier.testTag(NAV_TAG_SEARCH).padding(horizontal = 8.dp, vertical = 4.dp),
        )

        NavRailItem(
            label = "Home",
            icon = Icons.Filled.Home,
            onClick = onHomeClick,
            modifier = Modifier.testTag(NAV_TAG_HOME),
        )
    }
}

@Composable
private fun SearchIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val background =
        when {
            isFocused -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.secondary
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(SEARCH_BUTTON_SIZE_DP.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
                .background(background, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun NavRailItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val contentColor =
        if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    val scale by
        animateFloatAsState(if (isFocused) FOCUSED_TEXT_SCALE else UNFOCUSED_TEXT_SCALE, label = "navItemScale")

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}
