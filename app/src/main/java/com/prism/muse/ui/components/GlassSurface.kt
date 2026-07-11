package com.prism.muse.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.muse.ui.theme.GlassShapeMedium
import com.prism.muse.ui.theme.GlassStroke
import com.prism.muse.ui.theme.GlassWhite04
import com.prism.muse.ui.theme.GlassWhite08

/** Frosted acrylic panel: gradient fill + hairline border + drop shadow. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapeMedium,
    tint: Color = Color.White,
    elevation: Dp = 18.dp,
    blurRadius: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .then(if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurRadius) else Modifier)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = 0.14f),
                        tint.copy(alpha = 0.06f)
                    )
                )
            )
            .border(1.dp, GlassStroke, shape)
    ) {
        content()
    }
}

/** Thin translucent divider / chip background, e.g. for the transparent bottom nav bar. */
fun Modifier.glassBar(shape: Shape = RoundedCornerShape(0.dp)) = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(GlassWhite08, GlassWhite04)))
    .border(1.dp, GlassStroke, shape)
