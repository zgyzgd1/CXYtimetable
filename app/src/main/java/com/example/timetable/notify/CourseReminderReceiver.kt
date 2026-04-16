package com.example.timetable.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timetable.MainActivity
import com.example.timetable.data.formatMinutes

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!CourseReminderScheduler.notificationsEnabled(context)) return

        CourseReminderScheduler.ensureNotificationChannel(context)

        val requestCode = intent.getIntExtra(CourseReminderScheduler.EXTRA_REQUEST_CODE, 0)
        val title = intent.getStringExtra(CourseReminderScheduler.EXTRA_TITLE).orEmpty().ifBlank { "课程提醒" }
        val location = intent.getStringExtra(CourseReminderScheduler.EXTRA_LOCATION).orEmpty()
        val date = intent.getStringExtra(CourseReminderScheduler.EXTRA_DATE).orEmpty()
        val startMinutes = intent.getIntExtra(CourseReminderScheduler.EXTRA_START_MINUTES, 8 * 60)

        val openIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentText = buildString {
            append(date)
            append(" ")
            append(formatMinutes(startMinutes))
            if (location.isNotBlank()) {
                append(" · ")
                append(location)
            }
        }

        val notification = NotificationCompat.Builder(context, CourseReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("即将上课：$title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }

        // 接力机制：当前提醒发送后，立即安排下一次最近的提醒
        CourseReminderScheduler.resyncFromStorage(context)
    }
}
