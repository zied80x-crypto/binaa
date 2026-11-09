package tn.binaa.app.reports

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporters {

    private fun exportDir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    // ------------------------------------------------------------------ PDF (A4, RTL)

    fun pdf(ctx: Context, r: Report): File {
        val pageW = 595; val pageH = 842; val margin = 28f
        val landscape = r.headers.size > 6
        val w = if (landscape) pageH else pageW
        val h = if (landscape) pageW else pageH
        val doc = PdfDocument()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(15, 61, 94) }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val cell = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.BLACK }
        val head = TextPaint(cell).apply { typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val bold = TextPaint(cell).apply { typeface = Typeface.DEFAULT_BOLD }
        val line = Paint().apply { color = Color.rgb(200, 205, 212); strokeWidth = 0.5f }
        val fill = Paint().apply { color = Color.rgb(15, 61, 94) }
        val zebra = Paint().apply { color = Color.rgb(244, 246, 249) }

        val tableW = w - 2 * margin
        val colW = tableW / r.headers.size
        fun layout(text: String, p: TextPaint, width: Int) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.ANYRTL_LTR)
                .build()

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun drawRow(values: List<String>, p: TextPaint, bg: Paint?) {
            val layouts = values.map { layout(it, p, (colW - 6).toInt()) }
            val rowH = (layouts.maxOf { it.height } + 8).toFloat()
            if (y + rowH > h - margin - 16) {
                page?.let { doc.finishPage(it) }
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
                y = margin
            }
            val c = page!!.canvas
            if (bg != null) c.drawRect(margin, y, w - margin, y + rowH, bg)
            // RTL: first column on the right
            layouts.forEachIndexed { i, l ->
                val right = w - margin - i * colW
                c.save(); c.translate(right - colW + 3, y + 4); l.draw(c); c.restore()
            }
            c.drawLine(margin, y + rowH, w - margin, y + rowH, line)
            y += rowH
        }

        pageNo = 1
        page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        y = margin
        page!!.canvas.let { c ->
            val t = layout(r.title, title, tableW.toInt()); c.save(); c.translate(margin, y); t.draw(c); c.restore(); y += t.height + 4
            val s = layout(r.subtitle + "   •   أُنشئ في " + Formats.dateTime(System.currentTimeMillis()), sub, tableW.toInt())
            c.save(); c.translate(margin, y); s.draw(c); c.restore(); y += s.height + 10
        }
        drawRow(r.headers, head, fill)
        r.rows.forEachIndexed { i, row -> drawRow(row.map(::cellText), cell, if (i % 2 == 1) zebra else null) }
        if (r.rows.isEmpty()) drawRow(listOf("لا توجد بيانات") + List(r.headers.size - 1) { "" }, cell, null)
        r.totals?.let { drawRow(it.map(::cellText), bold, zebra) }
        page?.let { doc.finishPage(it) }

        val f = File(exportDir(ctx), "${r.fileName}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    // ------------------------------------------------------------------ XLSX (minimal OOXML writer, RTL sheet)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun colName(i: Int): String {
        var n = i + 1; val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun xCell(ref: String, v: Any, style: Int): String = when (v) {
        is M -> "<c r=\"$ref\" s=\"${if (style == 1) 4 else 2}\"><v>${Money.toDecimal(v.millimes).toPlainString()}</v></c>"
        is Int, is Long -> "<c r=\"$ref\" s=\"$style\"><v>$v</v></c>"
        else -> "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t>${esc(v.toString())}</t></is></c>"
    }

    fun xlsx(ctx: Context, r: Report): File {
        val sb = StringBuilder()
        var rowNo = 0
        fun row(values: List<Any>, style: Int) {
            rowNo++
            sb.append("<row r=\"$rowNo\">")
            values.forEachIndexed { i, v -> sb.append(xCell("${colName(i)}$rowNo", v, style)) }
            sb.append("</row>")
        }
        row(listOf(r.title), 1)
        row(listOf(r.subtitle), 0)
        row(r.headers, 3)
        r.rows.forEach { row(it, 0) }
        r.totals?.let { row(it, 1) }
        val cols = r.headers.indices.joinToString("") { "<col min=\"${it + 1}\" max=\"${it + 1}\" width=\"18\" customWidth=\"1\"/>" }
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><cols>$cols</cols><sheetData>$sb</sheetData></worksheet>"""
        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.000"/></numFmts>
<fonts count="3"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0F3D5E"/></patternFill></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="5">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/>
</cellXfs></styleSheet>"""
        val files = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="تقرير" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet,
            "xl/styles.xml" to styles,
        )
        val f = File(exportDir(ctx), "${r.fileName}.xlsx")
        ZipOutputStream(f.outputStream()).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
        return f
    }

    fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "مشاركة / حفظ").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
