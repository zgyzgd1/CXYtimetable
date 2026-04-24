package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SemesterStoreTest {

    @Test
    fun currentWeekNumberReturnsOneOnSemesterStartDay() {
        val semesterStart = LocalDate.of(2026, 3, 2)
        val weekIndex = weekIndexFromSemesterStart(semesterStart, semesterStart)

        assertNotNull(weekIndex)
        assertEquals(1, weekIndex)
    }

    @Test
    fun currentWeekNumberReturnsCorrectWeekInMiddleOfSemester() {
        val semesterStart = LocalDate.of(2026, 3, 2)
        val today = LocalDate.of(2026, 4, 6) // 5 weeks later (same weekday)
        val weekIndex = weekIndexFromSemesterStart(semesterStart, today)

        assertNotNull(weekIndex)
        assertEquals(6, weekIndex)
    }

    @Test
    fun currentWeekNumberReturnsNullBeforeSemesterStarts() {
        val semesterStart = LocalDate.of(2026, 9, 1)
        val today = LocalDate.of(2026, 8, 31) // day before
        val weekIndex = weekIndexFromSemesterStart(semesterStart, today)

        // Before semester should return null or non-positive
        assertNull(weekIndex)
    }

    @Test
    fun weekIndexFromSemesterStartAccountsForDifferentWeekday() {
        // Semester starts on Monday 2026-03-02
        val semesterStart = LocalDate.of(2026, 3, 2)
        // Wednesday in same week = still week 1
        val wednesday = LocalDate.of(2026, 3, 4)
        val weekIndex = weekIndexFromSemesterStart(semesterStart, wednesday)

        assertEquals(1, weekIndex)
    }

    @Test
    fun weekIndexFromSemesterStartAdvancesAfterSunday() {
        val semesterStart = LocalDate.of(2026, 3, 2) // Monday
        val nextMonday = LocalDate.of(2026, 3, 9)
        val weekIndex = weekIndexFromSemesterStart(semesterStart, nextMonday)

        assertEquals(2, weekIndex)
    }
}
