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
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = com.prism.muse.PrismApp.graph(context).prefs
    val style by prefs.visualizerStyle.collectAsState()

    var sessionIdState by remember { mutableStateOf(viewModel.audioSessionId) }

    val barCount = 48
    // Raw capture targets (written from the Visualizer callback thread) and the
    // displayed levels, eased toward the targets every frame: fast attack, slow
    // decay. This is what stops the bars from jumping wildly between captures.
    val targetLevels = remember { FloatArray(barCount) }
    val levels = remember { FloatArray(barCount) }
    var hasFft by remember { mutableStateOf(false) }
    var frameTick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            for (i in 0 until barCount) {
                val t = targetLevels[i]
                val c = levels[i]
                levels[i] = c + (t - c) * if (t > c) 0.35f else 0.10f
            }
            frameTick++
        }
    }

    // The Visualizer audio effect needs RECORD_AUDIO; ask when the screen opens
    // instead of at app launch. Without it we fall back to the wave animation.
    var micGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }
    LaunchedEffect(Unit) {
        if (!micGranted) permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val id = runCatching { viewModel.audioSessionId }.getOrDefault(0)
            if (id != 0 && id != sessionIdState) {
                sessionIdState = id
            }
            kotlinx.coroutines.delay(500)
        }
    }

    DisposableEffect(sessionIdState, micGranted) {
        val viz = if (sessionIdState != 0 && micGranted) {
            runCatching {
                Visualizer(sessionIdState).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1] / 2
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(viz: Visualizer?, bytes: ByteArray?, rate: Int) = Unit
                        override fun onFftDataCapture(viz: Visualizer?, bytes: ByteArray?, rate: Int) {
                            // The FFT buffer is complex pairs: [dcRe, nyquistRe,
                            // re1, im1, re2, im2, …] — treating raw bytes as
                            // magnitudes (the old code) produced garbage jumps.
                            if (bytes == null || bytes.size < 4) return
                            val bins = bytes.size / 2 - 1
                            if (bins <= 0) return
                            for (i in 0 until barCount) {
                                // Quadratic bin mapping: more bars for the
                                // musical low/mid range.
                                val f = i.toFloat() / (barCount - 1)
                                val bin = (1 + (bins - 1) * f * f).toInt().coerceIn(1, bins)
                                val re = bytes[2 * bin].toInt()
                                val im = bytes[2 * bin + 1].toInt()
                                val mag = kotlin.math.sqrt((re * re + im * im).toFloat()) / 180f
                                targetLevels[i] =
                                    (kotlin.math.ln(1f + mag * 6f) / kotlin.math.ln(7f)).coerceIn(0f, 1f)
                            }
                            hasFft = true
                        }
                    }, Visualizer.getMaxCaptureRate(), false, true)
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
                // Reading frameTick redraws the canvas every frame while the
                // easing loop runs — the smoothing itself happens up in levels[].
                val tick = frameTick
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                val centerY = h / 2
                val barWidth = w / barCount

                if (hasFft && state.isPlaying) {
                    // Real FFT data, eased at 60fps
                    when (style) {
                        "wave" -> {
                            val step = w / (barCount - 1)
                            fun spectrumPath(sign: Float, amp: Float): androidx.compose.ui.graphics.Path {
                                val p = androidx.compose.ui.graphics.Path()
                                p.moveTo(0f, centerY - sign * levels[0] * h * amp)
                                for (i in 1 until barCount) {
                                    p.lineTo(i * step, centerY - sign * levels[i] * h * amp)
                                }
                                return p
                            }
                            drawLine(accent.copy(alpha = 0.15f), Offset(0f, centerY), Offset(w, centerY), 1.5f)
                            drawPath(spectrumPath(1f, 0.38f), accent.copy(alpha = 0.85f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f, cap = StrokeCap.Round))
                            drawPath(spectrumPath(-1f, 0.24f), accent.copy(alpha = 0.3f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f, cap = StrokeCap.Round))
                        }
                        "ring" -> {
                            val cx = w / 2
                            val cy = centerY
                            val r0 = min(w, h) * 0.22f
                            drawCircle(accent.copy(alpha = 0.18f), r0 - 8f, Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f))
                            val twoPi = 2f * Math.PI.toFloat()
                            for (i in 0 until barCount) {
                                val angle = (i.toFloat() / barCount) * twoPi - twoPi / 4
                                val len = 6f + levels[i] * r0 * 1.15f
                                drawLine(
                                    accent.copy(alpha = 0.75f),
                                    Offset(cx + cos(angle) * r0, cy + sin(angle) * r0),
                                    Offset(cx + cos(angle) * (r0 + len), cy + sin(angle) * (r0 + len)),
                                    5f, StrokeCap.Round
                                )
                            }
                        }
                        else -> {
                            val minBar = 3f
                            for (i in 0 until barCount) {
                                val barH = (levels[i] * h * 0.62f).coerceAtLeast(minBar)
                                val x = i * barWidth + barWidth / 2
                                drawLine(accent.copy(alpha = 0.75f), Offset(x, centerY), Offset(x, centerY - barH),
                                    barWidth * 0.5f, StrokeCap.Round)
                                drawLine(accent.copy(alpha = 0.25f), Offset(x, centerY), Offset(x, centerY + barH * 0.6f),
                                    barWidth * 0.5f, StrokeCap.Round)
                            }
                        }
                    }
                } else if (state.isPlaying) {
                    // Fallback: wave-based animation if no FFT (mic permission
                    // denied or no session) — driven by the frame clock so it
                    // stays fluid instead of stepping with the position poller.
                    val t = tick / 60f
                    for (i in 0 until barCount) {
                        val nh = (0.35f + 0.65f * abs(sin(i * 0.22f + t * 0.9f) * cos(i * 0.16f + t * 1.3f))) * h * 0.55f
                        val x = i * barWidth + barWidth / 2
                        drawLine(accent.copy(alpha = 0.5f), Offset(x, centerY), Offset(x, centerY - nh),
                            barWidth * 0.5f, StrokeCap.Round)
                        drawLine(accent.copy(alpha = 0.2f), Offset(x, centerY), Offset(x, centerY + nh * 0.6f),
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

            Row(
                Modifier.padding(start = 22.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                listOf("bars", "wave", "ring").forEach { s ->
                    Text(
                        s,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s == style) accent else TextTertiary,
                        modifier = Modifier.clickable { prefs.setVisualizerStyle(s) }
                    )
                }
            }
        }
    }
}
