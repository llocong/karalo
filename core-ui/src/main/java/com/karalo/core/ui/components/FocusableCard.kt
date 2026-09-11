package com.karalo.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClassicCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.karalo.core.common.text.formatDuration

private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f
private const val DURATION_BADGE_BACKGROUND_ALPHA = 0.8f
private const val CARD_CORNER_RADIUS_DP = 8

/**
 * A D-pad-focusable result card built on TV Material's official Classic Card pattern
 * (developer.android.com/design/ui/tv/guides/components/cards): thumbnail image slot, title, and
 * an optional subtitle, scaling up with a border on focus so the currently-selected item is
 * unambiguous from a couch (the component's own default/focused/pressed state behavior). The
 * video's duration, when known, is badged over the bottom-right corner of the thumbnail, per the
 * brand board's home-screen video grid.
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
    ClassicCard(
        onClick = onClick,
        modifier = modifier,
        image = {
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
                    style = MaterialTheme.typography.labelSmall,
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
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        subtitle = {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
        shape = CardDefaults.shape(shape = RoundedCornerShape(CARD_CORNER_RADIUS_DP.dp)),
        colors =
            CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border =
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(width = 3.dp, color = MaterialTheme.colorScheme.border)),
            ),
        contentPadding = PaddingValues(bottom = 8.dp),
    )
}
