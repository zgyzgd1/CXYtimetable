package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.countConflictPairs
import com.example.timetable.data.countConflictPairsBetween
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal const val MAX_ICS_IMPORT_BYTES = 1024 * 1024

internal fun countImportConflicts(
    validEntries: List<TimetableEntry>,
    existingEntries: List<TimetableEntry>,
): Int {
    if (validEntries.isEmpty()) return 0

    val internalConflicts = countConflictPairs(validEntries)
    val existingConflicts = countConflictPairsBetween(validEntries, existingEntries)
    return internalConflicts + existingConflicts
}

internal fun readLimitedUtf8Text(inputStream: InputStream, maxBytes: Int): String {
    require(maxBytes > 0)

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    var totalRead = 0

    while (true) {
        val read = inputStream.read(buffer)
        if (read < 0) break
        if (read == 0) continue

        totalRead += read
        if (totalRead > maxBytes) {
            throw IOException(importSizeLimitMessage(maxBytes))
        }
        output.write(buffer, 0, read)
    }

    return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
}

internal fun importSizeLimitMessage(maxBytes: Int = MAX_ICS_IMPORT_BYTES): String {
    return "ICS file exceeds the import limit of ${formatImportSize(maxBytes)}."
}

private fun formatImportSize(maxBytes: Int): String {
    val kibibyte = 1024
    val mebibyte = kibibyte * kibibyte
    return when {
        maxBytes >= mebibyte && maxBytes % mebibyte == 0 -> "${maxBytes / mebibyte} MB"
        maxBytes >= kibibyte && maxBytes % kibibyte == 0 -> "${maxBytes / kibibyte} KB"
        else -> "$maxBytes bytes"
    }
}

/**
 * Import preview: parsed import results that have not yet been written to the database.
 */
data class ImportPreview(
    val validEntries: List<TimetableEntry>,
    val invalidCount: Int,
    val conflictCount: Int,
    val totalParsed: Int,
    val truncated: Boolean = false,
)
