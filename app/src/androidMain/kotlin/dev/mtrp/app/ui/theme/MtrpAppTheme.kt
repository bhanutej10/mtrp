package dev.mtrp.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * MTRP Android app theme.
 * Matches the desktop app colour scheme.
 * Author: K. Bhanutej
 */
private val DarkColors = darkColorScheme(
    primary          = Color(0xFF2DD4BF),
    onPrimary        = Color(0xFF0A1F1E),
    primaryContainer = Color(0xFF0D2318),
    onPrimaryContainer = Color(0xFF3DD68C),
    secondary        = Color(0xFF60A5FA),
    onSecondary      = Color(0xFF0D1A2E),
    background       = Color(0xFF0F1117),
    onBackground     = Color(0xFFE8EAF0),
    surface          = Color(0xFF171B26),
    onSurface        = Color(0xFFE8EAF0),
    surfaceVariant   = Color(0xFF1E2433),
    onSurfaceVariant = Color(0xFF8B93A8),
    error            = Color(0xFFF87171),
    onError          = Color(0xFF1F0A0A)
)

@Composable
fun MtrpAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content     = content
    )
}
