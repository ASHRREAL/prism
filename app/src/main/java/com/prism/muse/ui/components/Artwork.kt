package com.prism.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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

/**
 * Album/track artwork. When [seed] is an http(s) URL (a real Navidrome
 * getCoverArt link) it loads via Coil; otherwise it renders the deterministic
 * gradient stand-in. Always sharp-cornered per the aria design.
 */
@Composable
fun Artwork(
    seed: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 0,
    overrideColor: Color? = null,
    icon: Boolean = true
) {
    if (seed.startsWith("http")) {
        AsyncImage(
            model = seed,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val base = overrideColor ?: seedColor(seed)
    val light = lerp(base, Color.White, 0.12f)
    val dark = lerp(base, Color.Black, 0.55f)

    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(light, base, dark))),
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
}
