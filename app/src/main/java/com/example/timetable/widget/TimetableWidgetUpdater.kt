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
        updateTodayScheduleWidgets(appContext, appWidgetManager, entries, today, nowMinutes)
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
        nowMinutes: Int,
    ) {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return

        val state = buildTodayScheduleWidgetState(entries, today, nowMinutes, context)
        appWidgetIds.forEach { appWidgetId ->
            val views = buildTodayScheduleRemoteViews(context, appWidgetId, state)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

internal data class TodayScheduleWidgetState(
    val dateLabel: String,
    val courseItems: List<CourseItem>,
    val emptyText: String?,
    val targetDate: LocalDate,
)

internal data class CourseItem(
    val title: String,
    val time: String,
    val isPast: Boolean = false,
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
    nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
    context: Context,
): TodayScheduleWidgetState {
    val todayEntries = entriesForDate(entries, today)
    val courseItems = todayEntries
        .take(MAX_VISIBLE_COURSES)
        .map { entry ->
            CourseItem(
                title = entry.title.ifBlank { context.getString(R.string.label_unnamed_course) },
                time = "${formatMinutes(entry.startMinutes)}-${formatMinutes(entry.endMinutes)}",
                isPast = entry.endMinutes < nowMinutes,
            )
        }

    val emptyText = if (courseItems.isEmpty()) context.getString(R.string.widget_no_courses_today) else null

    return TodayScheduleWidgetState(
        dateLabel = "${today.monthValue}/${today.dayOfMonth}${dayLabel(today.dayOfWeek.value, context)}",
        courseItems = courseItems,
        emptyText = emptyText,
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

/** Pre-computed view ID arrays to avoid [android.content.res.Resources.getIdentifier] at runtime. */
private val COURSE_ITEM_IDS = intArrayOf(
    R.id.widget_course_item_1,
    R.id.widget_course_item_2,
    R.id.widget_course_item_3,
    R.id.widget_course_item_4,
)

private val COURSE_TITLE_IDS = intArrayOf(
    R.id.widget_course_title_1,
    R.id.widget_course_title_2,
    R.id.widget_course_title_3,
    R.id.widget_course_title_4,
)

private val COURSE_TIME_IDS = intArrayOf(
    R.id.widget_course_time_1,
    R.id.widget_course_time_2,
    R.id.widget_course_time_3,
    R.id.widget_course_time_4,
)

private val COURSE_DOT_IDS = intArrayOf(
    R.id.widget_course_dot_1,
    R.id.widget_course_dot_2,
    R.id.widget_course_dot_3,
    R.id.widget_course_dot_4,
)

/** Maximum number of course items displayed in the today schedule widget. Must match the layout. */
internal const val MAX_VISIBLE_COURSES = 4

private fun buildTodayScheduleRemoteViews(
    context: Context,
    appWidgetId: Int,
    state: TodayScheduleWidgetState,
): RemoteViews {
    val titleActiveColor = context.getColor(R.color.widget_course_title_active)
    val titlePastColor = context.getColor(R.color.widget_course_title_past)
    val timeActiveColor = context.getColor(R.color.widget_course_time_active)
    val timePastColor = context.getColor(R.color.widget_course_time_past)

    return RemoteViews(context.packageName, R.layout.widget_today_schedule).apply {
        setTextViewText(R.id.widget_today_date, state.dateLabel)

        // Empty state handling
        if (state.emptyText != null) {
            setViewVisibility(R.id.widget_course_list, View.GONE)
            setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
            setTextViewText(R.id.widget_empty_state, state.emptyText)
        } else {
            setViewVisibility(R.id.widget_course_list, View.VISIBLE)
            setViewVisibility(R.id.widget_empty_state, View.GONE)
        }

        for (i in 0 until MAX_VISIBLE_COURSES) {
            val item = state.courseItems.getOrNull(i)
            val itemId = COURSE_ITEM_IDS[i]
            val titleId = COURSE_TITLE_IDS[i]
            val timeId = COURSE_TIME_IDS[i]
            val dotId = COURSE_DOT_IDS[i]

            if (item != null) {
                setViewVisibility(itemId, View.VISIBLE)
                setTextViewText(titleId, item.title)
                setTextViewText(timeId, item.time)

                if (item.isPast) {
                    setTextColor(titleId, titlePastColor)
                    setTextColor(timeId, timePastColor)
                    setImageViewResource(dotId, R.drawable.widget_course_dot_past)
                } else {
                    setTextColor(titleId, titleActiveColor)
                    setTextColor(timeId, timeActiveColor)
                    setImageViewResource(dotId, R.drawable.widget_course_dot)
                }
            } else {
                setViewVisibility(itemId, View.GONE)
            }
        }

        // "+" button → open app to add course
        setOnClickPendingIntent(
            R.id.widget_today_add_btn,
            createAddCoursePendingIntent(
                context = context,
                appWidgetId = appWidgetId,
                selectedDate = state.targetDate.toString(),
            ),
        )

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

private fun createAddCoursePendingIntent(
    context: Context,
    appWidgetId: Int,
    selectedDate: String,
): PendingIntent {
    val intent = MainActivity.createLaunchIntent(
        context = context,
        selectedDate = selectedDate,
        destination = AppDestination.DAY,
    ).apply {
        data = Uri.parse("timetable://widget/add/$appWidgetId?date=$selectedDate")
        putExtra(EXTRA_WIDGET_ADD_COURSE, true)
    }
    return PendingIntent.getActivity(
        context,
        appWidgetId + 1000,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Extra key to signal that the widget "+" button was tapped. */
internal const val EXTRA_WIDGET_ADD_COURSE = "extra_widget_add_course"

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
    return context.getString(R.string.widget_date_format, date.monthValue, date.dayOfMonth) + " " + dayLabel(date.dayOfWeek.value, context)
}
