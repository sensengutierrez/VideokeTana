package com.videoketana.app.data

import android.os.Handler
import android.os.Looper
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kliyente ng Supabase Realtime (Phoenix websocket), kapareho ng channel ng web: "room-XXXX".
 *  - broadcast: cmd, state, hello, score, rtc, host
 *  - postgres_changes sa table na queue
 * Ang lahat ng callback ay tinatawag sa main thread.
 */
class Realtime(
    private val room: String,
    private val onBroadcast: (event: String, payload: JSONObject) -> Unit,
    private val onQueueChange: () -> Unit = {},
    private val onStatus: (connected: Boolean) -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var ref = 0
    private var joinRef = ""
    private var closed = false
    private var connected = false
    private val topic get() = "realtime:room-$room"
    private val heartbeat = object : Runnable {
        override fun run() {
            send(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()).put("ref", nextRef()))
            main.postDelayed(this, 25_000)
        }
    }

    private fun nextRef() = (++ref).toString()

    fun connect() {
        closed = false
        val url = Config.SUPABASE_URL.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=${Config.SUPABASE_KEY}&vsn=1.0.0"
        ws = Supa.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                joinRef = nextRef()
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("self", false).put("ack", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", JSONArray().put(JSONObject().put("event", "*").put("schema", "public").put("table", "queue")))
                    .put("private", false)
                send(JSONObject().put("topic", topic).put("event", "phx_join")
                    .put("payload", JSONObject().put("config", config))
                    .put("ref", joinRef).put("join_ref", joinRef))
                main.removeCallbacks(heartbeat); main.postDelayed(heartbeat, 25_000)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = try { JSONObject(text) } catch (e: Exception) { return }
                val event = m.optString("event")
                val payload = m.optJSONObject("payload") ?: JSONObject()
                main.post {
                    when (event) {
                        "phx_reply" -> if (m.optString("ref") == joinRef) {
                            val ok = payload.optString("status") == "ok"
                            connected = ok; onStatus(ok)
                        }
                        "broadcast" -> onBroadcast(payload.optString("event"), payload.optJSONObject("payload") ?: JSONObject())
                        "postgres_changes" -> onQueueChange()
                        "phx_error", "phx_close" -> { connected = false; onStatus(false) }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = retry()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = retry()
        })
    }

    private fun retry() {
        main.post {
            connected = false; onStatus(false)
            main.removeCallbacks(heartbeat)
            if (!closed) main.postDelayed({ if (!closed) connect() }, 3000)
        }
    }

    private fun send(o: JSONObject) { ws?.send(o.toString()) }

    /** Magpadala ng broadcast sa room (hal. "cmd", "state"). */
    fun broadcast(event: String, payload: JSONObject) {
        send(JSONObject().put("topic", topic).put("event", "broadcast")
            .put("payload", JSONObject().put("type", "broadcast").put("event", event).put("payload", payload))
            .put("ref", nextRef()).put("join_ref", joinRef))
    }

    val isConnected get() = connected

    fun close() {
        closed = true
        main.removeCallbacks(heartbeat)
        try { ws?.close(1000, "bye") } catch (e: Exception) {}
        ws = null
    }
}
