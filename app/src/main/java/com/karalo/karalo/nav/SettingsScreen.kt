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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

const val SETTINGS_TAG_THEME_PREFIX = "settings_theme_"

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
 * Settings: the seasonal theme picker. The host's choice applies to the TV right away and, through
 * the backend, to every phone joined to the session.
 *
 * Only claims focus when *selected* (clicked) in the nav rail, via [contentFocusTrigger] -- landing
 * on the currently-applied theme. [contentFocusTrigger] is bumped only on selection, not on merely
 * *focusing* Settings in the rail to preview it (see KaraloNavRailContent) -- claiming focus
 * unconditionally on every mount would steal it away from the rail the instant the item is
 * focused, since focusing already navigates here to preview it.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val seasonalTheme by viewModel.seasonalTheme.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    SettingsScreenContent(
        seasonalTheme = seasonalTheme,
        onThemeSelected = viewModel::onThemeSelected,
        contentFocusTrigger = contentFocusTrigger,
        railFocusRequester = railFocusRequester,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreenContent(
    seasonalTheme: SeasonalTheme,
    onThemeSelected: (SeasonalTheme) -> Unit,
    modifier: Modifier = Modifier,
    contentFocusTrigger: Int = 0,
    railFocusRequester: FocusRequester? = null,
) {
    val focusRequesters = remember { THEME_OPTIONS.associate { it.theme to FocusRequester() } }
    var consumedFocusTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(contentFocusTrigger) {
        if (contentFocusTrigger > consumedFocusTrigger) {
            consumedFocusTrigger = contentFocusTrigger
            focusRequesters.getValue(seasonalTheme).requestFocus()
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(KaraloPagePadding)
                // BACK or LEFT while browsing opens the drawer with Settings' own item focused,
                // matching Home/Search's own BACK handling (see the matching comment on
                // HomeScreenContent's own Column for why this is a raw key event intercept rather
                // than a BackHandler). LEFT is handled the same way since the options are one
                // column -- there's nothing else to their left.
                .onPreviewKeyEvent { keyEvent ->
                    val isBackOrLeftDown =
                        keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Back || keyEvent.key == Key.DirectionLeft)
                    if (isBackOrLeftDown && railFocusRequester != null) {
                        railFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                },
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
        Spacer(modifier = Modifier.height(33.dp))
        Text(
            text = "SEASONAL THEME",
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.12.em,
                ),
            color = KaraloOnSurfaceVariant,
        )
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
                            // Nothing above the first option; keep UP from leaking into the rail.
                            .onPreviewKeyEvent { event ->
                                index == 0 && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp
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
