package com.yuanyue.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookFormatParsersTest {
    private fun tempFile(extension: String): File =
        File.createTempFile("yuanyue-test-", ".$extension").apply { deleteOnExit() }

    @Test
    fun `parses docx headings and paragraphs`() {
        val file = tempFile("docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>第一章 DOCX 测试</w:t></w:r></w:p>
                    <w:p><w:r><w:t>这是 DOCX 的第一段。</w:t></w:r></w:p>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>第二章 DOCX 继续</w:t></w:r></w:p>
                    <w:p><w:r><w:t>这是 DOCX 的第二段。</w:t></w:r></w:p>
                  </w:body>
                </w:document>
            """.trimIndent()
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val chapters = DocxBookParser.parse(file)

        assertEquals(2, chapters.size)
        assertEquals("第一章 DOCX 测试", chapters[0].title)
        assertTrue(chapters[0].content.contains("第一段"))
        assertEquals("第二章 DOCX 继续", chapters[1].title)
    }

    @Test
    fun `parses html and fb2`() {
        val html = tempFile("html").apply {
            writeText("<html><body><h1>第一章 HTML</h1><p>网页正文第一段。</p></body></html>")
        }
        val htmlChapters = HtmlBookParser.parse(html)
        assertTrue(htmlChapters.isNotEmpty())
        assertTrue(htmlChapters.joinToString { it.content }.contains("网页正文第一段"))

        val fb2 = tempFile("fb2").apply {
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <body>
                    <section><title><p>第一章 FB2</p></title><p>FB2 正文一。</p></section>
                    <section><title><p>第二章 FB2</p></title><p>FB2 正文二。</p></section>
                  </body>
                </FictionBook>
                """.trimIndent(),
            )
        }
        val fb2Chapters = Fb2BookParser.parse(fb2)
        assertEquals(2, fb2Chapters.size)
        assertTrue(fb2Chapters[0].content.contains("FB2 正文一"))
    }

    @Test
    fun `parses rtf text`() {
        val rtf = tempFile("rtf").apply {
            writeText("{\\rtf1\\ansi{\\fonttbl{\\f0 Arial;}}\\f0 Chapter One\\par First paragraph.\\par}")
        }
        val chapters = RtfBookParser.parse(rtf)
        val content = chapters.joinToString("\n") { it.content }
        assertTrue(content.contains("Chapter One"))
        assertTrue(content.contains("First paragraph."))
    }

    @Test
    fun `parses uncompressed mobi text records`() {
        val text = "<html><body><h1>第一章 MOBI 测试</h1><p>MOBI 正文第一段。</p></body></html>"
            .toByteArray(Charsets.UTF_8)
        val record0 = ByteArray(80)
        put16(record0, 0, 1)
        put32(record0, 4, text.size)
        put16(record0, 8, 1)
        put16(record0, 10, 4096)
        put16(record0, 12, 0)
        "MOBI".toByteArray(Charsets.US_ASCII).copyInto(record0, 16)
        put32(record0, 28, 65001)

        val record0Offset = 94
        val record1Offset = record0Offset + record0.size
        val header = ByteArray(record0Offset)
        put16(header, 76, 2)
        put32(header, 78, record0Offset)
        put32(header, 86, record1Offset)

        val file = tempFile("mobi")
        file.writeBytes(header + record0 + text)

        val chapters = MobiBookParser.parse(file)

        assertTrue(chapters.isNotEmpty())
        assertTrue(chapters.joinToString { it.content }.contains("MOBI 正文第一段"))
    }

    @Test
    fun `detects and extracts image epub comic pages`() {
        val file = tempFile("epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <manifest>
                    <item id="p1" href="page1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="p2" href="page2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="i1" href="images/1.png" media-type="image/png"/>
                    <item id="i2" href="images/2.png" media-type="image/png"/>
                  </manifest>
                  <spine><itemref idref="p1"/><itemref idref="p2"/></spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            listOf("page1" to "1.png", "page2" to "2.png").forEach { (page, image) ->
                zip.putNextEntry(ZipEntry("OEBPS/$page.xhtml"))
                zip.write("<html><body><img src=\"images/$image\"/></body></html>".toByteArray())
                zip.closeEntry()
            }
            listOf("1.png", "2.png").forEach { name ->
                zip.putNextEntry(ZipEntry("OEBPS/images/$name"))
                zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
                zip.closeEntry()
            }
        }

        assertTrue(EpubComicParser.isComic(file))
        val cache = File.createTempFile("yuanyue-comic-", "").apply {
            delete()
            mkdirs()
        }
        val pages = EpubComicParser.extract(file, cache)
        assertEquals(2, pages.size)
        assertTrue(pages.all { File(it.imagePath).exists() })
    }

    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun put32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
