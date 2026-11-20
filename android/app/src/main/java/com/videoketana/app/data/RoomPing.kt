package com.videoketana.app.data

import org.json.JSONObject

/** Mabilisang pagpapadala ng utos sa TV ng isang room (hal. galing Settings), tapos isasara. */
object RoomPing {
    fun cmd(room: String, action: String, value: Any?) {
        if (!validRoom(room)) return
        var sent = false
        lateinit var rt: Realtime
        rt = Realtime(room, onBroadcast = { _, _ -> }, onStatus = { ok ->
            if (ok && !sent) {
                sent = true
                rt.broadcast("cmd", JSONObject().put("action", action).put("value", value))
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ rt.close() }, 1500)
            }
        })
        rt.connect()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (!sent) rt.close() }, 8000)
    }
}
