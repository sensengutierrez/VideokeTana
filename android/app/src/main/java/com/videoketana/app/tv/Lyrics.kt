package com.videoketana.app.tv

/** Isang linya ng lyrics (LRC): oras sa segundo at ang text. */
data class LyricLine(val t: Double, val text: String)

/** Basahin ang .lrc: [mm:ss.xx] text; may [offset:+/-ms] din. */
fun parseLrc(text: String): Pair<List<LyricLine>, Double> {
    var offset = 0.0
    val out = mutableListOf<LyricLine>()
    val tag = Regex("\\[(\\d{1,3}):(\\d{1,2}(?:[.:]\\d{1,3})?)]")
    text.lines().forEach { line ->
        Regex("^\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(line)?.let { offset = it.groupValues[1].toDouble() / 1000; return@forEach }
        val tags = tag.findAll(line).toList()
        if (tags.isEmpty()) return@forEach
        val words = line.replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("<[^>]*>"), "").trim()
        tags.forEach { m -> out += LyricLine(m.groupValues[1].toInt() * 60 + m.groupValues[2].replace(':', '.').toDouble(), words) }
    }
    return out.sortedBy { it.t } to offset
}

/** Ano ang ipapakita sa oras na t: kasalukuyang linya, gaano na kalayo (0..1), at ang susunod na linya. */
data class LyricView(val current: String, val progress: Float, val next: String, val countdown: Int, val more: List<String> = emptyList(),
                     val el: Double = 0.0, val dur: Double = 0.0)

fun lyricAt(lines: List<LyricLine>, t: Double): LyricView {
    if (lines.isEmpty()) return LyricView("", 0f, "", 0)
    var idx = -1
    for (k in lines.indices) { if (lines[k].t <= t) idx = k else break }
    if (idx < 0) {
        val first = lines.first()
        val left = (first.t - t)
        val up = lines.filter { it.text.isNotBlank() }.map { it.text }
        return LyricView("", 0f, first.text, if (left <= 3.5) kotlin.math.ceil(left).toInt().coerceAtLeast(1) else 0, up.drop(1).take(2))
    }
    val line = lines[idx]
    val nextT = if (idx + 1 < lines.size) lines[idx + 1].t else line.t + 5
    // tantiya kung gaano katagal kinakanta ang linya (para hindi agad-agad matapos ang highlight)
    val dur = minOf(nextT - line.t, maxOf(2.0, line.text.length * 0.18) + 1).coerceAtLeast(0.5)
    val p = ((t - line.t) / dur).coerceIn(0.0, 1.0).toFloat()
    val up = lines.drop(idx + 1).filter { it.text.isNotBlank() }.take(3).map { it.text }
    val next = up.firstOrNull() ?: ""
    // mahabang patlang (interlude): bilang bago ang susunod na linya
    val cd = if (line.text.isBlank() && nextT - t <= 3.5) kotlin.math.ceil(nextT - t).toInt().coerceAtLeast(1) else 0
    return LyricView(line.text, p, next, cd, up.drop(1), t - line.t, dur)
}
