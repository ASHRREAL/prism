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
import kotlin.math.pow
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

                // "pattern" mode — a ferrofluid droplet: a gooey metallic blob whose
                // rim erupts into audio-reactive spikes (sharpened with a power curve
                // so loud bins read as needles). Filled with a liquid-metal gradient
                // plus a rim glow and a specular pool so it looks wet and 3D.
                fun drawFerrofluid(
                    amp: FloatArray,
                    phase: Float,
                    mainAlpha: Float,
                    glowAlpha: Float
                ) {
                    val n = amp.size
                    if (n == 0) return
                    val cx = w / 2
                    val cy = centerY
                    val twoPi = 2f * Math.PI.toFloat()
                    val baseR = min(w, h) * 0.17f
                    val maxSpike = min(w, h) * 0.32f
                    val points = 256
                    val spin = phase * 0.15f

                    // Radius as a function of angle: a mirrored, interpolated bin
                    // sample (so the blob is left/right symmetric and smooth),
                    // sharpened into spikes, plus two rotating harmonics so the
                    // membrane keeps rippling even when the audio is quiet.
                    fun radiusAt(u: Float): Float {
                        val frac = abs(((u / twoPi) % 1f) * 2f - 1f)
                        val fi = frac * (n - 1)
                        val i0 = fi.toInt().coerceIn(0, n - 1)
                        val i1 = (i0 + 1).coerceAtMost(n - 1)
                        val lvl = amp[i0] + (amp[i1] - amp[i0]) * (fi - i0)
                        val spike = lvl.pow(1.7f)
                        val wobble = 0.08f * (sin(u * 5f + phase * 1.3f) + 0.5f * sin(u * 9f - phase))
                        return baseR * (1f + wobble) + maxSpike * spike
                    }

                    val path = androidx.compose.ui.graphics.Path()
                    for (i in 0..points) {
                        val u = (i.toFloat() / points) * twoPi
                        val r = radiusAt(u)
                        val x = cx + r * cos(u + spin)
                        val y = cy + r * sin(u + spin)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()

                    // Under-glow bloom.
                    if (glowAlpha > 0f) {
                        drawPath(path, accent.copy(alpha = glowAlpha * 0.12f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(26f, cap = StrokeCap.Round))
                        drawPath(path, accent.copy(alpha = glowAlpha * 0.22f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(12f, cap = StrokeCap.Round))
                    }
                    // Liquid-metal body — lit from the top-left.
                    val fill = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.lerp(accent, Color.White, 0.35f).copy(alpha = mainAlpha),
                            accent.copy(alpha = mainAlpha * 0.9f),
                            androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.5f).copy(alpha = mainAlpha * 0.95f)
                        ),
                        center = Offset(cx - baseR * 0.3f, cy - baseR * 0.5f),
                        radius = baseR + maxSpike * 0.9f
                    )
                    drawPath(path, fill)
                    // Bright wet rim.
                    drawPath(
                        path,
                        androidx.compose.ui.graphics.lerp(accent, Color.White, 0.4f).copy(alpha = mainAlpha * 0.9f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f, cap = StrokeCap.Round)
                    )
                    // Specular pool (kept near the core so it stays inside the body).
                    drawCircle(Color.White.copy(alpha = mainAlpha * 0.16f), baseR * 0.42f,
                        Offset(cx - baseR * 0.3f, cy - baseR * 0.5f))
                    drawCircle(Color.White.copy(alpha = mainAlpha * 0.5f), baseR * 0.12f,
                        Offset(cx - baseR * 0.36f, cy - baseR * 0.58f))
                }

                if (hasFft && state.isPlaying) {
                    // Real FFT data, eased at 60fps
                    when (style) {
                        "wave" -> {
                            // Symmetric spectrum wave: bass at the centre, treble
                            // radiating to both edges (mirrored left/right), and
                            // reflected with equal amplitude above and below the
                            // mid-line so it fills the whole canvas.
                            val half = w / 2f
                            fun ampAtX(x: Float): Float {
                                val d = (abs(x - half) / half).coerceIn(0f, 1f)
                                val idx = (d * (barCount - 1)).toInt().coerceIn(0, barCount - 1)
                                return levels[idx]
                            }
                            fun wavePath(sign: Float, scale: Float): androidx.compose.ui.graphics.Path {
                                val p = androidx.compose.ui.graphics.Path()
                                val steps = 72
                                for (s in 0..steps) {
                                    val x = s / steps.toFloat() * w
                                    val y = centerY - sign * ampAtX(x) * h * scale
                                    if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
                                }
                                return p
                            }
                            drawLine(accent.copy(alpha = 0.12f), Offset(0f, centerY), Offset(w, centerY), 1f)
                            drawPath(wavePath(1f, 0.44f), accent.copy(alpha = 0.85f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f, cap = StrokeCap.Round))
                            drawPath(wavePath(-1f, 0.44f), accent.copy(alpha = 0.85f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f, cap = StrokeCap.Round))
                            drawPath(wavePath(1f, 0.28f), accent.copy(alpha = 0.3f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f, cap = StrokeCap.Round))
                            drawPath(wavePath(-1f, 0.28f), accent.copy(alpha = 0.3f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f, cap = StrokeCap.Round))
                        }
                        "ring" -> {
                            // Kaleidoscopic ring: bars radiate BOTH outward and
                            // inward from the circle, mirrored around the vertical
                            // axis so the spectrum wraps symmetrically all the way
                            // around and fills the space around the centre.
                            val cx = w / 2
                            val cy = centerY
                            val r0 = min(w, h) * 0.26f
                            drawCircle(accent.copy(alpha = 0.15f), r0, Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                            val twoPi = 2f * Math.PI.toFloat()
                            val count = barCount * 2
                            for (i in 0 until count) {
                                val angle = (i.toFloat() / count) * twoPi - twoPi / 4
                                // Mirror the bins left/right for a symmetric bloom.
                                val frac = abs((i.toFloat() / count) * 2f - 1f)
                                val bin = (frac * (barCount - 1)).toInt().coerceIn(0, barCount - 1)
                                val lvl = levels[bin]
                                val out = 6f + lvl * r0 * 1.4f
                                val inn = lvl * r0 * 0.6f
                                val ca = cos(angle)
                                val sa = sin(angle)
                                drawLine(accent.copy(alpha = 0.78f),
                                    Offset(cx + ca * r0, cy + sa * r0),
                                    Offset(cx + ca * (r0 + out), cy + sa * (r0 + out)),
                                    4f, StrokeCap.Round)
                                drawLine(accent.copy(alpha = 0.4f),
                                    Offset(cx + ca * r0, cy + sa * r0),
                                    Offset(cx + ca * (r0 - inn), cy + sa * (r0 - inn)),
                                    3f, StrokeCap.Round)
                            }
                        }
                        "pattern" -> {
                            drawFerrofluid(levels, phase = tick / 90f, mainAlpha = 1f, glowAlpha = 1f)
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
                        // Synthetic envelope so the droplet still breathes when
                        // there's no microphone capture to drive it.
                        val synth = FloatArray(barCount)
                        val t = tick / 30f
                        for (i in 0 until barCount) {
                            synth[i] = (0.25f + 0.35f * abs(sin(i * 0.30f + t * 0.9f) *
                                cos(i * 0.18f + t * 0.6f))).coerceIn(0f, 1f)
                        }
                        drawFerrofluid(synth, phase = tick / 90f, mainAlpha = 0.85f, glowAlpha = 0.6f)
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
                        drawFerrofluid(synth, phase = tick / 110f, mainAlpha = 0.4f, glowAlpha = 0.25f)
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
