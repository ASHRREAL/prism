package com.prism.muse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Slow-moving layered waves + radial glow behind an accent color, echoing the
 * floating-glass Windows Phone hub reference: deep base, soft top-light,
 * three translucent wave layers drifting at different speeds/depths.
 */
@Composable
fun WaveBackground(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "waves")
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase1"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase2"
    )
    val phase3 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase3"
    )

    val deep = lerp(accent, Color.Black, 0.78f)
    val mid = lerp(accent, Color.Black, 0.55f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(lerp(accent, Color.Black, 0.35f), deep, Color.Black)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Soft top-left bloom, like light hitting glass.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.05f),
                    radius = size.width * 0.9f
                )
            )

            drawWaveLayer(phase1, mid.copy(alpha = 0.28f), amplitude = 46f, baseline = 0.62f)
            drawWaveLayer(phase2, accent.copy(alpha = 0.20f), amplitude = 64f, baseline = 0.74f)
            drawWaveLayer(phase3, Color.White.copy(alpha = 0.06f), amplitude = 28f, baseline = 0.83f)
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveLayer(
    phase: Float,
    color: Color,
    amplitude: Float,
    baseline: Float
) {
    val width = size.width
    val height = size.height
    val baseY = height * baseline
    val path = Path().apply {
        moveTo(0f, height)
        lineTo(0f, baseY)
        val steps = 64
        for (i in 0..steps) {
            val x = width * i / steps
            val y = baseY + amplitude * sin((i.toFloat() / steps) * 2 * PI.toFloat() * 1.4f + phase)
            lineTo(x, y)
        }
        lineTo(width, height)
        close()
    }
    drawPath(path, color = color)
}
