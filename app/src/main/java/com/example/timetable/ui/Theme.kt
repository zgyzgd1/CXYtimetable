package com.example.timetable.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme

// Light theme color scheme
private val LightColors = lightColorScheme(
    primary = Color(0xFF2457F5),          // Primary: blue
    onPrimary = Color.White,               // Text on primary
    secondary = Color(0xFF0F9D58),         // Secondary: green
    onSecondary = Color.White,             // Text on secondary
    tertiary = Color(0xFFF59E0B),          // Tertiary: orange
    onTertiary = Color.White,              // Text on tertiary
    background = Color(0xFFF5F7FB),        // Background: light gray-blue
    onBackground = Color(0xFF101828),      // Text on background: dark gray
    surface = Color.White,                 // Surface: white
    onSurface = Color(0xFF101828),         // Text on surface
)

// Dark theme color scheme
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB2FF),           // Primary: light blue
    onPrimary = Color(0xFF0F172A),         // Text on primary: dark blue
    secondary = Color(0xFF71D29E),         // Secondary: light green
    onSecondary = Color(0xFF04130B),       // Text on secondary
    tertiary = Color(0xFFFFC76B),          // Tertiary: light orange
    onTertiary = Color(0xFF201300),        // Text on tertiary
    background = Color(0xFF0B1120),        // Background: dark blue-black
    onBackground = Color(0xFFE5E7EB),      // Text on background: light gray
    surface = Color(0xFF121A2A),           // Surface: dark blue-gray
    onSurface = Color(0xFFE5E7EB),          // Text on surface
)

/**
 * Timetable app theme.
 * Selects the appropriate color scheme based on system settings and device capabilities:
 * - Android 12+ supports dynamic color (Material You)
 * - Other versions use pre-defined light/dark themes
 *
 * @param content The content wrapped by the theme
 */
@Composable
fun TimetableTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Choose color scheme based on system version and dark mode
    val colorScheme = when {
        // Android 12+ with system dark mode: use dynamic dark theme
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && isSystemInDarkTheme() -> {
            dynamicDarkColorScheme(context)
        }
        // Android 12+ with system light mode: use dynamic light theme
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(context)
        }
        // Other versions with dark mode: use pre-defined dark theme
        isSystemInDarkTheme() -> DarkColors
        // Other versions with light mode: use pre-defined light theme
        else -> LightColors
    }

    // Apply Material Design 3 theme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
