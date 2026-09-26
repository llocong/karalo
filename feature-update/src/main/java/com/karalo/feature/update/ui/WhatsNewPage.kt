@file:Suppress("MagicNumber") // sizes and colors are the design's own values

package com.karalo.feature.update.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.karalo.core.ui.components.KaraloPillButton
import com.karalo.core.ui.icons.KaraloIcons
import com.karalo.core.ui.theme.KaraloOnBackground
import com.karalo.core.ui.theme.KaraloOnSurfaceVariant
import com.karalo.core.ui.theme.LocalKaraloTokens
import com.karalo.feature.update.domain.ReleaseNotes
import com.karalo.feature.update.domain.UpdateRelease
import com.karalo.feature.update.domain.UpdateState
import kotlinx.coroutines.launch

const val WHATS_NEW_TAG_ACTION = "whats_new_action"
const val WHATS_NEW_TAG_NOTES = "whats_new_notes"
const val WHATS_NEW_TAG_MORE = "whats_new_more"

// The "TV — What's new" artboard, in dp (1cqw = 9.6dp).
private val PAGE_GAP = 23.dp
private val NOTES_SHAPE = RoundedCornerShape(13.dp)
private val NOTES_PADDING = 23.dp
private val NOTES_BOTTOM_PADDING = 58.dp
private val SECTION_GAP = 23.dp
private val ITEM_GAP = 9.6.dp
private val BULLET_SIZE = 7.7.dp
private val BULLET_GAP = 13.dp
private val FADE_HEIGHT = 67.dp
private val FOCUS_OUTLINE_WIDTH = 3.dp
private val FOCUS_OUTLINE_OFFSET = 3.dp

// One D-pad press scrolls this much of the visible notes, so the line at the bottom stays in view.
private const val SCROLL_STEP_FRACTION = 0.6f

/**
 * What's new in the available version, opened from the update card. The action button (Update
 * now, or Restart now once downloaded; none while downloading) gets focus first; DOWN moves into
 * the notes, where UP and DOWN scroll, and UP at the top goes back to the button. BACK calls
 * [onBack]; LEFT calls [onLeft] (the nav rail, like every other page).
 */
@Composable
fun WhatsNewPage(
    state: UpdateState,
    onBack: () -> Unit,
    onUpdateNow: () -> Unit,
    onRestartNow: () -> Unit,
    modifier: Modifier = Modifier,
    onLeft: () -> Unit = {},
    // Bumped to put focus back on the page, e.g. after coming back from the system installer.
    focusTrigger: Int = 0,
) {
    val release = state.releaseOrNull
    LaunchedEffect(release == null) { if (release == null) onBack() }
    if (release == null) return
    val action: Pair<String, () -> Unit>? =
        when (state) {
            is UpdateState.Available -> "Update now" to onUpdateNow
            is UpdateState.ReadyToRestart -> "Restart now" to onRestartNow
            else -> null
        }
    val actionFocusRequester = remember { FocusRequester() }
    val notesFocusRequester = remember { FocusRequester() }
    val hasAction = action != null
    LaunchedEffect(hasAction, focusTrigger) {
        runCatching { if (hasAction) actionFocusRequester.requestFocus() else notesFocusRequester.requestFocus() }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Back -> {
                            onBack()
                            true
                        }
                        Key.DirectionLeft -> {
                            onLeft()
                            true
                        }
                        else -> false
                    }
                },
    ) {
        WhatsNewHeader(
            release = release,
            action = action,
            actionFocusRequester = actionFocusRequester,
            onDownFromAction = { notesFocusRequester.requestFocus() },
        )
        ReleaseNotesCard(
            notes = release.notes,
            focusRequester = notesFocusRequester,
            onUpAtTop = { if (hasAction) actionFocusRequester.requestFocus() },
            modifier = Modifier.weight(1f),
        )
    }
}

/** "‹ Settings", the title and release date, and the action button on the right. */
@Composable
private fun WhatsNewHeader(
    release: UpdateRelease,
    action: Pair<String, () -> Unit>?,
    actionFocusRequester: FocusRequester,
    onDownFromAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PAGE_GAP)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.6.dp)) {
            Icon(
                KaraloIcons.ChevronLeft,
                contentDescription = null,
                tint = KaraloOnSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = "Settings",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                color = KaraloOnSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(29.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "What’s new in ${release.version}",
                    style = MaterialTheme.typography.titleLarge,
                    color = KaraloOnBackground,
                )
                releasedLabel(release.releaseDate)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = KaraloOnSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (action != null) {
                KaraloPillButton(
                    text = action.first,
                    onClick = action.second,
                    primary = true,
                    modifier =
                        Modifier
                            .focusRequester(actionFocusRequester)
                            .testTag(WHATS_NEW_TAG_ACTION)
                            // DOWN goes into the notes; there's nothing above the button.
                            .onPreviewKeyEvent { event ->
                                val down = event.type == KeyEventType.KeyDown
                                if (down && event.key == Key.DirectionDown) onDownFromAction()
                                down && (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                            },
                )
            }
        }
    }
}

@Composable
private fun ReleaseNotesCard(
    notes: ReleaseNotes,
    focusRequester: FocusRequester,
    onUpAtTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKaraloTokens.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    if (!focused) return@drawBehind
                    val stroke = FOCUS_OUTLINE_WIDTH.toPx()
                    val inset = FOCUS_OUTLINE_OFFSET.toPx() + stroke / 2
                    drawRoundRect(
                        color = KaraloOnBackground,
                        topLeft = Offset(-inset, -inset),
                        size = Size(size.width + inset * 2, size.height + inset * 2),
                        cornerRadius = CornerRadius(13.dp.toPx() + inset),
                        style = Stroke(width = stroke),
                    )
                }.background(tokens.surface, NOTES_SHAPE)
                .border(2.dp, tokens.outline, NOTES_SHAPE)
                .onSizeChanged { viewportHeight = it.height }
                .testTag(WHATS_NEW_TAG_NOTES)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                // The notes are one focusable block, scrolled with UP and DOWN: they're text, with
                // nothing inside to focus, and a long list must still be reachable with a remote.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = viewportHeight * SCROLL_STEP_FRACTION
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (scrollState.canScrollForward) scope.launch { scrollState.animateScrollBy(step) }
                            true
                        }
                        Key.DirectionUp -> {
                            if (scrollState.value ==
                                0
                            ) {
                                onUpAtTop()
                            } else {
                                scope.launch { scrollState.animateScrollBy(-step) }
                            }
                            true
                        }
                        Key.DirectionRight -> true
                        else -> false
                    }
                }.focusable(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = NOTES_PADDING,
                        end = NOTES_PADDING,
                        top = NOTES_PADDING,
                        bottom = NOTES_BOTTOM_PADDING,
                    ),
        ) {
            if (notes.isEmpty) {
                Text(
                    text = "No release notes for this version.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp),
                    color = KaraloOnSurfaceVariant,
                )
            }
            NotesSection("NEW", notes.new)
            NotesSection("IMPROVED", notes.improved)
            NotesSection("FIXED", notes.fixed)
        }
        if (scrollState.canScrollForward) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(FADE_HEIGHT)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.7f to tokens.surface,
                                1f to tokens.surface,
                            ),
                            RoundedCornerShape(bottomStart = 13.dp, bottomEnd = 13.dp),
                        ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.7.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-19).dp, y = (-15).dp)
                        .background(tokens.raised, CircleShape)
                        .padding(horizontal = 11.5.dp, vertical = 5.8.dp)
                        .testTag(WHATS_NEW_TAG_MORE),
            ) {
                Icon(
                    KaraloIcons.ChevronDown,
                    contentDescription = null,
                    tint = NotesText,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = if (focused) "Scroll with ▲ ▼" else "More below",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = NotesText,
                )
            }
        }
    }
}

@Composable
private fun NotesSection(
    label: String,
    items: List<String>,
) {
    if (items.isEmpty()) return
    val accent = LocalKaraloTokens.current.accent
    Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.12.em,
                ),
            color = KaraloOnSurfaceVariant,
        )
        for (item in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(BULLET_GAP)) {
                // Centered on the first line: half a 1.5-line-height line of 15.5sp text, minus half the dot.
                Box(modifier = Modifier.padding(top = 8.dp).size(BULLET_SIZE).background(accent, CircleShape))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 23.sp),
                    color = NotesText,
                )
            }
        }
    }
}
