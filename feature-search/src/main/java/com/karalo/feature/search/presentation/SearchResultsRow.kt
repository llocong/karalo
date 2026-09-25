package com.karalo.feature.search.presentation

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.core.ui.components.TvCarousel
import com.karalo.core.ui.components.TvCarouselImagePrefetch
import com.karalo.feature.search.domain.SearchResultItem

// Matches Home's shelf cards (see HomeScreen.kt's SHELF_CARD_WIDTH) so results scroll the same way
// as any Home shelf, per the app's one consistent "browse a row of videos" pattern -- results just
// happen to be a single, ungrouped row instead of several categorized ones. Content padding and
// item spacing are left at TvCarouselDefaults, which already match this row's previous values.
internal val RESULT_CARD_WIDTH = 240.dp
private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f

/**
 * A horizontal, lazily-composed row of search-result video cards -- see [TvCarousel] for the
 * shared scrolling/focus-management engine this row is built on.
 */
@Composable
fun SearchResultsRow(
    items: List<SearchResultItem>,
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    focusFirstItemTrigger: Int = 0,
    restoreFocusItemKey: String? = null,
    restoreFocusRequester: FocusRequester? = null,
    railFocusRequester: FocusRequester? = null,
    queryFieldFocusRequester: FocusRequester? = null,
    focusFirstItemOnDownTrigger: Int = 0,
) {
    val density = LocalDensity.current
    val prefetchSizePx =
        remember(density) {
            with(density) {
                val widthPx = RESULT_CARD_WIDTH.roundToPx()
                IntSize(widthPx, (widthPx / THUMBNAIL_ASPECT_RATIO).toInt())
            }
        }

    TvCarousel(
        items = items,
        key = { it.videoId },
        modifier = modifier,
        firstItemFocusRequester = firstItemFocusRequester,
        focusFirstItemTrigger = focusFirstItemTrigger,
        focusFirstItemOnDownTrigger = focusFirstItemOnDownTrigger,
        upFocusRequester = queryFieldFocusRequester,
        leftEdgeFocusRequester = railFocusRequester,
        restoreFocusItemKey = restoreFocusItemKey,
        restoreFocusRequester = restoreFocusRequester,
        imagePrefetch =
            TvCarouselImagePrefetch(
                thumbnailUrl = { it.thumbnailUrl },
                sizePx = prefetchSizePx,
            ),
    ) { index, item, itemModifier ->
        FocusableCard(
            title = formatVideoTitle(item.title),
            subtitle = null,
            thumbnailUrl = item.thumbnailUrl,
            durationSeconds = item.durationSeconds,
            onClick = { onResultClick(index, item.videoId) },
            modifier = Modifier.width(RESULT_CARD_WIDTH).then(itemModifier),
        )
    }
}
