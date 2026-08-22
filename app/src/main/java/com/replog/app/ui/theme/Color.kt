package com.replog.app.ui.theme

import androidx.compose.ui.graphics.Color

val GreenPrimary = Color(0xFF22C55E)
val GreenDim = Color(0xFF16A34A)
val GreenContainerDark = Color(0xFF0E2A1B)

// Dark palette (default) — GitHub-inspired neutrals
val DarkBg = Color(0xFF0B0E13)
val DarkSurface = Color(0xFF11151C)
val DarkSurfaceHigh = Color(0xFF161B24)
val DarkBorder = Color(0xFF232A36)
val DarkText = Color(0xFFE6EDF3)
val DarkTextDim = Color(0xFF8B949E)

val Heat0 = Color(0xFF161C26)
val Heat1 = Color(0xFF0E4429)
val Heat2 = Color(0xFF006D32)
val Heat3 = Color(0xFF26A641)
val Heat4 = Color(0xFF39D353)

// Light palette
val LightBg = Color(0xFFF7F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFF0F2F5)
val LightBorder = Color(0xFFE1E4E8)
val LightText = Color(0xFF14181F)
val LightTextDim = Color(0xFF57606A)

val LightHeat0 = Color(0xFFEBEDF0)
val LightHeat1 = Color(0xFF9BE9A8)
val LightHeat2 = Color(0xFF40C463)
val LightHeat3 = Color(0xFF30A14E)
val LightHeat4 = Color(0xFF216E39)

fun heatColors(dark: Boolean) =
    if (dark) listOf(Heat0, Heat1, Heat2, Heat3, Heat4)
    else listOf(LightHeat0, LightHeat1, LightHeat2, LightHeat3, LightHeat4)
