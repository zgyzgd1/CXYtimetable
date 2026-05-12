package com.example.timetable

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import com.example.timetable.data.parseEntryDate
import com.example.timetable.ui.AppDestination
import com.example.timetable.ui.AppLaunchTarget
import com.example.timetable.ui.ScheduleApp
import com.example.timetable.ui.TimetableTheme
import com.example.timetable.widget.EXTRA_WIDGET_ADD_COURSE

class MainActivity : ComponentActivity() {
    private var launchTarget by mutableStateOf(AppLaunchTarget())
    private var launchRequestCounter = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchTarget = nextLaunchTarget(intent)
        setContent {
            TimetableTheme {
                Surface {
                    ScheduleApp(launchTarget = launchTarget)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchTarget = nextLaunchTarget(intent)
    }

    private fun nextLaunchTarget(intent: Intent?): AppLaunchTarget {
        launchRequestCounter += 1
        return parseLaunchTarget(intent).copy(launchRequestId = launchRequestCounter)
    }

    companion object {
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
        const val EXTRA_DESTINATION = "extra_destination"

        fun createLaunchIntent(
            context: Context,
            selectedDate: String? = null,
            destination: AppDestination = AppDestination.DAY,
        ): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_DESTINATION, destination.name)
                if (!selectedDate.isNullOrBlank()) {
                    putExtra(EXTRA_SELECTED_DATE, selectedDate)
                }
            }
        }

        fun parseLaunchTarget(intent: Intent?): AppLaunchTarget {
            return resolveLaunchTarget(
                selectedDate = intent?.getStringExtra(EXTRA_SELECTED_DATE),
                destination = intent?.getStringExtra(EXTRA_DESTINATION),
                openAddCourse = intent?.getBooleanExtra(EXTRA_WIDGET_ADD_COURSE, false) == true,
            )
        }

        fun resolveLaunchTarget(
            selectedDate: String?,
            destination: String?,
            openAddCourse: Boolean = false,
        ): AppLaunchTarget {
            val normalizedDate = selectedDate?.trim()?.takeIf { value ->
                value.isNotBlank() && parseEntryDate(value) != null
            }
            return AppLaunchTarget(
                selectedDate = normalizedDate,
                destination = AppDestination.fromSavedName(destination),
                openAddCourse = openAddCourse,
            )
        }
    }
}
