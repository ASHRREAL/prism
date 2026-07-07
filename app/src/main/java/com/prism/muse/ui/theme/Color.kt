package com.prism.muse.ui.theme

import androidx.compose.ui.graphics.Color

// aria design tokens — near-pure black ground, everything else is typography.
val VoidBlack = Color(0xFF050505)
val DeepCharcoal = Color(0xFF0A0C10)
val InkNavy = Color(0xFF0B111C) // faint blue-black used behind Now Playing

// Hairlines and dim chrome
val Hairline = Color(0x1FFFFFFF)
val GlassStroke = Color(0x33FFFFFF)
val GlassWhite12 = Color(0x1FFFFFFF)
val GlassWhite08 = Color(0x14FFFFFF)
val GlassWhite04 = Color(0x0AFFFFFF)

// Text ramp (mockup uses white at ~96 / 60 / 35% alpha)
val TextPrimary = Color(0xF5FFFFFF)
val TextSecondary = Color(0x99FFFFFF)
val TextTertiary = Color(0x59FFFFFF)

// aria accent: light cyan used for artist links, active states, "on" values.
val DefaultAccent = Color(0xFF8FD1E8)
val DefaultAccentSoft = Color(0xFF5FA8C4)

val AccentColors = mapOf(
    "cyan" to Color(0xFF8FD1E8),
    "mint" to Color(0xFF5CE0A8),
    "blue" to Color(0xFF3FA9F5),
    "purple" to Color(0xFFB57CE0),
    "amber" to Color(0xFFF4A259),
    "rose" to Color(0xFFE05C6D),
    "lime" to Color(0xFF8AC24A),
    "violet" to Color(0xFF6C63FF),
)

fun accentColorByName(name: String): Color = AccentColors[name] ?: DefaultAccent
