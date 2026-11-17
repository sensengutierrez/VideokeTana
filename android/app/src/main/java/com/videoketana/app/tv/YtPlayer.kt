package com.videoketana.app.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.videoketana.app.data.Config

/**
 * YouTube player ng TV screen. Binubuksan ang videoketana.tigomenote.com/yt.html para may tamang origin
 * (kung wala, tinatanggihan ng YouTube: "This video is unavailable", error 152).
 */
class YtController {
    var web: WebView? = null
    var onReady: () -> Unit = {}
    var onState: (Int) -> Unit = {}          // 0 = tapos, 1 = tumutugtog, 2 = naka-pause
    var onError: (String) -> Unit = {}       // YouTube code (hal. 150) o "noapi" / "page"
    private val main = Handler(Looper.getMainLooper())

    private fun js(s: String) { main.post { web?.evaluateJavascript(s, null) } }
    fun load(id: String, volume: Int) = js("load('${id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}', $volume)")
    fun pause() = js("cmd('pause')")
    fun play() = js("cmd('play')")
    fun seek(sec: Int) = js("cmd('seek', $sec)")
    fun volume(v: Int) = js("cmd('vol', $v)")
    fun stop() = js("cmd('stop')")
    fun rate(r: Float) = js("cmd('rate', ${"%.2f".format(java.util.Locale.US, r)})")

    inner class Bridge {
        @JavascriptInterface
        fun event(name: String, data: String) {
            main.post {
                when (name) {
                    "ready" -> onReady()
                    "state" -> onState(data.toIntOrNull() ?: -1)
                    "error" -> onError(data)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun create(c: Context): WebView = WebView(c).apply {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) main.post { onError("page") }
            }
        }
        webChromeClient = WebChromeClient()
        addJavascriptInterface(Bridge(), "AndroidYT")
        loadUrl(Config.SITE + "yt.html?v=4")
        web = this
    }

    /** Bagong load ng player page (kapag pumalya ang una). */
    fun reload() { main.post { web?.loadUrl(Config.SITE + "yt.html?v=4&t=" + System.currentTimeMillis()) } }

    fun destroy() { web?.destroy(); web = null }
}
