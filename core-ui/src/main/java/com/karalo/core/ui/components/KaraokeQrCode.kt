package com.karalo.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karalo.core.ui.qr.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val QUIET_ZONE_PADDING = 8.dp

/**
 * Single reusable QR display used in all 3 karaoke placements (nav-drawer card, player overlay,
 * empty-queue waiting screen) so sizing/contrast stay visually identical everywhere the spec
 * requires it. [sizeDp] is the only thing callers vary.
 *
 * The bitmap is generated exactly once per distinct [content] via the `LaunchedEffect(content,
 * sizePx)` below, off the main thread ([Dispatchers.Default]) -- NOT inline inside a plain
 * `remember { }` block, since that would still run synchronously during composition. [content] is
 * stable for a session's entire lifetime (it encodes the join URL, which only changes if the
 * session itself does, which this feature's contract says never happens without a fresh app
 * install), so after the first composition this is a pure cache hit: unrelated recompositions
 * (playback progress ticks, queue updates, focus changes) never trigger a re-encode.
 */
@Composable
fun KaraokeQrCode(
    content: String,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.roundToPx() }
    var bitmap by remember(content, sizePx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(content, sizePx) {
        bitmap = withContext(Dispatchers.Default) { generateQrBitmap(content, sizePx) }
    }

    Box(
        modifier =
            modifier
                .size(sizeDp)
                .background(Color.White)
                .padding(QUIET_ZONE_PADDING),
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Scan to join the karaoke session",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
