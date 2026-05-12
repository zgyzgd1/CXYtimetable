package com.example.timetable.jw

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface

private const val IMPORT_REQUEST_TIMEOUT_MS = 30_000L

data class JwBridgeMessages(
    val importNotRequested: String,
    val payloadTooLarge: String,
)

class JwBridge(
    private val onCoursesJson: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onLog: (String) -> Unit,
    private val messages: JwBridgeMessages,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var currentUrl: String? = null
        set(value) {
            synchronized(this) {
                if (field != value) {
                    currentPageToken += 1
                }
                field = value
                if (!JwImportContract.isAllowedUrl(value) || requestedPageToken != currentPageToken) {
                    clearImportRequestLocked()
                }
            }
        }

    @Volatile
    private var waitingForImportResult: Boolean = false
    private var requestedPageToken: Long = -1L
    private var currentPageToken: Long = 0L
    private var importRequestExpiresAtMillis: Long = 0L

    fun markImportRequested() {
        synchronized(this) {
            if (!JwImportContract.isAllowedUrl(currentUrl)) {
                clearImportRequestLocked()
                return
            }
            waitingForImportResult = true
            requestedPageToken = currentPageToken
            importRequestExpiresAtMillis = SystemClock.elapsedRealtime() + IMPORT_REQUEST_TIMEOUT_MS
        }
    }

    fun cancelImportRequest() {
        synchronized(this) {
            clearImportRequestLocked()
        }
    }

    @JavascriptInterface
    fun postCourses(json: String) {
        if (!JwImportContract.isAllowedUrl(currentUrl)) {
            cancelImportRequest()
            return
        }
        if (!consumeImportRequest()) {
            postError(messages.importNotRequested)
            return
        }
        if (json.toByteArray(Charsets.UTF_8).size > JwImportContract.MAX_BRIDGE_JSON_BYTES) {
            postError(messages.payloadTooLarge)
            return
        }
        mainHandler.post { onCoursesJson(json) }
    }

    @JavascriptInterface
    fun postError(message: String) {
        if (!JwImportContract.isAllowedUrl(currentUrl)) {
            cancelImportRequest()
            return
        }
        cancelImportRequest()
        mainHandler.post { onError(message.take(300)) }
    }

    @JavascriptInterface
    fun postLog(message: String) {
        if (!JwImportContract.isAllowedUrl(currentUrl)) return
        mainHandler.post { onLog(message.take(300)) }
    }

    private fun consumeImportRequest(): Boolean {
        return synchronized(this) {
            if (!waitingForImportResult || requestedPageToken != currentPageToken) {
                clearImportRequestLocked()
                return@synchronized false
            }
            if (SystemClock.elapsedRealtime() > importRequestExpiresAtMillis) {
                clearImportRequestLocked()
                return@synchronized false
            }
            clearImportRequestLocked()
            true
        }
    }

    private fun clearImportRequestLocked() {
        waitingForImportResult = false
        requestedPageToken = -1L
        importRequestExpiresAtMillis = 0L
    }
}
