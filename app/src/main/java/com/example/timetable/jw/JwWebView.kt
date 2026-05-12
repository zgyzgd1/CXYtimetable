package com.example.timetable.jw

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JwWebView(
    bridge: JwBridge,
    url: String,
    webMode: JwWebMode,
    onWebViewCreated: (WebView) -> Unit,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onProgressChange: (Int) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val container = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            val mainWebView = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            configureWebView(
                webView = mainWebView,
                bridge = bridge,
                webMode = webMode,
                onUrlChange = onUrlChange,
                onTitleChange = onTitleChange,
                onProgressChange = onProgressChange,
                onError = onError,
                onActiveWebViewChange = onWebViewCreated,
                onCreatePopup = { popupWebView ->
                    container.addView(popupWebView)
                    onWebViewCreated(popupWebView)
                },
                onClosePopup = { popupWebView ->
                    container.removeView(popupWebView)
                    popupWebView.destroy()
                    onWebViewCreated(mainWebView)
                },
            )
            val defaultUserAgent = mainWebView.settings.userAgentString.orEmpty()
            mainWebView.settings.configureForJwImport(webMode, defaultUserAgent)
            mainWebView.tag = JwWebViewState(
                defaultUserAgent = defaultUserAgent,
                webMode = webMode,
                requestedUrl = url,
            )
            container.addView(mainWebView)
            onWebViewCreated(mainWebView)
            mainWebView.loadUrl(url)
            container
        },
        update = { container ->
            val mainWebView = container.getChildAt(0) as? WebView ?: return@AndroidView
            val state = mainWebView.tag as? JwWebViewState
            val defaultUserAgent = state?.defaultUserAgent ?: mainWebView.settings.userAgentString.orEmpty()
            val requestChanged = state?.requestedUrl != url
            val modeChanged = state?.webMode != webMode
            if (modeChanged) {
                mainWebView.settings.configureForJwImport(webMode, defaultUserAgent)
            }
            mainWebView.tag = JwWebViewState(
                defaultUserAgent = defaultUserAgent,
                webMode = webMode,
                requestedUrl = url,
            )
            if (requestChanged) {
                bridge.currentUrl = url
                mainWebView.loadUrl(url)
            } else if (modeChanged) {
                mainWebView.reload()
            }
        },
    )
}

private fun configureWebView(
    webView: WebView,
    bridge: JwBridge,
    webMode: JwWebMode,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onProgressChange: (Int) -> Unit,
    onError: (String) -> Unit,
    onActiveWebViewChange: (WebView) -> Unit,
    onCreatePopup: (WebView) -> Unit,
    onClosePopup: (WebView) -> Unit,
) {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    webView.setBackgroundColor(Color.WHITE)
    webView.addJavascriptInterface(bridge, JwImportContract.BRIDGE_NAME)
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            return !request.url.isWebNavigation()
        }

        override fun onPageStarted(view: WebView, pageUrl: String, favicon: Bitmap?) {
            bridge.currentUrl = pageUrl
            onUrlChange(pageUrl)
            onActiveWebViewChange(view)
        }

        override fun onPageFinished(view: WebView, pageUrl: String) {
            bridge.currentUrl = pageUrl
            onUrlChange(pageUrl)
            onTitleChange(view.title.orEmpty())
            onActiveWebViewChange(view)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                onError(error.description?.toString().orEmpty())
            }
        }
    }
    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            onProgressChange(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            onTitleChange(title.orEmpty())
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val popupWebView = WebView(view.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                val popupDefaultUserAgent = view.settings.userAgentString.orEmpty()
                settings.configureForJwImport(webMode, popupDefaultUserAgent)
            }
            configureWebView(
                webView = popupWebView,
                bridge = bridge,
                webMode = webMode,
                onUrlChange = onUrlChange,
                onTitleChange = onTitleChange,
                onProgressChange = onProgressChange,
                onError = onError,
                onActiveWebViewChange = onActiveWebViewChange,
                onCreatePopup = onCreatePopup,
                onClosePopup = onClosePopup,
            )
            onCreatePopup(popupWebView)
            transport.webView = popupWebView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            onClosePopup(window)
        }
    }
}

private data class JwWebViewState(
    val defaultUserAgent: String,
    val webMode: JwWebMode,
    val requestedUrl: String,
)

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
private fun WebSettings.configureForJwImport(
    webMode: JwWebMode,
    defaultUserAgent: String,
) {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    loadsImagesAutomatically = true
    javaScriptCanOpenWindowsAutomatically = true
    setSupportMultipleWindows(true)
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    safeBrowsingEnabled = true
    cacheMode = WebSettings.LOAD_DEFAULT
    textZoom = 100
    userAgentString = JwUserAgent.forMode(webMode, defaultUserAgent)
    useWideViewPort = webMode == JwWebMode.DESKTOP
    loadWithOverviewMode = webMode == JwWebMode.DESKTOP
    setSupportZoom(webMode == JwWebMode.DESKTOP)
    builtInZoomControls = webMode == JwWebMode.DESKTOP
    displayZoomControls = false
}

private fun Uri.isWebNavigation(): Boolean {
    return scheme == "http" || scheme == "https"
}
