package com.example.timetable.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 课程强调色板 CompositionLocal */
val LocalCourseAccentColors = compositionLocalOf { LightCourseAccentColors }

/** 亮色主题课程强调色 */
val LightCourseAccentColors = listOf(
    Color(0xFF7986CB),
    Color(0xFF4DB6AC),
    Color(0xFFFF8A65),
    Color(0xFFA1887F),
    Color(0xFF4FC3F7),
    Color(0xFFAED581),
    Color(0xFFBA68C8),
    Color(0xFFFFB74D),
    Color(0xFFF06292),
    Color(0xFF4DD0E1),
)

/** 暗色主题课程强调色（更亮以适配深色背景） */
val DarkCourseAccentColors = listOf(
    Color(0xFF9FA8DA),
    Color(0xFF80CBC4),
    Color(0xFFFFAB91),
    Color(0xFFBCAAA4),
    Color(0xFF81D4FA),
    Color(0xFFC5E1A5),
    Color(0xFFCE93D8),
    Color(0xFFFFCC80),
    Color(0xFFF48FB1),
    Color(0xFF80DEEA),
)

object AppShape {
    val CardLarge = RoundedCornerShape(24.dp)
    val CardMedium = RoundedCornerShape(20.dp)
    val CardSmall = RoundedCornerShape(16.dp)
    val CardExtraSmall = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(12.dp)
    val Badge = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(999.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val DialogContent = RoundedCornerShape(26.dp)
    val CalendarDay = RoundedCornerShape(18.dp)
}

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

    val courseAccentColors = if (isSystemInDarkTheme()) DarkCourseAccentColors else LightCourseAccentColors

    // Apply Material Design 3 theme
    CompositionLocalProvider(LocalCourseAccentColors provides courseAccentColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
