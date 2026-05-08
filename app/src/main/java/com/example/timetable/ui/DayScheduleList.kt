package com.example.timetable.ui

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.formatDateLabel
import com.example.timetable.notify.CourseReminderScheduler
import com.example.timetable.R
import kotlinx.coroutines.launch

@Composable
internal fun DayScheduleList(
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    entries: List<TimetableEntry>,
    selectedDate: String,
    selectedLocalDate: java.time.LocalDate,
    selectedDayEntries: List<TimetableEntry>,
    dateRangeEntriesCache: DateRangeEntriesCache,
    nextCourseSnapshot: NextCourseSnapshot?,
    importLauncher: ActivityResultLauncher<Array<String>>,
    exportLauncher: ActivityResultLauncher<String>,
    reminderConfig: ReminderConfig,
    appearanceConfig: AppearanceConfig,
    callbacks: DayListCallbacks,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pre-read string resources for use in callbacks
    val exportFilename = stringResource(R.string.export_filename)
    val msgSwitchedDefaultBg = stringResource(R.string.msg_switched_default_background)
    val msgDisabledImageBg = stringResource(R.string.msg_disabled_image_background)
    val msgClearedCustomBg = stringResource(R.string.msg_cleared_custom_background)
    val labelUnnamedCourse = stringResource(R.string.label_unnamed_course)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        state = listState,
    ) {
        item {
            HeroSection(
                config = HeroSectionConfig(
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
                    onExport = { exportLauncher.launch(exportFilename) },
                    onEnableNotifications = reminderConfig.onEnableNotifications,
                    notificationPermissionRequired = reminderConfig.notificationPermissionRequired,
                    notificationGranted = reminderConfig.notificationGranted,
                    exactAlarmPermissionRequired = CourseReminderScheduler.exactAlarmPermissionRequired(),
                    exactAlarmEnabled = reminderConfig.exactAlarmEnabled,
                    onOpenExactAlarmSettings = reminderConfig.onOpenExactAlarmSettings,
                    reminderMinutes = reminderConfig.minutes,
                    reminderOptions = reminderConfig.options,
                    onReminderMinutesChange = reminderConfig.onMinutesChange,
                    backgroundMode = appearanceConfig.backgroundAppearance.mode,
                    hasCustomBackground = BackgroundImageManager.hasCustomBackground(context),
                    onSelectBackgroundImage = appearanceConfig.onSelectBackgroundImage,
                    onUseBundledBackground = {
                        AppearanceStore.setBackgroundMode(context, AppBackgroundMode.BUNDLED_IMAGE)
                        appearanceConfig.onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                        scope.launch { snackbarHostState.showSnackbar(msgSwitchedDefaultBg) }
                    },
                    onUseGradientBackground = {
                        AppearanceStore.setBackgroundMode(context, AppBackgroundMode.GRADIENT)
                        appearanceConfig.onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                        scope.launch { snackbarHostState.showSnackbar(msgDisabledImageBg) }
                    },
                    onAdjustCustomBackground = appearanceConfig.onAdjustCustomBackground,
                    onClearCustomBackground = {
                        scope.launch {
                            BackgroundImageManager.clearCustomBackground(context)
                            if (appearanceConfig.backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
                                AppearanceStore.setBackgroundMode(context, AppBackgroundMode.BUNDLED_IMAGE)
                            }
                            appearanceConfig.onBackgroundAppearanceChange(AppearanceStore.getBackgroundAppearance(context))
                            snackbarHostState.showSnackbar(msgClearedCustomBg)
                        }
                    },
                    weekCardAlpha = appearanceConfig.weekCardAlpha,
                    onWeekCardAlphaChange = appearanceConfig.onWeekCardAlphaChange,
                    weekCardHue = appearanceConfig.weekCardHue,
                    onWeekCardHueChange = appearanceConfig.onWeekCardHueChange,
                ),
            )
        }
        nextCourseSnapshot?.let { upcoming ->
            item {
                NextCourseCard(
                    state = upcoming.toCardState(unnamedLabel = labelUnnamedCourse),
                    onViewDay = { callbacks.onDateChanged(upcoming.occurrenceDate.toString()) },
                )
            }
        }
        item {
            PerpetualCalendar(
                selectedDate = selectedDate,
                entries = entries,
                entriesByDateResolver = dateRangeEntriesCache::resolve,
                onDateChanged = callbacks.onDateChanged,
            )
        }
        item {
            SectionHeader(title = formatDateLabel(selectedDate, context))
        }
        if (selectedDayEntries.isEmpty()) {
            item {
                EmptyStateCard(
                    onAdd = { callbacks.onCreateEntry(selectedLocalDate, selectedDayEntries) },
                )
            }
        } else {
            items(selectedDayEntries, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    onEdit = { callbacks.onEditEntry(entry) },
                    onDuplicate = { callbacks.onDuplicateEntry(entry) },
                    onDelete = { callbacks.onDeleteEntry(entry) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(56.dp)) }
    }
}
