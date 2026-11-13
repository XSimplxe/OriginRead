package com.yuanyue.reader.data.parser

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.yuanyue.reader.model.BookChapter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object BookTextParser {
    fun parse(context: Context, file: File, formatName: String): List<BookChapter> = when (formatName) {
        "PDF" -> PdfBookParser(context).parse(file)
        "DOCX" -> DocxBookParser.parse(file)
        "RTF" -> RtfBookParser.parse(file)
        "HTML" -> HtmlBookParser.parse(file)
        "FB2" -> Fb2BookParser.parse(file)
        "MOBI", "AZW3" -> MobiBookParser.parse(file)
        else -> throw IllegalArgumentException("暂不支持该格式：$formatName")
    }
}

private fun File.baseTitle(): String = nameWithoutExtension.ifBlank { "未命名书籍" }

private fun htmlToChapters(file: File, html: String): List<BookChapter> {
    val text = HtmlTextExtractor.toText(html)
    if (text.isBlank()) throw IllegalArgumentException("没有提取到文字内容")
    return TxtChapterParser.split(text).ifEmpty {
        listOf(BookChapter(file.baseTitle(), text))
    }
}

object HtmlBookParser {
    fun parse(file: File): List<BookChapter> {
        val html = TextFileReader.readSmart(file)
        val document = Jsoup.parse(html)
        val body = document.body()?.html().orEmpty()
        return htmlToChapters(file, body)
    }
}

object Fb2BookParser {
    fun parse(file: File): List<BookChapter> {
        val raw = TextFileReader.readSmart(file)
        val document = runCatching { Jsoup.parse(raw, "", Parser.xmlParser()) }.getOrElse {
            throw IllegalArgumentException("FB2 文件解析失败")
        }
        val sections = document.select("body > section")
        val chapters = sections.mapNotNull { section ->
            val title = section.selectFirst("title")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "正文"
            val paragraphs = section.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
            val content = paragraphs.joinToString("\n\n")
            if (content.isBlank()) null else BookChapter(title, content)
        }
        if (chapters.isNotEmpty()) return chapters
        val text = HtmlTextExtractor.toText(document.body()?.html().orEmpty())
        if (text.isBlank()) throw IllegalArgumentException("FB2 中没有可读取的文字")
        return listOf(BookChapter(file.baseTitle(), text))
    }
}

object DocxBookParser {
    fun parse(file: File): List<BookChapter> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: throw IllegalArgumentException("DOCX 缺少 word/document.xml")
            val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(InputSource(StringReader(xml)))

            val paragraphs = document.getElementsByTagNameNS("*", "p")
            val chapters = mutableListOf<BookChapter>()
            val builder = StringBuilder()
            var title = "正文"

            fun flush() {
                if (builder.isNotBlank()) {
                    chapters += BookChapter(title, builder.toString().trim())
                    builder.clear()
                }
            }

            for (index in 0 until paragraphs.length) {
                val paragraph = paragraphs.item(index) as? Element ?: continue
                val text = paragraph.textContent.trim()
                if (text.isEmpty()) continue
                val styleElement = paragraph.getElementsByTagNameNS("*", "pStyle").item(0) as? Element
                val style = buildString {
                    styleElement?.let {
                        append(it.getAttribute("w:val"))
                        append(it.textContent)
                    }
                }
                val isHeading = style.contains("Heading", ignoreCase = true) ||
                    style.contains("Title", ignoreCase = true) ||
                    style.contains("标题")
                if (isHeading) {
                    flush()
                    title = text
                } else {
                    if (builder.isNotEmpty()) builder.append("\n\n")
                    builder.append(text)
                }
            }
            flush()
            if (chapters.isNotEmpty()) return chapters
            throw IllegalArgumentException("DOCX 中没有可读取的文字")
        }
    }
}

object RtfBookParser {
    fun parse(file: File): List<BookChapter> {
        var text = TextFileReader.readSmart(file)
        text = text.replace(
            Regex(
                "\\{\\\\(?:fonttbl|colortbl|stylesheet|info|pict|object|header|footer)[\\s\\S]*?\\}",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        text = text.replace(Regex("\\\\u(-?\\d+)\\??")) { match ->
            val code = match.groupValues[1].toIntOrNull() ?: 0
            val normalized = if (code < 0) code + 65536 else code
            normalized.toChar().toString()
        }
        text = text
            .replace("\\par", "\n")
            .replace("\\line", "\n")
            .replace("\\tab", "\t")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
            .replace(Regex("[{}]"), "")
        val plain = text.trim()
        if (plain.isBlank()) throw IllegalArgumentException("RTF 中没有可读取的文字")
        return TxtChapterParser.split(plain).ifEmpty {
            listOf(BookChapter(file.baseTitle(), plain))
        }
    }
}

class PdfBookParser(private val context: Context) {
    fun parse(file: File): List<BookChapter> {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val chapters = mutableListOf<BookChapter>()
            for (page in 1..document.numberOfPages) {
                stripper.startPage = page
                stripper.endPage = page
                val text = runCatching { stripper.getText(document) }.getOrDefault("").trim()
                if (text.isNotBlank()) {
                    chapters += BookChapter("第 $page 页", text)
                }
            }
            if (chapters.isEmpty()) {
                throw IllegalArgumentException("PDF 中没有可提取的文字，可能是扫描版 PDF")
            }
            return chapters
        }
    }
}

/**
 * 无 DRM 的 MOBI / AZW3（KF8）文字提取。
 * 支持 PalmDOC 不压缩和 PalmDOC LZ77 压缩；HUFF/CDIC 压缩和 DRM 会明确报错。
 */
object MobiBookParser {
    fun parse(file: File): List<BookChapter> {
        val bytes = file.readBytes()
        if (bytes.size < 100) throw IllegalArgumentException("MOBI 文件损坏或过小")
        val recordCount = readU16(bytes, 76)
        if (recordCount <= 1) throw IllegalArgumentException("MOBI 文件没有文本记录")
        val offsets = IntArray(recordCount) { readU32(bytes, 78 + it * 8) }
        val record0Offset = offsets[0].coerceIn(0, bytes.size)
        val compression = readU16(bytes, record0Offset)
        val textRecordCount = readU16(bytes, record0Offset + 8)
        val encryption = readU16(bytes, record0Offset + 12)
        if (encryption != 0) {
            throw IllegalArgumentException("该 MOBI/AZW3 受 DRM 保护，无法读取")
        }
        if (compression != 1 && compression != 2) {
            throw IllegalArgumentException("暂不支持该 MOBI 压缩格式（$compression）")
        }
        val encoding = if (
            record0Offset + 20 <= bytes.size &&
            String(bytes, record0Offset + 16, 4, Charsets.US_ASCII) == "MOBI"
        ) {
            readU32(bytes, record0Offset + 28)
        } else {
            65001
        }
        val charset = when (encoding) {
            1252 -> Charset.forName("windows-1252")
            936 -> Charset.forName("GBK")
            else -> Charsets.UTF_8
        }

        val text = StringBuilder()
        for (index in 1..textRecordCount.coerceAtMost(recordCount - 1)) {
            val start = offsets[index].coerceIn(0, bytes.size)
            val end = if (index + 1 < recordCount) offsets[index + 1].coerceIn(start, bytes.size) else bytes.size
            val record = bytes.copyOfRange(start, end)
            val data = if (compression == 1) record else decompressPalmDoc(record)
            text.append(String(data, charset))
        }

        val plain = HtmlTextExtractor.toText(text.toString())
        if (plain.isBlank()) throw IllegalArgumentException("MOBI/AZW3 中没有可读取的文字")
        return TxtChapterParser.split(plain).ifEmpty {
            listOf(BookChapter(file.baseTitle(), plain))
        }
    }

    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var index = 0
        while (index < data.size) {
            val value = data[index].toInt() and 0xFF
            index++
            when {
                value == 0 -> output.write(0)
                value <= 8 -> {
                    repeat(value) {
                        if (index < data.size) output.write(data[index].toInt())
                        index++
                    }
                }

                value <= 0x7F -> output.write(value)
                value >= 0xC0 -> {
                    output.write(' '.code)
                    output.write(value xor 0x80)
                }

                else -> {
                    if (index >= data.size) break
                    val next = data[index].toInt() and 0xFF
                    index++
                    val pair = (value shl 8) or next
                    val distance = (pair shr 3) and 0x7FF
                    val length = (pair and 0x7) + 3
                    val current = output.toByteArray()
                    val start = current.size - distance
                    if (start >= 0) {
                        repeat(length) { offset ->
                            val position = start + offset
                            if (position < current.size) output.write(current[position].toInt())
                        }
                    }
                }
            }
        }
        return output.toByteArray()
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
