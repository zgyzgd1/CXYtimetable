package com.example.timetable.jw.hebau

data class HebauAcademicImportPayload(
    val source: String,
    val sourceKind: String,
    val semesterName: String,
    val semesterStartDate: String?,
    val courses: List<HebauRawCourse>,
    val sectionTimes: List<HebauSectionTime>,
)

data class HebauRawCourse(
    val name: String,
    val teacher: String?,
    val position: String?,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
    val courseClass: String? = null,
    val remark: String? = null,
)

data class HebauSectionTime(
    val section: Int,
    val start: String,
    val end: String,
)

data class HebauParseResult(
    val payload: HebauAcademicImportPayload,
    val errors: List<String> = emptyList(),
)

data class HebauMappingResult(
    val entries: List<com.example.timetable.data.TimetableEntry>,
    val errors: List<String> = emptyList(),
)
