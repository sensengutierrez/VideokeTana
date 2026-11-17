package com.videoketana.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Videoke app: buong-screen na app para sa phone, tablet at Smart TV (Android TV / Mi TV).
 * Iisang code ang app at ang website (nasa frontend/ ng repo), kaya sabay silang naa-update
 * at nagkakausap ang app at ang phone na naka-browser lang.
 *
 * Mga ginagawa ng app na wala sa browser:
 *  - walang address bar; buong screen; hindi namamatay ang screen habang bukas
 *  - lumalabas sa home screen ng Smart TV; diretso sa TV screen (host) kapag TV
 *  - humihingi ng pahintulot sa mic (phone bilang mic) at camera (pag-scan ng QR)
 *  - pumipili ng file (kanta, wallpaper) at full screen na video
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val SITE = "https://sensengutierrez.github.io/VideokeTana/"
        const val SITE_HOST = "sensengutierrez.github.io"
    }

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var isTv = false
    private lateinit var chrome: WebChromeClient

    // naghihintay na hiling ng web page para sa mic/camera
    private var pendingPermission: PermissionRequest? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val req = pendingPermission ?: return@registerForActivityResult
        pendingPermission = null
        val allowed = req.resources.filter { res ->
            when (res) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> result[Manifest.permission.RECORD_AUDIO] == true || has(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> result[Manifest.permission.CAMERA] == true || has(Manifest.permission.CAMERA)
                else -> false
            }
        }
        if (allowed.isEmpty()) req.deny() else req.grant(allowed.toTypedArray())
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTv = ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, !isTv)

        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#15171C")) }
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#15171C"))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        if (isTv) hideSystemBars()

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false   // para tumuloy-tuloy ang kanta
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            // para malaman ng web na nasa app (hal. itago ang "I-download ang app")
            userAgentString = "$userAgentString VideokeApp/${packageManager.getPackageInfo(packageName, 0).versionName}" + if (isTv) " VideokeTV" else ""
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                // sariling site at YouTube player: dito sa app; iba pa: sa browser
                if (url.host == SITE_HOST || url.host?.endsWith("youtube.com") == true || url.host?.endsWith("youtube-nocookie.com") == true) return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, url)); true } catch (e: ActivityNotFoundException) { false }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) view.loadUrl("file:///android_asset/offline.html")
            }
        }

        chrome = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val need = mutableListOf<String>()
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources && !has(Manifest.permission.RECORD_AUDIO)) need += Manifest.permission.RECORD_AUDIO
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources && !has(Manifest.permission.CAMERA)) need += Manifest.permission.CAMERA
                    if (need.isEmpty()) {
                        request.grant(request.resources.filter {
                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }.toTypedArray())
                    } else {
                        pendingPermission = request
                        askPermissions.launch(need.toTypedArray())
                    }
                }
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val types = params.acceptTypes.filter { it.isNotBlank() }.flatMap { t ->
                    when {
                        t.startsWith(".") -> listOf("*/*")          // hal. .lrc
                        else -> listOf(t)
                    }
                }.ifEmpty { listOf("*/*") }.distinct()
                return try { pickFiles.launch(types.toTypedArray()); true } catch (e: Exception) { fileCallback = null; false }
            }

            // full screen na video (hal. YouTube)
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view; customCallback = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                web.visibility = View.GONE
                hideSystemBars()
            }

            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customCallback?.onCustomViewHidden(); customCallback = null
                web.visibility = View.VISIBLE
                if (!isTv) showSystemBars()
            }
        }

        web.webChromeClient = chrome

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> chrome.onHideCustomView()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(startUrl(intent))
    }

    // Smart TV: diretso sa TV screen. Link (hal. QR ng room): ang link mismo. Iba: home.
    private fun startUrl(i: Intent?): String {
        val data = i?.data
        if (data != null && data.host == SITE_HOST) return data.toString()
        return if (isTv) SITE + "host/" else SITE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        if (data != null && data.host == SITE_HOST) web.loadUrl(data.toString())
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { super.onPause(); web.onPause() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
