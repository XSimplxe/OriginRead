package com.yuanyue.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class BookSourceImportTest {
    private fun sourceJson(name: String, url: String): String = """
        {
          "bookSourceName": "$name",
          "bookSourceUrl": "$url",
          "searchUrl": "$url/search?key={{key}}",
          "bookList": ".book-item",
          "bookName": ".title@text",
          "bookUrl": "a@href",
          "chapterList": ".chapters a",
          "chapterUrl": "a@href",
          "content": "#content"
        }
    """.trimIndent()

    @Test
    fun `parses array of sources`() {
        val json = "[" + sourceJson("源A", "https://a.com") + "," +
            sourceJson("源B", "https://b.com") + "]"

        val parsed = BookSourceImporter.parseSources(json)

        assertEquals(2, parsed.sources.size)
        assertEquals(listOf("源A", "源B"), parsed.sources.map { it.name })
        assertTrue(parsed.failures.isEmpty())
    }

    @Test
    fun `parses wrapper object with bookSources array`() {
        val json = """{"bookSources":[""" + sourceJson("源A", "https://a.com") + "]}"

        val parsed = BookSourceImporter.parseSources(json)

        assertEquals(1, parsed.sources.size)
        assertEquals("源A", parsed.sources.first().name)
    }

    @Test
    fun `parses json lines`() {
        fun line(name: String, url: String) =
            "{\"bookSourceName\":\"$name\",\"bookSourceUrl\":\"$url\"," +
                "\"searchUrl\":\"$url/s?q={{key}}\",\"bookList\":\".b\"," +
                "\"bookName\":\"a\",\"bookUrl\":\"a@href\"," +
                "\"chapterList\":\".c\",\"chapterUrl\":\"a@href\",\"content\":\"#c\"}"
        val json = line("源A", "https://a.com") + "\n" + line("源B", "https://b.com")

        val parsed = BookSourceImporter.parseSources(json)

        assertEquals(2, parsed.sources.size)
    }

    @Test
    fun `skips invalid item and reports it`() {
        val json = "[" + sourceJson("源A", "https://a.com") +
            """,{"bookSourceUrl":"https://bad.com"}]"""

        val parsed = BookSourceImporter.parseSources(json)

        assertEquals(1, parsed.sources.size)
        assertTrue(parsed.failures.any { it.contains("缺少书源名称") })
    }

    @Test
    fun `extracts legado share link`() {
        val target = "https://example.com/sources.json"
        val encoded = URLEncoder.encode(target, "UTF-8")
        val link = "legado://import/bookSource?src=$encoded"

        assertEquals(target, BookSourceLinkParser.parseShareLink(link))
        assertEquals(listOf(target), BookSourceLinkParser.extractLinks(link))
    }

    @Test
    fun `extracts multiple plain links from text`() {
        val text = """
            分享两个书源：
            https://a.com/sources.json
            https://b.com/bookSource.json
        """.trimIndent()

        val links = BookSourceLinkParser.extractLinks(text)

        assertEquals(2, links.size)
    }

    @Test
    fun `extracts json links from html page`() {
        val html = """
            <html><body>
              <a href="/bookSource.json">书源下载</a>
              <a href="/readme.html">说明</a>
              <a href="https://cdn.example.com/other.jsonl">备用</a>
            </body></html>
        """.trimIndent()

        val links = BookSourceLinkParser.extractHtmlJsonLinks(html, "https://example.com/sub/page")

        assertTrue(links.contains("https://example.com/bookSource.json"))
        assertTrue(links.contains("https://cdn.example.com/other.jsonl"))
        assertTrue(links.none { it.endsWith("readme.html") })
    }

    @Test
    fun `extracts embedded json from html pre block`() {
        val source = sourceJson("内嵌源", "https://inline.example.com")
        val html = "<html><body><pre>${source.replace("<", "&lt;")}</pre></body></html>"

        val embedded = BookSourceLinkParser.extractEmbeddedJson(html)

        assertTrue(embedded != null && embedded.contains("bookSourceName"))
        assertEquals(1, BookSourceImporter.parseSources(embedded!!).sources.size)
    }
}
