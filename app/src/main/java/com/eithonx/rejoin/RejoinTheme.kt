package com.eithonx.rejoin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF4F50A5),
    secondary = Color(0xFF815316),
    tertiary = Color(0xFF23694F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E4FF),
    onPrimaryContainer = Color(0xFF24266B),
    background = Color(0xFFFBFAFE),
    onBackground = Color(0xFF1B1B23),
    surface = Color(0xFFFBFAFE),
    onSurface = Color(0xFF1B1B23),
    onSurfaceVariant = Color(0xFF494853),
    surfaceContainerLow = Color(0xFFF5F3FA),
    surfaceContainer = Color(0xFFEFEDF5),
    surfaceContainerHigh = Color(0xFFE9E7F0),
    outline = Color(0xFF777581),
    outlineVariant = Color(0xFFCCC9D7)
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    secondary = Color(0xFFF3BD7A),
    tertiary = Color(0xFF8CD7B5),
    onPrimary = Color(0xFF292969),
    primaryContainer = Color(0xFF3D3D85),
    onPrimaryContainer = Color(0xFFE5E4FF),
    background = Color(0xFF15151C),
    onBackground = Color(0xFFE9E7F0),
    surface = Color(0xFF15151C),
    onSurface = Color(0xFFE9E7F0),
    onSurfaceVariant = Color(0xFFCAC7D5),
    surfaceContainerLow = Color(0xFF1B1A23),
    surfaceContainer = Color(0xFF201F29),
    surfaceContainerHigh = Color(0xFF2A2933),
    outline = Color(0xFF938F9D),
    outlineVariant = Color(0xFF494651)
)

@Composable
internal fun RejoinTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors, content = content)
}
