package com.example.timetable.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.timetable.util.OneTimeAction
import com.example.timetable.widget.TimetableWidgetUpdater

private const val EXACT_ALARM_PERMISSION_STATE_CHANGED =
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
private const val RESCHEDULE_PENDING_RESULT_TIMEOUT_MS = 8_000L
private const val TAG = "ReminderReschedule"

class CourseReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shouldResync = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> true
            else -> false
        }

        if (!shouldResync) return

        val pendingResult = goAsync()
        val finishOnce = OneTimeAction()
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            finishOnce.run {
                pendingResult.finish()
            }
        }
        handler.postDelayed(timeoutRunnable, RESCHEDULE_PENDING_RESULT_TIMEOUT_MS)

        try {
            ReminderFallbackWorker.ensureScheduled(context)
            TimetableWidgetUpdater.refreshAllFromStorage(context)
            CourseReminderScheduler.resyncFromStorage(context, forceReschedule = true) {
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
            Log.e(TAG, "Reminder reschedule broadcast failed.", error)
        }
    }
}
