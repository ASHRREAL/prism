package com.prism.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Deterministic pastel-to-deep gradient derived from [seed]. Stands in for real
 * Navidrome/embedded cover art until artwork loading is wired up — every album,
 * artist, and playlist in the mock library maps to a stable, distinct gradient.
 */
fun seedColor(seed: String): Color {
    val hash = abs(seed.hashCode())
    val hue = (hash % 360).toFloat()
    return Color.hsv(hue, 0.55f, 0.85f)
}

@Composable
fun Artwork(
    seed: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    overrideColor: Color? = null,
    icon: Boolean = true
) {
    val base = overrideColor ?: seedColor(seed)
    val light = lerp(base, Color.White, 0.28f)
    val dark = lerp(base, Color.Black, 0.45f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(light, base, dark),
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxSize(0.38f)
            )
        }
    }
}
