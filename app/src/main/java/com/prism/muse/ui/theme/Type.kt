package com.prism.muse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp

// aria typography: Roboto Thin for the giant screen titles, Roboto Light for
// section headers, Roboto Condensed for lists/body — matching the reference UI.
val MetroFontFamily = FontFamily.SansSerif

val CondensedFontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Medium)
)

/** Giant thin screen title — "music", "aria", "settings". */
val HubTitle = TextStyle(
    fontFamily = MetroFontFamily,
    fontWeight = FontWeight.Thin,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    letterSpacing = (-1).sp
)

/** Section header — "recently played", "up next". */
val SectionHeader = TextStyle(
    fontFamily = MetroFontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 30.sp,
    lineHeight = 34.sp
)

/** Small tracked uppercase label — "NOW PLAYING", settings group headers. */
val TrackedLabel = TextStyle(
    fontFamily = MetroFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    letterSpacing = 3.sp
)

/** Big typographic list entry (artists, genres). */
val MetroListEntry = TextStyle(
    fontFamily = MetroFontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 28.sp,
    lineHeight = 34.sp
)

val PrismTypography = Typography(
    displayLarge = HubTitle,
    displayMedium = SectionHeader,
    titleLarge = TextStyle(
        fontFamily = MetroFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 26.sp
    ),
    // List row primary text — condensed like the mockup.
    titleMedium = TextStyle(
        fontFamily = CondensedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = CondensedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CondensedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TrackedLabel
)
