package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleViewModelSyncTokenTest {
    @Test
    fun runConflictCalculationUsesProvidedDispatcher() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "conflict-calculation-worker")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val threadName = runConflictCalculation(dispatcher) {
                Thread.currentThread().name
            }

            assertTrue(threadName.contains("conflict-calculation-worker"))
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun debouncedEntrySideEffectsSuppressesTransientInitialEmptyList() = runBlocking {
        val upstream = MutableStateFlow(emptyList<TimetableEntry>())
        val received = mutableListOf<List<TimetableEntry>>()
        val loadedEntries = listOf(sampleEntry())
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            upstream.debouncedEntrySideEffects(debounceMillis = 40).collect(received::add)
        }

        delay(10)
        upstream.value = loadedEntries
        delay(80)
        job.cancel()

        assertEquals(listOf(loadedEntries), received)
    }

    @Test
    fun debouncedEntrySideEffectsStillEmitsSettledEmptyList() = runBlocking {
        val upstream = MutableStateFlow(emptyList<TimetableEntry>())
        val received = mutableListOf<List<TimetableEntry>>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            upstream.debouncedEntrySideEffects(debounceMillis = 40).collect(received::add)
        }

        delay(80)
        job.cancel()

        assertEquals(listOf(emptyList<TimetableEntry>()), received)
    }

    @Test
    fun reminderSyncTokenIsStableForSameEntriesAndReminderMinutes() {
        val entries = listOf(sampleEntry())

        val first = reminderSyncToken(entries, listOf(20, 5))
        val second = reminderSyncToken(entries, listOf(5, 20, 20))

        assertEquals(first, second)
    }

    @Test
    fun reminderSyncTokenChangesWhenReminderMinutesChange() {
        val entries = listOf(sampleEntry())

        val first = reminderSyncToken(entries, listOf(20))
        val second = reminderSyncToken(entries, listOf(10))

        assertNotEquals(first, second)
    }

    @Test
    fun reminderSyncTokenChangesWhenReminderContentChanges() {
        val original = listOf(sampleEntry(title = "高数", location = "A-101"))
        val updated = listOf(sampleEntry(title = "线代", location = "B-202"))

        val first = reminderSyncToken(original, listOf(20))
        val second = reminderSyncToken(updated, listOf(20))

        assertNotEquals(first, second)
    }

    @Test
    fun widgetRefreshTokenChangesWhenWidgetVisibleFieldsChange() {
        val original = listOf(sampleEntry(title = "高数", location = "A-101"))
        val updated = listOf(sampleEntry(title = "线代", location = "B-202"))

        val first = widgetRefreshToken(original)
        val second = widgetRefreshToken(updated)

        assertNotEquals(first, second)
    }

    @Test
    fun reminderSyncTokenDoesNotCollideWhenFieldContainsDelimiters() {
        val first = listOf(sampleEntry(title = "A|B", location = "C\nD"))
        val second = listOf(sampleEntry(title = "A", location = "B|C\nD"))

        val firstToken = reminderSyncToken(first, listOf(20))
        val secondToken = reminderSyncToken(second, listOf(20))

        assertNotEquals(firstToken, secondToken)
    }

    @Test
    fun reminderSyncTokenDoesNotCollideForKnownStringHashCollision() {
        val first = listOf(sampleEntry(title = "Aa"))
        val second = listOf(sampleEntry(title = "BB"))

        val firstToken = reminderSyncToken(first, listOf(20))
        val secondToken = reminderSyncToken(second, listOf(20))

        assertNotEquals(firstToken, secondToken)
    }

    @Test
    fun runReminderSyncIfNeededSkipsMatchingTokenWithoutForce() = runBlocking {
        val entries = listOf(sampleEntry())
        val reminderMinutes = listOf(20)
        val existingToken = reminderSyncToken(entries, reminderMinutes)
        var syncCalled = false

        val updatedToken = runReminderSyncIfNeeded(
            entries = entries,
            reminderMinutes = reminderMinutes,
            lastSyncToken = existingToken,
            force = false,
        ) { _, _ ->
            syncCalled = true
        }

        assertFalse(syncCalled)
        assertEquals(existingToken, updatedToken)
    }

    @Test
    fun runReminderSyncIfNeededPassesForceToSchedulerWhenRequested() = runBlocking {
        val entries = listOf(sampleEntry())
        val reminderMinutes = listOf(20)
        val existingToken = reminderSyncToken(entries, reminderMinutes)
        var capturedForce: Boolean? = null

        val updatedToken = runReminderSyncIfNeeded(
            entries = entries,
            reminderMinutes = reminderMinutes,
            lastSyncToken = existingToken,
            force = true,
        ) { syncedEntries, forceReschedule ->
            assertEquals(entries, syncedEntries)
            capturedForce = forceReschedule
        }

        assertTrue(capturedForce == true)
        assertEquals(existingToken, updatedToken)
    }

    @Test
    fun widgetRefreshTokenDoesNotCollideWhenFieldContainsDelimiters() {
        val first = listOf(sampleEntry(title = "A|B", location = "C\nD"))
        val second = listOf(sampleEntry(title = "A", location = "B|C\nD"))

        val firstToken = widgetRefreshToken(first)
        val secondToken = widgetRefreshToken(second)

        assertNotEquals(firstToken, secondToken)
    }

    @Test
    fun widgetRefreshTokenDoesNotCollideForKnownStringHashCollision() {
        val first = listOf(sampleEntry(title = "Aa"))
        val second = listOf(sampleEntry(title = "BB"))

        val firstToken = widgetRefreshToken(first)
        val secondToken = widgetRefreshToken(second)

        assertNotEquals(firstToken, secondToken)
    }

    private fun sampleEntry(
        title: String = "高等数学",
        location: String = "A-101",
    ): TimetableEntry {
        return TimetableEntry(
            id = "entry-1",
            title = title,
            date = "2026-04-27",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            location = location,
            recurrenceType = "WEEKLY",
            semesterStartDate = "2026-02-23",
            weekRule = "ALL",
        )
    }
}
