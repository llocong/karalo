package com.karalo.feature.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.karalo.core.common.text.formatVideoTitle
import com.karalo.core.ui.components.FocusableCard
import com.karalo.core.ui.components.LoadingIndicator
import com.karalo.core.ui.components.TvCarousel
import com.karalo.core.ui.components.TvCarouselImagePrefetch
import com.karalo.core.ui.focus.SlotPivotBringIntoViewSpec
import com.karalo.core.ui.theme.KaraloPageHeaderHeight
import com.karalo.core.ui.theme.KaraloPagePadding
import com.karalo.core.ui.theme.KaraloShelfCardGutter
import com.karalo.core.ui.theme.KaraloShelfCardWidth
import com.karalo.core.ui.theme.KaraloTextSecondary
import com.karalo.core.ui.theme.KaraloTileLabelTextStyle
import com.karalo.core.ui.theme.LocalKaraloTokens
import com.karalo.feature.search.domain.SearchResultItem
import kotlinx.coroutines.delay

// Sizes from the "Karalo TV Playlists" design, whose cqw units are 1% of the 960dp-wide screen.
private val COVER_SIZE = 108.dp
private val COVER_CORNER_RADIUS = 12.dp
private val RING_WIDTH = 3.dp
private val RING_OFFSET = 3.dp

// Each playlist tile reserves room for its focus ring inside its own bounds, so neither the row's
// clip nor a scroll that stops right at a tile's edge ever cuts the ring off.
private val RING_INSET = RING_OFFSET + RING_WIDTH
private val TILE_WIDTH = COVER_SIZE + RING_INSET * 2

// 15dp between covers, less the ring room on either side of each.
private val TILE_SPACING = 15.dp - RING_INSET * 2

// Covers line up with the title; the row itself runs to the screen's right edge.
private val ROW_START_PADDING = KaraloPagePadding - RING_INSET
private val COVER_LABEL_SPACING = 9.dp
private val COVER_LABEL_FONT_SIZE = 13.sp
private val COVER_LABEL_LINE_HEIGHT = 18.sp
private const val UNSELECTED_TILE_ALPHA = 0.85f

// The row scrolls once focus passes the fourth tile, then keeps it there (see SlotPivotBringIntoViewSpec).
private const val PIVOT_SLOT = 3

private val TITLE_ROW_SPACING = 13.dp
private val ROW_HEADER_SPACING = 19.dp
private val HEADER_NAME_FONT_SIZE = 19.sp
private val HEADER_COUNT_FONT_SIZE = 14.sp
private val HEADER_COUNT_SPACING = 13.dp

// Same row padding as Home's shelf: room above and below for a focused song tile's scale-up.
private val SONGS_ROW_VERTICAL_PADDING = 20.dp
private val SONGS_CONTENT_PADDING =
    PaddingValues(horizontal = KaraloPagePadding, vertical = SONGS_ROW_VERTICAL_PADDING)
private val SONGS_PLACEHOLDER_HEIGHT = 200.dp
private const val THUMBNAIL_ASPECT_RATIO = 16f / 9f

// See HomeScreenContent's firstVideoFocusTrigger effect: the first request can lose a race with
// the rail's own focus handling right after a select.
private const val CONTENT_FOCUS_RETRY_DELAY_MS = 50L

/**
 * The Playlists page: a row of playlist covers, and below it a carousel of the focused playlist's
 * songs, built from the same song tile as Home. LEFT/RIGHT browses playlists (the carousel follows
 * straight away), DOWN/UP moves between the two rows.
 */
@Composable
fun PlaylistsScreen(
    onResultClick: (startIndex: Int, videoId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    playerReturnTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    PlaylistsScreenContent(
        uiState = uiState,
        onPlaylistFocused = viewModel::onPlaylistFocused,
        onResultClick = { items, index ->
            viewModel.onResultClicked(items)
            onResultClick(index, items[index].videoId)
        },
        contentFocusTrigger = contentFocusTrigger,
        playerReturnTrigger = playerReturnTrigger,
        railFocusRequester = railFocusRequester,
        modifier = modifier,
    )
}

@Composable
internal fun PlaylistsScreenContent(
    uiState: PlaylistsUiState,
    onPlaylistFocused: (Int) -> Unit,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    playerReturnTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
) {
    val coverFocusRequesters = remember(uiState.playlists.size) { List(uiState.playlists.size) { FocusRequester() } }
    val selectedCoverFocusRequester = coverFocusRequesters[uiState.selectedIndex]
    val firstSongFocusRequester = remember { FocusRequester() }
    val songs = (uiState.selectedSongs as? PlaylistSongsState.Loaded)?.items?.takeIf { it.isNotEmpty() }

    // Selecting Playlists in the rail lands on the selected playlist's cover.
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(contentFocusTrigger) {
        if (contentFocusTrigger > consumedFocusTrigger) {
            consumedFocusTrigger = contentFocusTrigger
            selectedCoverFocusRequester.requestFocus()
            delay(CONTENT_FOCUS_RETRY_DELAY_MS)
            selectedCoverFocusRequester.requestFocus()
        }
    }

    // A genuine return from the player lands back on the song that was played -- the same
    // pattern as HomeScreenContent (see its own doc for why a rail focus-preview mustn't do this).
    var lastPlayedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocusRequester = remember { FocusRequester() }
    var consumedPlayerReturnTrigger by rememberSaveable { mutableIntStateOf(0) }
    val canRestoreLastPlayed = playerReturnTrigger > consumedPlayerReturnTrigger
    LaunchedEffect(playerReturnTrigger) {
        if (playerReturnTrigger > consumedPlayerReturnTrigger) {
            consumedPlayerReturnTrigger = playerReturnTrigger
        }
    }
    val trackedOnResultClick: (List<SearchResultItem>, Int) -> Unit = { items, index ->
        lastPlayedVideoId = items[index].videoId
        onResultClick(items, index)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = KaraloPagePadding)
                // BACK while browsing moves focus to Playlists' own rail item -- see the matching
                // comment on HomeScreenContent for why this is a raw key intercept.
                .onPreviewKeyEvent { keyEvent ->
                    val isBackKeyDown = keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back
                    if (isBackKeyDown && railFocusRequester != null) {
                        railFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                },
    ) {
        // Same title style and header height as Settings and History.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(start = KaraloPagePadding).heightIn(min = KaraloPageHeaderHeight),
        ) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(TITLE_ROW_SPACING))
        PlaylistRow(
            playlists = uiState.playlists,
            selectedIndex = uiState.selectedIndex,
            coverFocusRequesters = coverFocusRequesters,
            onPlaylistFocused = onPlaylistFocused,
            // Only when there are songs to move to -- otherwise DOWN stays on the cover.
            downFocusRequester = if (songs != null) firstSongFocusRequester else null,
            railFocusRequester = railFocusRequester,
        )
        Spacer(modifier = Modifier.height(ROW_HEADER_SPACING))
        PlaylistHeader(name = uiState.selected.name, songCount = songs?.size)
        // Keyed on the playlist so switching playlists starts its carousel back at the first song.
        key(uiState.selected.id) {
            PlaylistSongs(
                playlistName = uiState.selected.name,
                state = uiState.selectedSongs,
                onResultClick = trackedOnResultClick,
                firstSongFocusRequester = firstSongFocusRequester,
                upFocusRequester = selectedCoverFocusRequester,
                railFocusRequester = railFocusRequester,
                restoreFocusItemKey = lastPlayedVideoId.takeIf { canRestoreLastPlayed },
                restoreFocusRequester = restoreFocusRequester,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlists: List<Playlist>,
    selectedIndex: Int,
    coverFocusRequesters: List<FocusRequester>,
    onPlaylistFocused: (Int) -> Unit,
    downFocusRequester: FocusRequester?,
    railFocusRequester: FocusRequester?,
) {
    val density = LocalDensity.current
    val bringIntoViewSpec =
        remember(density) {
            SlotPivotBringIntoViewSpec(
                pivotPx = with(density) { (ROW_START_PADDING + (TILE_WIDTH + TILE_SPACING) * PIVOT_SLOT).toPx() },
            )
        }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        LazyRow(
            contentPadding = PaddingValues(start = ROW_START_PADDING),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                PlaylistTile(
                    playlist = playlist,
                    selected = index == selectedIndex,
                    // OK on a cover goes down to its songs, like DOWN.
                    onClick = { downFocusRequester?.requestFocus() },
                    modifier =
                        Modifier
                            .focusRequester(coverFocusRequesters[index])
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = downFocusRequester ?: FocusRequester.Cancel
                                if (index == 0 && railFocusRequester != null) left = railFocusRequester
                                if (index == playlists.lastIndex) right = FocusRequester.Cancel
                            }.onFocusChanged { if (it.isFocused) onPlaylistFocused(index) },
                )
            }
        }
    }
}

/**
 * A square playlist cover with its name below. [selected] -- the playlist the row's focus is on,
 * or was last on while focus is down in the songs -- gets the accent ring and the bright label.
 */
@Composable
private fun PlaylistTile(
    playlist: Playlist,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverShape = RoundedCornerShape(COVER_CORNER_RADIUS)
    // Always drawn (transparent while unselected), like FocusableCard's border, so the modifier
    // chain stays the same on every focus move.
    val ringColor = if (selected) LocalKaraloTokens.current.accent else Color.Transparent
    Column(
        modifier =
            modifier
                .width(TILE_WIDTH)
                // See FocusableCard: clickable alone isn't focusable while the device is in touch mode.
                .focusProperties { canFocus = true }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).graphicsLayer { alpha = if (selected) 1f else UNSELECTED_TILE_ALPHA }
                .padding(RING_INSET),
    ) {
        AsyncImage(
            model = playlist.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(COVER_SIZE)
                    .drawWithContent {
                        drawContent()
                        val ring = RING_WIDTH.toPx()
                        val inset = RING_OFFSET.toPx() + ring / 2
                        val radius = COVER_CORNER_RADIUS.toPx() + inset
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(-inset, -inset),
                            size = Size(size.width + inset * 2, size.height + inset * 2),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = ring),
                        )
                    }
                    // After the ring's draw, so only the image is clipped, not the ring around it.
                    .clip(coverShape),
        )
        Text(
            text = playlist.name,
            style =
                (if (selected) MaterialTheme.typography.titleSmall else KaraloTileLabelTextStyle).copy(
                    fontSize = COVER_LABEL_FONT_SIZE,
                    lineHeight = COVER_LABEL_LINE_HEIGHT,
                    letterSpacing = 0.sp,
                ),
            color = if (selected) MaterialTheme.colorScheme.onBackground else KaraloTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = COVER_LABEL_SPACING),
        )
    }
}

/** The selected playlist's name, with its song count once its songs have loaded. */
@Composable
private fun PlaylistHeader(
    name: String,
    songCount: Int?,
) {
    Row(modifier = Modifier.padding(horizontal = KaraloPagePadding)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = HEADER_NAME_FONT_SIZE),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
        )
        if (songCount != null) {
            Spacer(modifier = Modifier.width(HEADER_COUNT_SPACING))
            Text(
                text = if (songCount == 1) "1 song" else "$songCount songs",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = HEADER_COUNT_FONT_SIZE),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun PlaylistSongs(
    playlistName: String,
    state: PlaylistSongsState,
    onResultClick: (List<SearchResultItem>, Int) -> Unit,
    firstSongFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    railFocusRequester: FocusRequester?,
    restoreFocusItemKey: String?,
    restoreFocusRequester: FocusRequester,
) {
    val density = LocalDensity.current
    val prefetchSizePx =
        remember(density) {
            with(density) {
                val widthPx = KaraloShelfCardWidth.roundToPx()
                IntSize(widthPx, (widthPx / THUMBNAIL_ASPECT_RATIO).toInt())
            }
        }
    val placeholderModifier = Modifier.fillMaxWidth().height(SONGS_PLACEHOLDER_HEIGHT)
    when (state) {
        is PlaylistSongsState.Loading -> LoadingIndicator(modifier = placeholderModifier)
        is PlaylistSongsState.Error ->
            SongsMessage("Couldn't load \"$playlistName\". Check your connection and try again.", placeholderModifier)
        is PlaylistSongsState.Loaded ->
            if (state.items.isEmpty()) {
                SongsMessage("No songs found for \"$playlistName\".", placeholderModifier)
            } else {
                TvCarousel(
                    items = state.items,
                    key = { it.videoId },
                    contentPadding = SONGS_CONTENT_PADDING,
                    horizontalArrangement = Arrangement.spacedBy(KaraloShelfCardGutter),
                    firstItemFocusRequester = firstSongFocusRequester,
                    upFocusRequester = upFocusRequester,
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
                        onClick = { onResultClick(state.items, index) },
                        modifier = Modifier.width(KaraloShelfCardWidth).then(itemModifier),
                    )
                }
            }
    }
}

@Composable
private fun SongsMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = KaraloPagePadding, vertical = SONGS_ROW_VERTICAL_PADDING),
    )
}
