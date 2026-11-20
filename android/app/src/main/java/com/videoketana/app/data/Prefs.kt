package com.videoketana.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Mga setting ng app sa device na ito. */
object Prefs {
    private lateinit var sp: SharedPreferences

    // nakikita ng Compose: kapag nagbago, nagre-render ulit ang screen
    var lang by mutableStateOf("fil")
        private set
    var dark by mutableStateOf(true)
        private set
    // wallpaper at transparency (nakikita agad sa lahat ng screen)
    var wallpaper by mutableStateOf("")
        private set
    var wallVersion by mutableStateOf(0)
        private set
    var glass by mutableStateOf(0)            // 0 = buo, 85 = pinaka see-through
        private set
    var wallDim by mutableStateOf(45)
        private set
    // estilo ng numero at orasan: dot (LCD na tuldok), seg (7-segment), bold, thin
    var displayFont by mutableStateOf("dot")
        private set

    fun init(c: Context) {
        sp = c.getSharedPreferences("videoketana", Context.MODE_PRIVATE)
        lang = sp.getString("lang", "fil") ?: "fil"
        dark = sp.getBoolean("dark", true)
        wallpaper = sp.getString("wallpaper", "") ?: ""
        glass = sp.getInt("glass", 0)
        wallDim = sp.getInt("wallDim", 45)
        displayFont = sp.getString("displayFont", "dot") ?: "dot"
    }
    fun changeDisplayFont(v: String) { displayFont = v; sp.edit().putString("displayFont", v).apply() }
    var tvLandscape: Boolean
        get() = sp.getBoolean("tvLandscape", true)
        set(v) = sp.edit().putBoolean("tvLandscape", v).apply()
    fun changeWallpaper(path: String) { wallpaper = path; wallVersion++; sp.edit().putString("wallpaper", path).apply() }
    fun changeGlass(v: Int) { glass = v; sp.edit().putInt("glass", v).apply() }
    fun changeWallDim(v: Int) { wallDim = v; sp.edit().putInt("wallDim", v).apply() }

    // StandBy: dim na orasan sa phone kapag hindi kumakanta (parang StandBy ng iPhone, pero may ibang kulay)
    var standby: Boolean
        get() = sp.getBoolean("standby", true)
        set(v) = sp.edit().putBoolean("standby", v).apply()
    var standbyAfter: Int
        get() = sp.getInt("standbyAfter", 30)
        set(v) = sp.edit().putInt("standbyAfter", v).apply()
    var standbyColor: Long
        get() = sp.getLong("standbyColor", 0xFF62E3D2)
        set(v) = sp.edit().putLong("standbyColor", v).apply()

    fun changeLang(v: String) { lang = v; sp.edit().putString("lang", v).apply() }
    fun changeDark(v: Boolean) { dark = v; sp.edit().putBoolean("dark", v).apply() }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v.take(40)).apply()
    var lastRoom: String
        get() = sp.getString("lastRoom", "") ?: ""
        set(v) = sp.edit().putString("lastRoom", v).apply()
    var hostRoom: String
        get() = sp.getString("hostRoom", "") ?: ""
        set(v) = sp.edit().putString("hostRoom", v).apply()
    var sfx: Boolean
        get() = sp.getBoolean("sfx", true)
        set(v) = sp.edit().putBoolean("sfx", v).apply()
    var sfxVolume: Int
        get() = sp.getInt("sfxVolume", 90)
        set(v) = sp.edit().putInt("sfxVolume", v).apply()
    var keyBeep: Boolean
        get() = sp.getBoolean("keyBeep", true)
        set(v) = sp.edit().putBoolean("keyBeep", v).apply()
    var numVoice: Boolean                       // boses na nagbibilang sa pag-type ng numero
        get() = sp.getBoolean("numVoice", true)
        set(v) = sp.edit().putBoolean("numVoice", v).apply()
    var scoreOn: Boolean
        get() = sp.getBoolean("scoreOn", true)
        set(v) = sp.edit().putBoolean("scoreOn", v).apply()
    var favorites: Set<Int>
        get() = (sp.getString("favs", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = sp.edit().putString("favs", v.joinToString(",")).apply()
    fun int(k: String, def: Int = 0) = sp.getInt(k, def)
    fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun str(k: String) = sp.getString(k, "") ?: ""
    fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun long(k: String) = sp.getLong(k, 0L)
    fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()
    /** Interlude (musika habang naghihintay ng kanta) at background ng TV. */
    var interlude: Boolean
        get() = sp.getBoolean("interlude", true)
        set(v) = sp.edit().putBoolean("interlude", v).apply()
    var interludeVol: Int
        get() = sp.getInt("interludeVol", 40)
        set(v) = sp.edit().putInt("interludeVol", v).apply()
    var bgScene: String                         // "cycle" o isa sa BG_SCENES
        get() = sp.getString("bgScene", "cycle") ?: "cycle"
        set(v) = sp.edit().putString("bgScene", v).apply()
    var bgMode: String                          // "auto" = device + online, "device", "online", "anim" = sariling animation lang
        get() = sp.getString("bgMode", "auto") ?: "auto"
        set(v) = sp.edit().putString("bgMode", v).apply()
    var skipUpdate: Int
        get() = sp.getInt("skipUpdate", 0)
        set(v) = sp.edit().putInt("skipUpdate", v).apply()
}

private val ROOM_RE = Regex("^[A-Z0-9]{4,8}$")
fun cleanRoom(s: String) = s.trim().uppercase().filter { it.isLetterOrDigit() }
fun validRoom(s: String) = ROOM_RE.matches(s)

/** Room code na madaling basahin (walang 0/O/1/I/L). */
fun makeRoomCode(): String {
    val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    return (1..4).map { chars.random() }.joinToString("")
}

/** Kunin ang room mula sa QR (hal. https://videoketana.tigomenote.com/remote/?room=ABCD) o sa mismong code. */
fun roomFromScan(text: String): String? {
    val m = Regex("[?&]room=([A-Za-z0-9]{4,8})").find(text)
    if (m != null) return m.groupValues[1].uppercase()
    val c = cleanRoom(text)
    return if (validRoom(c)) c else null
}
