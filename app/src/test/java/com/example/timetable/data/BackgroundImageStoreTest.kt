package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundImageStoreTest {
    @Test
    fun normalizeStoredUriReturnsNullForBlankValue() {
        assertNull(BackgroundImageStore.normalizeStoredUri(null))
        assertNull(BackgroundImageStore.normalizeStoredUri(""))
        assertNull(BackgroundImageStore.normalizeStoredUri("   "))
    }

    @Test
    fun normalizeStoredUriTrimsMeaningfulValue() {
        assertEquals(
            "content://images/custom-background",
            BackgroundImageStore.normalizeStoredUri("  content://images/custom-background  "),
        )
    }
}
