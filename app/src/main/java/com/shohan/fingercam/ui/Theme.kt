package com.shohan.fingercam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackWhiteColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF000000),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF000000),
    surfaceTint = Color(0xFFFFFFFF),
    outline = Color(0xFF000000)
)

@Composable
fun FingerCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlackWhiteColors, content = content)
}
