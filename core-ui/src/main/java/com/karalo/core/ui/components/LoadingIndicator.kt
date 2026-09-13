package com.karalo.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

private const val STROKE_WIDTH_DP = 4
private const val SWEEP_ANGLE_DEGREES = 270f
private const val ROTATION_DURATION_MS = 1200

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loadingRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(ROTATION_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "loadingRotationValue",
    )
    val color = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(48.dp)) {
            rotate(rotation) {
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = SWEEP_ANGLE_DEGREES,
                    useCenter = false,
                    style = Stroke(width = STROKE_WIDTH_DP.dp.toPx()),
                    size = Size(size.width, size.height),
                )
            }
        }
    }
}
