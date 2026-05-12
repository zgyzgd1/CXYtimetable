package com.example.timetable.jw.hebau

import java.security.MessageDigest
import java.util.Locale

object HebauStableId {
    fun forCourse(
        payload: HebauAcademicImportPayload,
        course: HebauRawCourse,
    ): String {
        val key = listOf(
            payload.source,
            payload.semesterName.ifBlank { "unknown-semester" },
            course.name,
            course.teacher.orEmpty(),
            course.position.orEmpty(),
            course.courseClass.orEmpty(),
            course.day.toString(),
            course.startSection.toString(),
            course.endSection.toString(),
            course.weeks.distinct().sorted().joinToString(","),
        ).joinToString("|") { normalize(it) }
        return "hebau:${sha256(key).take(32)}"
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
