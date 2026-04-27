package com.example.timetable.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
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

private const val SWIPE_THRESHOLD_DP = 48f
private val HORIZONTAL_PADDING = 12.dp
private val VERTICAL_PADDING = 8.dp
private val SPACING = 12.dp
private val BUTTON_HORIZONTAL_PADDING = 14.dp
private val BUTTON_VERTICAL_PADDING = 8.dp
private const val FIXED_TIME_BUTTON_ALPHA = 0.3f
private const val BACK_TO_TODAY_BUTTON_ALPHA = 0.15f

/**
 * 周视图配置数据类
 */
data class WeekViewConfig(
    val selectedDate: String,
    val selectedLocalDate: LocalDate,
    val selectedWeekStart: LocalDate,
    val selectedWeekEnd: LocalDate,
    val minDate: LocalDate,
    val maxDate: LocalDate
)

/**
 * 周视图数据类
 */
data class WeekViewData(
    val entries: List<TimetableEntry>,
    val dateRangeEntriesCache: DateRangeEntriesCache,
    val weekTimeSlots: List<WeekTimeSlot>,
    val weekCardAlpha: Float,
    val weekCardHue: Float
)

/**
 * 周视图回调函数类
 */
data class WeekViewCallbacks(
    val onDateChanged: (String) -> Unit,
    val onEditEntry: (TimetableEntry) -> Unit,
    val onEditWeekSlot: (Int) -> Unit,
    val onAddWeekSlot: (WeekTimeSlot) -> Unit,
    val onEditFixedWeekSchedule: () -> Unit
)

/**
 * 周视图内容。
 *
 * 显示一周的课程表，包含日历条、周课程表和操作按钮。
 *
 * @param config 周视图配置
 * @param data 周视图数据
 * @param snackbarHostState  Snackbar 主机状态
 * @param callbacks 周视图回调函数
 * @param contentPadding 内边距
 */
@Composable
internal fun WeekViewContent(
    config: WeekViewConfig,
    data: WeekViewData,
    snackbarHostState: SnackbarHostState,
    callbacks: WeekViewCallbacks,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 主布局：垂直排列的列
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        // 1. 周日历条：显示当前周的日期，支持日期选择
        WeekCalendarStrip(
            selectedDate = config.selectedLocalDate,
            onDateSelected = { date ->
                // 检查日期是否在有效范围内
                if (date in config.minDate..config.maxDate) {
                    callbacks.onDateChanged(date.toString())
                }
            },
        )
        // 2. 操作按钮行：包含固定时间和返回今天按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SPACING, Alignment.End),
        ) {
            Surface(
                onClick = callbacks.onEditFixedWeekSchedule,
                shape = AppShape.Chip,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = FIXED_TIME_BUTTON_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.action_fixed_time),
                    modifier = Modifier.padding(horizontal = BUTTON_HORIZONTAL_PADDING, vertical = BUTTON_VERTICAL_PADDING),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
            val today = LocalDate.now()
            if (config.selectedLocalDate != today) {
                Surface(
                    onClick = { callbacks.onDateChanged(today.toString()) },
                    shape = AppShape.Chip,
                color = MaterialTheme.colorScheme.primary.copy(alpha = BACK_TO_TODAY_BUTTON_ALPHA),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = stringResource(R.string.action_back_to_today),
                    modifier = Modifier.padding(horizontal = BUTTON_HORIZONTAL_PADDING, vertical = BUTTON_VERTICAL_PADDING),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
            }
        }
        // 3. 周课程表：显示一周的课程安排，支持水平拖动切换周
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // 水平拖动手势：左滑切换到下一周，右滑切换到上一周
                // 方向锁定：水平拖动开始后消费所有事件，避免与垂直滚动冲突
                .pointerInput(config.selectedDate) {
                    var totalHorizontalDrag = 0f
                    var horizontalDragLocked = false
                    val directionLockThreshold = viewConfiguration.touchSlop
                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDragLocked = false
                            totalHorizontalDrag = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalHorizontalDrag += dragAmount
                            // Once horizontal drag exceeds touch slop, lock direction
                            if (!horizontalDragLocked && kotlin.math.abs(totalHorizontalDrag) > directionLockThreshold) {
                                horizontalDragLocked = true
                            }
                        },
                        onDragEnd = {
                            val swipeThresholdPx = density * SWIPE_THRESHOLD_DP
                            when {
                                totalHorizontalDrag > swipeThresholdPx -> {
                                    // 右滑：切换到上一周
                                    val previousDate = config.selectedLocalDate.minusDays(7)
                                    if (previousDate >= config.minDate) callbacks.onDateChanged(previousDate.toString())
                                }
                                totalHorizontalDrag < -swipeThresholdPx -> {
                                    // 左滑：切换到下一周
                                    val nextDate = config.selectedLocalDate.plusDays(7)
                                    if (nextDate <= config.maxDate) callbacks.onDateChanged(nextDate.toString())
                                }
                            }
                            totalHorizontalDrag = 0f
                            horizontalDragLocked = false
                        },
                        onDragCancel = {
                            totalHorizontalDrag = 0f
                            horizontalDragLocked = false
                        },
                    )
                }
        ) {
            val visibleEntriesByDate = data.dateRangeEntriesCache.resolve(config.selectedWeekStart, config.selectedWeekEnd)
            WeekScheduleBoard(
                selectedDate = config.selectedLocalDate,
                weekStart = config.selectedWeekStart,
                weekEnd = config.selectedWeekEnd,
                entriesByDay = visibleEntriesByDate,
                slots = data.weekTimeSlots,
                cardAlpha = data.weekCardAlpha,
                cardHue = data.weekCardHue,
                onAddSlot = {
                    val nextSlot = defaultNewWeekSlot(data.weekTimeSlots)
                    if (nextSlot == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.msg_no_more_slots),
                                duration = SnackbarHostState.SnackbarDuration.Short
                            )
                        }
                    } else {
                        callbacks.onAddWeekSlot(nextSlot)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.msg_slot_added_success),
                                duration = SnackbarHostState.SnackbarDuration.Short
                            )
                        }
                    }
                },
                onCustomizeSlotCount = callbacks.onEditFixedWeekSchedule,
                onEntryClick = callbacks.onEditEntry,
                onSlotClick = callbacks.onEditWeekSlot,
            )
        }
    }
}
