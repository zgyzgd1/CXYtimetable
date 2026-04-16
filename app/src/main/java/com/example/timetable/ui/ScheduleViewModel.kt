package com.example.timetable.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.formatMinutes
import com.example.timetable.notify.CourseReminderScheduler
import com.example.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 课程表视图模型
 * 管理课程数据的状态和业务逻辑，包括增删改查、导入导出等功能
 * 使用 Kotlin Flow 实现响应式数据流
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val entryComparator = compareBy<TimetableEntry> { it.date }.thenBy { it.startMinutes }

    // 存储课程列表的可变状态流
    private val _entries = MutableStateFlow<List<TimetableEntry>>(emptyList())
    // 对外暴露的只读课程列表流
    val entries = _entries.asStateFlow()

    // 用于发送用户提示消息的共享流
    private val _messages = MutableSharedFlow<String>()
    // 对外暴露的只读消息流
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val loadState = TimetableRepository.loadEntries(getApplication())
            val sorted = sortEntries(loadState.entries)
            _entries.value = sorted

            if (loadState.shouldPersist) {
                TimetableRepository.saveEntries(getApplication(), sorted)
            }
            syncReminders(sorted)
            loadState.warningMessage?.let { postMessage(it) }
        }
    }

    /**
     * 添加或更新课程条目
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
                mutable[index] = normalized
            } else {
                mutable += normalized
            }
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
     */
    fun deleteEntry(entryId: String) {
        val updated = _entries.updateAndGet { current -> current.filterNot { it.id == entryId } }
        persistEntries(updated)
        syncReminders(updated)
        postMessage("已删除课程")
    }

    /**
     * 导出课程表为 ICS 格式
     */
    fun exportIcs(): String = IcsCalendar.write(_entries.value)

    /**
     * 从 ICS 文件导入课程数据
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

    fun updateReminderMinutes(minutes: Int) {
        CourseReminderScheduler.setReminderMinutes(getApplication(), minutes)
        syncReminders(_entries.value)
        postMessage("已设置为提前 $minutes 分钟提醒")
    }

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

    private fun persistEntries(entries: List<TimetableEntry>) {
        viewModelScope.launch {
            TimetableRepository.saveEntries(getApplication(), entries).onFailure {
                postMessage("保存失败：${it.message ?: "未知错误"}")
            }
        }
    }

    private suspend fun persistEntriesNow(entries: List<TimetableEntry>): Boolean {
        return TimetableRepository.saveEntries(getApplication(), entries).isSuccess
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

    private fun syncReminders(entries: List<TimetableEntry>) {
        viewModelScope.launch(Dispatchers.Default) {
            CourseReminderScheduler.sync(getApplication(), entries)
        }
    }

    private fun postMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    private companion object {
        const val MAX_SHARE_PAYLOAD_LENGTH = 300_000
    }
}
