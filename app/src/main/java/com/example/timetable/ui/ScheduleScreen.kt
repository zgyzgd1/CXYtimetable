package com.example.timetable.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.dayLabel
import com.example.timetable.data.formatDateLabel
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.parseMinutes
import com.example.timetable.data.parseEntryDate
import com.example.timetable.notify.CourseReminderScheduler
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/**
 * 课程表主应用界面
 * 整合所有 UI 组件，管理应用状态和用户交互
 *
 * @param viewModel 视图模型实例，提供数据和业务逻辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(viewModel: ScheduleViewModel = viewModel()) {
    // 收集课程列表状态
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    // Snackbar 主机状态，用于显示提示消息
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // 当前选中的日期，支持较广时间范围
    val minDate = LocalDate.of(1970, 1, 1)
    val maxDate = LocalDate.of(2100, 12, 31)
    val initialDate = LocalDate.now().takeIf { it in minDate..maxDate } ?: LocalDate.of(2026, 1, 1)
    var selectedDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    val selectedLocalDate = parseEntryDate(selectedDate) ?: minDate
    // 正在编辑的课程条目，null 表示没有在编辑
    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    // 是否显示二维码分享弹窗
    var showQr by remember { mutableStateOf(false) }
    // 是否显示分享码导入弹窗
    var showShareImport by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf(CourseReminderScheduler.getReminderMinutes(context)) }
    val reminderOptions = remember { CourseReminderScheduler.reminderMinuteOptions() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            if (granted) {
                snackbarHostState.showSnackbar("已开启手机通知提醒")
            } else {
                snackbarHostState.showSnackbar("未授予通知权限，可能无法收到提醒")
            }
        }
    }

    // 监听视图模型的消息并显示 Snackbar
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 创建导入文件的启动器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFromIcs(context.contentResolver, uri)
        }
    }

    // 创建导出文件的启动器
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
                    snackbarHostState.showSnackbar("已导出日历")
                }.onFailure {
                    snackbarHostState.showSnackbar("导出失败：${it.message ?: "未知错误"}")
                }
            }
        }
    }

    // 根据选中的日期过滤课程列表
    val filteredEntries = remember(entries, selectedDate) {
        entries.filter { it.date == selectedDate }.sortedBy { it.startMinutes }
    }

    // 构建应用的主框架
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("课程表助手") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            // 浮动操作按钮：新增课程
            FloatingActionButton(onClick = {
                editingEntry = TimetableEntry(
                    title = "",
                    date = selectedDate,
                    dayOfWeek = selectedLocalDate.dayOfWeek.value,
                    startMinutes = 8 * 60,
                    endMinutes = 9 * 60,
                )
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新增课程")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        )
                    )
                )
                .pointerInput(selectedDate) {
                    var totalHorizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            totalHorizontalDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                // 右滑：查看昨天
                                totalHorizontalDrag > 80f -> {
                                    val previousDate = selectedLocalDate.minusDays(1)
                                    if (previousDate >= minDate) {
                                        selectedDate = previousDate.toString()
                                    }
                                }
                                // 左滑：查看明天
                                totalHorizontalDrag < -80f -> {
                                    val nextDate = selectedLocalDate.plusDays(1)
                                    if (nextDate <= maxDate) {
                                        selectedDate = nextDate.toString()
                                    }
                                }
                            }
                            totalHorizontalDrag = 0f
                        },
                    )
                }
                .padding(padding),
        ) {
            // 可滚动的课程列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                state = listState,
            ) {
                // 顶部英雄区域：显示统计信息和操作按钮
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
                                )
                            )
                        },
                        onExport = { exportLauncher.launch("课程表导出.ics") },
                        onImportShare = { showShareImport = true },
                        onShare = { showQr = true },
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
                                else -> {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                        reminderMinutes = reminderMinutes,
                        reminderOptions = reminderOptions,
                        onReminderMinutesChange = { minutes ->
                            reminderMinutes = minutes
                            viewModel.updateReminderMinutes(minutes)
                        },
                    )
                }
                // 日期切换
                item {
                    PerpetualCalendar(
                        selectedDate = selectedDate,
                        entries = entries,
                        onDateChanged = { selectedDate = it },
                    )
                }
                // 当前选中日期的标题
                item {
                    SectionHeader(title = formatDateLabel(selectedDate))
                }
                // 如果没有课程，显示空状态卡片
                if (filteredEntries.isEmpty()) {
                    item {
                        EmptyStateCard(onAdd = {
                            editingEntry = TimetableEntry(
                                title = "",
                                date = selectedDate,
                                dayOfWeek = selectedLocalDate.dayOfWeek.value,
                                startMinutes = 8 * 60,
                                endMinutes = 9 * 60,
                            )
                        })
                    }
                } else {
                    // 显示课程列表
                    items(filteredEntries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onEdit = { editingEntry = entry },
                            onDelete = { viewModel.deleteEntry(entry.id) },
                        )
                    }
                }
                // 底部留白
                item { Spacer(modifier = Modifier.height(56.dp)) }
            }
        }
    }

    // 课程编辑器对话框
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

    // 二维码分享弹窗
    if (showQr) {
        val payload = viewModel.exportSharePayload()
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showQr = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("二维码分享", style = MaterialTheme.typography.titleLarge)
                Text(
                    "该二维码包含当前课程表数据，另一台设备可用同类页面导入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QrCode(
                    content = payload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp)),
                )
                OutlinedButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享课程表"))
                }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("分享文本")
                }
            }
        }
    }

    if (showShareImport) {
        SharePayloadImportDialog(
            onDismiss = { showShareImport = false },
            onImport = { payload ->
                viewModel.importFromSharePayload(payload)
                showShareImport = false
            },
        )
    }
}

/**
 * 英雄区域组件
 * 显示应用简介、课程统计和主要操作按钮
 *
 * @param courseCount 课程总数
 * @param onImport 导入按钮点击回调
 * @param onExport 导出按钮点击回调
 * @param onShare 分享按钮点击回调
 */
@Composable
private fun HeroSection(
    courseCount: Int,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onImportShare: () -> Unit,
    onShare: () -> Unit,
    onEnableNotifications: () -> Unit,
    reminderMinutes: Int,
    reminderOptions: List<Int>,
    onReminderMinutesChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "简约、清晰、可分享",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "当前共 $courseCount 门课程。支持导入 .ics、导出 .ics，并可用二维码分享课程表。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Text("导入日历", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Text("导出日历", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onImportShare, modifier = Modifier.weight(1f)) {
                        Text("导入分享码", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Text("二维码分享", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            TextButton(onClick = onEnableNotifications) {
                Text("开启手机通知")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "提醒提前时间：$reminderMinutes 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    reminderOptions.forEach { option ->
                        if (option == reminderMinutes) {
                            Button(onClick = { onReminderMinutesChange(option) }) {
                                Text("${option}m")
                            }
                        } else {
                            OutlinedButton(onClick = { onReminderMinutesChange(option) }) {
                                Text("${option}m")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharePayloadImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var payload by rememberSaveable { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入分享码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = payload,
                    onValueChange = {
                        payload = it
                        errorText = null
                    },
                    label = "分享文本",
                    minLines = 4,
                )
                if (errorText != null) {
                    Text(errorText!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val input = payload.trim()
                if (input.isBlank()) {
                    errorText = "请输入分享文本"
                } else if (input.length > 300_000) {
                    errorText = "分享内容过长"
                } else {
                    onImport(input)
                }
            }) {
                Text("导入")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 星期选择标签页组件
 * 显示一周七天的选择按钮
 *
 * @param selectedDay 当前选中的星期
 * @param onDaySelected 选择星期时的回调
 */
@Composable
private fun PerpetualCalendar(
    selectedDate: String,
    entries: List<TimetableEntry>,
    onDateChanged: (String) -> Unit,
) {
    val min = LocalDate.of(1970, 1, 1)
    val max = LocalDate.of(2100, 12, 31)
    val selected = parseEntryDate(selectedDate) ?: min
    var visibleMonthText by rememberSaveable(selected.withDayOfMonth(1).toString()) {
        mutableStateOf(selected.withDayOfMonth(1).toString())
    }

    val visibleMonth = parseEntryDate(visibleMonthText)?.let { YearMonth.from(it) } ?: YearMonth.from(selected)
    val monthStart = visibleMonth.atDay(1)
    val monthStartOffset = monthStart.dayOfWeek.value - 1
    val daysInMonth = visibleMonth.lengthOfMonth()
    val totalCells = monthStartOffset + daysInMonth
    val totalRows = (totalCells + 6) / 7
    val entriesByDate = remember(entries) { entries.groupingBy { it.date }.eachCount() }
    val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val prevMonth = visibleMonth.minusMonths(1)
                val nextMonth = visibleMonth.plusMonths(1)
                OutlinedButton(
                    onClick = { visibleMonthText = prevMonth.atDay(1).toString() },
                    enabled = prevMonth.atDay(1) >= min.withDayOfMonth(1),
                ) { Text("上月") }
                Text(
                    "${visibleMonth.year}年${visibleMonth.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = { visibleMonthText = nextMonth.atDay(1).toString() },
                    enabled = nextMonth.atDay(1) <= max.withDayOfMonth(1),
                ) { Text("下月") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            for (row in 0 until totalRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (column in 0 until 7) {
                        val dayNumber = row * 7 + column - monthStartOffset + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = visibleMonth.atDay(dayNumber)
                            val entryCount = entriesByDate[date.toString()] ?: 0
                            Surface(
                                onClick = { onDateChanged(date.toString()) },
                                enabled = date in min..max,
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = if (date == selected) 3.dp else 0.dp,
                                color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .border(
                                        width = if (date == selected) 2.dp else 1.dp,
                                        color = if (date == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(16.dp),
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (date == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                                text = weekdayLabels[date.dayOfWeek.value - 1],
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (date == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (entryCount > 0) {
                                            Text(
                                                text = "$entryCount 门课",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (date == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分区标题组件
 * 显示当前查看的星期标题
 *
 * @param title 标题文本
 */
@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text("按时间顺序展示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 课程卡片组件
 * 显示单个课程的详细信息，包括时间、地点等
 *
 * @param entry 课程条目数据
 * @param onEdit 编辑按钮点击回调
 * @param onDelete 删除按钮点击回调
 */
@Composable
private fun EntryCard(
    entry: TimetableEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧星期标识
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayLabel(entry.dayOfWeek),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // 中间课程信息
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatDateLabel(entry.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatMinutes(entry.startMinutes)} - ${formatMinutes(entry.endMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.location.isNotBlank()) {
                    Text(entry.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.note.isNotBlank()) {
                    Text(entry.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 右侧操作按钮
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

/**
 * 空状态卡片组件
 * 当没有课程时显示提示信息
 *
 * @param onAdd 添加课程按钮点击回调
 */
@Composable
private fun EmptyStateCard(onAdd: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("今天还没有课程", style = MaterialTheme.typography.titleLarge)
            Text(
                "点击右下角按钮添加一条课程，或者切换到其他日期查看。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onAdd) {
                Text("添加课程")
            }
        }
    }
}

/**
 * 课程编辑器对话框
 * 提供表单用于添加或编辑课程信息
 *
 * @param initial 初始课程数据
 * @param onDismiss 取消编辑回调
 * @param onSave 保存课程回调
 */
@Composable
private fun EntryEditorDialog(
    initial: TimetableEntry,
    onDismiss: () -> Unit,
    onSave: (TimetableEntry) -> Unit,
) {
    // 表单字段状态
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var dateText by rememberSaveable(initial.id) { mutableStateOf(initial.date) }
    var startTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.startMinutes)) }
    var endTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.endMinutes)) }
    var location by rememberSaveable(initial.id) { mutableStateOf(initial.location) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var errorText by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.title.isBlank()) "新增课程" else "编辑课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 课程名称输入框
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorText = null
                    },
                    label = "课程名称",
                    singleLine = true,
                )
                // 日期输入
                AppTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        errorText = null
                    },
                    label = "日期",
                    placeholder = "2026-09-01",
                    singleLine = true,
                )
                // 开始和结束时间输入
                TimeRangeFields(
                    startTime = startTime,
                    endTime = endTime,
                    onStartChanged = {
                        startTime = it
                        errorText = null
                    },
                    onEndChanged = {
                        endTime = it
                        errorText = null
                    },
                )
                // 地点输入框
                AppTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = "地点",
                    singleLine = true,
                )
                // 备注输入框
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注",
                    minLines = 2,
                )
                // 错误提示文本
                if (errorText != null) {
                    Text(errorText!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedStart = parseMinutes(startTime)
                val parsedEnd = parseMinutes(endTime)
                val parsedDate = parseEntryDate(dateText)
                when {
                    title.trim().isBlank() -> errorText = "请输入课程名称"
                    title.trim().length > 64 -> errorText = "课程名称不能超过 64 字"
                    parsedDate == null -> errorText = "请输入合法日期，范围 1970-01-01 到 2100-12-31"
                    parsedStart == null || parsedEnd == null -> errorText = "请输入合法时间，例如 08:00"
                    parsedStart >= parsedEnd -> errorText = "结束时间需要晚于开始时间"
                    location.trim().length > 64 -> errorText = "地点不能超过 64 字"
                    note.trim().length > 256 -> errorText = "备注不能超过 256 字"
                    else -> onSave(
                        initial.copy(
                            title = title.trim(),
                            date = parsedDate.toString(),
                            dayOfWeek = parsedDate.dayOfWeek.value,
                            startMinutes = parsedStart,
                            endMinutes = parsedEnd,
                            location = location.trim(),
                            note = note.trim(),
                        )
                    )
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun TimeRangeFields(
    startTime: String,
    endTime: String,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField(
            value = startTime,
            onValueChange = onStartChanged,
            label = "开始时间",
            placeholder = "08:00",
            keyboardType = KeyboardType.Text,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        AppTextField(
            value = endTime,
            onValueChange = onEndChanged,
            label = "结束时间",
            placeholder = "09:30",
            keyboardType = KeyboardType.Text,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
    )
}
