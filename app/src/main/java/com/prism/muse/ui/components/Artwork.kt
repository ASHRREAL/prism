package com.prism.muse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import kotlin.math.abs

/**
 * Deterministic muted gradient derived from [seed] — the placeholder when no
 * real artwork exists (demo mode, or tracks without covers).
 */
fun seedColor(seed: String): Color {
    val hash = abs(seed.hashCode())
    val hue = (hash % 360).toFloat()
    return Color.hsv(hue, 0.35f, 0.45f)
}

/** Two base tones + a glow accent for the generated cover, all from [seed]. */
private fun coverPalette(seed: String): Triple<Color, Color, Color> {
    val h = abs(seed.hashCode())
    val hue1 = (h % 360).toFloat()
    val hue2 = ((h / 7) % 360).toFloat()
    val base = Color.hsv(hue1, 0.42f, 0.52f)
    val deep = Color.hsv(hue2, 0.5f, 0.30f)
    val glow = Color.hsv((hue1 + 40f) % 360f, 0.55f, 0.72f)
    return Triple(base, deep, glow)
}

/** 1–2 uppercase initials from a title/artist; empty for URLs or blanks. */
private fun initialsFor(text: String): String {
    val cleaned = text.trim()
    if (cleaned.isEmpty() || cleaned.startsWith("http")) return ""
    val words = cleaned.split(' ', '-', '_', '.').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * Procedural cover art: mesh-gradient background, specular highlight, and
 * initials. Deterministic from seed — same song = same cover.
 */
@Composable
fun GeneratedCover(
    seed: String,
    label: String?,
    modifier: Modifier = Modifier,
    showInitials: Boolean = true
) {
    val (base, deep, glow) = remember(seed) { coverPalette(seed) }
    val hash = remember(seed) { abs(seed.hashCode()) }
    val initials = remember(label, seed) { initialsFor(label ?: seed) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Diagonal base gradient — light corner to deep corner.
            drawRect(
                Brush.linearGradient(
                    colors = listOf(lerp(base, Color.White, 0.08f), base, deep),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
            // A few soft radial blobs for a hand-designed mesh-gradient feel.
            val rnd = java.util.Random(hash.toLong())
            repeat(3) { i ->
                val cx = (0.18f + 0.64f * rnd.nextFloat()) * size.width
                val cy = (0.18f + 0.64f * rnd.nextFloat()) * size.height
                val r = (0.34f + 0.34f * rnd.nextFloat()) * size.minDimension
                val tint = if (i % 2 == 0) glow else lerp(deep, Color.Black, 0.2f)
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }
            // Fine diagonal sheen lines — a subtle printed texture.
            repeat(6) { i ->
                val x = (i / 5f) * size.width
                drawLine(
                    Color.White.copy(alpha = 0.05f),
                    Offset(x, 0f),
                    Offset(x - size.height * 0.4f, size.height),
                    strokeWidth = 1.5f
                )
            }
            // Top-left specular highlight so the tile catches the light on tilt.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * 0.28f, size.height * 0.22f),
                    radius = size.minDimension * 0.72f
                )
            )
        }
        if (showInitials && initials.isNotEmpty()) {
            Text(
                initials,
                color = Color.White.copy(alpha = 0.82f),
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                fontSize = (maxWidth.value * 0.34f).sp,
                lineHeight = (maxWidth.value * 0.34f).sp
            )
        } else if (showInitials) {
            // No usable initials (e.g. an opaque URL seed): fall back to the note.
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.28f),
                modifier = Modifier.fillMaxSize(0.38f)
            )
        }
    }
}

/**
 * Album/track artwork. Loads via Coil when an http URL; falls back to
 * GeneratedCover when loading fails or when there's no real artwork.
 */
@Composable
fun Artwork(
    seed: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 0,
    overrideColor: Color? = null,
    icon: Boolean = true,
    label: String? = null
) {
    if (seed.startsWith("http")) {
        SubcomposeAsyncImage(
            model = seed,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { GeneratedCover(seed, label, Modifier.fillMaxSize(), showInitials = false) },
            // Navidrome answers 404 for tracks with no cover; on any load failure
            // draw our own generated art rather than a broken/placeholder image.
            error = { GeneratedCover(seed, label, Modifier.fillMaxSize(), showInitials = icon) }
        )
        return
    }

    // An explicit tint (album dominant color) keeps the simple flat gradient.
    if (overrideColor != null) {
        val light = lerp(overrideColor, Color.White, 0.12f)
        val dark = lerp(overrideColor, Color.Black, 0.55f)
        BoxWithConstraints(
            modifier.background(Brush.linearGradient(listOf(light, overrideColor, dark))),
            contentAlignment = Alignment.Center
        ) {
            if (icon) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxSize(0.38f)
                )
            }
        }
        return
    }

    GeneratedCover(seed = seed, label = label, modifier = modifier, showInitials = icon)
}
