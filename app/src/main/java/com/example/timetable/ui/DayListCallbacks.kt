package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import java.time.LocalDate

/**
 * 日视图列表的回调集合。
 *
 * 将分散的回调参数合并为一个数据类，减少 `DayScheduleList` 的参数数量。
 *
 * @param onDateChanged 日期变更回调
 * @param onEditEntry 编辑课程回调
 * @param onDuplicateEntry 复制课程回调
 * @param onDeleteEntry 删除课程回调
 * @param onCreateEntry 创建课程回调
 */
internal data class DayListCallbacks(
    val onDateChanged: (String) -> Unit,
    val onEditEntry: (TimetableEntry) -> Unit,
    val onDuplicateEntry: (TimetableEntry) -> Unit,
    val onDeleteEntry: (TimetableEntry) -> Unit,
    val onCreateEntry: (LocalDate, List<TimetableEntry>) -> Unit,
)
