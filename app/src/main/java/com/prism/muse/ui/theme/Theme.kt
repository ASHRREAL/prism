package com.prism.muse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Current "dynamic" accent — normally [DefaultAccent], but album/artist/now-playing
 * screens override it via [ProvideAccent] using a color derived from the artwork
 * (stand-in today for real Palette-based extraction against Navidrome art).
 */
val LocalPrismAccent = compositionLocalOf { DefaultAccent }

@Composable
fun ProvideAccent(accent: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPrismAccent provides accent, content = content)
}

fun colorFromHex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

private val PrismDarkColors = darkColorScheme(
    primary = DefaultAccent,
    onPrimary = VoidBlack,
    secondary = DefaultAccentSoft,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = DeepCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = InkNavy,
    outline = GlassStroke
)

@Composable
fun PrismMuseTheme(
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit
) {
    val colors = PrismDarkColors.copy(primary = accent, secondary = accent.copy(alpha = 0.7f))
    ProvideAccent(accent) {
        MaterialTheme(
            colorScheme = colors,
            typography = PrismTypography,
            shapes = PrismShapes,
            content = content
        )
    }
}
