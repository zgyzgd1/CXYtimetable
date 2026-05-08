package com.example.timetable.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot

/**
 * 课程块在周视图中的布局信息。
 *
 * @param entry 对应的课程条目
 * @param topPx 距离顶部的像素偏移
 * @param heightPx 块高度（像素）
 * @param leftPx 距离左侧的像素偏移
 * @param widthPx 块宽度（像素）
 */
internal data class WeekEntryLayout(
    val entry: TimetableEntry,
    val topPx: Float,
    val heightPx: Float,
    val leftPx: Float,
    val widthPx: Float,
)

private data class EntryColumnAssignment(
    val entry: TimetableEntry,
    val columnIndex: Int,
    val columnCount: Int,
)

private data class MutableEntryColumnAssignment(
    val entry: TimetableEntry,
    val columnIndex: Int,
)

private data class TimeAnchor(
    val minute: Int,
    val positionPx: Float,
)

/**
 * 计算一周内课程块的布局列表。
 *
 * 根据时段位置和重叠课程的列分配，计算每个课程块的位置和尺寸。
 *
 * @param entries 当天课程列表
 * @param slots 时间段列表
 * @param laneWidthPx 列宽（像素）
 * @param slotHeightPx 每个时段高度（像素）
 * @param slotGapPx 时段间距（像素）
 * @return 课程块布局列表
 */
internal fun buildWeekEntryLayouts(
    entries: List<TimetableEntry>,
    slots: List<WeekTimeSlot>,
    laneWidthPx: Float,
    slotHeightPx: Float,
    slotGapPx: Float,
): List<WeekEntryLayout> {
    if (entries.isEmpty() || slots.isEmpty() || laneWidthPx <= 0f) return emptyList()

    val sortedSlots = slots.sortedBy { it.startMinutes }
    val firstMinute = sortedSlots.first().startMinutes
    val lastMinute = sortedSlots.last().endMinutes
    val anchors = buildLaneTimeAnchors(sortedSlots, slotHeightPx, slotGapPx)
    val columnGapPx = 6f

    return assignEntryColumns(
        entries = entries.filter { it.endMinutes > firstMinute && it.startMinutes < lastMinute },
    ).map { assignment ->
        val top = mapMinuteToLanePosition(
            minute = assignment.entry.startMinutes.coerceAtLeast(firstMinute),
            anchors = anchors,
        )
        val bottom = mapMinuteToLanePosition(
            minute = assignment.entry.endMinutes.coerceAtMost(lastMinute),
            anchors = anchors,
        )
        val columnCount = assignment.columnCount.coerceAtLeast(1)
        val totalGapPx = columnGapPx * (columnCount - 1)
        val widthPx = ((laneWidthPx - totalGapPx) / columnCount).coerceAtLeast(1f)

        WeekEntryLayout(
            entry = assignment.entry,
            topPx = top,
            heightPx = (bottom - top).coerceAtLeast(1f),
            leftPx = assignment.columnIndex * (widthPx + columnGapPx),
            widthPx = widthPx,
        )
    }.sortedWith(compareBy<WeekEntryLayout> { it.topPx }.thenBy { it.leftPx })
}

internal fun colorWithHueShift(base: Color, hueShift: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[0] = ((hsv[0] + hueShift) % 360f + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun buildLaneTimeAnchors(
    slots: List<WeekTimeSlot>,
    slotHeightPx: Float,
    slotGapPx: Float,
): List<TimeAnchor> {
    if (slots.isEmpty()) return emptyList()

    val anchors = mutableListOf<TimeAnchor>()
    slots.forEachIndexed { index, slot ->
        anchors += TimeAnchor(
            minute = slot.startMinutes,
            positionPx = index * (slotHeightPx + slotGapPx),
        )
    }

    val lastTopPx = (slots.lastIndex) * (slotHeightPx + slotGapPx)
    anchors += TimeAnchor(
        minute = slots.last().endMinutes,
        positionPx = lastTopPx + slotHeightPx,
    )
    return anchors
}

private fun mapMinuteToLanePosition(
    minute: Int,
    anchors: List<TimeAnchor>,
): Float {
    if (anchors.isEmpty()) return 0f

    val clampedMinute = minute.coerceIn(anchors.first().minute, anchors.last().minute)
    for (index in 0 until anchors.lastIndex) {
        val start = anchors[index]
        val end = anchors[index + 1]
        if (clampedMinute <= end.minute) {
            if (end.minute <= start.minute) return end.positionPx
            val fraction = (clampedMinute - start.minute).toFloat() / (end.minute - start.minute).toFloat()
            return start.positionPx + (end.positionPx - start.positionPx) * fraction
        }
    }
    return anchors.last().positionPx
}

private fun assignEntryColumns(entries: List<TimetableEntry>): List<EntryColumnAssignment> {
    if (entries.isEmpty()) return emptyList()

    val sortedEntries = entries.sortedWith(
        compareBy<TimetableEntry> { it.startMinutes }
            .thenBy { it.endMinutes }
            .thenBy { it.id },
    )
    val assignments = mutableListOf<EntryColumnAssignment>()
    val currentGroup = mutableListOf<MutableEntryColumnAssignment>()
    val columnEnds = mutableListOf<Int>()
    var groupMaxEnd = Int.MIN_VALUE

    fun flushGroup() {
        if (currentGroup.isEmpty()) return
        val columnCount = columnEnds.size.coerceAtLeast(1)
        currentGroup.forEach { assignment ->
            assignments += EntryColumnAssignment(
                entry = assignment.entry,
                columnIndex = assignment.columnIndex,
                columnCount = columnCount,
            )
        }
        currentGroup.clear()
        columnEnds.clear()
        groupMaxEnd = Int.MIN_VALUE
    }

    sortedEntries.forEach { entry ->
        if (currentGroup.isNotEmpty() && entry.startMinutes >= groupMaxEnd) {
            flushGroup()
        }

        val reusableColumn = columnEnds.indexOfFirst { columnEnd -> columnEnd <= entry.startMinutes }
        val columnIndex = if (reusableColumn >= 0) {
            columnEnds[reusableColumn] = entry.endMinutes
            reusableColumn
        } else {
            columnEnds += entry.endMinutes
            columnEnds.lastIndex
        }

        currentGroup += MutableEntryColumnAssignment(entry = entry, columnIndex = columnIndex)
        groupMaxEnd = maxOf(groupMaxEnd, entry.endMinutes)
    }

    flushGroup()
    return assignments
}
