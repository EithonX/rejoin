package com.eithonx.rejoin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val lightColors = lightColorScheme(
    primary = Color(0xFF244C69),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8F5),
    onPrimaryContainer = Color(0xFF173952),
    secondary = Color(0xFF83532C),
    tertiary = Color(0xFF246752),
    surface = Color(0xFFF7F4EE),
    onSurface = Color(0xFF262729),
    onSurfaceVariant = Color(0xFF555653),
    surfaceContainerLow = Color(0xFFFDFBF6),
    surfaceContainer = Color(0xFFEFEBE3),
    surfaceContainerHigh = Color(0xFFE9E5DC),
    outline = Color(0xFF767771),
    outlineVariant = Color(0xFFDBD6CB)
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFC1DDF2),
    onPrimary = Color(0xFF153348),
    primaryContainer = Color(0xFF28445B),
    onPrimaryContainer = Color(0xFFDFEFFC),
    secondary = Color(0xFFF1BE92),
    tertiary = Color(0xFFA8D7C0),
    surface = Color(0xFF171B20),
    onSurface = Color(0xFFF2EEE6),
    onSurfaceVariant = Color(0xFFBDBFBE),
    surfaceContainerLow = Color(0xFF1D232A),
    surfaceContainer = Color(0xFF242B33),
    surfaceContainerHigh = Color(0xFF2D353E),
    outline = Color(0xFF92989D),
    outlineVariant = Color(0xFF39434C),
    error = Color(0xFFFFB4A9)
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private val defaults = Typography()
private val typography = Typography(
    headlineMedium = defaults.headlineMedium.copy(
        fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-0.8).sp
    ),
    headlineLarge = defaults.headlineLarge.copy(
        fontFamily = FontFamily.Serif, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.3).sp
    ),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.45).sp),
    bodyMedium = defaults.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelSmall = defaults.labelSmall.copy(letterSpacing = 1.3.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
internal fun RejoinTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkColors else lightColors,
        shapes = shapes,
        typography = typography,
        content = content
    )
}
