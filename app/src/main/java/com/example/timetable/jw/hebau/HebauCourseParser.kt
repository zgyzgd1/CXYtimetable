package com.example.timetable.jw.hebau

import org.json.JSONArray
import org.json.JSONObject

object HebauCourseParser {
    fun parse(json: String): HebauParseResult {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("Invalid HEBAU course JSON.", it) }
        val coursesArray = root.optJSONArray("courses")
            ?: throw IllegalArgumentException("Missing courses array in HEBAU course JSON.")

        val errors = mutableListOf<String>()
        val coursesByKey = linkedMapOf<String, HebauRawCourse>()
        val weakKeys = linkedMapOf<String, String>()
        for (index in 0 until coursesArray.length()) {
            val item = coursesArray.optJSONObject(index)
            if (item == null) {
                errors += "Course ${index + 1}: item is not an object."
                continue
            }
            val course = runCatching { parseCourse(item) }
                .onFailure { errors += "Course ${index + 1}: ${it.message ?: "invalid course"}" }
                .getOrNull()
                ?: continue
            addCourse(coursesByKey, weakKeys, course)
        }

        val plainText = root.optString("plainText").trim()
        if (plainText.isNotBlank()) {
            val textResult = HebauPlainTextParser.parse(plainText)
            textResult.courses.forEach { course ->
                addCourse(coursesByKey, weakKeys, course)
            }
            errors += textResult.errors.map { "Plain text: $it" }
        }

        val sectionTimes = parseSectionTimes(root.optJSONArray("sectionTimes"), errors)
        return HebauParseResult(
            payload = HebauAcademicImportPayload(
                source = root.optString("source").ifBlank { "hebau-urp" },
                sourceKind = root.optString("sourceKind").ifBlank { if (plainText.isBlank()) "unknown" else "hebau-text" },
                semesterName = root.optString("semesterName").trim(),
                semesterStartDate = root.optString("semesterStartDate").trim().ifBlank { null },
                courses = coursesByKey.values.toList(),
                sectionTimes = sectionTimes,
            ),
            errors = errors,
        )
    }

    private fun parseCourse(item: JSONObject): HebauRawCourse {
        val name = item.optString("name").trim()
        val day = item.optInt("day", -1)
        val startSection = item.optInt("startSection", -1)
        val endSection = item.optInt("endSection", -1)
        val weeks = parseWeeks(item)

        require(name.isNotBlank()) { "name is blank." }
        require(day in 1..7) { "day must be in 1..7." }
        require(startSection > 0) { "startSection must be positive." }
        require(endSection > 0) { "endSection must be positive." }
        require(startSection <= endSection) { "startSection must be before endSection." }
        require(weeks.isNotEmpty()) { "weeks is empty." }
        require(weeks.all { it > 0 }) { "weeks must contain positive numbers only." }

        return HebauRawCourse(
            name = name,
            teacher = item.optString("teacher").trim().ifBlank { null },
            position = item.optString("position").trim().ifBlank { null },
            courseClass = optFirstString(item, "courseClass", "teachingClass", "className").ifBlank { null },
            day = day,
            startSection = startSection,
            endSection = endSection,
            weeks = weeks.distinct().sorted(),
            remark = item.optString("remark").trim().ifBlank { null },
        )
    }

    private fun optFirstString(item: JSONObject, vararg names: String): String {
        for (name in names) {
            val value = item.optString(name).trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun parseWeeks(item: JSONObject): List<Int> {
        val value = item.opt("weeks") ?: return emptyList()
        return when (value) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    val week = value.optInt(index, -1)
                    if (week > 0) add(week)
                }
            }
            is String -> parseWeekText(value)
            is Number -> listOf(value.toInt())
            else -> emptyList()
        }
    }

    private fun parseWeekText(text: String): List<Int> {
        return text.split(',')
            .flatMap { token ->
                val trimmed = token.trim()
                if ('-' in trimmed) {
                    val parts = trimmed.split('-', limit = 2)
                    val start = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (start != null && end != null && start > 0 && end >= start) {
                        (start..end).toList()
                    } else {
                        emptyList()
                    }
                } else {
                    listOfNotNull(trimmed.toIntOrNull())
                }
            }
    }

    private fun parseSectionTimes(array: JSONArray?, errors: MutableList<String>): List<HebauSectionTime> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                if (item == null) {
                    errors += "Section time ${index + 1}: item is not an object."
                    continue
                }
                val section = item.optInt("section", -1)
                val start = item.optString("start").trim()
                val end = item.optString("end").trim()
                if (section <= 0 || start.isBlank() || end.isBlank()) {
                    errors += "Section time ${index + 1}: section/start/end is invalid."
                    continue
                }
                add(HebauSectionTime(section, start, end))
            }
        }
    }

    private fun dedupeKey(course: HebauRawCourse): String {
        return listOf(
            course.name,
            course.teacher.orEmpty(),
            course.position.orEmpty(),
            course.courseClass.orEmpty(),
            course.day.toString(),
            course.startSection.toString(),
            course.endSection.toString(),
            course.weeks.joinToString(","),
        ).joinToString("|") { it.trim().lowercase() }
    }

    private fun weakDedupeKey(course: HebauRawCourse): String {
        return listOf(
            course.name,
            course.day.toString(),
            course.startSection.toString(),
            course.endSection.toString(),
            course.weeks.joinToString(","),
        ).joinToString("|") { it.trim().lowercase() }
    }

    private fun addCourse(
        coursesByKey: MutableMap<String, HebauRawCourse>,
        weakKeys: MutableMap<String, String>,
        course: HebauRawCourse,
    ) {
        val key = dedupeKey(course)
        val weakKey = weakDedupeKey(course)
        val existingKey = coursesByKey[key]?.let { key } ?: weakKeys[weakKey]
        if (existingKey != null) {
            coursesByKey[existingKey] = mergeCourse(coursesByKey.getValue(existingKey), course)
            return
        }
        coursesByKey[key] = course
        weakKeys[weakKey] = key
    }

    private fun mergeCourse(existing: HebauRawCourse, candidate: HebauRawCourse): HebauRawCourse {
        return existing.copy(
            teacher = existing.teacher.ifNullOrBlank(candidate.teacher),
            position = existing.position.ifNullOrBlank(candidate.position),
            courseClass = existing.courseClass.ifNullOrBlank(candidate.courseClass),
            remark = existing.remark.ifNullOrBlank(candidate.remark),
        )
    }

    private fun String?.ifNullOrBlank(fallback: String?): String? {
        return if (this.isNullOrBlank()) fallback?.takeIf { it.isNotBlank() } else this
    }
}
