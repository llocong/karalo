package com.karalo.core.ui.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Pure, allocation-cheap-per-call QR encoding via ZXing's core (non-Android) `MultiFormatWriter`
 * -- kept entirely off the UI thread by [com.karalo.core.ui.components.KaraokeQrCode], since
 * encoding + the resulting BitMatrix-to-Bitmap conversion is real CPU work this app's reference
 * (weak Google TV) hardware shouldn't do inline with a recomposition.
 *
 * `RGB_565` (not `ARGB_8888`) -- a static black/white QR needs no alpha channel, halving the
 * bitmap's memory footprint, worth calling out given this app's documented low-RAM-device
 * sensitivity. Error correction level M + zero margin (the caller owns the quiet zone/contrast
 * border via its own background, so every placement gets a consistent look) per this feature's
 * "high error correction, sufficient quiet zone" requirement.
 */
fun generateQrBitmap(
    content: String,
    sizePx: Int,
): Bitmap {
    val hints =
        mapOf(
            EncodeHintType.MARGIN to 0,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
    val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
