package com.example.timetable.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timetable.MainActivity
import com.example.timetable.data.formatMinutes
import com.example.timetable.ui.AppDestination

class CourseReminderReceiver : BroadcastReceiver() {
    @android.annotation.SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        try {
            if (CourseReminderScheduler.notificationsEnabled(context)) {
                CourseReminderScheduler.ensureNotificationChannel(context)

                val requestCode = intent.getIntExtra(CourseReminderScheduler.EXTRA_REQUEST_CODE, 0)
                val title = intent.getStringExtra(CourseReminderScheduler.EXTRA_TITLE).orEmpty().ifBlank { "课程提醒" }
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

                val contentTitle = buildReminderNotificationTitle(title, reminderMinutes)
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

            // 接力机制：当前提醒发送后，立即安排下一次最近的提醒
            CourseReminderScheduler.resyncFromStorage(context) {
                pendingResult.finish()
            }
        } catch (throwable: Throwable) {
            pendingResult.finish()
            throw throwable
        }
    }
}

internal fun buildReminderNotificationTitle(title: String, reminderMinutes: Int): String {
    val resolvedTitle = title.ifBlank { "课程提醒" }
    return "${formatReminderLeadTime(reminderMinutes)}：$resolvedTitle"
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

internal fun formatReminderLeadTime(reminderMinutes: Int): String {
    val safeMinutes = reminderMinutes.coerceAtLeast(1)
    val hours = safeMinutes / 60
    val minutes = safeMinutes % 60
    return when {
        hours == 0 -> "${safeMinutes} 分钟后上课"
        minutes == 0 -> "${hours} 小时后上课"
        else -> "${hours} 小时 ${minutes} 分钟后上课"
    }
}
