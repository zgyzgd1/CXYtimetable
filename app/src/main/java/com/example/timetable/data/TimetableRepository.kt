package com.example.timetable.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class EntryLoadState(
    val entries: List<TimetableEntry>,
    val shouldPersist: Boolean,
    val warningMessage: String? = null,
)

data class PayloadValidation(
    val valid: Boolean,
    val entryCount: Int,
)

object TimetableRepository {
    private const val STORAGE_FILE_NAME = "timetable_entries.json"
    private const val SHARE_PAYLOAD_VERSION = 1
    private val mutex = Mutex()

    private fun getStorageFile(context: Context): File = File(context.filesDir, STORAGE_FILE_NAME)

    suspend fun loadEntries(context: Context): EntryLoadState = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = getStorageFile(context)
            if (!file.exists()) {
                return@withContext EntryLoadState(
                    entries = sampleEntries(),
                    shouldPersist = true,
                )
            }

            val payload = runCatching { file.readText() }.getOrElse {
                val backup = backupCorruptedStorage(file)
                return@withContext EntryLoadState(
                    entries = emptyList(),
                    shouldPersist = false,
                    warningMessage = buildCorruptedStorageMessage(backup),
                )
            }

            val structure = validateStoredPayload(payload)
            if (!structure.valid) {
                val backup = backupCorruptedStorage(file)
                return@withContext EntryLoadState(
                    entries = emptyList(),
                    shouldPersist = false,
                    warningMessage = buildCorruptedStorageMessage(backup),
                )
            }

            val persisted = TimetableShareCodec.decode(payload)
            if (structure.entryCount > 0 && persisted.isEmpty()) {
                val backup = backupCorruptedStorage(file)
                return@withContext EntryLoadState(
                    entries = emptyList(),
                    shouldPersist = false,
                    warningMessage = buildCorruptedStorageMessage(backup),
                )
            }

            return@withContext EntryLoadState(
                entries = persisted,
                shouldPersist = false,
            )
        }
    }

    suspend fun saveEntries(context: Context, entries: List<TimetableEntry>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                getStorageFile(context).writeText(TimetableShareCodec.encode(entries))
            }
        }
    }

    private fun validateStoredPayload(payload: String): PayloadValidation {
        if (payload.isBlank()) {
            return PayloadValidation(valid = false, entryCount = 0)
        }

        val root = runCatching { JSONObject(payload) }.getOrNull()
            ?: return PayloadValidation(valid = false, entryCount = 0)
        if (root.optInt("version", -1) != SHARE_PAYLOAD_VERSION) {
            return PayloadValidation(valid = false, entryCount = 0)
        }

        val entries = root.optJSONArray("entries")
            ?: return PayloadValidation(valid = false, entryCount = 0)
        return PayloadValidation(valid = true, entryCount = entries.length())
    }

    private fun backupCorruptedStorage(file: File): File? {
        if (!file.exists()) return null
        return runCatching {
            val backup = File(file.parentFile, "timetable_entries.corrupt.${System.currentTimeMillis()}.json")
            file.copyTo(backup, overwrite = true)
            backup
        }.getOrNull()
    }

    private fun buildCorruptedStorageMessage(backupFile: File?): String {
        val backupName = backupFile?.name ?: "timetable_entries.corrupt.json"
        return "检测到本地课程数据异常，已保留备份：$backupName"
    }
}
