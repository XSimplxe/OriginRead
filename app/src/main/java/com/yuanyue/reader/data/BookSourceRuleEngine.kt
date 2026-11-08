package com.yuanyue.reader.data

import com.yuanyue.reader.data.parser.HtmlTextExtractor
import com.yuanyue.reader.model.BookSourceEntity
import com.yuanyue.reader.model.OnlineBookEntity
import com.yuanyue.reader.model.OnlineChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class BookSourceTestResult(
    val ok: Boolean,
    val message: String,
    val warning: String? = null,
    val sampleCount: Int = 0,
)

/**
 * 书源规则引擎。
 *
 * 规则兼容：
 * - 普通 CSS 选择器 / `a@href` 形式；
 * - Legado 的 `class.xxx@tag.a@href`、`tag.a.0@href`、`text`、`@text` 形式；
 * - `||` 备用规则。
 */
class BookSourceRuleEngine(
    private val client: OkHttpClient,
) {
    /** 添加书源前先跑一遍：结构检查 + 实际搜索 + 目录 + 正文抽样。 */
    suspend fun testSource(source: BookSourceEntity, keyword: String = "我"): BookSourceTestResult =
        withContext(Dispatchers.IO) {
            val missing = BookSourceImporter.missingFields(source)
            if (missing.isNotEmpty()) {
                return@withContext BookSourceTestResult(
                    ok = false,
                    message = "书源规则不完整，缺少：${missing.joinToString("、")}",
                )
            }

            var lastError: String? = null
            val keywords = listOf(keyword, "斗罗大陆").distinct()
            for (sample in keywords) {
                try {
                    val books = search(source, sample, page = 1)
                    if (books.isEmpty()) {
                        lastError = "搜索「$sample」没有返回结果"
                        continue
                    }
                    val first = books.first()
                    val chapterList = chapters(source, first)
                    if (chapterList.isEmpty()) {
                        return@withContext BookSourceTestResult(
                            ok = false,
                            message = "书源能搜索，但目录规则解析不到章节，可能已失效或规则不匹配",
                            sampleCount = books.size,
                        )
                    }
                    val contentLength = runCatching {
                        content(source, chapterList.first().url).length
                    }.getOrDefault(0)
                    val warning = if (contentLength < 20) {
                        "目录可用，但正文规则没有取到内容，阅读时可能失败"
                    } else {
                        null
                    }
                    return@withContext BookSourceTestResult(
                        ok = true,
                        message = "书源可用：搜索到 ${books.size} 条结果，目录 ${chapterList.size} 章",
                        warning = warning,
                        sampleCount = books.size,
                    )
                } catch (error: Exception) {
                    lastError = error.message ?: "请求失败"
                }
            }

            BookSourceTestResult(
                ok = false,
                message = "书源不可用：${lastError ?: "无法访问书源站点"}",
            )
        }

    suspend fun search(
        source: BookSourceEntity,
        keyword: String,
        page: Int = 1,
    ): List<OnlineBookEntity> = withContext(Dispatchers.IO) {
        val searchUrl = source.searchUrl
            ?: throw IllegalStateException("该书源未配置搜索地址")
        val url = resolveUrl(
            normalizeBaseUrl(source.url),
            renderUrl(
                template = searchUrl,
                replacements = mapOf(
                    "key" to keyword,
                    "searchKey" to keyword,
                    "page" to page.toString(),
                ),
            ),
        )
        if (!isHttpUrl(url)) {
            throw IllegalStateException("书源搜索地址不是完整链接：$searchUrl")
        }
        val html = fetch(url, source.url)
        val document = Jsoup.parse(html, url)
        val items = document.select(selectorOf(source.searchListRule))
        items.mapNotNull { item ->
            val title = extract(item, source.bookNameRule)
                ?.trim()
                .orEmpty()
                .ifBlank { item.text().trim() }
            val bookUrl = extract(item, source.bookUrlRule)
                ?.let { resolveUrl(url, it) }
                ?: resolveUrl(
                    url,
                    item.attr("href").ifBlank {
                        item.selectFirst("a")?.attr("href").orEmpty()
                    },
                )
            if (title.isBlank() || !isHttpUrl(bookUrl)) return@mapNotNull null
            OnlineBookEntity(
                sourceId = source.id,
                title = title,
                author = extract(item, source.bookAuthorRule)?.trim(),
                coverUrl = extract(item, source.bookCoverRule)?.let { resolveUrl(url, it) },
                bookUrl = bookUrl,
                intro = null,
                latestChapter = null,
            )
        }.distinctBy { it.bookUrl }
    }

    suspend fun detail(
        source: BookSourceEntity,
        book: OnlineBookEntity,
    ): OnlineBookEntity = withContext(Dispatchers.IO) {
        val html = fetch(book.bookUrl, source.url)
        val document = Jsoup.parse(html, book.bookUrl)
        book.copy(
            title = extract(document, source.bookNameRule)?.trim()?.ifBlank { book.title } ?: book.title,
            author = extract(document, source.bookAuthorRule)?.trim() ?: book.author,
            coverUrl = extract(document, source.coverRule)
                ?.let { resolveUrl(book.bookUrl, it) }
                ?: book.coverUrl,
            intro = extract(document, source.introRule)?.trim() ?: book.intro,
        )
    }

    suspend fun chapters(
        source: BookSourceEntity,
        book: OnlineBookEntity,
    ): List<OnlineChapterEntity> = withContext(Dispatchers.IO) {
        val bookUrl = normalizeBaseUrl(book.bookUrl)
        val tocUrl = resolveTocUrl(source, bookUrl)
        val pages = buildList {
            if (tocUrl != null && isHttpUrl(tocUrl)) add(tocUrl)
            if (bookUrl !in this) add(bookUrl)
        }
        for (pageUrl in pages) {
            val chapters = runCatching { parseChapters(source, book, pageUrl) }
                .getOrDefault(emptyList())
            if (chapters.isNotEmpty()) return@withContext chapters
        }
        emptyList()
    }

    private fun parseChapters(
        source: BookSourceEntity,
        book: OnlineBookEntity,
        pageUrl: String,
    ): List<OnlineChapterEntity> {
        val html = fetch(pageUrl, book.bookUrl)
        val document = Jsoup.parse(html, pageUrl)
        val items = document.select(selectorOf(source.chapterListRule))
        return items.mapIndexedNotNull { index, item ->
            val title = extract(item, source.chapterNameRule)
                ?.trim()
                .orEmpty()
                .ifBlank { item.text().trim() }
            val rawUrl = extract(item, source.chapterUrlRule)
                ?: item.attr("href").ifBlank {
                    item.selectFirst("a")?.attr("href").orEmpty()
                }
            val url = resolveUrl(pageUrl, rawUrl)
            if (title.isBlank() || !isHttpUrl(url)) return@mapIndexedNotNull null
            OnlineChapterEntity(
                bookId = book.id,
                sourceId = source.id,
                title = title,
                url = url,
                chapterIndex = index,
            )
        }
            .distinctBy { it.url }
            .mapIndexed { index, chapter -> chapter.copy(chapterIndex = index) }
    }

    /**
     * Legado 的 ruleBookInfo.tocUrl 既可能是 URL 模板，也可能是一个从详情页提取目录链接的选择器。
     * 之前一律当成 URL，选择器会变成站点下的奇怪路径，导致目录永远为空。
     */
    private fun resolveTocUrl(
        source: BookSourceEntity,
        bookUrl: String,
    ): String? {
        val rule = source.tocUrlRule?.trim().orEmpty()
        if (rule.isBlank()) return bookUrl

        if (looksLikeUrlRule(rule)) {
            val rendered = renderUrl(rule, mapOf("bookUrl" to bookUrl))
            return resolveUrl(bookUrl, rendered).takeIf { isHttpUrl(it) } ?: bookUrl
        }

        val html = runCatching { fetch(bookUrl, source.url) }.getOrNull() ?: return bookUrl
        val document = Jsoup.parse(html, bookUrl)
        val link = extractTocLink(document, rule) ?: return bookUrl
        return resolveUrl(bookUrl, link).takeIf { isHttpUrl(it) } ?: bookUrl
    }

    private fun looksLikeUrlRule(rule: String): Boolean {
        val value = rule.trim()
        val lower = value.lowercase()
        return value.contains("{{") ||
            value.startsWith("http://") ||
            value.startsWith("https://") ||
            value.startsWith("//") ||
            value.startsWith("/") ||
            value.startsWith("./") ||
            value.startsWith("../") ||
            value.startsWith("?") ||
            value.contains("://") ||
            lower.endsWith(".html") ||
            lower.endsWith(".htm") ||
            lower.endsWith(".php") ||
            lower.endsWith(".asp") ||
            lower.endsWith(".aspx") ||
            lower.endsWith(".jsp")
    }

    private fun extractTocLink(document: Document, rule: String): String? {
        val alternatives = rule.split("||").map { it.trim() }.filter { it.isNotEmpty() }
        for (alternative in alternatives) {
            val (selector, attribute) = parseRule(alternative)
            if (selector.isBlank()) continue
            val element = runCatching { document.selectFirst(selector) }.getOrNull() ?: continue
            val value = when (attribute) {
                "html" -> element.html()
                null -> element.attr("href")
                    .ifBlank { element.selectFirst("a[href]")?.attr("href").orEmpty() }
                    .ifBlank { element.attr("data-href") }
                    .ifBlank { element.attr("data-url") }
                    .ifBlank { element.text() }
                else -> element.attr(attribute)
            }.trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    suspend fun content(
        source: BookSourceEntity,
        chapterUrl: String,
    ): String = withContext(Dispatchers.IO) {
        val html = fetch(chapterUrl, source.url)
        val document = Jsoup.parse(html, chapterUrl)
        val rawHtml = extractContentHtml(document, source.contentRule)
            ?: document.body()?.html()
            .orEmpty()
        val text = HtmlTextExtractor.toText(rawHtml)
        applyReplaceRules(text, source.contentReplaceRules)
    }

    private fun extractContentHtml(document: Document, rule: String?): String? {
        if (rule.isNullOrBlank()) return document.body()?.html()
        val alternatives = rule.split("||").map { it.trim() }.filter { it.isNotEmpty() }
        for (alternative in alternatives) {
            val (selector, attribute) = parseRule(alternative)
            val wantsText = alternative == "text" || alternative.endsWith("@text")
            if (selector.isBlank()) {
                val body = document.body() ?: continue
                return if (wantsText) body.text() else body.html()
            }
            val elements = runCatching { document.select(selector) }.getOrNull() ?: continue
            if (elements.isEmpty()) continue
            return when {
                attribute == "html" -> elements.joinToString("\n") { it.html() }
                wantsText -> elements.joinToString("\n") { it.text() }
                attribute != null -> elements.joinToString("\n") { it.attr(attribute) }
                elements.size == 1 -> elements[0].html()
                else -> elements.joinToString("\n") { it.html() }
            }
        }
        return null
    }

    private fun renderUrl(
        template: String,
        replacements: Map<String, String>,
    ): String {
        var result = template
        replacements.forEach { (key, value) ->
            // bookUrl 是完整链接，不能 URL 编码；只有搜索词/页码才需要编码。
            val replacement = if (key.equals("bookUrl", ignoreCase = true)) {
                value
            } else {
                URLEncoder.encode(value, "UTF-8")
            }
            result = result
                .replace("{{$key}}", replacement)
                .replace("{{${key.uppercase()}}}", replacement)
                .replace("{{ ${key} }}", replacement)
        }
        // Legado 的 POST 参数后缀：,{"method":"POST",...}
        val optionIndex = result.indexOf(",{")
        if (optionIndex > 0 && result.substring(optionIndex).trimStart().startsWith(",{")) {
            result = result.substring(0, optionIndex)
        }
        return result
    }

    private fun fetch(url: String, referer: String? = null): String {
        val httpUrl = url.toHttpUrlOrNull()
            ?: throw IllegalStateException("书源地址不是完整链接：$url")
        val builder = Request.Builder()
            .url(httpUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        referer?.let { builder.header("Referer", it) }
        val call = client.newCall(builder.build())
        call.timeout().timeout(20, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("请求失败：HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    /**
     * 列表规则（搜索列表/目录列表）同样要先把 Legado 写法翻译成 CSS。
     * 之前直接把 class.book-list@tag.li 丢给 Jsoup，匹配不到任何元素，搜索和目录就会是空的。
     */
    private fun selectorOf(rule: String?, fallback: String = "a"): String {
        if (rule.isNullOrBlank()) return fallback
        val selector = rule.split("||")
            .map { parseRule(it.trim()).first }
            .firstOrNull { it.isNotBlank() }
        return selector ?: fallback
    }

    private fun extract(element: Element, rule: String?): String? {
        if (rule.isNullOrBlank()) return null
        val alternatives = rule.split("||").map { it.trim() }.filter { it.isNotEmpty() }
        for (alternative in alternatives) {
            val (selector, attribute) = parseRule(alternative)
            val target = if (selector.isBlank()) {
                element
            } else {
                runCatching { element.selectFirst(selector) }.getOrNull() ?: continue
            }
            val value = when (attribute) {
                null -> target.text()
                "html" -> target.html()
                else -> target.attr(attribute)
            }
            if (value.isNotBlank()) return value
        }
        return null
    }

    /**
     * 把 Legado 规则翻译成 CSS 选择器 + 属性名。
     * 例：`class.book-item@tag.a@href` -> (".book-item a", "href")
     *     `@css:.content p@text` -> (".content p", null)
     */
    private fun parseRule(rawRule: String): Pair<String, String?> {
        var rule = rawRule.trim().removePrefix("@css:").trim()
        if (rule.isEmpty()) return "" to null
        if (rule == "text" || rule == "@text") return "" to null

        val parts = rule.split('@').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "" to null

        val last = parts.last()
        val lastIsText = last == "text"
        val lastIsHtml = last == "html"
        val lastIsAttribute = !lastIsText && !lastIsHtml && isAttribute(last)

        val selectorParts = when {
            lastIsText || lastIsHtml || lastIsAttribute -> parts.dropLast(1)
            else -> parts
        }
        val selector = selectorParts
            .joinToString(" ") { translateSelector(it) }
            .trim()
        val attribute = when {
            lastIsText -> null
            lastIsHtml -> "html"
            lastIsAttribute -> last
            else -> null
        }
        return selector to attribute
    }

    private fun translateSelector(segment: String): String {
        val value = segment.trim()
        if (value.isEmpty()) return ""
        return when {
            value.startsWith("class.") ->
                "." + value.removePrefix("class.").trim().replace(" ", ".")

            value.startsWith("id.") ->
                "#" + value.removePrefix("id.").trim().replace(" ", "#")

            value.startsWith("tag.") -> {
                // tag.a.0 / tag.span.1 -> a / span
                value.removePrefix("tag.").split('.').firstOrNull().orEmpty()
            }

            else -> value
        }
    }

    private fun isAttribute(value: String): Boolean {
        if (value.startsWith("data-")) return true
        return value in setOf(
            "href", "src", "title", "alt", "content", "value", "style",
        )
    }

    private fun resolveUrl(base: String, value: String): String {
        if (isHttpUrl(value)) return value
        if (value.isBlank()) return base
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        return if (isHttpUrl(trimmed)) trimmed else "https://${trimmed.trimStart('/')}"
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("http://") || value.startsWith("https://")

    private fun applyReplaceRules(text: String, json: String?): String {
        if (json.isNullOrBlank()) return text
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return text
        var result = text
        for (i in 0 until array.length()) {
            when (val item = array.opt(i)) {
                is JSONObject -> {
                    val pattern = item.optString("pattern").ifBlank {
                        item.optString("rule")
                    }
                    val replacement = item.optString("replacement").ifBlank {
                        item.optString("to")
                    }
                    if (pattern.isNotBlank()) {
                        result = runCatching {
                            Regex(pattern).replace(result, replacement)
                        }.getOrDefault(result)
                    }
                }

                is String -> {
                    result = runCatching {
                        Regex(item).replace(result, "")
                    }.getOrDefault(result)
                }
            }
        }
        return result
    }
}
