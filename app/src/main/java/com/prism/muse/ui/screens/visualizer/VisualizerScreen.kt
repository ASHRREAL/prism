package com.prism.muse.ui.screens.visualizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.PlayerBackdrop
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VisualizerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    artUrl: String? = null
) {
    val state by viewModel.state.collectAsState()
    val accent = LocalPrismAccent.current
    val pos = state.positionSec

    // Multiple layers of animated waves/bars
    val transition = rememberInfiniteTransition(label = "viz")
    val phase1 by transition.animateFloat(0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "p1")
    val phase2 by transition.animateFloat(0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "p2")
    val phase3 by transition.animateFloat(0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Restart), label = "p3")
    val phase4 by transition.animateFloat(0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart), label = "p4")

    // Beat-synced pulse
    val beat = remember(pos) { (1f + sin(pos * PI.toFloat() / 2f) * 0.3f).coerceIn(0.5f, 1.5f) }
    var vizMode by remember { mutableStateOf(0) }
    val modes = listOf("bars", "waves", "circles")

    PlayerBackdrop(artUrl = artUrl) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var down = 0f
                    var right = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { if (down > 60f) onBack(); down = 0f; right = 0f },
                        onDragCancel = { down = 0f; right = 0f }
                    ) { _, dy ->
                        down += dy
                    }
                }
                .pointerInput(Unit) {
                    var right = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { if (right > 100f) onBack(); right = 0f },
                        onDragCancel = { right = 0f }
                    ) { _, dx -> right += dx }
                }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹ now playing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(end = 14.dp)
                )
            }
            Text(
                "visualizer",
                style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp),
                color = TextPrimary,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 6.dp)
            )

            Row(Modifier.padding(start = 22.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                modes.forEachIndexed { i, name ->
                    Text(name, style = SectionHeader.copy(fontSize = 18.sp),
                        color = if (i == vizMode) accent else TextSecondary,
                        modifier = Modifier.clickable { vizMode = i })
                }
            }

            // Main visualization canvas
            Canvas(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                val w = size.width
                val h = size.height
                val centerY = h / 2

                when (vizMode) {
                    0 -> { // Bars (mirrored)
                        val barCount = 48
                        val barWidth = w / barCount
                        val glowR = 60f * beat
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(w / 2, centerY)), center = Offset(w / 2, centerY), radius = glowR * 2)
                        for (i in 0 until barCount) {
                            val x = i * barWidth + barWidth / 2
                            val h1 = sin(i * 0.2f + phase1) * cos(i * 0.15f + phase2)
                            val h2 = sin(i * 0.35f + phase3) * cos(i * 0.08f + phase4)
                            val nh = (((h1 + h2) / 2f + 1f) / 2f * h * 0.7f).coerceAtLeast(4f)
                            drawLine(accent.copy(alpha = 0.6f), Offset(x, centerY), Offset(x, centerY - nh), (barWidth * 0.55f).coerceAtLeast(3f), StrokeCap.Round)
                            drawLine(accent.copy(alpha = 0.3f), Offset(x, centerY), Offset(x, centerY + nh), (barWidth * 0.55f).coerceAtLeast(3f), StrokeCap.Round)
                        }
                    }
                    1 -> { // Waves
                        val path = Path()
                        path.moveTo(0f, centerY + sin(phase1) * 40f)
                        for (i in 0..100) {
                            val x = w * i / 100f
                            val y = centerY + sin(i * 0.15f + phase1) * 50f * beat + cos(i * 0.25f + phase3) * 25f
                            path.lineTo(x, y)
                        }
                        drawPath(path, accent.copy(alpha = 0.5f), style = Stroke(3f, cap = StrokeCap.Round))
                        val path2 = Path()
                        path2.moveTo(0f, centerY + sin(phase2) * 30f)
                        for (i in 0..100) {
                            path2.lineTo(w * i / 100f, centerY + sin(i * 0.2f + phase2) * 35f + cos(i * 0.3f + phase4) * 20f)
                        }
                        drawPath(path2, accent.copy(alpha = 0.25f), style = Stroke(2f, cap = StrokeCap.Round))
                    }
                    2 -> { // Circles
                        val rings = 6
                        val maxR = minOf(w, h) * 0.35f
                        for (r in rings downTo 1) {
                            val rad = maxR * r.toFloat() / rings.toFloat() * (0.7f + 0.3f * sin(phase1 + r * 0.7f) * beat)
                            drawCircle(accent.copy(alpha = (0.25f / r).coerceAtMost(1f)), rad, Offset(w / 2, centerY),
                                style = Stroke((3f * r / rings).coerceAtLeast(1f), cap = StrokeCap.Round))
                        }
                        drawCircle(accent.copy(alpha = (0.5f * beat).coerceIn(0f, 1f)), 8f, Offset(w / 2, centerY))
                    }
                }
            }

            // Footer
            Text(
                "audio spectrum",
                style = MaterialTheme.typography.bodyLarge,
                color = TextTertiary,
                modifier = Modifier.padding(start = 22.dp, bottom = 24.dp)
            )
        }
    }
}
