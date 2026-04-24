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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import com.example.timetable.data.DateRangeEntriesCache
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.areWeekTimeSlotsNonOverlapping
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 周视图内容。
 *
 * 显示一周的课程表，包含日历条、周课程表和操作按钮。
 *
 * @param selectedDate 选中的日期
 * @param selectedLocalDate 选中的本地日期
 * @param selectedWeekStart 选中周的开始日期
 * @param selectedWeekEnd 选中周的结束日期
 * @param minDate 最小日期
 * @param maxDate 最大日期
 * @param entries 所有课程条目
 * @param dateRangeEntriesCache 日期范围课程缓存
 * @param weekTimeSlots 周时段列表
 * @param weekCardAlpha 周卡片透明度
 * @param weekCardHue 周卡片色调
 * @param snackbarHostState  Snackbar 主机状态
 * @param onDateChanged 日期变更回调
 * @param onEditEntry 编辑课程条目回调
 * @param onEditWeekSlot 编辑周时段回调
 * @param onAddWeekSlot 添加周时段回调
 * @param onEditFixedWeekSchedule 编辑固定周时间表回调
 * @param contentPadding 内边距
 */
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
    val context = LocalContext.current
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
            OutlinedButton(onClick = onEditFixedWeekSchedule) {
                Text(stringResource(R.string.action_fixed_time))
            }
            val today = LocalDate.now()
            if (selectedLocalDate != today) {
                OutlinedButton(onClick = { onDateChanged(today.toString()) }) {
                    Text(stringResource(R.string.action_back_to_today))
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
                .verticalScroll(rememberScrollState()),
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
                            snackbarHostState.showSnackbar(context.getString(R.string.msg_no_more_slots))
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
