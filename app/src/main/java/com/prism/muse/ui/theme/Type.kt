package com.prism.muse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Metro/Zune hubs lean on the platform's light-weight sans as a stand-in for
// Segoe UI Light; swap for a bundled font family once licensing is settled.
val MetroFontFamily = FontFamily.SansSerif

/** Oversized, light-weight "hub" title — e.g. the huge "Music" on the home screen. */
val HubTitle = TextStyle(
    fontFamily = MetroFontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
    lineHeight = 60.sp
)

/** Section header inside a hub pivot, e.g. "recently played". Lowercase reads more Metro. */
val SectionHeader = TextStyle(
    fontFamily = MetroFontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 30.sp,
    lineHeight = 34.sp
)

val PrismTypography = Typography(
    displayLarge = HubTitle,
    displayMedium = SectionHeader,
    titleLarge = TextStyle(
        fontFamily = MetroFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MetroFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MetroFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MetroFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MetroFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    )
)
