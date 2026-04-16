package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.example.timetable.ui.ScheduleApp
import com.example.timetable.ui.TimetableTheme

/**
 * 主活动类 - 应用入口点
 * 负责初始化 Compose UI 并设置主题
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 使用 Jetpack Compose 设置 UI 内容
        setContent {
            // 应用自定义主题
            TimetableTheme {
                // 使用 Surface 作为根容器，提供背景色和 elevation
                Surface {
                    // 启动课程表主界面
                    ScheduleApp()
                }
            }
        }
    }
}
