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

// 浅色主题配色方案
private val LightColors = lightColorScheme(
    primary = Color(0xFF2457F5),          // 主色调：蓝色
    onPrimary = Color.White,               // 主色调上的文字颜色
    secondary = Color(0xFF0F9D58),         // 次要色调：绿色
    onSecondary = Color.White,             // 次要色调上的文字颜色
    tertiary = Color(0xFFF59E0B),          // 第三色调：橙色
    onTertiary = Color.White,              // 第三色调上的文字颜色
    background = Color(0xFFF5F7FB),        // 背景色：浅灰蓝
    onBackground = Color(0xFF101828),      // 背景上的文字颜色：深灰
    surface = Color.White,                 // 表面色：白色
    onSurface = Color(0xFF101828),         // 表面上的文字颜色
)

// 深色主题配色方案
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB2FF),           // 主色调：浅蓝色
    onPrimary = Color(0xFF0F172A),         // 主色调上的文字颜色：深蓝
    secondary = Color(0xFF71D29E),         // 次要色调：浅绿色
    onSecondary = Color(0xFF04130B),       // 次要色调上的文字颜色
    tertiary = Color(0xFFFFC76B),          // 第三色调：浅橙色
    onTertiary = Color(0xFF201300),        // 第三色调上的文字颜色
    background = Color(0xFF0B1120),        // 背景色：深蓝黑
    onBackground = Color(0xFFE5E7EB),      // 背景上的文字颜色：浅灰
    surface = Color(0xFF121A2A),           // 表面色：深蓝灰
    onSurface = Color(0xFFE5E7EB),         // 表面上的文字颜色
)

/**
 * 课程表应用主题
 * 根据系统设置和设备能力选择合适的配色方案
 * - Android 12+ 支持动态取色（Material You）
 * - 其他版本使用预定义的明暗主题
 *
 * @param content 主题包裹的内容
 */
@Composable
fun TimetableTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // 根据系统版本和深色模式选择配色方案
    val colorScheme = when {
        // Android 12+ 且系统为深色模式：使用动态深色主题
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && isSystemInDarkTheme() -> {
            dynamicDarkColorScheme(context)
        }
        // Android 12+ 且系统为浅色模式：使用动态浅色主题
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(context)
        }
        // 其他版本且系统为深色模式：使用预定义深色主题
        isSystemInDarkTheme() -> DarkColors
        // 其他版本且系统为浅色模式：使用预定义浅色主题
        else -> LightColors
    }

    // 应用 Material Design 3 主题
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
