package com.prism.muse.ui.screens.visualizer

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.PlayerBackdrop
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun VisualizerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    artUrl: String? = null
) {
    val state by viewModel.state.collectAsState()
    val accent = LocalPrismAccent.current

    var sessionIdState by remember { mutableStateOf(viewModel.audioSessionId) }
    var waveformBytes by remember { mutableStateOf(ByteArray(0)) }
    var fftBytes by remember { mutableStateOf(ByteArray(0)) }

    LaunchedEffect(Unit) {
        while (true) {
            val id = runCatching { viewModel.audioSessionId }.getOrDefault(0)
            if (id != 0 && id != sessionIdState) {
                sessionIdState = id
            }
            kotlinx.coroutines.delay(500)
        }
    }

    DisposableEffect(sessionIdState) {
        val viz = if (sessionIdState != 0) {
            runCatching {
                Visualizer(sessionIdState).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1] / 2
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(viz: Visualizer?, bytes: ByteArray?, rate: Int) {
                            if (bytes != null) waveformBytes = bytes.copyOf()
                        }
                        override fun onFftDataCapture(viz: Visualizer?, bytes: ByteArray?, rate: Int) {
                            if (bytes != null) fftBytes = bytes.copyOf()
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, true, true)
                    enabled = true
                }
            }.getOrNull()
        } else null
        onDispose { viz?.apply { enabled = false; release() } }
    }

    PlayerBackdrop(artUrl = artUrl) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .pointerInput(Unit) {
                    var down = 0f
                    var right = 0f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.pressed) {
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                down += dy
                                right += dx
                            } else {
                                if (down > 60f) onBack()
                                else if (right > 100f) onBack()
                                down = 0f
                                right = 0f
                            }
                        }
                    }
                }
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹ now playing", style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            }
            Text("visualizer", style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = TextPrimary,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 6.dp))

            Canvas(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                val centerY = h / 2
                val barCount = 48
                val barWidth = w / barCount

                if (fftBytes.size >= barCount) {
                    // Real FFT data
                    for (i in 0 until barCount) {
                        val magnitude = (fftBytes.getOrElse(i) { 0 }.toInt() and 0xFF) / 255f
                        val barH = magnitude * h * 0.75f
                        val x = i * barWidth + barWidth / 2
                        drawLine(accent.copy(alpha = 0.7f), Offset(x, centerY), Offset(x, centerY - barH),
                            barWidth * 0.5f, StrokeCap.Round)
                        drawLine(accent.copy(alpha = 0.3f), Offset(x, centerY), Offset(x, centerY + barH),
                            barWidth * 0.5f, StrokeCap.Round)
                    }
                } else if (state.isPlaying) {
                    // Fallback: wave-based animation if no FFT
                    val t = state.positionSec
                    for (i in 0 until barCount) {
                        val nh = (0.35f + 0.65f * abs(sin(i * 0.22f + t * 0.5f) * cos(i * 0.16f + t * 0.7f))) * h * 0.65f
                        val x = i * barWidth + barWidth / 2
                        drawLine(accent.copy(alpha = 0.5f), Offset(x, centerY), Offset(x, centerY - nh),
                            barWidth * 0.5f, StrokeCap.Round)
                        drawLine(accent.copy(alpha = 0.2f), Offset(x, centerY), Offset(x, centerY + nh),
                            barWidth * 0.5f, StrokeCap.Round)
                    }
                    drawCircle(accent.copy(alpha = 0.4f), 6f, Offset(w / 2, centerY))
                } else {
                    // Idle: subtle ripple
                    for (i in 0 until barCount) {
                        val nh = (0.1f + 0.15f * abs(sin(i * 0.3f))) * h * 0.3f
                        val x = i * barWidth + barWidth / 2
                        drawLine(accent.copy(alpha = 0.2f), Offset(x, centerY), Offset(x, centerY - nh),
                            barWidth * 0.4f, StrokeCap.Round)
                        drawLine(accent.copy(alpha = 0.1f), Offset(x, centerY), Offset(x, centerY + nh),
                            barWidth * 0.4f, StrokeCap.Round)
                    }
                    drawCircle(accent.copy(alpha = 0.15f), 4f, Offset(w / 2, centerY))
                }
            }

            Text("audio spectrum", style = MaterialTheme.typography.bodyLarge, color = TextTertiary,
                modifier = Modifier.padding(start = 22.dp, bottom = 24.dp))
        }
    }
}
