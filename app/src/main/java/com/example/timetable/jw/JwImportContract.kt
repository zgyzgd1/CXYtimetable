package com.example.timetable.jw

import java.net.URI
import java.util.Locale

object JwImportContract {
    const val BRIDGE_NAME = "JwBridge"
    const val PRIMARY_ENTRY_URL = "http://urp.hebau.edu.cn:1009/jwapp/sys/homeapp/index.do"
    const val ALTERNATE_ENTRY_URL = "http://urp1.hebau.edu.cn:1010/xsxk/profile/index.html"
    const val MAX_BRIDGE_JSON_BYTES = 512 * 1024

    val allowedHosts = setOf(
        "urp.hebau.edu.cn",
        "urp1.hebau.edu.cn",
    )

    fun isAllowedUrl(url: String?): Boolean {
        val uri = parseUri(url) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        return scheme in setOf("http", "https") && host in allowedHosts
    }

    fun displayHost(url: String?): String {
        return parseUri(url)?.host.orEmpty()
    }

    private fun parseUri(url: String?): URI? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url) }.getOrNull()
    }
}
