package com.bycho.safereturnhome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SafetyGreen,
    onPrimary = SurfaceWarm,
    secondary = Slate,
    background = Mist,
    onBackground = Ink,
    surface = SurfaceWarm,
    onSurface = Ink,
    error = AlertRed
)

private val DarkColors = darkColorScheme(
    primary = SafetyGreenDark,
    onPrimary = SurfaceWarm,
    secondary = Slate,
    surface = Ink,
    onSurface = SurfaceWarm,
    background = Ink,
    onBackground = SurfaceWarm,
    error = AlertRed
)

@Composable
fun SafeReturnHomeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
