package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.formatMinutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

private val boardAccentColors = listOf(
    Color(0xFFF2B6CF),
    Color(0xFFE98AA9),
    Color(0xFF82A7F4),
    Color(0xFFF3BE6E),
    Color(0xFFE88974),
    Color(0xFF78D8CB),
    Color(0xFFB69AF8),
    Color(0xFF6F9ED6),
)

private data class WeekSlot(
    val startMinutes: Int,
    val endMinutes: Int,
)

@Composable
fun WeekScheduleBoard(
    selectedDate: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    entries: List<TimetableEntry>,
    cardAlpha: Float,
) {
    val days = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }
    val slots = remember(entries) { buildWeekSlots(entries) }
    val weekNumber = remember(selectedDate) {
        selectedDate.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
    }
    val horizontalScrollState = rememberScrollState()
    val timeColumnWidth = 64.dp
    val dayColumnWidth = 96.dp
    val headerHeight = 84.dp
    val slotHeight = 118.dp
    val boardHeight = headerHeight + slotHeight * slots.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFF3F4FB))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "%d/%d/%d".format(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                color = Color(0xFF17181D),
            )
            Text(
                text = "第 $weekNumber 周  ${formatWeekRange(weekStart, weekEnd)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4F5461),
            )
            if (entries.isEmpty()) {
                Text(
                    text = "本周暂无课程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8A90A0),
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(horizontalScrollState),
        ) {
            Box(
                modifier = Modifier
                    .width(timeColumnWidth)
                    .height(boardHeight),
            ) {
                slots.forEachIndexed { index, slot ->
                    val topPadding = headerHeight + slotHeight * index
                    Column(
                        modifier = Modifier
                            .padding(top = topPadding + 8.dp)
                            .width(timeColumnWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF111319),
                        )
                        Text(
                            text = formatMinutes(slot.startMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF313542),
                        )
                        Text(
                            text = formatMinutes(slot.endMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF313542),
                        )
                    }
                }
            }

            days.forEachIndexed { dayIndex, day ->
                Column(
                    modifier = Modifier.width(dayColumnWidth),
                ) {
                    Column(
                        modifier = Modifier
                            .height(headerHeight)
                            .padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = chineseWeekday(day.dayOfWeek),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF707687),
                        )
                        Text(
                            text = "%d/%d".format(day.monthValue, day.dayOfMonth),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                            color = Color(0xFF9BA1B1),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(slotHeight * slots.size)
                            .padding(horizontal = 6.dp),
                    ) {
                        repeat(slots.size) { slotIndex ->
                            Box(
                                modifier = Modifier
                                    .padding(top = slotHeight * slotIndex + 4.dp)
                                    .height(slotHeight - 8.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0x0FFFFFFF)),
                            )
                        }

                        entries
                            .filter { entryDate(it) == day }
                            .sortedBy { it.startMinutes }
                            .forEach { entry ->
                                val placement = rememberWeekPlacement(entry, slots)
                                if (placement != null) {
                                    val color = boardAccentColors[
                                        (entry.title.hashCode() and Int.MAX_VALUE) % boardAccentColors.size
                                    ]
                                    WeekEntryBlock(
                                        modifier = Modifier
                                            .padding(
                                                top = slotHeight * placement.firstIndex + 6.dp,
                                                start = 2.dp,
                                                end = 2.dp,
                                            )
                                            .height(slotHeight * placement.slotSpan - 12.dp)
                                            .fillMaxWidth(),
                                        entry = entry,
                                        color = color.copy(alpha = cardAlpha),
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekEntryBlock(
    modifier: Modifier,
    entry: TimetableEntry,
    color: Color,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.location.isNotBlank()) {
                Text(
                    text = entry.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.95f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatMinutes(entry.startMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Start,
            )
        }
    }
}

private data class WeekPlacement(
    val firstIndex: Int,
    val slotSpan: Int,
)

private fun rememberWeekPlacement(
    entry: TimetableEntry,
    slots: List<WeekSlot>,
): WeekPlacement? {
    val overlapping = slots.mapIndexedNotNull { index, slot ->
        if (entry.startMinutes < slot.endMinutes && slot.startMinutes < entry.endMinutes) index else null
    }
    if (overlapping.isEmpty()) return null
    return WeekPlacement(
        firstIndex = overlapping.first(),
        slotSpan = overlapping.size,
    )
}

private fun entryDate(entry: TimetableEntry): LocalDate? = runCatching {
    LocalDate.parse(entry.date)
}.getOrNull()

private fun buildWeekSlots(entries: List<TimetableEntry>): List<WeekSlot> {
    val rawSlots = entries
        .map { WeekSlot(it.startMinutes, it.endMinutes) }
        .distinct()
        .sortedBy { it.startMinutes }

    if (rawSlots.isNotEmpty()) return rawSlots

    return listOf(
        WeekSlot(8 * 60, 8 * 60 + 45),
        WeekSlot(8 * 60 + 55, 9 * 60 + 40),
        WeekSlot(10 * 60 + 10, 10 * 60 + 55),
        WeekSlot(11 * 60 + 5, 11 * 60 + 50),
        WeekSlot(14 * 60 + 30, 15 * 60 + 15),
        WeekSlot(15 * 60 + 25, 16 * 60 + 10),
        WeekSlot(16 * 60 + 20, 17 * 60 + 5),
        WeekSlot(17 * 60 + 15, 18 * 60),
    )
}

private fun chineseWeekday(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    return if (start.month == end.month) {
        "%d/%d - %d".format(start.monthValue, start.dayOfMonth, end.dayOfMonth)
    } else {
        "%d/%d - %d/%d".format(start.monthValue, start.dayOfMonth, end.monthValue, end.dayOfMonth)
    }
}
