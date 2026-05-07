package com.example.timetable.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableConflictsTest {

    @Test
    fun findConflictForEntryReturnsOverlappedEntryOnSameDate() {
        val date = LocalDate.of(2026, 4, 22)
        val existing = entry(
            title = "高等数学",
            date = date,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )
        val target = entry(
            title = "线性代数",
            date = date,
            startMinutes = 8 * 60 + 30,
            endMinutes = 9 * 60 + 20,
        )

        val conflict = findConflictForEntry(target, listOf(existing))

        assertEquals(existing.id, conflict?.id)
    }

    @Test
    fun findConflictForEntryIgnoresDifferentDatesAndSameId() {
        val date = LocalDate.of(2026, 4, 22)
        val existing = entry(
            title = "数据库",
            date = date,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
        )
        val sameIdTarget = existing.copy(
            startMinutes = 10 * 60 + 10,
            endMinutes = 10 * 60 + 50,
        )
        val differentDateTarget = entry(
            title = "大学英语",
            date = date.plusDays(1),
            startMinutes = 10 * 60 + 10,
            endMinutes = 10 * 60 + 50,
        )

        assertNull(findConflictForEntry(sameIdTarget, listOf(existing)))
        assertNull(findConflictForEntry(differentDateTarget, listOf(existing)))
    }

    @Test
    fun suggestAdjustedEntryAfterConflictsMovesToNearestAvailableTime() {
        val date = LocalDate.of(2026, 4, 22)
        val target = entry(
            title = "编译原理",
            date = date,
            startMinutes = 8 * 60 + 30,
            endMinutes = 9 * 60 + 30,
        )
        val existing = listOf(
            entry(
                title = "高数",
                date = date,
                startMinutes = 8 * 60,
                endMinutes = 9 * 60,
            ),
            entry(
                title = "英语",
                date = date,
                startMinutes = 9 * 60,
                endMinutes = 10 * 60,
            ),
        )

        val adjusted = suggestAdjustedEntryAfterConflicts(target, existing)

        assertNotNull(adjusted)
        assertEquals(10 * 60, adjusted?.startMinutes)
        assertEquals(11 * 60, adjusted?.endMinutes)
    }

    @Test
    fun findConflictForEntryMatchesWeeklyEntryThatOccursOnTargetDate() {
        val targetDate = LocalDate.of(2026, 3, 16)
        val existingWeekly = entry(
            title = "高等数学",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )
        val target = entry(
            title = "线性代数",
            date = targetDate,
            startMinutes = 8 * 60 + 20,
            endMinutes = 9 * 60 + 10,
        )

        val conflict = findConflictForEntry(target, listOf(existingWeekly))

        assertEquals(existingWeekly.id, conflict?.id)
    }

    @Test
    fun findConflictForEntryDetectsFutureOverlapForLaterStartingWeeklyEntry() {
        val existingWeekly = entry(
            title = "高等数学",
            date = LocalDate.of(2026, 3, 16),
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )
        val targetWeekly = entry(
            title = "线性代数",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60 + 20,
            endMinutes = 9 * 60 + 10,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )

        val conflict = findConflictForEntry(targetWeekly, listOf(existingWeekly))

        assertEquals(existingWeekly.id, conflict?.id)
    }

    @Test
    fun findConflictForEntryDetectsWeeklyOverlapAfterManySkippedWeeks() {
        val skippedWeeks = (1..512).joinToString(",")
        val existingWeekly = entry(
            title = "Math",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
            skipWeekList = skippedWeeks,
        )
        val targetWeekly = entry(
            title = "Physics",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60 + 20,
            endMinutes = 9 * 60 + 10,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
            skipWeekList = skippedWeeks,
        )

        val conflict = findConflictForEntry(targetWeekly, listOf(existingWeekly))

        assertEquals(existingWeekly.id, conflict?.id)
    }

    @Test
    fun findConflictForEntryDetectsOddEvenOverlapWithDifferentSemesterStarts() {
        val existingOdd = entry(
            title = "Math",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ODD.name,
        )
        val targetEven = entry(
            title = "Physics",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 8 * 60 + 20,
            endMinutes = 9 * 60 + 10,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-02-23",
            weekRule = WeekRule.EVEN.name,
        )

        val conflict = findConflictForEntry(targetEven, listOf(existingOdd))

        assertEquals(existingOdd.id, conflict?.id)
    }

    @Test
    fun suggestAdjustedEntryAfterConflictsAccountsForWeeklyEntriesOnTargetDate() {
        val targetDate = LocalDate.of(2026, 3, 16)
        val weeklyBlocking = entry(
            title = "英语",
            date = LocalDate.of(2026, 3, 2),
            startMinutes = 9 * 60,
            endMinutes = 10 * 60,
        ).copy(
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )
        val target = entry(
            title = "编译原理",
            date = targetDate,
            startMinutes = 8 * 60 + 30,
            endMinutes = 9 * 60 + 30,
        )

        val adjusted = suggestAdjustedEntryAfterConflicts(target, listOf(weeklyBlocking))

        assertNotNull(adjusted)
        assertNotEquals(9 * 60, adjusted?.startMinutes)
        assertEquals(10 * 60, adjusted?.startMinutes)
        assertEquals(11 * 60, adjusted?.endMinutes)
    }

    @Test
    fun countConflictPairsCountsOnlyEntriesThatOverlapInTimeAndOccurrenceDate() {
        val date = LocalDate.of(2026, 4, 22)
        val first = entry(
            title = "Database",
            date = date,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )
        val overlappingSameDate = entry(
            title = "Operating System",
            date = date,
            startMinutes = 8 * 60 + 30,
            endMinutes = 9 * 60 + 30,
        )
        val overlappingDifferentDate = entry(
            title = "Computer Network",
            date = date.plusDays(1),
            startMinutes = 8 * 60 + 30,
            endMinutes = 9 * 60 + 30,
        )
        val sameDateDifferentTime = entry(
            title = "English",
            date = date,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
        )

        assertEquals(
            1,
            countConflictPairs(
                listOf(
                    first,
                    overlappingSameDate,
                    overlappingDifferentDate,
                    sameDateDifferentTime,
                ),
            ),
        )
    }

    @Test
    fun countConflictPairsBetweenCountsEveryImportedExistingPair() {
        val date = LocalDate.of(2026, 4, 22)
        val imported = entry(
            title = "Imported",
            date = date,
            startMinutes = 8 * 60 + 20,
            endMinutes = 9 * 60 + 10,
        )
        val firstExisting = entry(
            title = "Existing 1",
            date = date,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )
        val secondExisting = entry(
            title = "Existing 2",
            date = date,
            startMinutes = 8 * 60 + 30,
            endMinutes = 9 * 60 + 30,
        )

        assertEquals(2, countConflictPairsBetween(listOf(imported), listOf(firstExisting, secondExisting)))
    }

    private fun entry(
        title: String,
        date: LocalDate,
        startMinutes: Int,
        endMinutes: Int,
    ): TimetableEntry {
        return TimetableEntry(
            title = title,
            date = date.toString(),
            dayOfWeek = date.dayOfWeek.value,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
        )
    }
}
