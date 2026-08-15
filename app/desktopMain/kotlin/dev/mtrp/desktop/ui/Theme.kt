package dev.mtrp.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * MTRP desktop app theme.
 * Dark theme matching the phase documentation style.
 * Author: K. Bhanutej
 */
private val DarkColors = darkColorScheme(
    primary          = Color(0xFF2DD4BF),   // teal
    onPrimary        = Color(0xFF0A1F1E),
    secondary        = Color(0xFF60A5FA),   // blue
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
fun MtrpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content     = content
    )
}
