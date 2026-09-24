package com.karalo.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The one content margin every top-level page (Search, Home, History, Settings) uses on all four
 * sides, measured from the nav rail's edge and the screen's -- the "Karalo Themes" design's
 * 3.6cqw content inset, which also clears the TV overscan safe zone once the rail sits to its left.
 * Rows that scroll horizontally apply it as their own content padding (rather than the page
 * insetting them) so a focused edge tile can scale up into it instead of being clipped.
 */
val KaraloPagePadding = 34.dp

/**
 * Minimum height of a page's title row (History's title + action buttons, Settings' title), with
 * the title centered in it -- so page titles sit on the same line whether or not the page has
 * header actions beside them.
 */
val KaraloPageHeaderHeight = 40.dp
