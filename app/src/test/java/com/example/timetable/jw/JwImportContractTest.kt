package com.example.timetable.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwImportContractTest {
    @Test
    fun isAllowedUrlAcceptsHebauHttpAndHttpsUrls() {
        assertTrue(JwImportContract.isAllowedUrl("http://urp.hebau.edu.cn:1009/jwapp/sys/homeapp/index.do"))
        assertTrue(JwImportContract.isAllowedUrl("https://urp1.hebau.edu.cn:1010/xsxk/profile/index.html"))
    }

    @Test
    fun isAllowedUrlRejectsSubdomainsAndNonWebSchemes() {
        assertFalse(JwImportContract.isAllowedUrl("http://evil.urp.hebau.edu.cn:1009/course"))
        assertFalse(JwImportContract.isAllowedUrl("javascript:alert(1)"))
        assertFalse(JwImportContract.isAllowedUrl("file:///android_asset/jw/hebau_urp.js"))
        assertFalse(JwImportContract.isAllowedUrl(null))
    }

    @Test
    fun displayHostReturnsParsedHostOnly() {
        assertEquals("urp.hebau.edu.cn", JwImportContract.displayHost("http://urp.hebau.edu.cn:1009/path"))
        assertEquals("", JwImportContract.displayHost("not a url"))
    }
}
