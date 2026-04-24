package com.example.timetable.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.timetable.R
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.timetable.data.BackgroundAppearance
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.formatDateLabel
import com.example.timetable.notify.CourseReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun DayScheduleList(
    context: Context,
    scope: CoroutineScope,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    entries: List<TimetableEntry>,
    selectedDate: String,
    selectedLocalDate: java.time.LocalDate,
    filteredEntries: List<TimetableEntry>,
    selectedDayEntries: List<TimetableEntry>,
    dateRangeEntriesCache: DateRangeEntriesCache,
    nextCourseSnapshot: NextCourseSnapshot?,
    importLauncher: ActivityResultLauncher<Array<String>>,
    exportLauncher: ActivityResultLauncher<String>,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    exactAlarmSettingsLauncher: ActivityResultLauncher<Intent>,
    exactAlarmEnabled: Boolean,
    reminderMinutes: List<Int>,
    reminderOptions: List<Int>,
    onReminderMinutesChange: (List<Int>) -> Unit,
    backgroundAppearance: BackgroundAppearance,
    onBackgroundAppearanceChange: (BackgroundAppearance) -> Unit,
    onSelectBackgroundImage: () -> Unit,
    onAdjustCustomBackground: () -> Unit,
    weekCardAlpha: Float,
    onWeekCardAlphaChange: (Float) -> Unit,
    weekCardHue: Float,
    onWeekCardHueChange: (Float) -> Unit,
    onDateChanged: (String) -> Unit,
    onEditEntry: (TimetableEntry) -> Unit,
    onDuplicateEntry: (TimetableEntry) -> Unit,
    onDeleteEntry: (TimetableEntry) -> Unit,
    onCreateEntry: (java.time.LocalDate, List<TimetableEntry>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        state = listState,
    ) {
        item {
            HeroSection(
                courseCount = entries.size,
                onImport = {
                    importLauncher.launch(
                        arrayOf(
                            "text/calendar",
                            "text/plain",
                            "application/ics",
                            "application/x-ical",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                onExport = { exportLauncher.launch(context.getString(R.string.export_filename)) },
                onEnableNotifications = {
                    when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.msg_notifications_not_required))
                            }
                        }
                        CourseReminderScheduler.notificationsEnabled(context) ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(com.example.timetable.R.string.msg_notifications_enabled))
                            }
                        }
                        else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                exactAlarmPermissionRequired = CourseReminderScheduler.exactAlarmPermissionRequired(),
                exactAlarmEnabled = exactAlarmEnabled,
                onOpenExactAlarmSettings = {
                    val intent = CourseReminderScheduler.buildExactAlarmSettingsIntent(context)
                    if (intent == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.msg_exact_alarm_not_required))
                        }
                    } else {
                        exactAlarmSettingsLauncher.launch(intent)
                    }
                },
                reminderMinutes = reminderMinutes,
                reminderOptions = reminderOptions,
                onReminderMinutesChange = onReminderMinutesChange,
                backgroundMode = backgroundAppearance.mode,
                hasCustomBackground = BackgroundImageManager.hasCustomBackground(context),
                onSelectBackgroundImage = onSelectBackgroundImage,
                onUseBundledBackground = {
                    AppearanceStore.setBackgroundMode(context, AppBackgroundMode.BUNDLED_IMAGE)
                    onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_switched_default_background)) }
                },
                onUseGradientBackground = {
                    AppearanceStore.setBackgroundMode(context, AppBackgroundMode.GRADIENT)
                    onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_disabled_image_background)) }
                },
                onAdjustCustomBackground = onAdjustCustomBackground,
                onClearCustomBackground = {
                    scope.launch {
                        BackgroundImageManager.clearCustomBackground(context)
                        if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
                            AppearanceStore.setBackgroundMode(context, AppBackgroundMode.BUNDLED_IMAGE)
                        }
                        onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                        snackbarHostState.showSnackbar(context.getString(R.string.msg_cleared_custom_background))
                    }
                },
                weekCardAlpha = weekCardAlpha,
                onWeekCardAlphaChange = onWeekCardAlphaChange,
                weekCardHue = weekCardHue,
                onWeekCardHueChange = onWeekCardHueChange,
            )
        }
        nextCourseSnapshot?.let { upcoming ->
            item {
                NextCourseCard(
                    state = upcoming.toCardState(unnamedLabel = context.getString(R.string.label_unnamed_course)),
                    onViewDay = { onDateChanged(upcoming.occurrenceDate.toString()) },
                )
            }
        }
        item {
            PerpetualCalendar(
                selectedDate = selectedDate,
                entries = entries,
                entriesByDateResolver = dateRangeEntriesCache::resolve,
                onDateChanged = onDateChanged,
            )
        }
        item {
            SectionHeader(title = formatDateLabel(selectedDate))
        }
        if (filteredEntries.isEmpty()) {
            item {
                EmptyStateCard(
                    onAdd = { onCreateEntry(selectedLocalDate, selectedDayEntries) },
                )
            }
        } else {
            items(filteredEntries, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    onEdit = { onEditEntry(entry) },
                    onDuplicate = { onDuplicateEntry(entry) },
                    onDelete = { onDeleteEntry(entry) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(56.dp)) }
    }
}
