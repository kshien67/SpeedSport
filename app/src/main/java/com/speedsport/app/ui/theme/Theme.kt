package com.speedsport.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.speedsport.app.R

// Helpers to read XML colors into Compose (static constants are fine too)
private val LightColors = lightColorScheme(
    primary = Color(0xFF0EA5E9),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF083314),
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF3B2007),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1220),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF001B2A),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF00210E),
    tertiary = Color(0xFFFDBA74),
    onTertiary = Color(0xFF2A1404),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EEF9),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE6EEF9),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun SpeedSportTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
