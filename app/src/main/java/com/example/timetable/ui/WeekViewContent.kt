package com.example.timetable.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 周视图内容�? *
 * 显示一周的课程表，包含日历条、周课程表和操作按钮�? *
 * @param selectedDate 选中的日�? * @param selectedLocalDate 选中的本地日�? * @param selectedWeekStart 选中周的开始日�? * @param selectedWeekEnd 选中周的结束日期
 * @param minDate 最小日�? * @param maxDate 最大日�? * @param entries 所有课程条�? * @param dateRangeEntriesCache 日期范围课程缓存
 * @param weekTimeSlots 周时段列�? * @param weekCardAlpha 周卡片透明�? * @param weekCardHue 周卡片色�? * @param snackbarHostState  Snackbar 主机状�? * @param onDateChanged 日期变更回调
 * @param onEditEntry 编辑课程条目回调
 * @param onEditWeekSlot 编辑周时段回�? * @param onAddWeekSlot 添加周时段回�? * @param onEditFixedWeekSchedule 编辑固定周时间表回调
 * @param contentPadding 内边�? */
@Composable
internal fun WeekViewContent(
    selectedDate: String,
    selectedLocalDate: LocalDate,
    selectedWeekStart: LocalDate,
    selectedWeekEnd: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    entries: List<TimetableEntry>,
    dateRangeEntriesCache: DateRangeEntriesCache,
    weekTimeSlots: List<WeekTimeSlot>,
    weekCardAlpha: Float,
    weekCardHue: Float,
    snackbarHostState: SnackbarHostState,
    onDateChanged: (String) -> Unit,
    onEditEntry: (TimetableEntry) -> Unit,
    onEditWeekSlot: (Int) -> Unit,
    onAddWeekSlot: (WeekTimeSlot) -> Unit,
    onEditFixedWeekSchedule: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val isWeekMode = true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WeekCalendarStrip(
            selectedDate = selectedLocalDate,
            onDateSelected = { date ->
                if (date in minDate..maxDate) {
                    onDateChanged(date.toString())
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            Surface(
                onClick = onEditFixedWeekSchedule,
                shape = AppShape.Chip,
                color = MaterialTheme.colorScheme.surfaceVariant.overlayHeavy(),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.action_fixed_time),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
            val today = LocalDate.now()
            if (selectedLocalDate != today) {
                Surface(
                    onClick = { onDateChanged(today.toString()) },
                    shape = AppShape.Chip,
                    color = MaterialTheme.colorScheme.primary.accentMedium(),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(R.string.action_back_to_today),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .pointerInput(selectedDate, isWeekMode) {
                    var totalHorizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            // 只处理水平方向的拖动，忽略垂直分�?                            change.consume()
                            totalHorizontalDrag += dragAmount
                        },
                        onDragEnd = {
                            val swipeThresholdPx = density * 48f
                            when {
                                totalHorizontalDrag > swipeThresholdPx -> {
                                    val previousDate = selectedLocalDate.minusDays(7)
                                    if (previousDate >= minDate) onDateChanged(previousDate.toString())
                                }
                                totalHorizontalDrag < -swipeThresholdPx -> {
                                    val nextDate = selectedLocalDate.plusDays(7)
                                    if (nextDate <= maxDate) onDateChanged(nextDate.toString())
                                }
                            }
                            totalHorizontalDrag = 0f
                        },
                    )
                }
        ) {
            val visibleEntriesByDate = dateRangeEntriesCache.resolve(selectedWeekStart, selectedWeekEnd)
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
                            snackbarHostState.showSnackbar(resources.getString(R.string.msg_no_more_slots))
                        }
                    } else {
                        onAddWeekSlot(nextSlot)
                    }
                },
                onCustomizeSlotCount = onEditFixedWeekSchedule,
                onEntryClick = onEditEntry,
                onSlotClick = onEditWeekSlot,
            )
        }
    }
}
