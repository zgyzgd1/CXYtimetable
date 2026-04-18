package com.example.timetable.data

import android.content.Context
import androidx.room.withTransaction
import com.example.timetable.data.room.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

object TimetableRepository {
    private const val STORAGE_FILE_NAME = "timetable_entries.json"
    private const val PREFS_NAME = "timetable_repository_prefs"
    private const val KEY_SAMPLE_ENTRIES_SEEDED = "sample_entries_seeded"

    private fun getLegacyStorageFile(context: Context): File = File(context.filesDir, STORAGE_FILE_NAME)

    private fun getPreferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun hasSeededSampleEntries(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SAMPLE_ENTRIES_SEEDED, false)
    }

    private fun markSampleEntriesSeeded(context: Context) {
        getPreferences(context).edit().putBoolean(KEY_SAMPLE_ENTRIES_SEEDED, true).apply()
    }

    internal fun shouldSeedSampleEntries(hasEntries: Boolean, hasSeededSampleEntries: Boolean): Boolean {
        return !hasEntries && !hasSeededSampleEntries
    }

    private suspend fun migrateLegacyStorageIfPresent(
        context: Context,
        dao: com.example.timetable.data.room.TimetableDao,
    ): Boolean {
        val file = getLegacyStorageFile(context)
        if (!file.exists()) return false

        val payload = runCatching { file.readText() }.getOrDefault("")
        val persisted = TimetableShareCodec.decode(payload)

        if (persisted.isNotEmpty()) {
            dao.upsertEntries(persisted)
            markSampleEntriesSeeded(context)
        }

        // 删除旧 JSON 缓存，此后均以 Room 数据库为源
        file.delete()
        return persisted.isNotEmpty()
    }

    private suspend fun seedSampleEntriesIfNeeded(context: Context, dao: com.example.timetable.data.room.TimetableDao) {
        val currentEntries = dao.getAllEntries()
        if (shouldSeedSampleEntries(currentEntries.isNotEmpty(), hasSeededSampleEntries(context))) {
            dao.upsertEntries(sampleEntries())
        }
        markSampleEntriesSeeded(context)
    }

    /**
     * 迁移与初始化函数：在 ViewModel 启动时调用一次。
     * 检测是否存在旧版的 JSON 文件，如果存在则解析内容并持久化进入 SQLite Room。
     * 迁移完毕后安全删除旧版 JSON。如果连 DB 也是空的则插入模板数据。
     */
    suspend fun ensureMigrated(context: Context) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).timetableDao()

        val migrated = migrateLegacyStorageIfPresent(context, dao)
        if (!migrated) {
            seedSampleEntriesIfNeeded(context, dao)
        }
    }

    /**
     * 获取数据库的实时响应流
     */
    fun getEntriesStream(context: Context): Flow<List<TimetableEntry>> {
        return AppDatabase.getDatabase(context).timetableDao().getAllEntriesStream()
    }

    /**
     * 一次性获取挂起查询（后台广播接收器接力时使用此接口读取）
     */
    suspend fun getEntriesNow(context: Context): List<TimetableEntry> {
        return withContext(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(context).timetableDao()
            migrateLegacyStorageIfPresent(context, dao)
            dao.getAllEntries()
        }
    }

    suspend fun upsertEntry(context: Context, entry: TimetableEntry) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).timetableDao().upsertEntry(entry)
    }

    suspend fun deleteEntry(context: Context, entryId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).timetableDao().deleteEntry(entryId)
    }

    /**
     * 覆盖式替换所有的课表项，用于“导入 ICS 文件”动作
     */
    suspend fun replaceAllEntries(context: Context, entries: List<TimetableEntry>) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            val dao = db.timetableDao()
            dao.deleteAll()
            dao.upsertEntries(entries)
        }
    }
}
