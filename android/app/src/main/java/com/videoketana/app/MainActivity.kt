package com.videoketana.app

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.videoketana.app.data.Prefs
import com.videoketana.app.data.roomFromScan
import com.videoketana.app.remote.RemoteScreen
import com.videoketana.app.tv.TvScreen
import com.videoketana.app.ui.HomeScreen
import com.videoketana.app.ui.Intro
import com.videoketana.app.ui.SettingsScreen
import com.videoketana.app.ui.SongbookScreen
import com.videoketana.app.ui.Synth
import com.videoketana.app.ui.VkTheme
import com.videoketana.app.ui.vk

/** Mga screen ng app. */
sealed class Screen {
    object Home : Screen()
    object Tv : Screen()
    object Songbook : Screen()
    object Settings : Screen()
    object Library : Screen()
    data class Remote(val room: String) : Screen()
}

class MainActivity : ComponentActivity() {
    // room mula sa link/QR na nagbukas sa app
    private var linkRoom by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val isTv = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        if (isTv) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        linkRoom = intent?.dataString?.let { roomFromScan(it) }
        Synth.warm("beep", "applause", "fanfare")

        setContent {
            VkTheme {
                App(isTv = isTv, linkRoom = linkRoom, consumeLink = { linkRoom = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { roomFromScan(it) }?.let { linkRoom = it }
    }
}

@Composable
fun App(isTv: Boolean, linkRoom: String?, consumeLink: () -> Unit) {
    var intro by rememberSaveable { mutableStateOf(true) }
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(if (isTv) Screen.Tv else Screen.Home) }
    androidx.compose.runtime.LaunchedEffect(linkRoom) { if (linkRoom != null) { screen = Screen.Remote(linkRoom); consumeLink() } }

    val go: (Screen) -> Unit = { screen = it }
    BackHandler(enabled = screen != Screen.Home && !(isTv && screen == Screen.Tv)) { screen = Screen.Home }

    // TV screen: transparent para makita ang YouTube video na nasa likod (tingnan ang Tv.kt)
    Box(Modifier.fillMaxSize().background(if (screen == Screen.Tv) androidx.compose.ui.graphics.Color.Transparent else if (Prefs.dark) com.videoketana.app.ui.DarkVk.bg else com.videoketana.app.ui.LightVk.bg)) {
        // sariling wallpaper sa likod ng lahat (maliban sa TV screen)
        if (Prefs.wallpaper.isNotEmpty() && screen != Screen.Tv) {
            val bmp = androidx.compose.runtime.remember(Prefs.wallpaper, Prefs.wallVersion) {
                try { android.graphics.BitmapFactory.decodeFile(Prefs.wallpaper)?.asImageBitmap() } catch (e: Exception) { null }
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.fillMaxSize().background((if (Prefs.dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White).copy(alpha = Prefs.wallDim / 100f)))
            }
        }
        val pad = if (screen == Screen.Tv) Modifier else Modifier.safeDrawingPadding()
        Box(pad.fillMaxSize()) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(go)
                Screen.Tv -> TvScreen(onClose = { screen = Screen.Home }, isTv = isTv)
                Screen.Songbook -> SongbookScreen(go)
                Screen.Settings -> SettingsScreen(go)
                Screen.Library -> com.videoketana.app.ui.LibraryScreen(go)
                is Screen.Remote -> RemoteScreen(s.room, go)
            }
        }
        if (intro) Intro(onDone = { intro = false })
    }
}

/** Para matandaan ang screen kapag umikot ang phone. */
val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
    save = { s -> when (s) { Screen.Home -> "home"; Screen.Tv -> "tv"; Screen.Songbook -> "book"; Screen.Settings -> "set"; Screen.Library -> "lib"; is Screen.Remote -> "r:" + s.room } },
    restore = { v -> when { v == "tv" -> Screen.Tv; v == "book" -> Screen.Songbook; v == "set" -> Screen.Settings; v == "lib" -> Screen.Library; v.startsWith("r:") -> Screen.Remote(v.drop(2)); else -> Screen.Home } }
)
