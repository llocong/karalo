package com.karalo.karalo.nav

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val NAV_RAIL_WIDTH_DP = 220
private const val SEARCH_BUTTON_SIZE_DP = 44
private const val FOCUSED_SCALE = 1.15f
private const val UNFOCUSED_SCALE = 1f
private const val SEARCH_ICON_FOCUSED_SCALE = 1.2f
private const val SEARCH_ICON_UNFOCUSED_SCALE = 1f
private const val PULSE_MIN_ALPHA = 0.08f
private const val PULSE_MAX_ALPHA = 0.3f
private const val PULSE_DURATION_MS = 900

@Suppress("MagicNumber") // (0f, 0.5f) = left edge, vertically centered -- not arbitrary
private val LEFT_CENTER_TRANSFORM_ORIGIN = TransformOrigin(0f, 0.5f)

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
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            SearchIconButton(
                selected = currentRoute == NavDestination.Search.route,
                onClick = onSearchClick,
                modifier = Modifier.testTag(NAV_TAG_SEARCH),
            )
        }

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
    val background = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondary
    val iconScale by
        animateFloatAsState(
            if (isFocused) SEARCH_ICON_FOCUSED_SCALE else SEARCH_ICON_UNFOCUSED_SCALE,
            label = "searchIconScale",
        )

    // Pulses continuously while focused instead of a static highlight, to read as "selected" from
    // across the room without a jarring color swap.
    val infiniteTransition = rememberInfiniteTransition(label = "searchFocusPulse")
    val pulseAlpha by
        infiniteTransition.animateFloat(
            initialValue = PULSE_MIN_ALPHA,
            targetValue = PULSE_MAX_ALPHA,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(PULSE_DURATION_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "searchFocusPulseAlpha",
        )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(SEARCH_BUTTON_SIZE_DP.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).background(background, CircleShape),
    ) {
        if (isFocused) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = pulseAlpha), CircleShape),
            )
        }
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
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
    val scale by animateFloatAsState(if (isFocused) FOCUSED_SCALE else UNFOCUSED_SCALE, label = "navItemScale")

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
            // Scaled from its left-center, not its center, so growing on focus doesn't shift the
            // icon's left edge -- every nav item's icon stays pinned to the same x position.
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = LEFT_CENTER_TRANSFORM_ORIGIN
                },
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
