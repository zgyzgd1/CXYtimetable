package com.example.timetable.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.timetable.data.TimetableEntry

/**
 * Room 数据库定义。
 *
 * ## 版本演进策略
 *
 * 1. **每次新增或修改字段时**，必须递增 [version]，并在 [Companion] 中
 *    添加对应的 `MIGRATION_N_M` 对象。
 *
 * 2. **禁止使用 `fallbackToDestructiveMigration()`**，
 *    否则版本升级会静默清空用户数据。
 *
 * 3. **已开启 `exportSchema = true`**，每次编译时 Room 会将当前
 *    schema 导出到 `app/schemas/` 目录，便于：
 *    - 版本对比和 code review
 *    - 后续可接入 `MigrationTestHelper` 做自动化迁移测试
 *
 * 4. **新增 Migration 模板**：
 *    ```kotlin
 *    private val MIGRATION_2_3 = object : Migration(2, 3) {
 *        override fun migrate(db: SupportSQLiteDatabase) {
 *            db.execSQL("ALTER TABLE timetable_entries ADD COLUMN newField TEXT NOT NULL DEFAULT ''")
 *        }
 *    }
 *    ```
 *    然后在 `getDatabase()` 中添加 `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`。
 */
@Database(entities = [TimetableEntry::class], version = 2, exportSchema = true)
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * v1 → v2: 新增周循环课程字段。
         * 添加于 2026-04，随 Room 迁移引入。
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
    }
}
