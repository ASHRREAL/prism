package com.prism.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prism.muse.ui.theme.InkNavy
import com.prism.muse.ui.theme.VoidBlack

/** Blurred cover-art backdrop with a dark top-to-bottom gradient. Used across player screens. */
@Composable
fun PlayerBackdrop(artUrl: String?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(VoidBlack)) {
        if (artUrl != null && artUrl.startsWith("http")) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.65f }
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(InkNavy.copy(alpha = 0.7f), Color.Transparent),
                        center = Offset(280f, 180f),
                        radius = 1400f
                    )
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.65f to Color.Black.copy(alpha = 0.85f),
                    1f to Color.Black
                )
            )
        )
        content()
    }
}
