package com.yuanyue.reader.data

import org.jsoup.Jsoup
import java.net.URLDecoder

/**
 * 从分享文本、订阅地址或网页中提取书源链接。
 *
 * 支持：
 * - `legado://import/bookSource?src=<url>` / `yuedu://...` 分享链接；
 * - 文本里直接出现的 http(s) 链接；
 * - 订阅页面中的 `.json` / `.jsonl` / bookSource 链接。
 */
object BookSourceLinkParser {
    private val urlRegex = Regex("""https?://[^\s"'<>]+""")
    private val shareRegex = Regex("""(?:legado|yuedu)://[^\s"'<>]+""")

    fun extractLinks(text: String): List<String> {
        val result = mutableListOf<String>()
        shareRegex.findAll(text).forEach { match ->
            parseShareLink(match.value)?.let { result += it }
        }
        urlRegex.findAll(text).forEach { match ->
            cleanUrl(match.value)?.let { result += it }
        }
        return result.distinct()
    }

    fun parseShareLink(link: String): String? {
        val scheme = link.substringBefore("://", "")
        if (!scheme.equals("legado", true) && !scheme.equals("yuedu", true)) return null
        val query = link.substringAfter('?', "")
        if (query.isBlank()) return null
        val src = query.split('&')
            .firstOrNull { it.substringBefore('=').trim().equals("src", ignoreCase = true) }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { URLDecoder.decode(src, "UTF-8") }.getOrDefault(src)
    }

    fun extractHtmlJsonLinks(html: String, baseUrl: String): List<String> {
        val document = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return emptyList()
        return document.select("a[href]").mapNotNull { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val absolute = if (href.startsWith("http")) {
                href
            } else {
                runCatching { java.net.URI(baseUrl).resolve(href).toString() }.getOrDefault("")
            }
            if (absolute.startsWith("http") && looksLikeSourceLink(absolute)) absolute else null
        }.distinct()
    }

    /** 从网页的 pre/code/textarea 或 application/json script 中提取内嵌的书源 JSON。 */
    fun extractEmbeddedJson(html: String): String? {
        val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        val candidates = buildList {
            document.select("script[type=application/json]").forEach { add(it.data()) }
            document.select("pre, textarea, code").forEach { add(it.text()) }
        }
        return candidates.firstOrNull { block ->
            val text = block.trim()
            (text.startsWith("[") || text.startsWith("{")) &&
                text.contains("bookSourceName", ignoreCase = true)
        }?.trim()
    }

    private fun looksLikeSourceLink(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".json") ||
            path.endsWith(".jsonl") ||
            path.contains("booksource") ||
            path.contains("book_source") ||
            path.contains("书源") ||
            path.contains("sources.json")
    }

    private fun cleanUrl(raw: String): String? {
        val cleaned = raw.trim().trimEnd(
            '.', ',', ';', ')', ']', '}', '，', '。', '；', '）', '】',
        )
        return cleaned.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
