package com.karalo.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.karalo.core.common.text.formatDuration

private const val FOCUSED_SCALE = 1.08f
private const val UNFOCUSED_SCALE = 1f
private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f
private const val DURATION_BADGE_BACKGROUND_ALPHA = 0.8f

/**
 * A D-pad-focusable result card: thumbnail + title + subtitle, scaling up with a border on focus
 * so the currently-selected item is unambiguous from a couch. The video's duration, when known,
 * is badged over the bottom-right corner of the thumbnail, per the brand board's home-screen
 * video grid.
 */
@Composable
fun FocusableCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    durationSeconds: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) FOCUSED_SCALE else UNFOCUSED_SCALE, label = "cardScale")

    Card(
        onClick = onClick,
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(THUMBNAIL_ASPECT_RATIO),
                )
                if (durationSeconds != null && durationSeconds >= 0) {
                    Text(
                        text = formatDuration(durationSeconds),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.background.copy(alpha = DURATION_BADGE_BACKGROUND_ALPHA),
                                    RoundedCornerShape(4.dp),
                                ).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
