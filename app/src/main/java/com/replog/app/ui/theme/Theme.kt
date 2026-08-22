package com.replog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = Color(0xFF04140A),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = Color(0xFFB7F0C9),
    secondary = Color(0xFF58A6FF),
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkTextDim,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Color(0xFFF87171)
)

private val LightScheme = lightColorScheme(
    primary = GreenDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E14),
    secondary = Color(0xFF0969DA),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextDim,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = Color(0xFFDC2626)
)

@Composable
fun RepLogTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = RepLogTypography,
        content = content
    )
}
