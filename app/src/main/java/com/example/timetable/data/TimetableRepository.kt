package com.example.timetable.data

import android.content.Context
import androidx.room.withTransaction
import com.example.timetable.data.room.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

object TimetableRepository {
    private const val STORAGE_FILE_NAME = "timetable_entries.json"
    private const val PREFS_NAME = "timetable_repository_prefs"
    private const val KEY_SAMPLE_ENTRIES_SEEDED = "sample_entries_seeded"
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

    private suspend fun seedSampleEntriesIfNeeded(context: Context, dao: com.example.timetable.data.room.TimetableDao) {
        val currentEntries = dao.getAllEntries()
        if (shouldSeedSampleEntries(currentEntries.isNotEmpty(), hasSeededSampleEntries(context))) {
            dao.upsertEntries(sampleEntries())
        }
        markSampleEntriesSeeded(context)
    }

    private suspend fun ensureRoomBackedStorageReady(context: Context) {
        bootstrapMutex.withLock {
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext)
            val legacyMigration = loadLegacyEntriesIfPresent(appContext)
            var shouldDeleteLegacyFile = false
            db.withTransaction {
                val dao = db.timetableDao()
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

    fun getEntriesStream(context: Context): Flow<List<TimetableEntry>> {
        return AppDatabase.getDatabase(context).timetableDao().getAllEntriesStream()
    }

    suspend fun getEntriesNow(context: Context): List<TimetableEntry> {
        return withContext(Dispatchers.IO) {
            ensureRoomBackedStorageReady(context)
            val dao = AppDatabase.getDatabase(context).timetableDao()
            dao.getAllEntries()
        }
    }

    suspend fun upsertEntry(context: Context, entry: TimetableEntry) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).timetableDao().upsertEntry(entry)
    }

    suspend fun deleteEntry(context: Context, entryId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).timetableDao().deleteEntry(entryId)
    }

    suspend fun replaceAllEntries(context: Context, entries: List<TimetableEntry>) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            val dao = db.timetableDao()
            dao.deleteAll()
            dao.upsertEntries(entries)
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
            TimetableEntry(
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
