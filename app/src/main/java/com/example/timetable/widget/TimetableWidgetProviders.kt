package com.example.timetable.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.timetable.util.OneTimeAction
import kotlinx.coroutines.Job

private const val WIDGET_PROVIDER_PENDING_RESULT_TIMEOUT_MS = 8_000L
private const val TAG = "TimetableWidgetProvider"

abstract class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        refreshWidgetsFromStorage(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refreshWidgetsFromStorage(context)
    }

    private fun refreshWidgetsFromStorage(context: Context) {
        val pendingResult = goAsync()
        val finishOnce = OneTimeAction()
        val handler = Handler(Looper.getMainLooper())
        var refreshJob: Job? = null
        val timeoutRunnable = Runnable {
            refreshJob?.cancel()
            finishOnce.run {
                pendingResult.finish()
            }
        }

        handler.postDelayed(timeoutRunnable, WIDGET_PROVIDER_PENDING_RESULT_TIMEOUT_MS)

        try {
            refreshJob = TimetableWidgetUpdater.refreshAllFromStorage(context) {
                handler.removeCallbacks(timeoutRunnable)
                finishOnce.run {
                    pendingResult.finish()
                }
            }
        } catch (error: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            finishOnce.run {
                pendingResult.finish()
            }
            Log.e(TAG, "Widget refresh broadcast failed.", error)
        }
    }
}

class TodayScheduleWidgetProvider : TimetableWidgetProvider()

class NextCourseWidgetProvider : TimetableWidgetProvider()
