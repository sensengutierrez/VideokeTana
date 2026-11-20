package com.videoketana.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Maliit na gumagawa ng Excel (.xlsx), kapareho ng web (frontend/assets/js/xlsx.js). */
object Xlsx {
    private fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").filter { it == '\t' || it == '\n' || it >= ' ' }
    private fun col(i0: Int): String { var i = i0 + 1; val sb = StringBuilder(); while (i > 0) { val m = (i - 1) % 26; sb.insert(0, ('A' + m)); i = (i - 1) / 26 }; return sb.toString() }

    fun write(f: File, rows: List<List<Any?>>, sheet: String = "Songbook") {
        val last = col(rows[0].size - 1)
        val widths = rows[0].indices.map { c -> rows.maxOf { (it.getOrNull(c)?.toString()?.length ?: 0) + 2 }.coerceIn(8, 60) }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>""")
        widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sb.append("</cols><sheetData>")
        rows.forEachIndexed { ri, r ->
            sb.append("""<row r="${ri + 1}">""")
            r.forEachIndexed { ci, v ->
                val ref = col(ci) + (ri + 1); val st = if (ri == 0) """ s="1"""" else ""
                if (v is Number) sb.append("""<c r="$ref"$st><v>$v</v></c>""")
                else sb.append("""<c r="$ref" t="inlineStr"$st><is><t>${x(v?.toString() ?: "")}</t></is></c>""")
            }
            sb.append("</row>")
        }
        sb.append("""</sheetData><autoFilter ref="A1:$last${rows.size}"/></worksheet>""")
        val files = listOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${x(sheet)}" sheetId="1" r:id="rId1"/></sheets><definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">'${x(sheet)}'!${'$'}A${'$'}1:${'$'}$last${'$'}${rows.size}</definedName></definedNames></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0B8576"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="2"><xf/><xf fontId="1" fillId="2" applyFont="1" applyFill="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""",
            "xl/worksheets/sheet1.xml" to sb.toString()
        )
        ZipOutputStream(f.outputStream()).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray(Charsets.UTF_8)); z.closeEntry() } }
    }

    /** Buong songbook: online + mga kanta sa folder ng device na ito. Ibinabalik ang file at kung saan na-save. */
    suspend fun songbook(ctx: Context): Pair<File, String> {
        val rows = mutableListOf<List<Any?>>(listOf("Numero", "Title", "Artist", "Kategorya", "Uri", "Pinagmulan"))
        Supa.songs("", 10000).forEach { s ->
            val kind = when { s.kind == "youtube" -> "YouTube"; s.kind == "video" -> "Video"; s.mediaPath?.let { Regex("\\.(mid|midi|kar)$", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true -> "MIDI"; else -> "Audio" }
            rows += listOf(s.code, s.title, s.artist, s.category ?: "", kind, "Online")
        }
        if (LocalLib.hasPermission(ctx)) LocalLib.scan().forEach { rows += listOf(it.code, it.title, it.artist, "", if (it.isMidi) "MIDI" else it.ext.uppercase(), "Sa device na ito") }
        val name = "VideokeTana-Songbook-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".xlsx"
        val f = File(ctx.cacheDir, name)
        write(f, rows)
        // kopya sa Download
        var where = ""
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideokeTana")
                }
                ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)?.let { u -> ctx.contentResolver.openOutputStream(u)?.use { o -> f.inputStream().use { it.copyTo(o) } }; where = "Download/VideokeTana/$name" }
            } else {
                val d = File(LocalLib.root, name); f.copyTo(d, true); where = "Download/VideokeTana/$name"
            }
        } catch (e: Exception) { }
        return f to where
    }

    fun share(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Songbook").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
