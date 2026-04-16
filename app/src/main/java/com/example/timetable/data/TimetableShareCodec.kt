package com.example.timetable.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 课程表分享编解码器
 * 使用 JSON 格式对课程数据进行编码和解码，用于二维码分享功能
 * 相比 ICS 格式更轻量，适合在二维码中存储
 */
object TimetableShareCodec {
    private const val CURRENT_VERSION = 1

    /**
     * 将课程列表编码为 JSON 字符串
     *
     * @param entries 课程条目列表
     * @return JSON 格式的字符串，包含版本号和课程数据
     */
    fun encode(entries: List<TimetableEntry>): String {
        val root = JSONObject()
        val array = JSONArray()

        // 按星期和时间排序后遍历所有课程
        entries
            .sortedWith(compareBy<TimetableEntry> { it.dayOfWeek }.thenBy { it.startMinutes })
            .forEach { entry ->
                // 将每个课程条目转换为 JSON 对象
                array.put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("title", entry.title)
                        .put("date", entry.date)
                        .put("dayOfWeek", entry.dayOfWeek)
                        .put("startMinutes", entry.startMinutes)
                        .put("endMinutes", entry.endMinutes)
                        .put("location", entry.location)
                        .put("note", entry.note)
                )
            }

        // 添加版本号和课程数组
        root.put("version", CURRENT_VERSION)
        root.put("entries", array)
        return root.toString()
    }

    /**
     * 从 JSON 字符串解码出课程列表
     *
     * @param payload JSON 格式的字符串
     * @return 解码后的课程条目列表，解析失败返回空列表
     */
    fun decode(payload: String): List<TimetableEntry> {
        val root = runCatching { JSONObject(payload) }.getOrElse { return emptyList() }
        if (root.optInt("version", CURRENT_VERSION) != CURRENT_VERSION) {
            return emptyList()
        }
        val array = root.optJSONArray("entries") ?: return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching { parseItem(item) }
                    .getOrNull()
                    ?.let(::add)
            }
        }
    }

    private fun parseItem(item: JSONObject): TimetableEntry? {
        val title = item.optString("title").trim()
        val dayOfWeek = item.optInt("dayOfWeek")
        val date = item.optString("date").ifBlank { defaultDateForWeekday(dayOfWeek) }
        val startMinutes = item.optInt("startMinutes")
        val endMinutes = item.optInt("endMinutes")
        val parsedDate = parseEntryDate(date)
        if (
            title.isBlank() ||
                parsedDate == null ||
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
