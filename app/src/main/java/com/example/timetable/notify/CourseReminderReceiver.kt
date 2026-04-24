package com.example.timetable.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timetable.MainActivity
import com.example.timetable.R
import com.example.timetable.data.formatMinutes
import com.example.timetable.ui.AppDestination

class CourseReminderReceiver : BroadcastReceiver() {
    @android.annotation.SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        // Timeout protection: goAsync() has a 10s limit; set 8s timeout to ensure pendingResult.finish() is called
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            try {
                pendingResult.finish()
            } catch (_: Exception) {
                // Ignore if already finished
            }
        }
        handler.postDelayed(timeoutRunnable, 8_000L)

        try {
            if (CourseReminderScheduler.notificationsEnabled(context)) {
                CourseReminderScheduler.ensureNotificationChannel(context)

                val requestCode = intent.getIntExtra(CourseReminderScheduler.EXTRA_REQUEST_CODE, 0)
                val title = intent.getStringExtra(CourseReminderScheduler.EXTRA_TITLE).orEmpty().ifBlank { context.getString(R.string.notify_reminder_title) }
                val location = intent.getStringExtra(CourseReminderScheduler.EXTRA_LOCATION).orEmpty()
                val date = intent.getStringExtra(CourseReminderScheduler.EXTRA_DATE).orEmpty()
                val startMinutes = intent.getIntExtra(CourseReminderScheduler.EXTRA_START_MINUTES, 8 * 60)
                val reminderMinutes = intent.getIntExtra(
                    CourseReminderScheduler.EXTRA_REMINDER_MINUTES,
                    CourseReminderScheduler.defaultReminderMinutes(),
                )

                val openIntent = MainActivity.createLaunchIntent(
                    context = context,
                    selectedDate = date.ifBlank { null },
                    destination = AppDestination.DAY,
                ).apply {
                    action = Intent.ACTION_VIEW
                }
                val contentIntent = PendingIntent.getActivity(
                    context,
                    requestCode,
                    openIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                val contentTitle = buildReminderNotificationTitle(title, reminderMinutes, context)
                val contentText = buildReminderNotificationText(
                    date = date,
                    startMinutes = startMinutes,
                    location = location,
                )

                val notification = NotificationCompat.Builder(context, CourseReminderScheduler.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .build()

                runCatching {
                    @android.annotation.SuppressLint("MissingPermission")
                    val nm = NotificationManagerCompat.from(context)
                    nm.notify(requestCode, notification)
                }
            }

            // Relay mechanism: after current reminder is sent, immediately schedule the next nearest reminder
            CourseReminderScheduler.resyncFromStorage(context) {
                handler.removeCallbacks(timeoutRunnable)
                pendingResult.finish()
            }
        } catch (throwable: Throwable) {
            handler.removeCallbacks(timeoutRunnable)
            pendingResult.finish()
            throw throwable
        }
    }
}

internal fun buildReminderNotificationTitle(title: String, reminderMinutes: Int, context: Context): String {
    val resolvedTitle = title.ifBlank { context.getString(R.string.notify_reminder_title) }
    return "${formatReminderLeadTime(reminderMinutes, context)}：$resolvedTitle"
}

internal fun buildReminderNotificationText(
    date: String,
    startMinutes: Int,
    location: String,
): String {
    val parts = mutableListOf<String>()
    val timeLabel = buildString {
        if (date.isNotBlank()) {
            append(date)
            append(" ")
        }
        append(formatMinutes(startMinutes))
    }
    parts += timeLabel
    if (location.isNotBlank()) {
        parts += location
    }
    return parts.joinToString(" · ")
}

internal fun formatReminderLeadTime(reminderMinutes: Int, context: Context): String {
    val safeMinutes = reminderMinutes.coerceAtLeast(1)
    val hours = safeMinutes / 60
    val minutes = safeMinutes % 60
    return when {
        hours == 0 -> context.getString(R.string.notify_minutes_later, safeMinutes)
        minutes == 0 -> context.getString(R.string.notify_hours_later, hours)
        else -> context.getString(R.string.notify_hours_minutes_later, hours, minutes)
    }
}
