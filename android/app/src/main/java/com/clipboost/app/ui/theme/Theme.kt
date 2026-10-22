package com.clipboost.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Palette from the ClipBoost design canvas: graphite ground, one lime accent. */
object Cb {
    val Ground = Color(0xFF0A0B0E)
    val Surface = Color(0xFF14161B)
    val SurfaceLow = Color(0xFF121418)
    val SurfaceHigh = Color(0xFF1B1E24)
    val Line = Color(0xFF262A31)
    val LineStrong = Color(0xFF3A3F48)
    val Text = Color(0xFFF3F4F6)
    val TextSoft = Color(0xFFB4BAC4)
    val TextMuted = Color(0xFF9BA1AC)
    val Accent = Color(0xFFC8F135)
    val OnAccent = Color(0xFF0A0B0E)
    val Warn = Color(0xFFFFB86B)
    val Error = Color(0xFFFF8A8A)
}

private val Mono = FontFamily.Monospace

val CbTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.03).em),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.08.em),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp),
)

@Composable
fun ClipBoostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cb.Accent,
            onPrimary = Cb.OnAccent,
            background = Cb.Ground,
            onBackground = Cb.Text,
            surface = Cb.Surface,
            onSurface = Cb.Text,
            surfaceVariant = Cb.SurfaceHigh,
            onSurfaceVariant = Cb.TextSoft,
            outline = Cb.Line,
            error = Cb.Error,
        ),
        typography = CbTypography,
        content = content,
    )
}
