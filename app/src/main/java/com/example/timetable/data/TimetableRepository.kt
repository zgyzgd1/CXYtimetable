package com.example.timetable.data

import android.content.Context
import androidx.room.withTransaction
import com.example.timetable.data.room.AppDatabase
import com.example.timetable.data.room.TimetableDao
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 课程表数据仓库。
 *
 * **注意：此类假设单进程执行。** 如果引入多进程组件（例如，带有 `android:process` 的小部件），
 * 请使用 ContentProvider 或其他跨进程数据共享机制，而不是直接访问 Room；否则
 * [bootstrapMutex] 和 SharedPreferences 状态无法跨进程同步。
 */
object TimetableRepository {
    private const val STORAGE_FILE_NAME = "timetable_entries.json"
    private const val PREFS_NAME = "timetable_repository_prefs"
    private const val KEY_SAMPLE_ENTRIES_SEEDED = "sample_entries_seeded"
    private const val KEY_ACTIVE_GROUP_ID = "active_group_id"
    private val bootstrapMutex = Mutex()

    internal data class LegacyLoadResult(
        val file: File,
        val payload: String,
        val entries: List<TimetableEntry>,
    ) {
        val hasPayload: Boolean
            get() = payload.isNotBlank()
    }

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

    internal fun shouldSeedSampleEntriesAfterLegacyLoad(
        legacyLoadResult: LegacyLoadResult?,
        hasEntries: Boolean,
        hasSeededSampleEntries: Boolean,
    ): Boolean {
        if (legacyLoadResult != null && legacyLoadResult.hasPayload && legacyLoadResult.entries.isEmpty()) {
            return false
        }
        return shouldSeedSampleEntries(hasEntries, hasSeededSampleEntries)
    }

    private fun loadLegacyEntriesIfPresent(context: Context): LegacyLoadResult? {
        val file = getLegacyStorageFile(context)
        if (!file.exists()) return null

        val payload = runCatching { file.readText() }.getOrDefault("")
        return LegacyLoadResult(
            file = file,
            payload = payload,
            entries = decodeLegacyEntries(payload),
        )
    }

    private suspend fun seedSampleEntriesIfNeeded(context: Context, dao: TimetableDao) {
        val currentEntries = dao.getEntries(TimetableGroup.DEFAULT_ID)
        if (shouldSeedSampleEntries(currentEntries.isNotEmpty(), hasSeededSampleEntries(context))) {
            dao.upsertEntries(sampleEntries())
        }
        markSampleEntriesSeeded(context)
    }

    private suspend fun ensureDefaultGroup(dao: TimetableDao) {
        if (dao.getGroups().none { it.id == TimetableGroup.DEFAULT_ID }) {
            dao.upsertGroup(TimetableGroup.default())
        }
    }

    private suspend fun ensureRoomBackedStorageReady(context: Context) {
        bootstrapMutex.withLock {
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext)
            val legacyMigration = loadLegacyEntriesIfPresent(appContext)
            var shouldDeleteLegacyFile = false
            db.withTransaction {
                val dao = db.timetableDao()
                ensureDefaultGroup(dao)
                val migratedEntries = legacyMigration?.entries.orEmpty()
                val migrated = migratedEntries.isNotEmpty()
                if (migrated) {
                    dao.upsertEntries(migratedEntries)
                    markSampleEntriesSeeded(appContext)
                }
                if (
                    !migrated &&
                    shouldSeedSampleEntriesAfterLegacyLoad(
                        legacyLoadResult = legacyMigration,
                        hasEntries = dao.getAllEntries().isNotEmpty(),
                        hasSeededSampleEntries = hasSeededSampleEntries(appContext),
                    )
                ) {
                    seedSampleEntriesIfNeeded(appContext, dao)
                }
                shouldDeleteLegacyFile = legacyMigration != null && (!legacyMigration.hasPayload || migrated)
            }
            if (shouldDeleteLegacyFile) {
                legacyMigration?.file?.delete()
            }
        }
    }

    suspend fun ensureMigrated(context: Context) = withContext(Dispatchers.IO) {
        ensureRoomBackedStorageReady(context)
    }

    fun getGroupsStream(context: Context): Flow<List<TimetableGroup>> {
        return AppDatabase.getDatabase(context).timetableDao().getGroupsStream()
    }

    fun getActiveGroupId(context: Context): String {
        return getPreferences(context).getString(KEY_ACTIVE_GROUP_ID, TimetableGroup.DEFAULT_ID)
            ?: TimetableGroup.DEFAULT_ID
    }

    suspend fun resolveActiveGroupId(context: Context): String = withContext(Dispatchers.IO) {
        ensureRoomBackedStorageReady(context)
        val appContext = context.applicationContext
        val dao = AppDatabase.getDatabase(appContext).timetableDao()
        val groups = dao.getGroups()
        val activeGroupId = getActiveGroupId(appContext)
        if (groups.any { it.id == activeGroupId }) {
            activeGroupId
        } else {
            setActiveGroupId(appContext, TimetableGroup.DEFAULT_ID)
            TimetableGroup.DEFAULT_ID
        }
    }

    fun setActiveGroupId(context: Context, groupId: String) {
        getPreferences(context).edit().putString(KEY_ACTIVE_GROUP_ID, groupId.ifBlank { TimetableGroup.DEFAULT_ID }).apply()
    }

    fun getEntriesStream(context: Context, groupId: String = TimetableGroup.DEFAULT_ID): Flow<List<TimetableEntry>> {
        return AppDatabase.getDatabase(context).timetableDao().getEntriesStream(groupId.ifBlank { TimetableGroup.DEFAULT_ID })
    }

    suspend fun getEntriesNow(context: Context): List<TimetableEntry> {
        return withContext(Dispatchers.IO) {
            ensureRoomBackedStorageReady(context)
            val dao = AppDatabase.getDatabase(context).timetableDao()
            dao.getEntries(resolveActiveGroupId(context))
        }
    }

    suspend fun upsertEntry(context: Context, entry: TimetableEntry) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).timetableDao().upsertEntry(entry)
    }

    suspend fun deleteEntry(context: Context, entryId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).timetableDao().deleteEntry(entryId)
    }

    suspend fun replaceAllEntries(context: Context, entries: List<TimetableEntry>) = withContext(Dispatchers.IO) {
        replaceEntriesInGroup(context, resolveActiveGroupId(context), entries)
    }

    suspend fun replaceEntriesInGroup(context: Context, groupId: String, entries: List<TimetableEntry>) = withContext(Dispatchers.IO) {
        val safeGroupId = groupId.ifBlank { TimetableGroup.DEFAULT_ID }
        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            val dao = db.timetableDao()
            ensureDefaultGroup(dao)
            dao.deleteEntriesInGroup(safeGroupId)
            dao.upsertEntries(entries.asImportedEntriesForGroup(safeGroupId))
        }
    }

    /**
     * Merge import: preserves existing entries and upserts new ones (by id).
     * Unlike [replaceAllEntries], this method does not delete any existing data.
     */
    suspend fun mergeEntries(context: Context, entries: List<TimetableEntry>) = withContext(Dispatchers.IO) {
        mergeEntries(context, resolveActiveGroupId(context), entries)
    }

    suspend fun mergeEntries(context: Context, groupId: String, entries: List<TimetableEntry>) = withContext(Dispatchers.IO) {
        val safeGroupId = groupId.ifBlank { TimetableGroup.DEFAULT_ID }
        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            val dao = db.timetableDao()
            ensureDefaultGroup(dao)
            dao.upsertEntries(entries.asImportedEntriesForGroup(safeGroupId))
        }
    }

    suspend fun createGroup(context: Context, name: String): TimetableGroup = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val group = TimetableGroup.create(name)
        db.withTransaction {
            val dao = db.timetableDao()
            ensureDefaultGroup(dao)
            dao.upsertGroup(group)
        }
        group
    }

    suspend fun createGroupWithEntries(context: Context, name: String, entries: List<TimetableEntry>): TimetableGroup = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val group = TimetableGroup.create(name)
        db.withTransaction {
            val dao = db.timetableDao()
            ensureDefaultGroup(dao)
            dao.upsertGroup(group)
            dao.upsertEntries(entries.asImportedEntriesForGroup(group.id))
        }
        setActiveGroupId(context, group.id)
        group
    }

    internal fun scopeImportedEntryId(groupId: String, entryId: String): String {
        val safeGroupId = groupId.ifBlank { TimetableGroup.DEFAULT_ID }
        val safeEntryId = entryId.ifBlank { java.util.UUID.randomUUID().toString() }
        val prefix = "$safeGroupId|"
        return if (safeEntryId.startsWith(prefix)) safeEntryId else "$prefix$safeEntryId"
    }

    private fun List<TimetableEntry>.asImportedEntriesForGroup(groupId: String): List<TimetableEntry> {
        val safeGroupId = groupId.ifBlank { TimetableGroup.DEFAULT_ID }
        return map { entry ->
            entry.copy(
                id = scopeImportedEntryId(safeGroupId, entry.id),
                groupId = safeGroupId,
            )
        }
    }

    internal fun decodeLegacyEntries(payload: String): List<TimetableEntry> {
        val root = runCatching { JSONObject(payload) }.getOrElse { return emptyList() }
        if (root.optInt("version", 1) != 1) return emptyList()
        val array = root.optJSONArray("entries") ?: return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseLegacyEntry(item)?.let(::add)
            }
        }
    }

    private fun parseLegacyEntry(item: JSONObject): TimetableEntry? {
        val title = item.optString("title").trim()
        val dayOfWeek = item.optInt("dayOfWeek")
        val date = item.optString("date").ifBlank { defaultDateForWeekday(dayOfWeek) }
        val parsedDate = parseEntryDate(date) ?: return null
        val startMinutes = item.optInt("startMinutes")
        val endMinutes = item.optInt("endMinutes")

        if (
            title.isBlank() ||
            startMinutes !in 0 until 24 * 60 ||
            endMinutes !in 1..24 * 60 ||
            startMinutes >= endMinutes
        ) {
            return null
        }

        return runCatching {
            TimetableEntry.create(
                id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                title = title,
                date = parsedDate.toString(),
                dayOfWeek = parsedDate.dayOfWeek.value,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                location = item.optString("location"),
                note = item.optString("note"),
            )
        }.getOrNull()
    }
}
