package com.example.timetable.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.AppCacheManager
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.areWeekTimeSlotsNonOverlapping
import com.example.timetable.data.findNextCourseSnapshot
import com.example.timetable.data.inferFixedWeekScheduleConfig
import com.example.timetable.data.syncWeekTimeSlotsWithEntryChange
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.formatDateLabel
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.parseEntryDate
import com.example.timetable.notify.CourseReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_WEEK_SLOT_START_MINUTES = 8 * 60
private const val DEFAULT_WEEK_SLOT_DURATION_MINUTES = 40
private const val DEFAULT_WEEK_SLOT_GAP_MINUTES = 5

private val appDestinationNameStateSaver = Saver<String, Any>(
    save = { it },
    restore = { savedValue ->
        AppDestination.fromSavedStateValue(savedValue).name
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(
    launchTarget: AppLaunchTarget = AppLaunchTarget(),
    viewModel: ScheduleViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var backgroundAppearance by remember(context) { mutableStateOf(AppearanceStore.getBackgroundAppearance(context)) }
    var weekCardAlpha by remember(context) { mutableStateOf(AppearanceStore.getWeekCardAlpha(context)) }
    var weekCardHue by remember(context) { mutableStateOf(AppearanceStore.getWeekCardHue(context)) }
    var weekTimeSlots by remember(context) { mutableStateOf(AppearanceStore.getWeekTimeSlots(context)) }

    val minDate = LocalDate.of(1970, 1, 1)
    val maxDate = LocalDate.of(2100, 12, 31)
    val initialDate = parseEntryDate(launchTarget.selectedDate.orEmpty())
        ?.takeIf { it in minDate..maxDate }
        ?: LocalDate.now().takeIf { it in minDate..maxDate }
        ?: LocalDate.of(2026, 1, 1)
    var selectedDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var currentDestinationName by rememberSaveable(stateSaver = appDestinationNameStateSaver) {
        mutableStateOf(launchTarget.destination.name)
    }
    val currentDestination = remember(currentDestinationName) {
        AppDestination.fromSavedName(currentDestinationName)
    }
    LaunchedEffect(currentDestinationName) {
        val normalizedDestinationName = currentDestination.name
        if (normalizedDestinationName != currentDestinationName) {
            currentDestinationName = normalizedDestinationName
        }
    }
    LaunchedEffect(launchTarget.selectedDate, launchTarget.destination) {
        val targetDate = parseEntryDate(launchTarget.selectedDate.orEmpty())?.takeIf { it in minDate..maxDate }
        if (targetDate != null && targetDate.toString() != selectedDate) {
            selectedDate = targetDate.toString()
        }
        if (currentDestinationName != launchTarget.destination.name) {
            currentDestinationName = launchTarget.destination.name
        }
    }
    val isWeekMode = currentDestination == AppDestination.WEEK
    val isSettingsPage = currentDestination == AppDestination.SETTINGS

    val selectedLocalDate = parseEntryDate(selectedDate) ?: minDate
    val selectedWeekStart = remember(selectedLocalDate) {
        selectedLocalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val selectedWeekEnd = remember(selectedWeekStart) { selectedWeekStart.plusDays(6) }
    val today = LocalDate.now()
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
    val nextCourseSnapshot = remember(entries, today, nowMinutes) {
        findNextCourseSnapshot(
            entries = entries,
            nowDate = today,
            nowMinutes = nowMinutes,
        )
    }

    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var pendingConflict by remember { mutableStateOf<PendingEntryConflict?>(null) }
    var editingWeekSlotIndex by remember { mutableStateOf<Int?>(null) }
    var addingWeekSlotInitial by remember { mutableStateOf<WeekTimeSlot?>(null) }
    var editingWeekSlotCount by remember { mutableStateOf(false) }
    var editingFixedWeekSchedule by remember { mutableStateOf(false) }
    var showBackgroundAdjustDialog by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf(CourseReminderScheduler.getReminderMinutesSet(context)) }
    val reminderOptions = remember { CourseReminderScheduler.reminderMinuteOptions() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "已开启通知提醒" else "未授予通知权限，可能无法收到提醒",
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect(snackbarHostState::showSnackbar)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromIcs(context.contentResolver, uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = viewModel.exportIcs()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(text)
                    }
                    snackbarHostState.showSnackbar("已导出日历文件")
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar("导出失败：${error.message ?: "未知错误"}")
                }
            }
        }
    }

    val backgroundImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        BackgroundImageManager.saveCustomBackground(context, context.contentResolver, uri)
                    }
                    backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                    showBackgroundAdjustDialog = true
                    snackbarHostState.showSnackbar("已更新背景图片")
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        "背景图片设置失败：${error.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    val dateRangeEntriesCache = remember(entries) {
        DateRangeEntriesCache(entries)
    }
    val visibleEntriesByDate = remember(dateRangeEntriesCache, isWeekMode, selectedLocalDate, selectedWeekStart, selectedWeekEnd) {
        if (isWeekMode) {
            dateRangeEntriesCache.resolve(selectedWeekStart, selectedWeekEnd)
        } else {
            dateRangeEntriesCache.resolve(selectedLocalDate, selectedLocalDate)
        }
    }
    val selectedDayEntries = remember(visibleEntriesByDate, selectedLocalDate) {
        visibleEntriesByDate[selectedLocalDate].orEmpty()
    }
    val filteredEntries = remember(selectedDayEntries) {
        selectedDayEntries
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackgroundLayer(backgroundAppearance = backgroundAppearance)

        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                when (currentDestination) {
                    AppDestination.DAY,
                    AppDestination.SETTINGS -> {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    text = if (currentDestination == AppDestination.DAY) "我的课表" else "设置",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                    AppDestination.WEEK -> Unit
                }
            },
            floatingActionButton = {
                if (!isSettingsPage) {
                    FloatingActionButton(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            editingEntry = createQuickEntryTemplate(
                                date = selectedLocalDate,
                                existingEntries = selectedDayEntries,
                            )
                        },
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "新增课程")
                    }
                }
            },
            bottomBar = {
                ViewModeSwitcher(
                    currentDestination = currentDestination,
                    onDestinationChange = { destination -> currentDestinationName = destination.name },
                )
            },
        ) { padding ->
            when (currentDestination) {
                AppDestination.WEEK -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WeekCalendarStrip(
                        selectedDate = selectedLocalDate,
                        onDateSelected = { date ->
                            if (date in minDate..maxDate) {
                                selectedDate = date.toString()
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = { editingFixedWeekSchedule = true }) {
                            Text("固定时间")
                        }
                        if (selectedLocalDate != today) {
                            OutlinedButton(onClick = { selectedDate = today.toString() }) {
                                Text("回到今天")
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(selectedDate, isWeekMode) {
                                var totalHorizontalDrag = 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount -> totalHorizontalDrag += dragAmount },
                                    onDragEnd = {
                                        when {
                                            totalHorizontalDrag > 80f -> {
                                                val previousDate = selectedLocalDate.minusDays(7)
                                                if (previousDate >= minDate) selectedDate = previousDate.toString()
                                            }
                                            totalHorizontalDrag < -80f -> {
                                                val nextDate = selectedLocalDate.plusDays(7)
                                                if (nextDate <= maxDate) selectedDate = nextDate.toString()
                                            }
                                        }
                                        totalHorizontalDrag = 0f
                                    },
                                )
                            }
                            .verticalScroll(rememberScrollState()),
                    ) {
                        WeekScheduleBoard(
                            selectedDate = selectedLocalDate,
                            weekStart = selectedWeekStart,
                            weekEnd = selectedWeekEnd,
                            entriesByDay = visibleEntriesByDate,
                            slots = weekTimeSlots,
                            cardAlpha = weekCardAlpha,
                            cardHue = weekCardHue,
                            onAddSlot = {
                                val nextSlot = defaultNewWeekSlot(weekTimeSlots)
                                if (nextSlot == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("当天已没有可新增的完整节次")
                                    }
                                } else {
                                    addingWeekSlotInitial = nextSlot
                                }
                            },
                            onCustomizeSlotCount = { editingWeekSlotCount = true },
                            onEntryClick = { editingEntry = it },
                            onSlotClick = { editingWeekSlotIndex = it },
                        )
                    }
                }
                }
                AppDestination.DAY -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedDate, isWeekMode) {
                            var totalHorizontalDrag = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount -> totalHorizontalDrag += dragAmount },
                                onDragEnd = {
                                    when {
                                        totalHorizontalDrag > 80f -> {
                                            val previousDate = selectedLocalDate.minusDays(1)
                                            if (previousDate >= minDate) selectedDate = previousDate.toString()
                                        }
                                        totalHorizontalDrag < -80f -> {
                                            val nextDate = selectedLocalDate.plusDays(1)
                                            if (nextDate <= maxDate) selectedDate = nextDate.toString()
                                        }
                                    }
                                    totalHorizontalDrag = 0f
                                },
                            )
                        }
                        .padding(padding),
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
                                onExport = { exportLauncher.launch("课表导出.ics") },
                                onEnableNotifications = {
                                    when {
                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("当前系统版本无需额外通知授权")
                                            }
                                        }
                                        CourseReminderScheduler.notificationsEnabled(context) ||
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            ) == PackageManager.PERMISSION_GRANTED -> {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("通知权限已开启")
                                            }
                                        }
                                        else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                reminderMinutes = reminderMinutes,
                                reminderOptions = reminderOptions,
                                onReminderMinutesChange = { minutes ->
                                    reminderMinutes = minutes
                                    viewModel.updateReminderMinutes(minutes)
                                },
                                backgroundMode = backgroundAppearance.mode,
                                hasCustomBackground = BackgroundImageManager.hasCustomBackground(context),
                                onSelectBackgroundImage = {
                                    backgroundImageLauncher.launch("image/*")
                                },
                                onUseBundledBackground = {
                                    AppearanceStore.setBackgroundMode(context, AppBackgroundMode.BUNDLED_IMAGE)
                                    backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                                    scope.launch { snackbarHostState.showSnackbar("已切换到默认背景") }
                                },
                                onUseGradientBackground = {
                                    AppearanceStore.setBackgroundMode(context, AppBackgroundMode.GRADIENT)
                                    backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                                    scope.launch { snackbarHostState.showSnackbar("已关闭图片背景") }
                                },
                                onAdjustCustomBackground = {
                                    showBackgroundAdjustDialog = true
                                },
                                onClearCustomBackground = {
                                    BackgroundImageManager.clearCustomBackground(context)
                                    if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
                                        AppearanceStore.setBackgroundMode(context, AppBackgroundMode.BUNDLED_IMAGE)
                                    }
                                    backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                                    scope.launch { snackbarHostState.showSnackbar("已清除自定义背景") }
                                },
                                weekCardAlpha = weekCardAlpha,
                                onWeekCardAlphaChange = { alpha ->
                                    weekCardAlpha = alpha
                                    AppearanceStore.setWeekCardAlpha(context, alpha)
                                },
                                weekCardHue = weekCardHue,
                                onWeekCardHueChange = { hue ->
                                    weekCardHue = hue
                                    AppearanceStore.setWeekCardHue(context, hue)
                                },
                            )
                        }
                        nextCourseSnapshot?.let { upcoming ->
                            item {
                                NextCourseCard(
                                    state = upcoming.toCardState(),
                                    onViewDay = { selectedDate = upcoming.occurrenceDate.toString() },
                                )
                            }
                        }
                        item {
                            PerpetualCalendar(
                                selectedDate = selectedDate,
                                entries = entries,
                                entriesByDateResolver = dateRangeEntriesCache::resolve,
                                onDateChanged = { selectedDate = it },
                            )
                        }
                        item {
                            SectionHeader(title = formatDateLabel(selectedDate))
                        }
                        if (filteredEntries.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    onAdd = {
                                        editingEntry = createQuickEntryTemplate(
                                            date = selectedLocalDate,
                                            existingEntries = selectedDayEntries,
                                        )
                                    },
                                )
                            }
                        } else {
                            items(filteredEntries, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    onEdit = { editingEntry = entry },
                                    onDuplicate = {
                                        editingEntry = duplicateEntryTemplate(entry)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("已复制课程，确认后保存")
                                        }
                                    },
                                    onDelete = { viewModel.deleteEntry(entry.id) },
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(56.dp)) }
                    }
                }
                }
                AppDestination.SETTINGS -> {
                    SettingsScreen(
                        clearingCache = clearingCache,
                        onClearCache = {
                            if (clearingCache) return@SettingsScreen
                            scope.launch {
                                clearingCache = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        AppCacheManager.clearAppCaches(context)
                                    }
                                    val message = if (result.bytesCleared > 0L) {
                                        "已清除 ${AppCacheManager.formatBytes(result.bytesCleared)} 缓存"
                                    } else {
                                        "没有可清理的缓存"
                                    }
                                    snackbarHostState.showSnackbar(message)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        "清除缓存失败：${error.message ?: "未知错误"}",
                                    )
                                } finally {
                                    clearingCache = false
                                }
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    if (showBackgroundAdjustDialog) {
        BackgroundImageAdjustDialog(
            backgroundAppearance = backgroundAppearance,
            onDismiss = { showBackgroundAdjustDialog = false },
            onSave = { transform ->
                AppearanceStore.setBackgroundImageTransform(context, transform)
                backgroundAppearance = AppearanceStore.getBackgroundAppearance(context)
                showBackgroundAdjustDialog = false
                scope.launch { snackbarHostState.showSnackbar("已更新背景展示范围") }
            },
        )
    }

    if (editingFixedWeekSchedule) {
        FixedWeekScheduleDialog(
            initialConfig = inferFixedWeekScheduleConfig(weekTimeSlots),
            initialSlots = weekTimeSlots,
            onDismiss = { editingFixedWeekSchedule = false },
            onSave = { updatedSlots ->
                weekTimeSlots = AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                editingFixedWeekSchedule = false
                scope.launch { snackbarHostState.showSnackbar("已更新周视图节次时间") }
            },
        )
    }

    editingEntry?.let { entry ->
        val existingEntry = entries.any { it.id == entry.id }
        val applyEntrySave: (TimetableEntry, Boolean) -> Unit = { updatedEntry, allowConflict ->
            viewModel.upsertEntry(updatedEntry, allowConflict = allowConflict)
            val syncedWeekTimeSlots = syncWeekTimeSlotsWithEntryChange(
                currentSlots = weekTimeSlots,
                originalEntry = entry.takeIf { existingEntry },
                updatedEntry = updatedEntry,
            )
            if (syncedWeekTimeSlots != weekTimeSlots) {
                weekTimeSlots = AppearanceStore.setWeekTimeSlots(context, syncedWeekTimeSlots)
            }
            pendingConflict = null
            editingEntry = null
        }
        EntryEditorDialog(
            initial = entry,
            onDismiss = {
                pendingConflict = null
                editingEntry = null
            },
            onSave = { updatedEntry ->
                val conflict = viewModel.previewConflict(updatedEntry)
                if (conflict != null) {
                    pendingConflict = PendingEntryConflict(
                        updatedEntry = updatedEntry,
                        conflictEntry = conflict,
                    )
                } else {
                    applyEntrySave(updatedEntry, false)
                }
            },
        )

        pendingConflict?.let { state ->
            AlertDialog(
                onDismissRequest = { pendingConflict = null },
                title = { Text("检测到时间冲突") },
                text = {
                    Text(
                        "与 ${state.conflictEntry.title}（${formatMinutes(state.conflictEntry.startMinutes)}-" +
                            "${formatMinutes(state.conflictEntry.endMinutes)}）重叠。你可以返回修改，或继续保存。",
                    )
                },
                confirmButton = {
                    Button(onClick = { applyEntrySave(state.updatedEntry, true) }) {
                        Text("仍然保存")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val adjusted = viewModel.suggestResolvedEntry(state.updatedEntry)
                                if (adjusted == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("当天没有可顺延的完整时间段")
                                    }
                                } else {
                                    applyEntrySave(adjusted, false)
                                }
                            },
                        ) {
                            Text("顺延并保存")
                        }
                        OutlinedButton(onClick = { pendingConflict = null }) {
                            Text("返回修改")
                        }
                    }
                },
            )
        }
    }

    editingWeekSlotIndex?.let { index ->
        val currentSlot = weekTimeSlots.getOrNull(index) ?: return@let
        WeekSlotEditorDialog(
            title = "编辑第 ${index + 1} 节时间",
            initial = currentSlot,
            onDismiss = { editingWeekSlotIndex = null },
            onSave = { updatedSlot ->
                val updatedSlots = weekTimeSlots.toMutableList().apply {
                    this[index] = updatedSlot
                }.sortedBy { it.startMinutes }
                if (!areWeekTimeSlotsNonOverlapping(updatedSlots)) {
                    scope.launch { snackbarHostState.showSnackbar("节次时间不能重叠") }
                    return@WeekSlotEditorDialog
                }
                weekTimeSlots = AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                editingWeekSlotIndex = null
            },
            onDelete = if (weekTimeSlots.size > 1) {
                {
                    val updatedSlots = weekTimeSlots.toMutableList().apply { removeAt(index) }
                    weekTimeSlots = AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                    editingWeekSlotIndex = null
                    scope.launch { snackbarHostState.showSnackbar("已删除第 ${index + 1} 节") }
                }
            } else {
                null
            },
        )
    }

    addingWeekSlotInitial?.let { initialSlot ->
        WeekSlotEditorDialog(
            title = "新增节次",
            initial = initialSlot,
            onDismiss = { addingWeekSlotInitial = null },
            onSave = { newSlot ->
                val updatedSlots = (weekTimeSlots + newSlot).sortedBy { it.startMinutes }
                if (!areWeekTimeSlotsNonOverlapping(updatedSlots)) {
                    scope.launch { snackbarHostState.showSnackbar("节次时间不能重叠") }
                    return@WeekSlotEditorDialog
                }
                weekTimeSlots = AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                addingWeekSlotInitial = null
                scope.launch { snackbarHostState.showSnackbar("已新增第 ${updatedSlots.indexOf(newSlot) + 1} 节") }
            },
        )
    }

    if (editingWeekSlotCount) {
        WeekSlotCountDialog(
            initialCount = weekTimeSlots.size,
            onDismiss = { editingWeekSlotCount = false },
            onSave = { count ->
                val updatedSlots = resizeWeekTimeSlots(weekTimeSlots, count)
                weekTimeSlots = AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                editingWeekSlotCount = false
                scope.launch {
                    val message = if (updatedSlots.size == count) {
                        "已调整为 $count 节"
                    } else {
                        "只能扩展到 ${updatedSlots.size} 节，后续时间不足"
                    }
                    snackbarHostState.showSnackbar(message)
                }
            },
        )
    }
}

internal fun defaultNewWeekSlot(slots: List<WeekTimeSlot>): WeekTimeSlot? {
    val lastSlot = slots.maxByOrNull { it.endMinutes }
    if (lastSlot == null) {
        return WeekTimeSlot(
            startMinutes = DEFAULT_WEEK_SLOT_START_MINUTES,
            endMinutes = DEFAULT_WEEK_SLOT_START_MINUTES + DEFAULT_WEEK_SLOT_DURATION_MINUTES,
        )
    }
    return nextWeekTimeSlot(lastSlot)
}

internal fun resizeWeekTimeSlots(slots: List<WeekTimeSlot>, targetCount: Int): List<WeekTimeSlot> {
    if (targetCount <= 0) return slots
    if (slots.size == targetCount) return slots.sortedBy { it.startMinutes }
    if (slots.size > targetCount) return slots.sortedBy { it.startMinutes }.take(targetCount)

    val expanded = slots.sortedBy { it.startMinutes }.toMutableList()
    while (expanded.size < targetCount) {
        val seed = expanded.lastOrNull()
        val nextSlot = if (seed == null) {
            WeekTimeSlot(
                startMinutes = DEFAULT_WEEK_SLOT_START_MINUTES,
                endMinutes = DEFAULT_WEEK_SLOT_START_MINUTES + DEFAULT_WEEK_SLOT_DURATION_MINUTES,
            )
        } else {
            nextWeekTimeSlot(seed)
        }
        if (nextSlot == null) break
        expanded += nextSlot
    }
    return expanded
}

internal fun nextWeekTimeSlot(previous: WeekTimeSlot): WeekTimeSlot? {
    val start = previous.endMinutes + DEFAULT_WEEK_SLOT_GAP_MINUTES
    val end = start + DEFAULT_WEEK_SLOT_DURATION_MINUTES
    if (end > 24 * 60) return null
    return WeekTimeSlot(startMinutes = start, endMinutes = end)
}

private data class PendingEntryConflict(
    val updatedEntry: TimetableEntry,
    val conflictEntry: TimetableEntry,
)
private fun NextCourseSnapshot.toCardState(): NextCourseCardState {
    return NextCourseCardState(
        title = entry.title.ifBlank { "未命名课程" },
        timeRange = "${formatMinutes(entry.startMinutes)} - ${formatMinutes(entry.endMinutes)}",
        location = entry.location,
        statusText = statusText,
    )
}

internal fun createQuickEntryTemplate(
    date: LocalDate,
    existingEntries: List<TimetableEntry>,
): TimetableEntry {
    val fallbackStart = 8 * 60
    val fallbackEnd = 9 * 60
    val lastEntry = existingEntries.maxByOrNull { it.endMinutes }

    val suggestedStart: Int
    val suggestedEnd: Int
    if (lastEntry == null) {
        suggestedStart = fallbackStart
        suggestedEnd = fallbackEnd
    } else {
        val templateDuration = (lastEntry.endMinutes - lastEntry.startMinutes).coerceIn(30, 120)
        suggestedStart = (lastEntry.endMinutes + DEFAULT_WEEK_SLOT_GAP_MINUTES).coerceIn(0, (24 * 60) - 1)
        suggestedEnd = (suggestedStart + templateDuration).coerceAtMost(24 * 60)
    }

    val (startMinutes, endMinutes) = if (suggestedEnd > suggestedStart) {
        suggestedStart to suggestedEnd
    } else {
        fallbackStart to fallbackEnd
    }

    return TimetableEntry(
        title = "",
        date = date.toString(),
        dayOfWeek = date.dayOfWeek.value,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
    )
}

internal fun duplicateEntryTemplate(source: TimetableEntry): TimetableEntry {
    return TimetableEntry(
        title = source.title,
        date = source.date,
        dayOfWeek = source.dayOfWeek,
        startMinutes = source.startMinutes,
        endMinutes = source.endMinutes,
        location = source.location,
        note = source.note,
        recurrenceType = source.recurrenceType,
        semesterStartDate = source.semesterStartDate,
        weekRule = source.weekRule,
        customWeekList = source.customWeekList,
        skipWeekList = source.skipWeekList,
    )
}
