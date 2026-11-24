package com.example.autosms

import android.content.Context
import android.net.Uri
import android.util.Xml
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Excel (.xlsx) ya CSV padhta hai. Column A = mobile number, Column B (optional) = us number ka alag message.
 * Header row apne aap skip ho jati hai (jisme 10 digit number nahi hota).
 */
object ExcelReader {
    fun read(ctx: Context, uri: Uri): List<Pair<String, String>> {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val rows = if (bytes.size > 1 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())
            readXlsx(bytes) else readCsv(String(bytes, Charsets.UTF_8))
        return rows.mapNotNull { (a, b) ->
            val num = a.filter { it.isDigit() || it == '+' }
            if (num.count { it.isDigit() } < 10) null else num to b.trim()
        }
    }

    private fun readCsv(text: String): List<Pair<String, String>> =
        text.lineSequence().filter { it.isNotBlank() }.map {
            val p = it.split(',', ';', '\t')
            p[0].trim().trim('"') to (p.getOrNull(1)?.trim()?.trim('"') ?: "")
        }.toList()

    private fun readXlsx(bytes: ByteArray): List<Pair<String, String>> {
        var shared: ByteArray? = null
        var sheet: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                when (e.name) {
                    "xl/sharedStrings.xml" -> shared = z.readBytes()
                    "xl/worksheets/sheet1.xml" -> sheet = z.readBytes()
                }
                e = z.nextEntry
            }
        }
        val strings = shared?.let { parseShared(it) } ?: emptyList()
        return sheet?.let { parseSheet(it, strings) } ?: emptyList()
    }

    private fun parser(b: ByteArray) = Xml.newPullParser().apply { setInput(ByteArrayInputStream(b), null) }

    private fun parseShared(b: ByteArray): List<String> {
        val p = parser(b); val out = ArrayList<String>()
        val sb = StringBuilder(); var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> if (p.name == "si") sb.setLength(0) else if (p.name == "t") inT = true
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> if (p.name == "t") inT = false else if (p.name == "si") out.add(sb.toString())
            }
            ev = p.next()
        }
        return out
    }

    private fun parseSheet(b: ByteArray, strings: List<String>): List<Pair<String, String>> {
        val p = parser(b); val out = ArrayList<Pair<String, String>>()
        var col = ""; var type = ""; var a = ""; var bb = ""
        val sb = StringBuilder(); var inV = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> { a = ""; bb = "" }
                    "c" -> {
                        col = (p.getAttributeValue(null, "r") ?: "").takeWhile { it.isLetter() }
                        type = p.getAttributeValue(null, "t") ?: ""
                        sb.setLength(0)
                    }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "c" -> {
                        var v = sb.toString()
                        if (type == "s") v = strings.getOrElse(v.trim().toIntOrNull() ?: -1) { "" }
                        else if (type != "str" && type != "inlineStr")
                            v = try { BigDecimal(v.trim()).stripTrailingZeros().toPlainString() } catch (e: Exception) { v }
                        if (col == "A") a = v else if (col == "B") bb = v
                    }
                    "row" -> if (a.isNotBlank()) out.add(a to bb)
                }
            }
            ev = p.next()
        }
        return out
    }
}
