package com.example.timetable.ui

import com.example.timetable.R
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleViewModelImportTest {

    @Test
    fun readLimitedUtf8TextReturnsFullTextWithinLimit() {
        val payload = "BEGIN:VCALENDAR\r\nEND:VCALENDAR"

        val result = readLimitedUtf8Text(
            inputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)),
            maxBytes = payload.toByteArray(Charsets.UTF_8).size,
        )

        assertEquals(payload, result)
    }

    @Test
    fun readLimitedUtf8TextRemovesUtf8Bom() {
        val payload = "\uFEFFBEGIN:VCALENDAR"

        val result = readLimitedUtf8Text(
            inputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)),
            maxBytes = payload.toByteArray(Charsets.UTF_8).size,
        )

        assertEquals("BEGIN:VCALENDAR", result)
    }

    @Test
    fun readLimitedUtf8TextRejectsOversizedPayload() {
        val payload = "0123456789ABCDEF"

        val error = assertThrows(IOException::class.java) {
            readLimitedUtf8Text(
                inputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)),
                maxBytes = 8,
            )
        }

        assertTrue(error.message?.contains("8 bytes") == true)
    }

    @Test
    fun importSizeLimitMessageUsesMegabyteLabelForDefaultLimit() {
        assertEquals(
            "ICS file exceeds the import limit of 1 MB.",
            importSizeLimitMessage(),
        )
    }

    @Test
    fun importReadFailureMessageResIdReportsMissingOrDeniedFiles() {
        assertEquals(
            R.string.vm_read_file_access_denied,
            importReadFailureMessageResId(FileNotFoundException("missing.ics")),
        )
        assertEquals(
            R.string.vm_read_file_access_denied,
            importReadFailureMessageResId(SecurityException("permission denied")),
        )
    }

    @Test
    fun importReadFailureMessageResIdReportsOversizedFiles() {
        assertEquals(
            R.string.vm_read_file_too_large,
            importReadFailureMessageResId(IOException(importSizeLimitMessage())),
        )
    }

    @Test
    fun importReadFailureMessageResIdFallsBackToGenericReadFailure() {
        assertEquals(
            R.string.vm_read_file_failed,
            importReadFailureMessageResId(IOException("disk read failed")),
        )
        assertEquals(
            R.string.vm_read_file_failed,
            importReadFailureMessageResId(IllegalStateException("bad stream")),
        )
    }
}
