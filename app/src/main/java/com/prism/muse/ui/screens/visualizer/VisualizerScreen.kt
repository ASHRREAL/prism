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

                // Symmetric spectrum: bass sits at the centre and treble radiates
                // out to BOTH edges (mirrored left/right), each bar mirrored above
                // and below the mid-line. Fills the whole canvas instead of a
                // single one-sided band climbing off one edge.
                fun drawMirroredBars(
                    level: (Int) -> Float,
                    upAlpha: Float,
                    downAlpha: Float,
                    heightFactor: Float,
                    minBar: Float
                ) {
                    val half = w / 2f
                    val slot = half / barCount
                    val stroke = slot * 0.55f
                    for (i in 0 until barCount) {
                        val v = level(i).coerceIn(0f, 1f)
                        val barH = (v * h * heightFactor).coerceAtLeast(minBar)
                        val off = (i + 0.5f) * slot
                        for (x in floatArrayOf(half - off, half + off)) {
                            drawLine(accent.copy(alpha = upAlpha), Offset(x, centerY),
                                Offset(x, centerY - barH), stroke, StrokeCap.Round)
                            drawLine(accent.copy(alpha = downAlpha), Offset(x, centerY),
                                Offset(x, centerY + barH), stroke, StrokeCap.Round)
                        }
                    }
                }

                // Animated Lissajous (a=1, b=2 → figure-8 / "infinity"). Layered as
                // four time-offset echo trails each deformed by a different bin
                // slice of the FFT, so the whole figure breathes asymmetrically
                // instead of just one comet moving. The ratio between the two
                // frequencies warbles slowly between 2 (figure-8) and ~2.7 so the
                // loop subtly morphs between an infinity and a trefoil knot.
                fun drawInfinity(
                    amp: FloatArray,
                    mainAlpha: Float,
                    glowAlpha: Float,
                    phase: Float,
                    radialBoost: Float
                ) {
                    val cx = w / 2
                    val cy = centerY
                    val baseR = min(w, h) * 0.32f
                    val ampLen = amp.size
                    if (ampLen == 0) return
                    val bRatio = 2f + 0.7f * (0.5f + 0.5f * sin(phase * 0.27f))
                    val points = 220
                    val twoPi = 2f * Math.PI.toFloat()

                    fun trailPath(trailIdx: Int, trailPhase: Float): androidx.compose.ui.graphics.Path {
                        val p = androidx.compose.ui.graphics.Path()
                        // Each trail reads a shifted bin slice so it deforms in a
                        // slightly different shape than its neighbours — gives the
                        // figure a real "trailing echoes" feel rather than 4
                        // identical stacked copies.
                        val binOffset = trailIdx * (ampLen / 4)
                        val scaleWobble = 1f + 0.04f * sin(phase * 1.8f + trailIdx)
                        for (i in 0..points) {
                            val u = (i.toFloat() / points) * twoPi
                            val bin = ((i * ampLen / points + binOffset) % ampLen).coerceIn(0, ampLen - 1)
                            val bulge = 1f + amp[bin] * radialBoost * scaleWobble
                            val x = cx + baseR * bulge * sin(u + trailPhase)
                            val y = cy + baseR * 0.62f * bulge * sin(bRatio * u + trailPhase * 0.5f)
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        p.close()
                        return p
                    }

                    // Echo trails back-to-front: outermost first (becomes the
                    // background glow), innermost last (sharpest top layer).
                    val trails = 4
                    for (trail in trails - 1 downTo 0) {
                        val trailPhase = phase - trail * 0.14f
                        val path = trailPath(trail, trailPhase)
                        val tAlpha = mainAlpha * (if (trail == 0) 1f else 0.32f - trail * 0.05f)
                        if (trail == 0 && glowAlpha > 0f) {
                            // Outer-most: layered glow rings for that video-scope bloom.
                            drawPath(path, accent.copy(alpha = glowAlpha * 0.10f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(22f, cap = StrokeCap.Round))
                            drawPath(path, accent.copy(alpha = glowAlpha * 0.32f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(11f, cap = StrokeCap.Round))
                            drawPath(path, accent.copy(alpha = tAlpha),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.4f, cap = StrokeCap.Round))
                        } else {
                            drawPath(path, accent.copy(alpha = tAlpha),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f, cap = StrokeCap.Round))
                        }
                    }

                    // Comet dots — six chase points evenly distributed around the
                    // loop, each pulsing with its own bin so all parts visibly move.
                    val comets = 6
                    for (k in 0 until comets) {
                        val du = phase * 1.6f + k * (twoPi / comets)
                        val binIdx = ((k * ampLen / comets) % ampLen).coerceIn(0, ampLen - 1)
                        val a = 1f + amp[binIdx] * radialBoost
                        val dx = cx + baseR * a * sin(du)
                        val dy = cy + baseR * 0.62f * a * sin(bRatio * du + phase * 0.5f)
                        // Pulse each comet's size with its own bin — the "infinite"
                        // trail reads as many beads streaming around the curve.
                        val pulse = 3f + 2.5f * (0.5f + 0.5f * sin(phase * 2.4f + k))
                        val dim = if (k % 2 == 0) 1f else 0.55f
                        drawCircle(accent.copy(alpha = mainAlpha * dim), pulse, Offset(dx, dy))
                        // Spark on the loud comets.
                        if (amp[binIdx] > 0.35f) {
                            drawCircle(accent.copy(alpha = mainAlpha * 0.25f), pulse * 2.4f, Offset(dx, dy))
                        }
                    }

                    // Slow counter-rotating trefoil overlay (a=1, b=3) so the
                    // figure has a second, slower-moving "ghost" shape behind it.
                    val ghost = androidx.compose.ui.graphics.Path()
                    for (i in 0..points) {
                        val u = (i.toFloat() / points) * twoPi
                        val x = cx + baseR * 0.78f * sin(u - phase * 0.4f)
                        val y = cy + baseR * 0.50f * sin(3f * u + phase * 0.2f)
                        if (i == 0) ghost.moveTo(x, y) else ghost.lineTo(x, y)
                    }
                    ghost.close()
                    drawPath(ghost, accent.copy(alpha = mainAlpha * 0.18f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f, cap = StrokeCap.Round))
                }

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
                        "pattern" -> {
                            // Audio-reactive infinity curve — bins push the
                            // figure-8 outward in time with the spectrum.
                            val t = tick / 90f
                            drawInfinity(levels, mainAlpha = 1f, glowAlpha = 1f, phase = t, radialBoost = 0.55f)
                        }
                        else -> {
                            drawLine(accent.copy(alpha = 0.12f), Offset(0f, centerY), Offset(w, centerY), 1f)
                            drawMirroredBars({ levels[it] }, upAlpha = 0.8f, downAlpha = 0.5f,
                                heightFactor = 0.48f, minBar = 3f)
                        }
                    }
                } else if (state.isPlaying) {
                    // Fallback: wave-based animation if no FFT (mic permission
                    // denied or no session) — driven by the frame clock so it
                    // stays fluid instead of stepping with the position poller.
                    if (style == "pattern") {
                        // Synthetic envelope so the figure-8 still breathes when
                        // there's no microphone capture to drive it.
                        val synth = FloatArray(barCount)
                        val t = tick / 30f
                        for (i in 0 until barCount) {
                            synth[i] = (0.25f + 0.35f * abs(sin(i * 0.30f + t * 0.9f) *
                                cos(i * 0.18f + t * 0.6f))).coerceIn(0f, 1f)
                        }
                        drawInfinity(synth, mainAlpha = 0.85f, glowAlpha = 0.6f, phase = tick / 90f, radialBoost = 0.4f)
                    } else {
                        val t = tick / 60f
                        drawMirroredBars(
                            { i -> 0.35f + 0.55f * abs(sin(i * 0.22f + t * 0.9f) * cos(i * 0.16f + t * 1.3f)) },
                            upAlpha = 0.5f, downAlpha = 0.32f, heightFactor = 0.46f, minBar = 2f
                        )
                        drawCircle(accent.copy(alpha = 0.4f), 6f, Offset(w / 2, centerY))
                    }
                } else {
                    // Idle: subtle ripple
                    if (style == "pattern") {
                        val synth = FloatArray(barCount)
                        val t = tick / 60f
                        for (i in 0 until barCount) {
                            synth[i] = (0.10f + 0.18f * abs(sin(i * 0.30f + t * 0.7f))).coerceIn(0f, 1f)
                        }
                        drawInfinity(synth, mainAlpha = 0.4f, glowAlpha = 0.25f, phase = tick / 110f, radialBoost = 0.25f)
                    } else {
                        val t = tick / 60f
                        drawMirroredBars(
                            { i -> 0.1f + 0.16f * abs(sin(i * 0.3f + t * 0.4f)) },
                            upAlpha = 0.2f, downAlpha = 0.12f, heightFactor = 0.4f, minBar = 1.5f
                        )
                        drawCircle(accent.copy(alpha = 0.15f), 4f, Offset(w / 2, centerY))
                    }
                }
            }

            Row(
                Modifier.padding(start = 22.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                listOf("bars", "wave", "ring", "pattern").forEach { s ->
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
