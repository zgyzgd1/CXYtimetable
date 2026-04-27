package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import com.example.timetable.data.EntryConstants
import com.example.timetable.data.EntryValidationError
import com.example.timetable.data.EntryValidator
import com.example.timetable.data.FixedWeekScheduleConfig
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.WeekRule
import com.example.timetable.data.areWeekTimeSlotsNonOverlapping
import com.example.timetable.data.buildWeekTimeSlotsFromFixedSchedule
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.normalizeWeekListText
import com.example.timetable.data.parseWeekList
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.parseMinutes
import com.example.timetable.data.resolveRecurrenceType
import com.example.timetable.data.resolveWeekRule
import com.example.timetable.data.SemesterStore

/**
 * 课程条目编辑器对话框。
 *
 * 用于创建或编辑课程表条目，包含标题、日期、时间、地点、备注等信息。
 *
 * @param initial 初始课程表条目
 * @param onDismiss 关闭对话框的回调
 * @param onSave 保存课程表条目的回调
 */
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

    // Pre-load string resources for use in validation logic
    val validationErrorMessages = remember {
        EntryValidationError.entries.associateWith { error ->
            context.getString(error.messageResId)
        }
    }

    // Auto-fill semester start date from global config if empty
    val globalSemesterDate = remember { SemesterStore.getSemesterStartDate(context) }
    if (semesterStartDateText.isBlank() && globalSemesterDate != null && recurrenceType == RecurrenceType.WEEKLY) {
        semesterStartDateText = globalSemesterDate.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.title.isBlank()) stringResource(R.string.title_new_course) else stringResource(R.string.title_edit_course)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorText = null
                    },
                    label = stringResource(R.string.label_course_name),
                    singleLine = true,
                )
                AppTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        errorText = null
                    },
                    label = stringResource(R.string.label_date),
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
                    label = stringResource(R.string.label_location),
                    singleLine = true,
                )
                AppTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        errorText = null
                    },
                    label = stringResource(R.string.label_note),
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
                        label = stringResource(R.string.label_semester_start_date),
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
                            label = stringResource(R.string.label_custom_weeks),
                            placeholder = "1,3,5 or 1-16",
                            singleLine = true,
                        )
                    }
                    AppTextField(
                        value = skipWeekListText,
                        onValueChange = {
                            skipWeekListText = it
                            errorText = null
                        },
                        label = stringResource(R.string.label_skip_weeks),
                        placeholder = stringResource(R.string.hint_skip_weeks_format),
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

                    val error = EntryValidator.validateDraft(
                        title = title,
                        parsedDate = parsedDate,
                        parsedStart = parsedStart,
                        parsedEnd = parsedEnd,
                        location = location,
                        note = note,
                        recurrenceType = recurrenceType,
                        parsedSemesterStart = parsedSemesterStart,
                        customWeekList = normalizedCustomWeekList,
                        skipWeekList = normalizedSkipWeekList,
                        weekRule = weekRule,
                    )
                    when {
                        error != null -> errorText = validationErrorMessages[error]
                        else -> {
                            // Persist the semester start date to global config
                            if (recurrenceType == RecurrenceType.WEEKLY && parsedSemesterStart != null) {
                                SemesterStore.setSemesterStartDate(context, parsedSemesterStart)
                            }
                            onSave(
                                TimetableEntry.create(
                                    id = initial.id,
                                    title = title.trim(),
                                    date = parsedDate!!.toString(),
                                    dayOfWeek = parsedDate!!.dayOfWeek.value,
                                    startMinutes = parsedStart!!,
                                    endMinutes = parsedEnd!!,
                                    location = location.trim(),
                                    note = note.trim(),
                                    recurrenceType = if (recurrenceType == RecurrenceType.WEEKLY) recurrenceType.name else RecurrenceType.NONE.name,
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
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 重复规则选择器。
 *
 * 用于选择课程的重复规则，如无重复或每周重复。
 *
 * @param selected 当前选择的重复规则
 * @param onSelected 选择重复规则的回调
 */
@Composable
private fun RecurrenceSelector(
    selected: RecurrenceType,
    onSelected: (RecurrenceType) -> Unit,
) {
    val labelNone = stringResource(R.string.recurrence_none)
    val labelWeekly = stringResource(R.string.recurrence_weekly)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.label_recurrence_rule), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurrenceType.entries.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(option) },
                ) {
                    Text(
                        when (option) {
                            RecurrenceType.NONE -> labelNone
                            RecurrenceType.WEEKLY -> labelWeekly
                        } + if (option == selected) " ✓" else "",
                    )
                }
            }
        }
    }
}

/**
 * 周规则选择器。
 *
 * 用于选择课程的周规则，如全部周、奇数周、偶数周或自定义周。
 *
 * @param selected 当前选择的周规则
 * @param onSelected 选择周规则的回调
 */
@Composable
private fun WeekRuleSelector(
    selected: WeekRule,
    onSelected: (WeekRule) -> Unit,
) {
    val labelAll = stringResource(R.string.week_rule_all)
    val labelOdd = stringResource(R.string.week_rule_odd)
    val labelEven = stringResource(R.string.week_rule_even)
    val labelCustom = stringResource(R.string.week_rule_custom)
    val weekRuleLabels = mapOf(
        WeekRule.ALL to labelAll,
        WeekRule.ODD to labelOdd,
        WeekRule.EVEN to labelEven,
        WeekRule.CUSTOM to labelCustom,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.label_week_rule), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val leftOptions = listOf(WeekRule.ALL, WeekRule.ODD)
            leftOptions.forEach { option ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(option) },
                ) {
                    Text(weekRuleLabels[option] + if (option == selected) " ✓" else "")
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
                    Text(weekRuleLabels[option] + if (option == selected) " ✓" else "")
                }
            }
        }
    }
}

/**
 * 时间范围输入字段。
 *
 * 用于输入开始时间和结束时间的字段。
 *
 * @param startTime 开始时间
 * @param endTime 结束时间
 * @param onStartChanged 开始时间变更的回调
 * @param onEndChanged 结束时间变更的回调
 */
@Composable
fun TimeRangeFields(
    startTime: String,
    endTime: String,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
) {
    val quickTimeOptions = remember {
        listOf("08:00", "08:30", "10:00", "10:30", "14:00", "14:30", "16:00", "16:30")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_quick_times),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickTimeOptions.forEach { time ->
                OutlinedButton(
                    onClick = {
                        if (startTime.isBlank() || startTime == time) {
                            onStartChanged(time)
                        } else {
                            onEndChanged(time)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = startTime,
                onValueChange = onStartChanged,
                label = stringResource(R.string.label_start_time),
                placeholder = "08:00",
                keyboardType = KeyboardType.Text,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = endTime,
                onValueChange = onEndChanged,
                label = stringResource(R.string.label_end_time),
                placeholder = "09:30",
                keyboardType = KeyboardType.Text,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 应用文本字段。
 *
 * 通用的文本输入字段，用于各种表单输入。
 *
 * @param value 输入值
 * @param onValueChange 值变更的回调
 * @param label 字段标签
 * @param modifier 修饰符
 * @param singleLine 是否单行
 * @param minLines 最小行数
 * @param placeholder 占位符
 * @param keyboardType 键盘类型
 */
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

/**
 * 周时段编辑器对话框。
 *
 * 用于编辑周时段的开始和结束时间。
 *
 * @param title 对话框标题
 * @param initial 初始周时段
 * @param onDismiss 关闭对话框的回调
 * @param onSave 保存周时段的回调
 * @param onDelete 删除周时段的回调
 */
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

    val strInvalidTime = stringResource(R.string.error_invalid_time)
    val strInvalidTimeRange = stringResource(R.string.error_invalid_time_range)

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
                        parsedStart == null || parsedEnd == null -> errorText = strInvalidTime
                        parsedStart >= parsedEnd -> errorText = strInvalidTimeRange
                        else -> onSave(WeekTimeSlot(parsedStart, parsedEnd))
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onDelete?.let {
                    OutlinedButton(onClick = it) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/**
 * 周时段数量对话框。
 *
 * 用于设置自定义周时段数量。
 *
 * @param initialCount 初始时段数量
 * @param onDismiss 关闭对话框的回调
 * @param onSave 保存时段数量的回调
 */
@Composable
fun WeekSlotCountDialog(
    initialCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var countText by rememberSaveable(initialCount) { mutableStateOf(initialCount.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val strInvalidNumber = stringResource(R.string.error_invalid_number)
    val strSlotRange = stringResource(R.string.error_slot_range)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_custom_slot_count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = countText,
                    onValueChange = {
                        countText = it
                        errorText = null
                    },
                    label = stringResource(R.string.label_total_slots),
                    placeholder = "20",
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.hint_slot_resize),
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
                        parsed == null -> errorText = strInvalidNumber
                        parsed !in EntryConstants.SLOT_COUNT_MIN..EntryConstants.SLOT_COUNT_MAX -> errorText = strSlotRange
                        else -> onSave(parsed)
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 固定周时间表对话框。
 *
 * 用于设置固定的周时间表，包括第一节课开始时间、课程时长、课间休息时长等。
 *
 * @param initialConfig 初始配置
 * @param initialSlots 初始时段列表
 * @param onDismiss 关闭对话框的回调
 * @param onSave 保存时段列表的回调
 */
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

    val strInvalidFixedParams = stringResource(R.string.error_invalid_fixed_params)
    val strIncompleteSlots = stringResource(R.string.error_incomplete_slots)
    val strSlotEndBeforeStart = stringResource(R.string.error_slot_end_before_start)
    val strSlotsCrossing = stringResource(R.string.error_slots_crossing)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_fixed_schedule)) },
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
                    label = stringResource(R.string.label_first_start),
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
                        label = stringResource(R.string.label_lesson_duration),
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
                        label = stringResource(R.string.label_break_duration),
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
                    label = stringResource(R.string.label_generate_slots),
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.hint_fixed_schedule),
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
                                errorText = strInvalidFixedParams
                            } else {
                                slotDrafts = generatedSlots.map { EditableWeekSlotDraft.fromSlot(it) }
                                errorText = null
                            }
                        },
                    ) {
                        Text(stringResource(R.string.action_generate_by_rule))
                    }
                    Text(
                        text = stringResource(R.string.label_current_slot_count, slotDrafts.size),
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
                        parsedSlots == null -> errorText = strIncompleteSlots
                        parsedSlots.any { it.startMinutes >= it.endMinutes } -> errorText = strSlotEndBeforeStart
                        !areWeekTimeSlotsNonOverlapping(parsedSlots) -> errorText = strSlotsCrossing
                        else -> onSave(parsedSlots)
                    }
                },
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 周时段草稿编辑器。
 *
 * 用于编辑周时段草稿的开始和结束时间。
 *
 * @param index 时段索引
 * @param draft 时段草稿
 * @param onStartChanged 开始时间变更的回调
 * @param onEndChanged 结束时间变更的回调
 */
@Composable
private fun WeekSlotDraftEditor(
    index: Int,
    draft: EditableWeekSlotDraft,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_slot_N, index + 1),
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

/**
 * 可编辑的周时段草稿。
 *
 * 用于在对话框中编辑周时段的开始和结束时间。
 *
 * @property startTime 开始时间
 * @property endTime 结束时间
 */
private data class EditableWeekSlotDraft(
    val startTime: String,
    val endTime: String,
) {
    companion object {
        /**
         * 从周时段创建草稿。
         *
         * @param slot 周时段
         * @return 可编辑的周时段草稿
         */
        fun fromSlot(slot: WeekTimeSlot): EditableWeekSlotDraft {
            return EditableWeekSlotDraft(
                startTime = formatMinutes(slot.startMinutes),
                endTime = formatMinutes(slot.endMinutes),
            )
        }
    }
}

/**
 * 将可编辑周时段草稿列表转换为周时段列表。
 *
 * @return 周时段列表，或 null 如果转换失败
 */
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

/**
 * 生成周时段列表。
 *
 * 根据给定的参数生成周时段列表。
 *
 * @param firstStartTime 第一节课开始时间
 * @param lessonDurationText 课程时长（分钟）
 * @param breakDurationText 课间休息时长（分钟）
 * @param slotCountText 时段数量
 * @return 周时段列表，或 null 如果参数无效
 */
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
        parsedStart >= EntryConstants.MINUTES_PER_DAY ||
        parsedLessonDuration == null ||
        parsedBreakDuration == null ||
        parsedSlotCount == null ||
        parsedLessonDuration !in 1..240 ||
        parsedBreakDuration !in 0..120 ||
        parsedSlotCount !in EntryConstants.SLOT_COUNT_MIN..EntryConstants.SLOT_COUNT_MAX
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
