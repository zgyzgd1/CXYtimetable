package com.example.timetable.jw

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwBridgeTest {
    @Test
    fun postCoursesAcceptsOneRequestedResultFromAllowedUrl() {
        val courses = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val bridge = bridge(courses, errors)
        bridge.currentUrl = JwImportContract.PRIMARY_ENTRY_URL
        bridge.markImportRequested()

        bridge.postCourses("""{"courses":[]}""")
        shadowOf(Looper.getMainLooper()).idle()
        bridge.postCourses("""{"courses":[{"name":"again"}]}""")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("""{"courses":[]}"""), courses)
        assertEquals(listOf("请先点击开始导入"), errors)
    }

    @Test
    fun postCoursesRejectsUnrequestedResult() {
        val courses = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val bridge = bridge(courses, errors)
        bridge.currentUrl = JwImportContract.ALTERNATE_ENTRY_URL

        bridge.postCourses("""{"courses":[]}""")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), courses)
        assertEquals(listOf("请先点击开始导入"), errors)
    }

    @Test
    fun postCoursesRejectsNonAllowlistedPageSilently() {
        val courses = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val bridge = bridge(courses, errors)
        bridge.currentUrl = "https://example.com"
        bridge.markImportRequested()

        bridge.postCourses("""{"courses":[]}""")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), courses)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun postCoursesRejectsOversizedJson() {
        val courses = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val bridge = bridge(courses, errors)
        bridge.currentUrl = JwImportContract.PRIMARY_ENTRY_URL
        bridge.markImportRequested()
        val oversizedJson = "x".repeat(JwImportContract.MAX_BRIDGE_JSON_BYTES + 1)

        bridge.postCourses(oversizedJson)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), courses)
        assertEquals(listOf("导入数据过大"), errors)
    }

    @Test
    fun postCoursesRejectsResultAfterAllowedPageNavigation() {
        val courses = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val bridge = bridge(courses, errors)
        bridge.currentUrl = JwImportContract.PRIMARY_ENTRY_URL
        bridge.markImportRequested()
        bridge.currentUrl = JwImportContract.ALTERNATE_ENTRY_URL

        bridge.postCourses("""{"courses":[]}""")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), courses)
        assertEquals(listOf("请先点击开始导入"), errors)
    }

    @Test
    fun postErrorConsumesPendingImportRequest() {
        val courses = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val bridge = bridge(courses, errors)
        bridge.currentUrl = JwImportContract.PRIMARY_ENTRY_URL
        bridge.markImportRequested()

        bridge.postError("script failed")
        shadowOf(Looper.getMainLooper()).idle()
        bridge.postCourses("""{"courses":[]}""")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), courses)
        assertEquals(listOf("script failed", "请先点击开始导入"), errors)
    }

    private fun bridge(
        courses: MutableList<String>,
        errors: MutableList<String>,
    ): JwBridge {
        return JwBridge(
            onCoursesJson = courses::add,
            onError = errors::add,
            onLog = {},
            messages = JwBridgeMessages(
                importNotRequested = "请先点击开始导入",
                payloadTooLarge = "导入数据过大",
            ),
        )
    }
}
