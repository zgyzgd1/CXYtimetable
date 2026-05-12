package com.example.timetable.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableGroup

/**
 * Room database definition.
 *
 * ## Version Evolution Strategy
 *
 * 1. **When adding or modifying columns**, always increment [version] and add a
 *    corresponding `MIGRATION_N_M` object in [Companion].
 *
 * 2. **Never use `fallbackToDestructiveMigration()`**,
 *    otherwise version upgrades will silently wipe user data.
 *
 * 3. **Schema export is enabled** (`exportSchema = true`); Room exports the current
 *    schema to `app/schemas/` on each compile, enabling:
 *    - Version comparison and code review
 *    - Future integration with `MigrationTestHelper` for automated migration testing
 *
 * 4. **New Migration template**:
 *    ```kotlin
 *    private val MIGRATION_2_3 = object : Migration(2, 3) {
 *        override fun migrate(db: SupportSQLiteDatabase) {
 *            db.execSQL("ALTER TABLE timetable_entries ADD COLUMN newField TEXT NOT NULL DEFAULT ''")
 *        }
 *    }
 *    ```
 *    Then add `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` in `getDatabase()`.
 */
@Database(entities = [TimetableEntry::class, TimetableGroup::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timetable_database"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * v1 -> v2: Added weekly recurrence fields.
         * Introduced April 2026 with Room migration.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN semesterStartDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN weekRule TEXT NOT NULL DEFAULT 'ALL'")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN customWeekList TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN skipWeekList TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v2 -> v3: Added query indexes.
         * Adds composite index on date+startMinutes and single-field index on dayOfWeek
         * to optimize high-frequency queries such as `ORDER BY date ASC, startMinutes ASC`.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timetable_entries_date_startMinutes ON timetable_entries(date, startMinutes)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timetable_entries_dayOfWeek ON timetable_entries(dayOfWeek)")
            }
        }

        /**
         * v3 -> v4: Added timetable groups.
         * Existing entries are assigned to the default group.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS timetable_groups (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO timetable_groups(id, name, createdAt, updatedAt) VALUES('default', '默认课表', $now, $now)",
                )
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN groupId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timetable_groups_updatedAt ON timetable_groups(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timetable_entries_groupId_date_startMinutes ON timetable_entries(groupId, date, startMinutes)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timetable_entries_groupId_dayOfWeek ON timetable_entries(groupId, dayOfWeek)")
            }
        }

        internal val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
    }
}
