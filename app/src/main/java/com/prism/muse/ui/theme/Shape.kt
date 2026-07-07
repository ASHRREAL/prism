package com.prism.muse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Windows Phone Metro / PSP XMB tiles are flat rectangles — no rounding anywhere.
val GlassShapeSmall = RoundedCornerShape(0.dp)
val GlassShapeMedium = RoundedCornerShape(0.dp)
val GlassShapeLarge = RoundedCornerShape(0.dp)
val GlassShapePill = RoundedCornerShape(0.dp)

val PrismShapes = Shapes(
    small = GlassShapeSmall,
    medium = GlassShapeMedium,
    large = GlassShapeLarge
)
