package com.example.timetable.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableShareCodec
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.sampleEntries
import com.example.timetable.notify.CourseReminderScheduler
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 课程表视图模型
 * 管理课程数据的状态和业务逻辑，包括增删改查、导入导出等功能
 * 使用 Kotlin Flow 实现响应式数据流
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val storageFile = File(application.filesDir, STORAGE_FILE_NAME)
    private val storageMutex = Mutex()
    private val entryComparator = compareBy<TimetableEntry> { it.date }.thenBy { it.startMinutes }
    private val initialLoadState = loadEntries()

    // 存储课程列表的可变状态流
    private val _entries = MutableStateFlow(initialLoadState.entries)
    // 对外暴露的只读课程列表流
    val entries = _entries.asStateFlow()

    // 用于发送用户提示消息的共享流
    private val _messages = MutableSharedFlow<String>()
    // 对外暴露的只读消息流
    val messages = _messages.asSharedFlow()

    init {
        if (initialLoadState.shouldPersist) {
            persistEntries(_entries.value)
        }
        syncReminders(_entries.value)
        initialLoadState.warningMessage?.let(::postMessage)
    }

    /**
     * 添加或更新课程条目
     * 如果条目 ID 已存在则更新，否则新增
     *
     * @param entry 要保存的课程条目
     */
    fun upsertEntry(entry: TimetableEntry) {
        val normalized = normalizeEntry(entry)
        validateEntry(normalized)?.let {
            postMessage("保存失败：$it")
            return
        }
        val conflict = findConflict(normalized, _entries.value)

        val updated = _entries.updateAndGet { current ->
            val mutable = current.toMutableList()
            val index = mutable.indexOfFirst { it.id == normalized.id }
            if (index >= 0) {
                // 更新已有条目
                mutable[index] = normalized
            } else {
                // 添加新条目
                mutable += normalized
            }
            // 按星期和时间排序
            sortEntries(mutable)
        }
        persistEntries(updated)
        syncReminders(updated)
        if (conflict == null) {
            postMessage("已保存课程")
        } else {
            postMessage(
                "已保存课程（与 ${conflict.title} ${formatMinutes(conflict.startMinutes)}-${formatMinutes(conflict.endMinutes)} 时间重叠）"
            )
        }
    }

    /**
     * 删除指定 ID 的课程条目
     *
     * @param entryId 要删除的课程条目 ID
     */
    fun deleteEntry(entryId: String) {
        val updated = _entries.updateAndGet { current -> current.filterNot { it.id == entryId } }
        persistEntries(updated)
        syncReminders(updated)
        postMessage("已删除课程")
    }

    /**
     * 导出课程表为 ICS 格式
     *
     * @return ICS 格式的字符串
     */
    fun exportIcs(): String = IcsCalendar.write(_entries.value)

    /**
     * 导出课程表为分享用的 JSON 载荷
     *
     * @return JSON 格式的字符串，用于二维码分享
     */
    fun exportSharePayload(): String = TimetableShareCodec.encode(_entries.value)

    /**
     * 从 ICS 文件导入课程数据
     * 在协程中异步读取文件内容并解析
     *
     * @param contentResolver 内容解析器，用于读取文件
     * @param uri 文件的 URI 地址
     */
    fun importFromIcs(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = readText(contentResolver, uri)
            if (text.isBlank()) {
                postMessage("导入失败：文件内容为空")
                return@launch
            }

            val imported = runCatching { IcsCalendar.parse(text) }
                .onFailure { postMessage("导入失败：${it.message ?: "日历格式异常"}") }
                .getOrDefault(emptyList())
            if (imported.isEmpty()) {
                postMessage("未识别到可导入的课程")
                return@launch
            }

            applyImportedEntries(imported)
        }
    }

    /**
     * 从分享载荷（JSON 字符串）导入课程数据
     * 通常用于扫描二维码后导入
     *
     * @param payload JSON 格式的课程数据字符串
     */
    fun importFromSharePayload(payload: String) {
        viewModelScope.launch {
            if (payload.length > MAX_SHARE_PAYLOAD_LENGTH) {
                postMessage("导入失败：分享内容过长")
                return@launch
            }
            val imported = TimetableShareCodec.decode(payload)
            if (imported.isEmpty()) {
                postMessage("未识别到可导入的二维码内容")
                return@launch
            }
            applyImportedEntries(imported)
        }
    }

    fun updateReminderMinutes(minutes: Int) {
        CourseReminderScheduler.setReminderMinutes(getApplication(), minutes)
        syncReminders(_entries.value)
        postMessage("已设置为提前 $minutes 分钟提醒")
    }

    /**
     * 异步读取 URI 对应的文本内容
     *
     * @param contentResolver 内容解析器
     * @param uri 文件的 URI 地址
     * @return 读取的文本内容，失败返回空字符串
     */
    private suspend fun readText(contentResolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                }.orEmpty()
            }.onFailure {
                postMessage("读取文件失败：${it.message ?: "未知错误"}")
            }.getOrDefault("")
        }
    }

    private fun loadEntries(): EntryLoadState {
        if (!storageFile.exists()) {
            return EntryLoadState(
                entries = sortEntries(sampleEntries()),
                shouldPersist = true,
            )
        }

        val payload = runCatching { storageFile.readText() }.getOrElse {
            val backup = backupCorruptedStorage()
            return EntryLoadState(
                entries = emptyList(),
                shouldPersist = false,
                warningMessage = buildCorruptedStorageMessage(backup),
            )
        }

        val structure = validateStoredPayload(payload)
        if (!structure.valid) {
            val backup = backupCorruptedStorage()
            return EntryLoadState(
                entries = emptyList(),
                shouldPersist = false,
                warningMessage = buildCorruptedStorageMessage(backup),
            )
        }

        val persisted = TimetableShareCodec.decode(payload)
        if (structure.entryCount > 0 && persisted.isEmpty()) {
            val backup = backupCorruptedStorage()
            return EntryLoadState(
                entries = emptyList(),
                shouldPersist = false,
                warningMessage = buildCorruptedStorageMessage(backup),
            )
        }

        return EntryLoadState(
            entries = sortEntries(persisted),
            shouldPersist = false,
        )
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

    private fun backupCorruptedStorage(): File? {
        if (!storageFile.exists()) return null
        return runCatching {
            val backup = File(storageFile.parentFile, "timetable_entries.corrupt.${System.currentTimeMillis()}.json")
            storageFile.copyTo(backup, overwrite = true)
            backup
        }.getOrNull()
    }

    private fun buildCorruptedStorageMessage(backupFile: File?): String {
        val backupName = backupFile?.name ?: "timetable_entries.corrupt.json"
        return "检测到本地课程数据异常，已保留备份：$backupName"
    }

    private fun persistEntries(entries: List<TimetableEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            writeEntries(entries).onFailure {
                postMessage("保存失败：${it.message ?: "未知错误"}")
            }
        }
    }

    private suspend fun persistEntriesNow(entries: List<TimetableEntry>): Boolean {
        return withContext(Dispatchers.IO) {
            writeEntries(entries).isSuccess
        }
    }

    private suspend fun applyImportedEntries(imported: List<TimetableEntry>) {
        val validEntries = mutableListOf<TimetableEntry>()
        var invalidCount = 0

        sortEntries(imported)
            .map(::normalizeEntry)
            .forEach { entry ->
                if (validateEntry(entry) != null) {
                    invalidCount++
                    return@forEach
                }

                validEntries += entry
            }

        if (validEntries.isEmpty()) {
            postMessage("导入失败：未发现有效课程")
            return
        }

        val updated = sortEntries(validEntries)
        val conflictCount = countConflictPairs(updated)
        _entries.value = updated
        if (persistEntriesNow(updated)) {
            syncReminders(updated)
            if (invalidCount == 0 && conflictCount == 0) {
                postMessage("已导入 ${updated.size} 条课程，并保存为当前课程表")
            } else {
                postMessage("已导入 ${updated.size} 条课程，跳过无效 ${invalidCount} 条，检测到冲突 ${conflictCount} 组")
            }
        } else {
            postMessage("导入成功，但保存失败")
        }
    }

    private fun normalizeEntry(entry: TimetableEntry): TimetableEntry {
        return entry.copy(
            title = entry.title.trim(),
            location = entry.location.trim(),
            note = entry.note.trim(),
        )
    }

    private fun validateEntry(entry: TimetableEntry): String? {
        return when {
            entry.title.isBlank() -> "课程名称不能为空"
            entry.title.length > 64 -> "课程名称不能超过 64 字"
            entry.location.length > 64 -> "地点不能超过 64 字"
            entry.note.length > 256 -> "备注不能超过 256 字"
            entry.startMinutes !in 0 until 24 * 60 -> "开始时间不合法"
            entry.endMinutes !in 1..24 * 60 -> "结束时间不合法"
            entry.startMinutes >= entry.endMinutes -> "结束时间需要晚于开始时间"
            else -> null
        }
    }

    private fun findConflict(target: TimetableEntry, entries: List<TimetableEntry>): TimetableEntry? {
        return entries.firstOrNull { existing ->
            existing.id != target.id &&
                existing.date == target.date &&
                target.startMinutes < existing.endMinutes &&
                existing.startMinutes < target.endMinutes
        }
    }

    private fun countConflictPairs(entries: List<TimetableEntry>): Int {
        var pairs = 0
        entries.groupBy { it.date }.values.forEach { sameDay ->
            val sorted = sameDay.sortedBy { it.startMinutes }
            for (index in sorted.indices) {
                val current = sorted[index]
                for (nextIndex in index + 1 until sorted.size) {
                    val next = sorted[nextIndex]
                    if (next.startMinutes >= current.endMinutes) break
                    if (current.startMinutes < next.endMinutes && next.startMinutes < current.endMinutes) {
                        pairs++
                    }
                }
            }
        }
        return pairs
    }

    private fun sortEntries(entries: List<TimetableEntry>): List<TimetableEntry> =
        entries.sortedWith(entryComparator)

    private suspend fun writeEntries(entries: List<TimetableEntry>): Result<Unit> = runCatching {
        storageMutex.withLock {
            storageFile.writeText(TimetableShareCodec.encode(entries))
        }
    }

    private fun syncReminders(entries: List<TimetableEntry>) {
        viewModelScope.launch(Dispatchers.Default) {
            CourseReminderScheduler.sync(getApplication(), entries)
        }
    }

    /**
     * 发送用户提示消息
     *
     * @param message 要显示的消息文本
     */
    private fun postMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    private data class EntryLoadState(
        val entries: List<TimetableEntry>,
        val shouldPersist: Boolean,
        val warningMessage: String? = null,
    )

    private data class PayloadValidation(
        val valid: Boolean,
        val entryCount: Int,
    )

    private companion object {
        const val STORAGE_FILE_NAME = "timetable_entries.json"
        const val MAX_SHARE_PAYLOAD_LENGTH = 300_000
        const val SHARE_PAYLOAD_VERSION = 1
    }
}
