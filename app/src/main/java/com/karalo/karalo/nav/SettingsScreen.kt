@file:Suppress("MagicNumber") // swatch gradients are the design's own values

package com.karalo.karalo.nav

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.karalo.core.common.model.SeasonalTheme
import com.karalo.core.ui.theme.HalloweenPumpkin
import com.karalo.core.ui.theme.HalloweenSidebarStart
import com.karalo.core.ui.theme.KaraloCoralAccent
import com.karalo.core.ui.theme.KaraloOnSurfaceVariant
import com.karalo.core.ui.theme.KaraloPageHeaderHeight
import com.karalo.core.ui.theme.KaraloPagePadding
import com.karalo.core.ui.theme.KaraloRailItemActive
import com.karalo.core.ui.theme.KaraloVioletPrimary
import com.karalo.core.ui.theme.LocalKaraloTokens
import com.karalo.feature.update.domain.UpdateState
import com.karalo.feature.update.ui.UpdateCard
import com.karalo.feature.update.ui.WhatsNewPage

const val SETTINGS_TAG_THEME_PREFIX = "settings_theme_"
const val SETTINGS_TAG_VERSION = "settings_version"

private val OPTION_SHAPE = RoundedCornerShape(13.dp)
private val OPTION_RING_WIDTH = 2.5.dp
private val SWATCH_SIZE = 42.dp
private val SWATCH_SHAPE = RoundedCornerShape(10.dp)
private val RADIO_SIZE = 25.dp
private val RADIO_DOT_SIZE = 12.dp
private const val OPTION_FOCUSED_SCALE = 1.03f

private data class ThemeOption(
    val theme: SeasonalTheme,
    val title: String,
    val description: String,
    val swatch: Brush,
)

private val THEME_OPTIONS =
    listOf(
        ThemeOption(
            SeasonalTheme.DEFAULT,
            "Default",
            "Karalo violet and coral",
            Brush.linearGradient(listOf(KaraloVioletPrimary, KaraloCoralAccent)),
        ),
        ThemeOption(
            SeasonalTheme.HALLOWEEN,
            "Halloween",
            "Pumpkin orange, bats and a Halloween playlist",
            Brush.linearGradient(listOf(HalloweenSidebarStart, HalloweenPumpkin)),
        ),
    )

/**
 * Settings: the update card (when there's a newer version), the seasonal theme picker and the
 * app's version. The host's theme choice applies to the TV right away and, through the backend,
 * to every phone joined to the session. "What's new" opens [WhatsNewPage] in place of the page.
 *
 * Only claims focus when *selected* (clicked) in the nav rail, via [contentFocusTrigger] -- landing
 * on the update card's main button if there is one, otherwise on the currently-applied theme.
 * [contentFocusTrigger] is bumped only on selection, not on merely *focusing* Settings in the rail
 * to preview it (see KaraloNavRailContent) -- claiming focus unconditionally on every mount would
 * steal it away from the rail the instant the item is focused, since focusing already navigates
 * here to preview it.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val seasonalTheme by viewModel.seasonalTheme.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    SettingsScreenContent(
        seasonalTheme = seasonalTheme,
        onThemeSelected = viewModel::onThemeSelected,
        updateState = updateState,
        installedVersion = viewModel.installedVersion,
        onUpdateNow = viewModel::onUpdateNow,
        onRestartNow = viewModel::onRestartNow,
        contentFocusTrigger = contentFocusTrigger,
        railFocusRequester = railFocusRequester,
        modifier = modifier,
    )
}

@Suppress("LongMethod", "LongParameterList") // one page, laid out top to bottom
@Composable
internal fun SettingsScreenContent(
    seasonalTheme: SeasonalTheme,
    onThemeSelected: (SeasonalTheme) -> Unit,
    modifier: Modifier = Modifier,
    updateState: UpdateState = UpdateState.UpToDate,
    installedVersion: String = "",
    onUpdateNow: () -> Unit = {},
    onRestartNow: () -> Unit = {},
    contentFocusTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
) {
    val focusRequesters = remember { THEME_OPTIONS.associate { it.theme to FocusRequester() } }
    val updatePrimaryFocusRequester = remember { FocusRequester() }
    val updateWhatsNewFocusRequester = remember { FocusRequester() }
    val showUpdateCard = updateState != UpdateState.UpToDate
    // The card's first button: Update now or Restart now, or What's new while downloading.
    val cardFirstFocusRequester =
        if (updateState is UpdateState.Downloading) updateWhatsNewFocusRequester else updatePrimaryFocusRequester
    var showWhatsNew by rememberSaveable { mutableStateOf(false) }
    var returnFromWhatsNew by remember { mutableIntStateOf(0) }
    // While focus is on the card's second button, LEFT moves to the first one, not the rail.
    var cardSecondButtonFocused by remember { mutableStateOf(false) }
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(contentFocusTrigger) {
        if (contentFocusTrigger > consumedFocusTrigger) {
            consumedFocusTrigger = contentFocusTrigger
            showWhatsNew = false
            if (showUpdateCard) {
                cardFirstFocusRequester.requestFocus()
            } else {
                focusRequesters
                    .getValue(
                        seasonalTheme,
                    ).requestFocus()
            }
        }
    }
    LaunchedEffect(returnFromWhatsNew) {
        if (returnFromWhatsNew > 0 && showUpdateCard) runCatching { updateWhatsNewFocusRequester.requestFocus() }
    }
    // Update now goes away once the download starts; What's new, the card's only button left,
    // takes over focus (with nothing focused, BACK would leave the app).
    var downloadStarted by remember { mutableIntStateOf(0) }
    LaunchedEffect(downloadStarted) {
        if (downloadStarted > 0) runCatching { updateWhatsNewFocusRequester.requestFocus() }
    }
    // Restart now opens another screen (Android's installer, or its "Install unknown apps"
    // setting), and coming back from it leaves nothing focused: put focus back on the card.
    var leftForInstaller by remember { mutableStateOf(false) }
    var whatsNewFocusTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && leftForInstaller) {
                    leftForInstaller = false
                    if (showWhatsNew) whatsNewFocusTrigger++ else runCatching { cardFirstFocusRequester.requestFocus() }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val restartNow = {
        leftForInstaller = true
        onRestartNow()
    }

    if (showWhatsNew) {
        WhatsNewPage(
            state = updateState,
            onBack = {
                showWhatsNew = false
                returnFromWhatsNew++
            },
            onUpdateNow = onUpdateNow,
            onRestartNow = restartNow,
            onLeft = { railFocusRequester?.requestFocus() },
            focusTrigger = whatsNewFocusTrigger,
            modifier = modifier.padding(KaraloPagePadding),
        )
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // Focused rows scroll into view as the D-pad moves down the page.
                .verticalScroll(rememberScrollState())
                .padding(KaraloPagePadding)
                .backAndLeftToRail(railFocusRequester, leftGoesToRail = { !cardSecondButtonFocused }),
    ) {
        // Same title style and header height as the History page, so the two titles line up.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.heightIn(min = KaraloPageHeaderHeight),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (showUpdateCard) {
            Spacer(modifier = Modifier.height(21.dp))
            UpdateCard(
                state = updateState,
                installedVersion = installedVersion,
                onUpdateNow = {
                    onUpdateNow()
                    downloadStarted++
                },
                onRestartNow = restartNow,
                onWhatsNew = { showWhatsNew = true },
                primaryFocusRequester = updatePrimaryFocusRequester,
                whatsNewFocusRequester = updateWhatsNewFocusRequester,
                onSecondButtonFocused = { cardSecondButtonFocused = it },
                // DOWN from the card goes to the first theme, from either button.
                modifier = Modifier.downTo(focusRequesters.getValue(THEME_OPTIONS.first().theme)),
            )
        }
        Spacer(modifier = Modifier.height(33.dp))
        ThemeSection(
            seasonalTheme = seasonalTheme,
            onThemeSelected = onThemeSelected,
            focusRequesters = focusRequesters,
            onUpFromFirst = if (showUpdateCard) ({ cardFirstFocusRequester.requestFocus() }) else null,
        )
        Spacer(modifier = Modifier.height(33.dp))
        AboutSection(versionLabel = if (showUpdateCard) installedVersion else "$installedVersion · Up to date")
    }
}

/**
 * BACK or LEFT while browsing opens the drawer with Settings' own item focused, matching
 * Home/Search's own BACK handling (see the matching comment on HomeScreenContent's own Column for
 * why this is a raw key event intercept rather than a BackHandler). LEFT is handled the same way
 * since the page is one column -- there's nothing else to its left, except the update card's first
 * button (hence [leftGoesToRail]).
 */
private fun Modifier.backAndLeftToRail(
    railFocusRequester: FocusRequester?,
    leftGoesToRail: () -> Boolean,
): Modifier =
    onPreviewKeyEvent { keyEvent ->
        val down = keyEvent.type == KeyEventType.KeyDown
        val toRail = down && (keyEvent.key == Key.Back || (keyEvent.key == Key.DirectionLeft && leftGoesToRail()))
        if (toRail && railFocusRequester != null) {
            railFocusRequester.requestFocus()
            true
        } else {
            false
        }
    }

/** DOWN moves focus to [target]. */
private fun Modifier.downTo(target: FocusRequester): Modifier =
    onPreviewKeyEvent { event ->
        val isDown = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
        if (isDown) target.requestFocus()
        isDown
    }

/**
 * The seasonal theme picker. UP from the first option calls [onUpFromFirst] (the update card),
 * or goes nowhere when it's null: it mustn't leak into the rail.
 */
@Composable
private fun ThemeSection(
    seasonalTheme: SeasonalTheme,
    onThemeSelected: (SeasonalTheme) -> Unit,
    focusRequesters: Map<SeasonalTheme, FocusRequester>,
    onUpFromFirst: (() -> Unit)?,
) {
    SectionLabel("SEASONAL THEME")
    Spacer(modifier = Modifier.height(23.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((index, option) in THEME_OPTIONS.withIndex()) {
            ThemeOptionRow(
                option = option,
                selected = option.theme == seasonalTheme,
                onClick = { onThemeSelected(option.theme) },
                modifier =
                    Modifier
                        .focusRequester(focusRequesters.getValue(option.theme))
                        .testTag(SETTINGS_TAG_THEME_PREFIX + option.theme.wireName)
                        .onPreviewKeyEvent { event ->
                            val upFromFirst =
                                index == 0 && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp
                            if (upFromFirst) onUpFromFirst?.invoke()
                            upFromFirst
                        },
            )
        }
    }
    Spacer(modifier = Modifier.height(23.dp))
    Text(
        text = "The theme also appears on every phone connected to the session.",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        color = KaraloOnSurfaceVariant,
    )
}

@Composable
private fun AboutSection(versionLabel: String) {
    SectionLabel("ABOUT")
    Spacer(modifier = Modifier.height(12.dp))
    VersionRow(
        label = versionLabel,
        // Nothing below the last row.
        modifier = Modifier.onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.12.em,
            ),
        color = KaraloOnSurfaceVariant,
    )
}

/**
 * About → Version. Focusable though it does nothing when clicked, so the D-pad can reach (and
 * scroll to) the end of the page.
 */
@Composable
private fun VersionRow(
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKaraloTokens.current
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth().testTag(SETTINGS_TAG_VERSION),
        shape = ClickableSurfaceDefaults.shape(shape = OPTION_SHAPE),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = tokens.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = KaraloRailItemActive.compositeOver(tokens.surface),
                focusedContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = OPTION_FOCUSED_SCALE),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 21.dp, vertical = 17.dp),
        ) {
            Text(text = "Version", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                color = KaraloOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    option: ThemeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKaraloTokens.current
    val ring = if (selected) tokens.accent else tokens.outlineStrong
    val ringBorder = Border(BorderStroke(OPTION_RING_WIDTH, ring), shape = OPTION_SHAPE)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = OPTION_SHAPE),
        // The accent ring already marks the *selected* theme, so focus is shown by lifting the
        // row (a slight scale and a lighter fill) instead of another outline.
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = tokens.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = KaraloRailItemActive.compositeOver(tokens.surface),
                focusedContentColor = MaterialTheme.colorScheme.onBackground,
                pressedContainerColor = KaraloRailItemActive.compositeOver(tokens.surface),
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = ringBorder,
                focusedBorder = ringBorder,
                pressedBorder = ringBorder,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = OPTION_FOCUSED_SCALE),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(19.dp),
            modifier = Modifier.padding(horizontal = 21.dp, vertical = 17.dp),
        ) {
            Box(modifier = Modifier.size(SWATCH_SIZE).background(option.swatch, SWATCH_SHAPE))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = KaraloOnSurfaceVariant,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(RADIO_SIZE).border(3.dp, ring, CircleShape),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(RADIO_DOT_SIZE)
                            .background(if (selected) tokens.accent else Color.Transparent, CircleShape),
                )
            }
        }
    }
}
