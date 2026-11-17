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

    fun init(c: Context) {
        sp = c.getSharedPreferences("videoketana", Context.MODE_PRIVATE)
        lang = sp.getString("lang", "fil") ?: "fil"
        dark = sp.getBoolean("dark", true)
    }

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
    var scoreOn: Boolean
        get() = sp.getBoolean("scoreOn", true)
        set(v) = sp.edit().putBoolean("scoreOn", v).apply()
    var favorites: Set<Int>
        get() = (sp.getString("favs", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = sp.edit().putString("favs", v.joinToString(",")).apply()
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
