package com.prism.muse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GlassShapeSmall = RoundedCornerShape(16.dp)
val GlassShapeMedium = RoundedCornerShape(20.dp)
val GlassShapeLarge = RoundedCornerShape(24.dp)
val GlassShapePill = RoundedCornerShape(50)

val PrismShapes = Shapes(
    small = GlassShapeSmall,
    medium = GlassShapeMedium,
    large = GlassShapeLarge
)
