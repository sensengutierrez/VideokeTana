package com.videoketana.app.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
    var onError: (Int) -> Unit = {}
    private val main = Handler(Looper.getMainLooper())

    private fun js(s: String) { main.post { web?.evaluateJavascript(s, null) } }
    fun load(id: String, volume: Int) = js("load('${id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}', $volume)")
    fun pause() = js("cmd('pause')")
    fun play() = js("cmd('play')")
    fun seek(sec: Int) = js("cmd('seek', $sec)")
    fun volume(v: Int) = js("cmd('vol', $v)")
    fun stop() = js("cmd('stop')")

    inner class Bridge {
        @JavascriptInterface
        fun event(name: String, data: String) {
            main.post {
                when (name) {
                    "ready" -> onReady()
                    "state" -> onState(data.toIntOrNull() ?: -1)
                    "error" -> onError(data.toIntOrNull() ?: 0)
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
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        addJavascriptInterface(Bridge(), "AndroidYT")
        loadUrl(Config.SITE + "yt.html")
        web = this
    }

    fun destroy() { web?.destroy(); web = null }
}
