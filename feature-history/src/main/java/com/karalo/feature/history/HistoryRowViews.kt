package com.karalo.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision

private val ROW_HEIGHT = 64.dp
private val THUMBNAIL_WIDTH = 96.dp
private val THUMBNAIL_HEIGHT = 54.dp
private val ROW_SHAPE = RoundedCornerShape(8.dp)
private val THUMBNAIL_SHAPE = RoundedCornerShape(4.dp)
private val FOCUSED_BORDER_WIDTH = 2.dp

/**
 * One compact song row: thumbnail, title, and a muted detail line. Built on a plain focusable
 * Box rather than tv-material's Surface/Card, and with no focus scale animation -- only a
 * background and border swap -- so holding UP/DOWN through a long list stays smooth (the same
 * reasoning as FocusableCard's own doc).
 */
@Composable
internal fun HistorySongRow(
    song: HistoryRow.Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    // Decoded at the thumbnail's own small size (not YouTube's full-size image), so scrolling a
    // long list never holds or draws large bitmaps.
    val imageRequest =
        remember(song.thumbnailUrl) {
            with(density) {
                ImageRequest
                    .Builder(context)
                    .data(song.thumbnailUrl)
                    .size(THUMBNAIL_WIDTH.roundToPx(), THUMBNAIL_HEIGHT.roundToPx())
                    .precision(Precision.INEXACT)
                    .build()
            }
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                // See FocusableCard: clickable alone isn't focusable while the device is in touch mode.
                .focusProperties { canFocus = true }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .background(if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, ROW_SHAPE)
                .border(
                    BorderStroke(
                        FOCUSED_BORDER_WIDTH,
                        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
                    ROW_SHAPE,
                ).padding(horizontal = 12.dp),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    .clip(THUMBNAIL_SHAPE)
                    .background(MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.detail.isNotEmpty()) {
                Text(
                    text = song.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A choice in the Sort panel, with a radio dot showing which one is current. */
@Composable
internal fun SortOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = SelectableSurfaceDefaults.shape(shape = ROW_SHAPE),
        colors =
            SelectableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onBackground,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                focusedSelectedContainerColor = MaterialTheme.colorScheme.primary,
                focusedSelectedContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            RadioDot(selected = selected)
            Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(22.dp)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), CircleShape),
    ) {
        if (selected) Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
    }
}
