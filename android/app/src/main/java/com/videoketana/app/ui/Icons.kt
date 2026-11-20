package com.videoketana.app.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Mga line icon ng VideokeTana (kapareho ng nasa web: frontend/assets/js/icons.js). */
object VkIcons {
    private val paths: Map<String, List<String>> = mapOf(
        "mic" to listOf("M12.0 2.5H12.0A3.0 3.0 0 0 1 15.0 5.5V11.5A3.0 3.0 0 0 1 12.0 14.5H12.0A3.0 3.0 0 0 1 9.0 11.5V5.5A3.0 3.0 0 0 1 12.0 2.5Z", "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21.5M8.5 21.5h7"),
        "tv" to listOf("M4.5 5.0H19.5A2.0 2.0 0 0 1 21.5 7.0V16.0A2.0 2.0 0 0 1 19.5 18.0H4.5A2.0 2.0 0 0 1 2.5 16.0V7.0A2.0 2.0 0 0 1 4.5 5.0Z", "M8 21h8M8.5 2.5 12 5l3.5-2.5"),
        "phone" to listOf("M8.5 2.5H15.5A2.5 2.5 0 0 1 18.0 5.0V19.0A2.5 2.5 0 0 1 15.5 21.5H8.5A2.5 2.5 0 0 1 6.0 19.0V5.0A2.5 2.5 0 0 1 8.5 2.5Z", "M10.5 18.5h3"),
        "book" to listOf("M4 4.5A1.5 1.5 0 0 1 5.5 3H19v15H5.5A1.5 1.5 0 0 0 4 19.5v-15ZM4 19.5A1.5 1.5 0 0 0 5.5 21H19v-3", "M8 7.5h7M8 11h5"),
        "gear" to listOf("M9.0 12.0A3.0 3.0 0 1 0 15.0 12.0A3.0 3.0 0 1 0 9.0 12.0Z", "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z"),
        "qr" to listOf("M4.5 3.5H8.5A1.0 1.0 0 0 1 9.5 4.5V8.5A1.0 1.0 0 0 1 8.5 9.5H4.5A1.0 1.0 0 0 1 3.5 8.5V4.5A1.0 1.0 0 0 1 4.5 3.5Z", "M15.5 3.5H19.5A1.0 1.0 0 0 1 20.5 4.5V8.5A1.0 1.0 0 0 1 19.5 9.5H15.5A1.0 1.0 0 0 1 14.5 8.5V4.5A1.0 1.0 0 0 1 15.5 3.5Z", "M4.5 14.5H8.5A1.0 1.0 0 0 1 9.5 15.5V19.5A1.0 1.0 0 0 1 8.5 20.5H4.5A1.0 1.0 0 0 1 3.5 19.5V15.5A1.0 1.0 0 0 1 4.5 14.5Z", "M14.5 14.5h2.5v2.5M20.5 14.5v0M14.5 20.5h0M17 20.5h3.5V17"),
        "globe" to listOf("M3.5 12.0A8.5 8.5 0 1 0 20.5 12.0A8.5 8.5 0 1 0 3.5 12.0Z", "M3.5 12h17M12 3.5c2.4 2.3 3.6 5.2 3.6 8.5s-1.2 6.2-3.6 8.5c-2.4-2.3-3.6-5.2-3.6-8.5S9.6 5.8 12 3.5Z"),
        "star" to listOf("m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9Z"),
        "x" to listOf("M6 6l12 12M18 6 6 18"),
        "sun" to listOf("M8.0 12.0A4.0 4.0 0 1 0 16.0 12.0A4.0 4.0 0 1 0 8.0 12.0Z", "M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"),
        "moon" to listOf("M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"),
        "search" to listOf("M4.5 11.0A6.5 6.5 0 1 0 17.5 11.0A6.5 6.5 0 1 0 4.5 11.0Z", "m16 16 4.5 4.5"),
        "list" to listOf("M9 6h11M9 12h11M9 18h11M4.5 6h0M4.5 12h0M4.5 18h0"),
        "hash" to listOf("M5 9h15M4 15h15M10 3.5 8 20.5M16 3.5l-2 17"),
        "yt" to listOf("M6.5 5.0H17.5A4.0 4.0 0 0 1 21.5 9.0V15.0A4.0 4.0 0 0 1 17.5 19.0H6.5A4.0 4.0 0 0 1 2.5 15.0V9.0A4.0 4.0 0 0 1 6.5 5.0Z", "m10 9.2 5 2.8-5 2.8Z"),
        "play" to listOf("M7 4.5v15l12-7.5Z"),
        "pause" to listOf("M8 5v14M16 5v14"),
        "stop" to listOf("M7.0 6.0H17.0A1.0 1.0 0 0 1 18.0 7.0V17.0A1.0 1.0 0 0 1 17.0 18.0H7.0A1.0 1.0 0 0 1 6.0 17.0V7.0A1.0 1.0 0 0 1 7.0 6.0Z"),
        "next" to listOf("M5 5v14l10-7ZM19 5v14"),
        "restart" to listOf("M4 12a8 8 0 1 0 2.4-5.7M4 4v4.5h4.5"),
        "voldown" to listOf("M4 9.5v5h3.5L12 18.5v-13L7.5 9.5Z", "M16 12h4"),
        "volup" to listOf("M4 9.5v5h3.5L12 18.5v-13L7.5 9.5Z", "M16 12h4M18 10v4"),
        "user" to listOf("M8.5 8.0A3.5 3.5 0 1 0 15.5 8.0A3.5 3.5 0 1 0 8.5 8.0Z", "M5 20a7 7 0 0 1 14 0"),
        "trash" to listOf("M4.5 7h15M10 11v6M14 11v6M6.5 7l1 12.5A1.5 1.5 0 0 0 9 21h6a1.5 1.5 0 0 0 1.5-1.5l1-12.5M9.5 7V4.5h5V7"),
        "chevron" to listOf("m9.5 6 6 6-6 6"),
        "up" to listOf("M12 19.5V5M6 11l6-6 6 6"),
        "music" to listOf("M9 18.5V5.5l11-2v13", "M4.0 18.5A2.5 2.5 0 1 0 9.0 18.5A2.5 2.5 0 1 0 4.0 18.5Z", "M15.0 16.5A2.5 2.5 0 1 0 20.0 16.5A2.5 2.5 0 1 0 15.0 16.5Z"),
        "check" to listOf("m5 12.5 4.5 4.5L19 7.5"),
        "refresh" to listOf("M20 12a8 8 0 1 1-2.4-5.7M20 4v4.5h-4.5"),
        "plus" to listOf("M12 5v14M5 12h14"),
        "back" to listOf("M14.5 6 8.5 12l6 6"),
        "del" to listOf("M8.5 5.5H20v13H8.5L3 12Z", "m11.5 9.5 5 5M16.5 9.5l-5 5"),
        "lock" to listOf("M6.5 10.5H17.5A2.0 2.0 0 0 1 19.5 12.5V18.5A2.0 2.0 0 0 1 17.5 20.5H6.5A2.0 2.0 0 0 1 4.5 18.5V12.5A2.0 2.0 0 0 1 6.5 10.5Z", "M8 10.5V7.5a4 4 0 0 1 8 0v3"),
        "rotate" to listOf("M5.0 8.0H15.0A2.0 2.0 0 0 1 17.0 10.0V16.0A2.0 2.0 0 0 1 15.0 18.0H5.0A2.0 2.0 0 0 1 3.0 16.0V10.0A2.0 2.0 0 0 1 5.0 8.0Z", "M13 3a8 8 0 0 1 8 8", "M21 7v4h-4"),
        "sliders" to listOf("M4 7h9M19 7h1M4 17h3M13 17h7", "M14.0 7.0A2.5 2.5 0 1 0 19.0 7.0A2.5 2.5 0 1 0 14.0 7.0Z", "M7.0 17.0A2.5 2.5 0 1 0 12.0 17.0A2.5 2.5 0 1 0 7.0 17.0Z"),
        "fullscreen" to listOf("M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"),
    )
    private val cache = HashMap<String, ImageVector>()

    fun get(name: String): ImageVector = cache.getOrPut(name) {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        (paths[name] ?: emptyList()).forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        b.build()
    }
}
