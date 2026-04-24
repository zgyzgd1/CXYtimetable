package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timetable.data.FixedWeekScheduleConfig
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.WeekRule
import com.example.timetable.data.areWeekTimeSlotsNonOverlapping
import com.example.timetable.data.buildWeekTimeSlotsFromFixedSchedule
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.parseWeekList
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.parseMinutes
import com.example.timetable.data.resolveRecurrenceType
import com.example.timetable.data.resolveWeekRule
import com.example.timetable.data.SemesterStore
import androidx.compose.ui.platform.LocalContext

@Composable
fun EntryEditorDialog(
    initial: TimetableEntry,
    onDismiss: () -> Unit,
    onSave: (TimetableEntry) -> Unit,
) {
    val initialRecurrence = resolveRecurrenceType(initial.recurrenceType) ?: RecurrenceType.NONE
    val initialWeekRule = resolveWeekRule(initial.weekRule) ?: WeekRule.ALL
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var dateText by rememberSaveable(initial.id) { mutableStateOf(initial.date) }
    var startTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.startMinutes)) }
    var endTime by rememberSaveable(initial.id) { mutableStateOf(formatMinutes(initial.endMinutes)) }
    var location by rememberSaveable(initial.id) { mutableStateOf(initial.location) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var recurrenceType by rememberSaveable(initial.id) { mutableStateOf(initialRecurrence) }
    var semesterStartDateText by rememberSaveable(initial.id) { mutableStateOf(initial.semesterStartDate) }
    var weekRule by rememberSaveable(initial.id) { mutableStateOf(initialWeekRule) }
    var customWeekListText by rememberSaveable(initial.id) { mutableStateOf(initial.customWeekList) }
    var skipWeekListText by rememberSaveable(initial.id) { mutableStateOf(initial.skipWeekList) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Auto-fill semester start date from global config if empty
    val globalSemesterDate = remember { SemesterStore.getSemesterStartDate(context) }
    if (semesterStartDateText.isBlank() && globalSemesterDate != null && recurrenceType == RecurrenceType.WEEKLY) {
        semesterStartDateText = globalSemesterDate.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.title.isBlank()) "新增课程" else "编辑课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorText = null
                    },
                    label = "课程名称",
                    singleLine = true,
                )
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
                AppTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = "地点",
                    singleLine = true,
                )
                AppTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        errorText = null
                    },
                    label = "备注",
                    minLines = 2,
                )
                RecurrenceSelector(
                    selected = recurrenceType,
                    onSelected = {
                        recurrenceType = it
                        errorText = null
                    },
                )
                if (recurrenceType == RecurrenceType.WEEKLY) {
                    AppTextField(
                        value = semesterStartDateText,
                        onValueChange = {
                            semesterStartDateText = it
                            errorText = null
                        },
                        label = "学期开学日期",
                        placeholder = "2026-09-01",
                        singleLine = true,
                    )
                    WeekRuleSelector(
                        selected = weekRule,
                        onSelected = {
                            weekRule = it
                            errorText = null
                        },
                    )
                    if (weekRule == WeekRule.CUSTOM) {
                        AppTextField(
                            value = customWeekListText,
                            onValueChange = {
                                customWeekListText = it
                                errorText = null
                            },
                            label = "自定义周次",
                            placeholder = "1,3,5 或 1-16",
                            singleLine = true,
                        )
                    }
                    AppTextField(
                        value = skipWeekListText,
                        onValueChange = {
                            skipWeekListText = it
                            errorText = null
                        },
                        label = "跳过周次(可选)",
                        placeholder = "如 8,12",
                        singleLine = true,
                    )
                }
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedStart = parseMinutes(startTime)
                    val parsedEnd = parseMinutes(endTime)
                    val parsedDate = parseEntryDate(dateText)
                    val normalizedCustomWeekList = normalizeWeekListText(customWeekListText)
                    val normalizedSkipWeekList = normalizeWeekListText(skipWeekListText)
                    val parsedSemesterStart = if (semesterStartDateText.isBlank()) {
                        parsedDate
                    } else {
                        parseEntryDate(semesterStartDateText)
                    }
                    val customWeeks = parseWeekList(normalizedCustomWeekList)
                    val skipWeeks = parseWeekList(normalizedSkipWeekList)
                    when {
                        title.trim().isBlank() -> errorText = "请输入课程名称"
                        title.trim().length > 64 -> errorText = "课程名称不能超过 64 个字符"
                        parsedDate == null -> errorText = "请输入合法日期，范围 1970-01-01 到 2100-12-31"
                        parsedStart == null || parsedEnd == null -> errorText = "请输入合法时间，例如 08:00"
                        parsedStart >= parsedEnd -> errorText = "结束时间需要晚于开始时间"
                        location.trim().length > 64 -> errorText = "地点不能超过 64 个字符"
                        note.trim().length > 256 -> errorText = "备注不能超过 256 个字符"
                        recurrenceType == RecurrenceType.WEEKLY && parsedSemesterStart == null -> errorText = "请输入合法的学期开学日期"
                        recurrenceType == RecurrenceType.WEEKLY && customWeeks == null -> errorText = "自定义周次格式错误，请使用 1,3,5 或 1-16"
                        recurrenceType == RecurrenceType.WEEKLY && skipWeeks == null -> errorText = "跳过周次格式错误，请使用 8,12 或 1-3"
                        recurrenceType == RecurrenceType.WEEKLY && weekRule == WeekRule.CUSTOM && customWeeks.isNullOrEmpty() -> {
                            errorText = "自定义周次不能为空"
                        }
                        else -> {
                            // Persist the semester start date to global config
                            if (recurrenceType == RecurrenceType.WEEKLY && parsedSemesterStart != null) {
                                SemesterStore.setSemesterStartDate(context, parsedSemesterStart)
                            }
                            onSave(
                                initial.copy(
                                    title = title.trim(),
                                    date = parsedDate.toString(),
                                    dayOfWeek = parsedDate.dayOfWeek.value,
                                    startMinutes = parsedStart,
                                    endMinutes = parsedEnd,
                                    location = location.trim(),
                                    note = note.trim(),
                                    recurrenceType = recurrenceType.name,
                                    semesterStartDate = if (recurrenceType == RecurrenceType.WEEKLY) {
                                        parsedSemesterStart.toString()
                                    } else {
                                        ""
                                    },
                                    weekRule = if (recurrenceType == RecurrenceType.WEEKLY) {
                                        weekRule.name
                                    } else {
                                        WeekRule.ALL.name
                                    },
                                    customWeekList = if (recurrenceType == RecurrenceType.WEEKLY) {
                                        normalizedCustomWeekList
                                    } else {
                                        ""
                                    },
                                    skipWeekList = if (recurrenceType == RecurrenceType.WEEKLY) {
                                        normalizedSkipWeekList
                                    } else {
                                        ""
                                    },
                                ),
                            )
                        }
                    }
                },
            ) {
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
private fun RecurrenceSelector(
    selected: RecurrenceType,
    onSelected: (RecurrenceType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("重复规则", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurrenceType.entries.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(option) },
                ) {
                    Text(
                        when (option) {
                            RecurrenceType.NONE -> "仅当天"
                            RecurrenceType.WEEKLY -> "按周循环"
                        } + if (option == selected) " ✓" else "",
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekRuleSelector(
    selected: WeekRule,
    onSelected: (WeekRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("周次规则", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val leftOptions = listOf(WeekRule.ALL, WeekRule.ODD)
            leftOptions.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(option) },
                ) {
                    Text(weekRuleLabel(option) + if (option == selected) " ✓" else "")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val rightOptions = listOf(WeekRule.EVEN, WeekRule.CUSTOM)
            rightOptions.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(option) },
                ) {
                    Text(weekRuleLabel(option) + if (option == selected) " ✓" else "")
                }
            }
        }
    }
}

private fun weekRuleLabel(rule: WeekRule): String {
    return when (rule) {
        WeekRule.ALL -> "每周"
        WeekRule.ODD -> "单周"
        WeekRule.EVEN -> "双周"
        WeekRule.CUSTOM -> "自定义"
    }
}

private fun normalizeWeekListText(raw: String): String {
    return raw
        .trim()
        .replace('，', ',')
        .replace(" ", "")
}

@Composable
fun TimeRangeFields(
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
fun AppTextField(
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

@Composable
fun WeekSlotEditorDialog(
    title: String,
    initial: WeekTimeSlot,
    onDismiss: () -> Unit,
    onSave: (WeekTimeSlot) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var startTime by rememberSaveable(title) { mutableStateOf(formatMinutes(initial.startMinutes)) }
    var endTime by rememberSaveable(title) { mutableStateOf(formatMinutes(initial.endMinutes)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedStart = parseMinutes(startTime)
                    val parsedEnd = parseMinutes(endTime)
                    when {
                        parsedStart == null || parsedEnd == null -> errorText = "请输入合法时间，例如 08:00"
                        parsedStart >= parsedEnd -> errorText = "结束时间需要晚于开始时间"
                        else -> onSave(WeekTimeSlot(parsedStart, parsedEnd))
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onDelete?.let {
                    OutlinedButton(onClick = it) {
                        Text("删除")
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
fun WeekSlotCountDialog(
    initialCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var countText by rememberSaveable(initialCount) { mutableStateOf(initialCount.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义节数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = countText,
                    onValueChange = {
                        countText = it
                        errorText = null
                    },
                    label = "总节数",
                    placeholder = "20",
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                )
                Text(
                    text = "会保留前面的节次时间，多出来的节次自动补在后面，减少时从末尾裁剪。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = countText.toIntOrNull()
                    when {
                        parsed == null -> errorText = "请输入数字"
                        parsed !in 1..20 -> errorText = "节数范围为 1 到 20"
                        else -> onSave(parsed)
                    }
                },
            ) {
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
fun FixedWeekScheduleDialog(
    initialConfig: FixedWeekScheduleConfig,
    initialSlots: List<WeekTimeSlot>,
    onDismiss: () -> Unit,
    onSave: (List<WeekTimeSlot>) -> Unit,
) {
    var firstStartTime by rememberSaveable(initialConfig) {
        mutableStateOf(formatMinutes(initialConfig.firstStartMinutes))
    }
    var lessonDurationText by rememberSaveable(initialConfig) {
        mutableStateOf(initialConfig.lessonDurationMinutes.toString())
    }
    var breakDurationText by rememberSaveable(initialConfig) {
        mutableStateOf(initialConfig.breakDurationMinutes.toString())
    }
    var slotCountText by rememberSaveable(initialConfig) {
        mutableStateOf(initialConfig.slotCount.toString())
    }
    var slotDrafts by remember(initialSlots, initialConfig) {
        mutableStateOf(
            initialSlots
                .takeIf { it.isNotEmpty() }
                ?.map { EditableWeekSlotDraft.fromSlot(it) }
                ?: buildWeekTimeSlotsFromFixedSchedule(initialConfig).map { EditableWeekSlotDraft.fromSlot(it) },
        )
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("固定上课时间") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppTextField(
                    value = firstStartTime,
                    onValueChange = {
                        firstStartTime = it
                        errorText = null
                    },
                    label = "首节开始时间",
                    placeholder = "08:00",
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(
                        value = lessonDurationText,
                        onValueChange = {
                            lessonDurationText = it
                            errorText = null
                        },
                        label = "单节时长(分钟)",
                        keyboardType = KeyboardType.Number,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = breakDurationText,
                        onValueChange = {
                            breakDurationText = it
                            errorText = null
                        },
                        label = "节间休息(分钟)",
                        keyboardType = KeyboardType.Number,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                AppTextField(
                    value = slotCountText,
                    onValueChange = {
                        slotCountText = it
                        errorText = null
                    },
                    label = "生成节数",
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                )
                Text(
                    text = "先用固定规则快速生成节次，再在下面逐节微调。每一节之间可以留空档，不要求连续。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val generatedSlots = buildGeneratedWeekSlots(
                                firstStartTime = firstStartTime,
                                lessonDurationText = lessonDurationText,
                                breakDurationText = breakDurationText,
                                slotCountText = slotCountText,
                            )
                            if (generatedSlots == null) {
                                errorText = "请先输入合法的固定时间参数"
                            } else {
                                slotDrafts = generatedSlots.map { EditableWeekSlotDraft.fromSlot(it) }
                                errorText = null
                            }
                        },
                    ) {
                        Text("按固定规则生成")
                    }
                    Text(
                        text = "当前 ${slotDrafts.size} 节",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                slotDrafts.forEachIndexed { index, draft ->
                    WeekSlotDraftEditor(
                        index = index,
                        draft = draft,
                        onStartChanged = { value ->
                            slotDrafts = slotDrafts.toMutableList().apply {
                                this[index] = this[index].copy(startTime = value)
                            }
                            errorText = null
                        },
                        onEndChanged = { value ->
                            slotDrafts = slotDrafts.toMutableList().apply {
                                this[index] = this[index].copy(endTime = value)
                            }
                            errorText = null
                        },
                    )
                }

                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedSlots = slotDrafts.toWeekTimeSlots()
                    when {
                        parsedSlots == null -> errorText = "请先把每一节的开始和结束时间填写完整，例如 08:00"
                        parsedSlots.any { it.startMinutes >= it.endMinutes } -> errorText = "每一节都需要满足结束时间晚于开始时间"
                        !areWeekTimeSlotsNonOverlapping(parsedSlots) -> errorText = "节次时间不能交叉，后面的节次可以留空档，但不能早于上一节结束"
                        else -> onSave(parsedSlots)
                    }
                },
            ) {
                Text("应用")
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
private fun WeekSlotDraftEditor(
    index: Int,
    draft: EditableWeekSlotDraft,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "第${index + 1}节",
            style = MaterialTheme.typography.titleSmall,
        )
        TimeRangeFields(
            startTime = draft.startTime,
            endTime = draft.endTime,
            onStartChanged = onStartChanged,
            onEndChanged = onEndChanged,
        )
    }
}

private data class EditableWeekSlotDraft(
    val startTime: String,
    val endTime: String,
) {
    companion object {
        fun fromSlot(slot: WeekTimeSlot): EditableWeekSlotDraft {
            return EditableWeekSlotDraft(
                startTime = formatMinutes(slot.startMinutes),
                endTime = formatMinutes(slot.endMinutes),
            )
        }
    }
}

private fun List<EditableWeekSlotDraft>.toWeekTimeSlots(): List<WeekTimeSlot>? {
    val parsed = mutableListOf<WeekTimeSlot>()
    forEach { draft ->
        val start = parseMinutes(draft.startTime) ?: return null
        val end = parseMinutes(draft.endTime) ?: return null
        parsed += WeekTimeSlot(
            startMinutes = start,
            endMinutes = end,
        )
    }
    return parsed
}

private fun buildGeneratedWeekSlots(
    firstStartTime: String,
    lessonDurationText: String,
    breakDurationText: String,
    slotCountText: String,
): List<WeekTimeSlot>? {
    val parsedStart = parseMinutes(firstStartTime)
    val parsedLessonDuration = lessonDurationText.toIntOrNull()
    val parsedBreakDuration = breakDurationText.toIntOrNull()
    val parsedSlotCount = slotCountText.toIntOrNull()
    if (
        parsedStart == null ||
        parsedStart >= 24 * 60 ||
        parsedLessonDuration == null ||
        parsedBreakDuration == null ||
        parsedSlotCount == null ||
        parsedLessonDuration !in 1..240 ||
        parsedBreakDuration !in 0..120 ||
        parsedSlotCount !in 1..20
    ) {
        return null
    }

    val config = FixedWeekScheduleConfig(
        firstStartMinutes = parsedStart,
        lessonDurationMinutes = parsedLessonDuration,
        breakDurationMinutes = parsedBreakDuration,
        slotCount = parsedSlotCount,
    )
    return buildWeekTimeSlotsFromFixedSchedule(config).takeIf { it.size == parsedSlotCount }
}
