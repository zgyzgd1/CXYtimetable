package com.example.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.timetable.MainActivity
import com.example.timetable.R
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableRepository
import com.example.timetable.data.dayLabel
import com.example.timetable.data.entriesForDate
import com.example.timetable.data.findNextCourseSnapshot
import com.example.timetable.data.formatMinutes
import com.example.timetable.ui.AppDestination
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TimetableWidgetUpdater {
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAllFromStorage(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        if (!hasAnyActiveWidgets(appContext, appWidgetManager)) return
        refreshScope.launch {
            val entries = TimetableRepository.getEntriesNow(appContext)
            refreshAll(appContext, entries, appWidgetManager = appWidgetManager)
        }
    }

    /**
     * Refreshes all home screen widgets.
     *
     * **Note: This method must be called from a background thread**, because
     * AppWidgetManager.updateAppWidget() involves RemoteViews construction and
     * cross-process communication that could cause ANR on the main thread.
     */
    fun refreshAll(
        context: Context,
        entries: List<TimetableEntry>,
        today: LocalDate = LocalDate.now(),
        nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext),
    ) {
        val appContext = context.applicationContext
        if (!hasAnyActiveWidgets(appContext, appWidgetManager)) return
        updateNextCourseWidgets(appContext, appWidgetManager, entries, today, nowMinutes)
        updateTodayScheduleWidgets(appContext, appWidgetManager, entries, today)
    }

    internal fun hasAnyActiveWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
    ): Boolean {
        val todayWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
        if (todayWidgetIds.isNotEmpty()) return true
        val nextWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NextCourseWidgetProvider::class.java))
        return nextWidgetIds.isNotEmpty()
    }

    private fun updateNextCourseWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entries: List<TimetableEntry>,
        today: LocalDate,
        nowMinutes: Int,
    ) {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NextCourseWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return

        val state = buildNextCourseWidgetState(entries, today, nowMinutes, context)
        appWidgetIds.forEach { appWidgetId ->
            val views = buildNextCourseRemoteViews(context, appWidgetId, state)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateTodayScheduleWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entries: List<TimetableEntry>,
        today: LocalDate,
    ) {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return

        val state = buildTodayScheduleWidgetState(entries, today, context)
        appWidgetIds.forEach { appWidgetId ->
            val views = buildTodayScheduleRemoteViews(context, appWidgetId, state)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

internal data class TodayScheduleWidgetState(
    val dateLabel: String,
    val summaryText: String,
    val entryLines: List<String>,
    val overflowText: String,
    val targetDate: LocalDate,
)

internal data class NextCourseWidgetState(
    val statusText: String,
    val title: String,
    val timeLabel: String,
    val locationText: String,
    val targetDate: LocalDate,
)

internal fun buildTodayScheduleWidgetState(
    entries: List<TimetableEntry>,
    today: LocalDate = LocalDate.now(),
    context: Context,
): TodayScheduleWidgetState {
    val todayEntries = entriesForDate(entries, today)
    val entryLines = todayEntries
        .take(3)
        .map { entry ->
            buildString {
                append(formatMinutes(entry.startMinutes))
                append(" - ")
                append(formatMinutes(entry.endMinutes))
                append("  ")
                append(entry.title.ifBlank { context.getString(R.string.label_unnamed_course) })
                if (entry.location.isNotBlank()) {
                    append(" / ")
                    append(entry.location)
                }
            }
        }

    return TodayScheduleWidgetState(
        dateLabel = formatWidgetDateLabel(today, context),
        summaryText = if (todayEntries.isEmpty()) {
            context.getString(R.string.widget_no_courses_today)
        } else {
            context.getString(R.string.widget_today_course_count, todayEntries.size)
        },
        entryLines = if (entryLines.isEmpty()) listOf(context.getString(R.string.widget_tap_to_add)) else entryLines,
        overflowText = if (todayEntries.size > entryLines.size) {
            context.getString(R.string.widget_more_hidden, todayEntries.size - entryLines.size)
        } else {
            ""
        },
        targetDate = today,
    )
}

internal fun buildNextCourseWidgetState(
    entries: List<TimetableEntry>,
    today: LocalDate = LocalDate.now(),
    nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
    context: Context,
): NextCourseWidgetState {
    val snapshot = findNextCourseSnapshot(entries, today, nowMinutes, context)
    if (snapshot == null) {
        val title = if (entries.isEmpty()) context.getString(R.string.widget_empty_timetable) else context.getString(R.string.widget_no_upcoming)
        return NextCourseWidgetState(
            statusText = context.getString(R.string.widget_next_course),
            title = title,
            timeLabel = formatWidgetDateLabel(today, context),
            locationText = context.getString(R.string.widget_tap_to_open),
            targetDate = today,
        )
    }

    return NextCourseWidgetState(
        statusText = snapshot.statusText,
        title = snapshot.entry.title.ifBlank { context.getString(R.string.label_unnamed_course) },
        timeLabel = buildNextCourseTimeLabel(snapshot, today, context),
        locationText = snapshot.entry.location.ifBlank { context.getString(R.string.widget_tap_to_view_date) },
        targetDate = snapshot.occurrenceDate,
    )
}

private fun buildTodayScheduleRemoteViews(
    context: Context,
    appWidgetId: Int,
    state: TodayScheduleWidgetState,
): RemoteViews {
    return RemoteViews(context.packageName, R.layout.widget_today_schedule).apply {
        setTextViewText(R.id.widget_today_date, state.dateLabel)
        setTextViewText(R.id.widget_today_summary, state.summaryText)
        bindTextLine(R.id.widget_today_line_one, state.entryLines.getOrNull(0))
        bindTextLine(R.id.widget_today_line_two, state.entryLines.getOrNull(1))
        bindTextLine(R.id.widget_today_line_three, state.entryLines.getOrNull(2))
        bindTextLine(R.id.widget_today_overflow, state.overflowText.ifBlank { null })
        setOnClickPendingIntent(
            R.id.widget_today_root,
            createOpenAppPendingIntent(
                context = context,
                widgetType = "today",
                appWidgetId = appWidgetId,
                selectedDate = state.targetDate.toString(),
            ),
        )
    }
}

private fun buildNextCourseRemoteViews(
    context: Context,
    appWidgetId: Int,
    state: NextCourseWidgetState,
): RemoteViews {
    return RemoteViews(context.packageName, R.layout.widget_next_course).apply {
        setTextViewText(R.id.widget_next_status, state.statusText)
        setTextViewText(R.id.widget_next_title, state.title)
        setTextViewText(R.id.widget_next_time, state.timeLabel)
        setTextViewText(R.id.widget_next_location, state.locationText)
        setOnClickPendingIntent(
            R.id.widget_next_root,
            createOpenAppPendingIntent(
                context = context,
                widgetType = "next",
                appWidgetId = appWidgetId,
                selectedDate = state.targetDate.toString(),
            ),
        )
    }
}

private fun RemoteViews.bindTextLine(viewId: Int, text: String?) {
    if (text.isNullOrBlank()) {
        setViewVisibility(viewId, View.GONE)
    } else {
        setViewVisibility(viewId, View.VISIBLE)
        setTextViewText(viewId, text)
    }
}

private fun createOpenAppPendingIntent(
    context: Context,
    widgetType: String,
    appWidgetId: Int,
    selectedDate: String,
): PendingIntent {
    val intent = MainActivity.createLaunchIntent(
        context = context,
        selectedDate = selectedDate,
        destination = AppDestination.DAY,
    ).apply {
        data = Uri.parse("timetable://widget/$widgetType/$appWidgetId?date=$selectedDate")
    }
    return PendingIntent.getActivity(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

private fun buildNextCourseTimeLabel(
    snapshot: NextCourseSnapshot,
    today: LocalDate,
    context: Context,
): String {
    val dateLabel = when (snapshot.occurrenceDate) {
        today -> context.getString(R.string.widget_today)
        today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
        today.plusDays(2) -> context.getString(R.string.widget_day_after_tomorrow)
        else -> formatWidgetDateLabel(snapshot.occurrenceDate, context)
    }
    return "$dateLabel ${formatMinutes(snapshot.entry.startMinutes)} - ${formatMinutes(snapshot.entry.endMinutes)}"
}

internal fun formatWidgetDateLabel(date: LocalDate, context: Context): String {
    return context.getString(R.string.widget_date_format, date.monthValue, date.dayOfMonth) + " " + dayLabel(date.dayOfWeek.value)
}
