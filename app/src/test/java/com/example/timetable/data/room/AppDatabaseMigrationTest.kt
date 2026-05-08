package com.example.timetable.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AppDatabaseMigrationTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.deleteDatabase(V1_DB_NAME)
        context.deleteDatabase(V2_DB_NAME)
    }

    @Test
    fun migratesVersion1ToCurrentAndPreservesRows() = runBlocking {
        createVersion1Database(V1_DB_NAME)

        val database = openMigratedDatabase(V1_DB_NAME)
        try {
            val entries = database.timetableDao().getAllEntries()

            assertEquals(1, entries.size)
            val migrated = entries.single()
            assertEquals("legacy-v1", migrated.id)
            assertEquals("Math", migrated.title)
            assertEquals("NONE", migrated.recurrenceType)
            assertEquals("", migrated.semesterStartDate)
            assertEquals("ALL", migrated.weekRule)
            assertEquals("", migrated.customWeekList)
            assertEquals("", migrated.skipWeekList)
            assertCurrentIndexesExist(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun migratesVersion2ToCurrentAndCreatesIndexes() = runBlocking {
        createVersion2Database(V2_DB_NAME)

        val database = openMigratedDatabase(V2_DB_NAME)
        try {
            val entries = database.timetableDao().getAllEntries()

            assertEquals(1, entries.size)
            val migrated = entries.single()
            assertEquals("legacy-v2", migrated.id)
            assertEquals("WEEKLY", migrated.recurrenceType)
            assertEquals("ODD", migrated.weekRule)
            assertCurrentIndexesExist(database)
        } finally {
            database.close()
        }
    }

    private fun openMigratedDatabase(dbName: String): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private fun createVersion1Database(dbName: String) {
        context.deleteDatabase(dbName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS timetable_entries (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    date TEXT NOT NULL,
                    dayOfWeek INTEGER NOT NULL,
                    startMinutes INTEGER NOT NULL,
                    endMinutes INTEGER NOT NULL,
                    location TEXT NOT NULL,
                    note TEXT NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO timetable_entries (
                    id, title, date, dayOfWeek, startMinutes, endMinutes, location, note
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("legacy-v1", "Math", "2026-09-01", 2, 8 * 60, 9 * 60, "A-101", "Bring book"),
            )
            db.version = 1
        }
    }

    private fun createVersion2Database(dbName: String) {
        context.deleteDatabase(dbName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS timetable_entries (
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    date TEXT NOT NULL,
                    dayOfWeek INTEGER NOT NULL,
                    startMinutes INTEGER NOT NULL,
                    endMinutes INTEGER NOT NULL,
                    location TEXT NOT NULL,
                    note TEXT NOT NULL,
                    recurrenceType TEXT NOT NULL,
                    semesterStartDate TEXT NOT NULL,
                    weekRule TEXT NOT NULL,
                    customWeekList TEXT NOT NULL,
                    skipWeekList TEXT NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO timetable_entries (
                    id, title, date, dayOfWeek, startMinutes, endMinutes, location, note,
                    recurrenceType, semesterStartDate, weekRule, customWeekList, skipWeekList
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    "legacy-v2",
                    "Physics",
                    "2026-09-02",
                    3,
                    10 * 60,
                    11 * 60,
                    "B-202",
                    "",
                    "WEEKLY",
                    "2026-09-01",
                    "ODD",
                    "",
                    "5",
                ),
            )
            db.version = 2
        }
    }

    private fun assertCurrentIndexesExist(database: AppDatabase) {
        val indexes = mutableSetOf<String>()
        database.openHelper.readableDatabase.query("PRAGMA index_list(`timetable_entries`)").use { cursor ->
            while (cursor.moveToNext()) {
                indexes += cursor.getString(1)
            }
        }

        assertTrue(indexes.contains("index_timetable_entries_date_startMinutes"))
        assertTrue(indexes.contains("index_timetable_entries_dayOfWeek"))
    }

    private companion object {
        const val V1_DB_NAME = "migration-v1.db"
        const val V2_DB_NAME = "migration-v2.db"
    }
}
