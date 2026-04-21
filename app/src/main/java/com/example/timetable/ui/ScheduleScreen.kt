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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.timetable.data.AppearanceStore
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.formatDateLabel
import com.example.timetable.data.parseEntryDate
import com.example.timetable.notify.CourseReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(viewModel: ScheduleViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val entriesByDate by viewModel.entriesByDate.collectAsStateWithLifecycle()
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
    val initialDate = LocalDate.now().takeIf { it in minDate..maxDate } ?: LocalDate.of(2026, 1, 1)
    var selectedDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var isWeekMode by rememberSaveable { mutableStateOf(false) }

    val selectedLocalDate = parseEntryDate(selectedDate) ?: minDate
    val selectedWeekStart = remember(selectedLocalDate) {
        selectedLocalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val selectedWeekEnd = remember(selectedWeekStart) { selectedWeekStart.plusDays(6) }

    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var editingWeekSlotIndex by remember { mutableStateOf<Int?>(null) }
    var addingWeekSlot by remember { mutableStateOf(false) }
    var editingWeekSlotCount by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf(CourseReminderScheduler.getReminderMinutes(context)) }
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
                runCatching {
                    val text = viewModel.exportIcs()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(text)
                    }
                    snackbarHostState.showSnackbar("已导出日历文件")
                }.onFailure {
                    snackbarHostState.showSnackbar("导出失败：${it.message ?: "未知错误"}")
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
                    snackbarHostState.showSnackbar("已更新背景图片")
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar(
                        "背景图片设置失败：${error.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    val filteredEntries = remember(entriesByDate, selectedDate, isWeekMode, selectedWeekStart) {
        if (isWeekMode) {
            buildList {
                for (offset in 0L..6L) {
                    val date = selectedWeekStart.plusDays(offset)
                    addAll(entriesByDate[date].orEmpty())
                }
            }
        } else {
            entriesByDate[selectedLocalDate].orEmpty()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackgroundLayer(backgroundAppearance = backgroundAppearance)

        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (!isWeekMode) {
                    LargeTopAppBar(
                        title = {
                            Text(
                                text = "我的课表",
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
            },
            floatingActionButton = {
                FloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        editingEntry = TimetableEntry(
                            title = "",
                            date = selectedDate,
                            dayOfWeek = selectedLocalDate.dayOfWeek.value,
                            startMinutes = 8 * 60,
                            endMinutes = 9 * 60,
                        )
                    },
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "新增课程")
                }
            },
            bottomBar = {
                ViewModeSwitcher(
                    isWeekMode = isWeekMode,
                    onModeChange = { isWeekMode = it },
                )
            },
        ) { padding ->
            if (isWeekMode) {
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
                            entries = filteredEntries,
                            slots = weekTimeSlots,
                            cardAlpha = weekCardAlpha,
                            cardHue = weekCardHue,
                            onAddSlot = { addingWeekSlot = true },
                            onCustomizeSlotCount = { editingWeekSlotCount = true },
                            onEntryClick = { editingEntry = it },
                            onSlotClick = { editingWeekSlotIndex = it },
                        )
                    }
                }
            } else {
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
                        item {
                            PerpetualCalendar(
                                selectedDate = selectedDate,
                                entries = entries,
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
                                        editingEntry = TimetableEntry(
                                            title = "",
                                            date = selectedDate,
                                            dayOfWeek = selectedLocalDate.dayOfWeek.value,
                                            startMinutes = 8 * 60,
                                            endMinutes = 9 * 60,
                                        )
                                    },
                                )
                            }
                        } else {
                            items(filteredEntries, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    onEdit = { editingEntry = entry },
                                    onDelete = { viewModel.deleteEntry(entry.id) },
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(56.dp)) }
                    }
                }
            }
        }
    }

    editingEntry?.let { entry ->
        EntryEditorDialog(
            initial = entry,
            onDismiss = { editingEntry = null },
            onSave = {
                viewModel.upsertEntry(it)
                editingEntry = null
            },
        )
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
                weekTimeSlots = updatedSlots
                AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                editingWeekSlotIndex = null
            },
            onDelete = if (weekTimeSlots.size > 1) {
                {
                    val updatedSlots = weekTimeSlots.toMutableList().apply { removeAt(index) }
                    weekTimeSlots = updatedSlots
                    AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                    editingWeekSlotIndex = null
                    scope.launch { snackbarHostState.showSnackbar("已删除第 ${index + 1} 节") }
                }
            } else {
                null
            },
        )
    }

    if (addingWeekSlot) {
        WeekSlotEditorDialog(
            title = "新增节次",
            initial = defaultNewWeekSlot(weekTimeSlots),
            onDismiss = { addingWeekSlot = false },
            onSave = { newSlot ->
                val updatedSlots = (weekTimeSlots + newSlot).sortedBy { it.startMinutes }
                weekTimeSlots = updatedSlots
                AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                addingWeekSlot = false
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
                weekTimeSlots = updatedSlots
                AppearanceStore.setWeekTimeSlots(context, updatedSlots)
                editingWeekSlotCount = false
                scope.launch { snackbarHostState.showSnackbar("已调整为 $count 节") }
            },
        )
    }
}

private fun defaultNewWeekSlot(slots: List<WeekTimeSlot>): WeekTimeSlot {
    val lastSlot = slots.maxByOrNull { it.endMinutes }
    if (lastSlot == null) return WeekTimeSlot(8 * 60, 8 * 60 + 40)
    return nextWeekTimeSlot(lastSlot)
}

private fun resizeWeekTimeSlots(slots: List<WeekTimeSlot>, targetCount: Int): List<WeekTimeSlot> {
    if (targetCount <= 0) return slots
    if (slots.size == targetCount) return slots.sortedBy { it.startMinutes }
    if (slots.size > targetCount) return slots.sortedBy { it.startMinutes }.take(targetCount)

    val expanded = slots.sortedBy { it.startMinutes }.toMutableList()
    while (expanded.size < targetCount) {
        val seed = expanded.lastOrNull()
        expanded += if (seed == null) {
            WeekTimeSlot(8 * 60, 8 * 60 + 40)
        } else {
            nextWeekTimeSlot(seed)
        }
    }
    return expanded
}

private fun nextWeekTimeSlot(previous: WeekTimeSlot): WeekTimeSlot {
    val start = (previous.endMinutes + 5).coerceAtMost(23 * 60 + 19)
    val end = (start + 40).coerceAtMost(24 * 60 - 1)
    return if (start < end) WeekTimeSlot(start, end) else WeekTimeSlot(23 * 60, 23 * 60 + 39)
}
