package com.example.timetable.jw.hebau

private const val MAX_TEXT_WEEK = 60
private const val MAX_TEXT_SECTION = 20
private const val MAX_TEXT_WINDOW_LINES = 8
private val whitespaceRegex = Regex("[\\t ]+")
private val teacherNameLineRegex = Regex(
    "^(?:[\\u4e00-\\u9fa5路]{2,8}(?:[銆?][\\u4e00-\\u9fa5路]{2,8})*|[A-Za-z][A-Za-z .'-]{1,48}(?:[,/][A-Za-z][A-Za-z .'-]{1,48})*)$",
)

data class HebauPlainTextParseResult(
    val courses: List<HebauRawCourse>,
    val errors: List<String> = emptyList(),
)

object HebauPlainTextParser {
    fun parse(text: String): HebauPlainTextParseResult {
        val lines = normalizeLines(text)
        if (lines.isEmpty()) return HebauPlainTextParseResult(emptyList())

        val coursesByKey = linkedMapOf<String, HebauRawCourse>()
        val errors = mutableListOf<String>()

        lines.forEach { line ->
            parseCandidate(line, listOf(line))?.let { course ->
                coursesByKey[textCourseKey(course)] = course
            }
        }

        var start = 0
        while (start < lines.size) {
            val maxEnd = minOf(lines.size, start + MAX_TEXT_WINDOW_LINES)
            var combined = ""
            var bestCourse: HebauRawCourse? = null
            var consumedEnd: Int? = null
            for (end in start until maxEnd) {
                if (bestCourse != null && !canExtendCourseBlock(lines[end])) {
                    break
                }
                combined = cleanText("$combined ${lines[end]}")
                if (!hasCourseTiming(combined)) continue
                parseCandidate(combined, lines.subList(start, end + 1))?.let { course ->
                    bestCourse = course
                    consumedEnd = end
                    return@let
                }
            }
            bestCourse?.let { course ->
                coursesByKey[textCourseKey(course)] = course
            }
            start = consumedEnd?.plus(1) ?: (start + 1)
        }

        if (coursesByKey.isEmpty() && text.isNotBlank()) {
            errors += "Plain text fallback did not find recognizable course rows."
        }
        return HebauPlainTextParseResult(
            courses = coursesByKey.values.toList(),
            errors = errors,
        )
    }

    private fun parseCandidate(text: String, lines: List<String>): HebauRawCourse? {
        val day = parseDay(text) ?: return null
        val sections = parseSections(text) ?: return null
        val weeks = parseWeeks(text)
        if (weeks.isEmpty()) return null
        val name = parseCourseName(text, lines) ?: return null
        if (isIgnoredCourseName(name)) return null

        return HebauRawCourse(
            name = name,
            teacher = parseTeacher(text, lines, name),
            position = parsePosition(text, lines),
            day = day,
            startSection = sections.first,
            endSection = sections.last,
            weeks = weeks,
            courseClass = parseCourseClass(text),
            remark = "",
        )
    }

    private fun normalizeLines(text: String): List<String> {
        return text
            .replace('\u00a0', ' ')
            .replace("\r", "\n")
            .split('\n')
            .map(::cleanText)
            .filter { it.isNotBlank() && !isHeaderOrNoiseLine(it) }
    }

    private fun cleanText(text: String): String {
        return text
            .replace(whitespaceRegex, " ")
            .replace(Regex("\\s*([,，、；;:：()（）\\[\\]【】])\\s*"), "$1")
            .trim()
    }

    private fun hasCourseTiming(text: String): Boolean {
        return parseDay(text) != null && parseSections(text) != null && parseWeeks(text).isNotEmpty()
    }

    private fun parseDay(text: String): Int? {
        val value = cleanText(text)
        val patterns = listOf(
            Regex("星期一|周一|礼拜一|Mon(?:day)?", RegexOption.IGNORE_CASE) to 1,
            Regex("星期二|周二|礼拜二|Tue(?:sday)?", RegexOption.IGNORE_CASE) to 2,
            Regex("星期三|周三|礼拜三|Wed(?:nesday)?", RegexOption.IGNORE_CASE) to 3,
            Regex("星期四|周四|礼拜四|Thu(?:rsday)?", RegexOption.IGNORE_CASE) to 4,
            Regex("星期五|周五|礼拜五|Fri(?:day)?", RegexOption.IGNORE_CASE) to 5,
            Regex("星期六|周六|礼拜六|Sat(?:urday)?", RegexOption.IGNORE_CASE) to 6,
            Regex("星期日|星期天|周日|周天|礼拜日|礼拜天|Sun(?:day)?", RegexOption.IGNORE_CASE) to 7,
        )
        patterns.firstOrNull { it.first.containsMatchIn(value) }?.let { return it.second }
        val numeric = Regex("(?:星期|周|礼拜)\\s*([1-7一二三四五六日天])").find(value)?.groupValues?.get(1)
            ?: return null
        return when (numeric) {
            "1", "一" -> 1
            "2", "二" -> 2
            "3", "三" -> 3
            "4", "四" -> 4
            "5", "五" -> 5
            "6", "六" -> 6
            "7", "日", "天" -> 7
            else -> null
        }
    }

    private fun parseSections(text: String): IntRange? {
        val value = cleanText(text)
        val labeled = Regex("(?:节次|上课节次|时间节次)[:：]?\\s*(\\d{1,2})\\s*[-~～至到—–,，、]?\\s*(\\d{1,2})?")
            .find(value)
        val sectionMatch = labeled ?: Regex("(?:第)?\\s*(\\d{1,2})\\s*(?:[-~～至到—–,，、]\\s*(\\d{1,2}))?\\s*(?:节|小节|课时)")
            .find(value)
        if (sectionMatch != null) {
            val start = sectionMatch.groupValues[1].toIntOrNull()
            val end = sectionMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: start
            if (start != null && end != null && isValidSectionRange(start, end)) {
                return start..end
            }
        }

        val timeMatch = Regex("(\\d{1,2}:\\d{2})\\s*[-~～至到—–]\\s*(\\d{1,2}:\\d{2})").find(value)
            ?: return null
        val startMinutes = parseClockMinutes(timeMatch.groupValues[1]) ?: return null
        val endMinutes = parseClockMinutes(timeMatch.groupValues[2]) ?: return null
        if (endMinutes <= startMinutes) return null
        val start = HebauSectionTimes.default.firstOrNull { parseClockMinutes(it.start) == startMinutes }?.section
        val end = HebauSectionTimes.default.firstOrNull { parseClockMinutes(it.end) == endMinutes }?.section
        return if (start != null && end != null && isValidSectionRange(start, end)) start..end else null
    }

    private fun parseClockMinutes(text: String): Int? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").find(text.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun isValidSectionRange(start: Int, end: Int): Boolean {
        return start in 1..MAX_TEXT_SECTION && end in start..MAX_TEXT_SECTION
    }

    private fun parseWeeks(text: String): List<Int> {
        val value = cleanText(text)
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-')
            .replace('~', '-')
            .replace('～', '-')
            .replace("至", "-")
            .replace("到", "-")
        val oddOnly = value.contains("单周") || Regex("[(（]?单[)）]?").containsMatchIn(value)
        val evenOnly = value.contains("双周") || Regex("[(（]?双[)）]?").containsMatchIn(value)
        val weeks = linkedSetOf<Int>()

        val patterns = listOf(
            Regex("(?:周次|周数|起止周|上课周次|上课周)[:：]?\\s*([0-9,，、\\-\\s]+)"),
            Regex("第?\\s*([0-9,，、\\-\\s]+)\\s*周"),
            Regex("([0-9,，、\\-\\s]+)[(（]\\s*周\\s*[)）]"),
        )
        patterns.forEach { pattern ->
            pattern.findAll(value).forEach { match ->
                addWeeks(match.groupValues[1], oddOnly, evenOnly, weeks)
            }
        }
        return weeks.sorted()
    }

    private fun addWeeks(fragment: String, oddOnly: Boolean, evenOnly: Boolean, weeks: MutableSet<Int>) {
        fragment
            .replace('，', ',')
            .replace('、', ',')
            .split(',')
            .map(::cleanText)
            .filter(String::isNotBlank)
            .forEach { token ->
                val range = Regex("^(\\d{1,2})\\s*-\\s*(\\d{1,2})$").find(token)
                if (range != null) {
                    val start = range.groupValues[1].toIntOrNull()
                    val end = range.groupValues[2].toIntOrNull()
                    if (start != null && end != null && start in 1..MAX_TEXT_WEEK && end in start..MAX_TEXT_WEEK) {
                        (start..end).forEach { week ->
                            if (weekMatchesParity(week, oddOnly, evenOnly)) weeks += week
                        }
                    }
                    return@forEach
                }

                val single = token.toIntOrNull()
                if (single != null && single in 1..MAX_TEXT_WEEK && weekMatchesParity(single, oddOnly, evenOnly)) {
                    weeks += single
                }
            }
    }

    private fun weekMatchesParity(week: Int, oddOnly: Boolean, evenOnly: Boolean): Boolean {
        return (!oddOnly || week % 2 == 1) && (!evenOnly || week % 2 == 0)
    }

    private fun parseCourseName(text: String, lines: List<String>): String? {
        parseLabeledValue(text, listOf("课程名称", "课程名", "课程"))?.let { return it.takeCourseName() }

        lines.firstNotNullOfOrNull { line ->
            if (lineHasTimingOrMetadata(line)) null else line.takeCourseName()
        }?.let { return it }

        val prefix = text.split(courseFieldBoundaryRegex(), limit = 2).firstOrNull().orEmpty()
        return prefix.takeCourseName()
    }

    private fun String.takeCourseName(): String? {
        val cleaned = cleanText(this)
            .removePrefix("课程名称")
            .removePrefix("课程名")
            .removePrefix("课程")
            .trim(':', '：', ' ', ',', '，', '、', ';', '；')
        if (cleaned.length !in 2..80) return null
        return cleaned
            .split(Regex("[,，、；;]"))
            .map(::cleanText)
            .firstOrNull { token ->
                token.length in 2..80 &&
                    !lineHasTimingOrMetadata(token) &&
                    !isLocationToken(token) &&
                    !isCourseClassToken(token)
            }
    }

    private fun parseTeacher(text: String, lines: List<String>, courseName: String): String? {
        parseLabeledValue(text, listOf("授课教师", "任课教师", "教师姓名", "主讲教师", "教师", "老师"))?.let { return it }
        return lines.firstOrNull { line ->
            line != courseName &&
                !lineHasTimingOrMetadata(line) &&
                !isLocationToken(line) &&
                !isCourseClassToken(line) &&
                isTeacherNameLine(line)
        }
    }

    private fun parsePosition(text: String, lines: List<String>): String? {
        parseLabeledValue(text, listOf("教学地点", "上课地点", "地点", "教室"))?.let { return it }
        return lines.lastOrNull(::isLocationToken)
    }

    private fun parseCourseClass(text: String): String? {
        return parseLabeledValue(text, listOf("教学班名称", "教学班", "课程班级", "行政班", "上课班级", "班级"))
    }

    private fun parseLabeledValue(text: String, labels: List<String>): String? {
        val boundary = fieldBoundaryPattern()
        labels.forEach { label ->
            val match = Regex("(?:^|[\\s,，、；;])$label\\s*[:：]?\\s*(.*?)(?=\\s*(?:$boundary)\\s*[:：]?|$)")
                .find(text)
            val parsed = match?.groupValues?.getOrNull(1)
                ?.trim(' ', ',', '，', '、', ';', '；', ':', '：')
                ?.takeIf(String::isNotBlank)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun fieldBoundaryPattern(): String {
        return "课程名称|课程名|授课教师|任课教师|教师姓名|主讲教师|教师|老师|" +
            "教学班名称|教学班|课程班级|行政班|上课班级|班级|" +
            "教学地点|上课地点|地点|教室|校区|周次|周数|起止周|上课周次|上课周|" +
            "星期|周[一二三四五六日天]|第?\\d{1,2}\\s*[-~～至到—–,，、]?\\s*\\d{0,2}\\s*(?:周|节)|\\d{1,2}:\\d{2}"
    }

    private fun courseFieldBoundaryRegex(): Regex {
        return Regex(fieldBoundaryPattern())
    }

    private fun lineHasTimingOrMetadata(line: String): Boolean {
        return parseDay(line) != null ||
            parseSections(line) != null ||
            parseWeeks(line).isNotEmpty() ||
            Regex("课程表|学年|学期|周次|节次|星期|教师|老师|地点|教室").containsMatchIn(line)
    }

    private fun isLocationToken(text: String): Boolean {
        return Regex("校区|教学楼|教室|实验|机房|楼|馆|室|厅|[A-Za-z]?\\d{2,}").containsMatchIn(text)
    }

    private fun isCourseClassToken(text: String): Boolean {
        return Regex("教学班|课程班级|行政班|上课班级|班级|专业|年级|[0-9]{2,}\\s*[-~～至到—–]\\s*[0-9]{2,}").containsMatchIn(text)
    }

    private fun isHeaderOrNoiseLine(line: String): Boolean {
        val compact = line.replace(" ", "")
        return Regex("^(课程名称|课程名|教师|老师|地点|教室|周次|节次|星期|时间)$").matches(compact)
    }

    private fun canExtendCourseBlock(line: String): Boolean {
        if (hasCourseTiming(line)) return false
        return isLocationToken(line) ||
            isCourseClassToken(line) ||
            parseLabeledValue(line, listOf("授课教师", "任课教师", "教师姓名", "主讲教师", "教师", "老师")) != null ||
            parseLabeledValue(line, listOf("教学地点", "上课地点", "地点", "教室")) != null ||
            isTeacherNameLine(line)
    }

    private fun isIgnoredCourseName(name: String): Boolean {
        val compact = name.replace(" ", "")
        return compact in setOf("课程表", "我的课表", "个人课表", "学期课表", "课程信息", "课程名称") ||
            isHeaderOrNoiseLine(name)
    }

    private fun isTeacherNameLine(line: String): Boolean {
        return teacherNameLineRegex.matches(line)
    }
}

private fun textCourseKey(course: HebauRawCourse): String {
    return listOf(
        course.name,
        course.teacher.orEmpty(),
        course.position.orEmpty(),
        course.courseClass.orEmpty(),
        course.day.toString(),
        course.startSection.toString(),
        course.endSection.toString(),
        course.weeks.distinct().sorted().joinToString(","),
    ).joinToString("|") { it.trim().lowercase() }
}
