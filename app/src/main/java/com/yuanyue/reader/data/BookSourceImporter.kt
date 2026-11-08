package com.yuanyue.reader.data

import com.yuanyue.reader.model.BookSourceEntity
import org.json.JSONArray
import org.json.JSONObject

data class ParsedBookSources(
    val sources: List<BookSourceEntity>,
    val failures: List<String> = emptyList(),
)

/**
 * 书源 JSON 解析。
 *
 * 兼容：
 * - 本应用早期的扁平字段；
 * - Legado「阅读 3.0」的嵌套字段（ruleSearch / ruleBookInfo / ruleToc / ruleContent）；
 * - 单对象、JSON 数组、JSONL（每行一个对象）、带 bookSources/sources/data 包装的对象。
 */
object BookSourceImporter {
    private val wrapperKeys = listOf(
        "bookSources",
        "bookSourceList",
        "bookSource",
        "sources",
        "data",
        "books",
        "list",
        "items",
    )

    fun fromJson(json: String): BookSourceEntity {
        val cleaned = clean(json)
        if (cleaned.startsWith("[")) {
            val count = runCatching { JSONArray(cleaned).length() }.getOrDefault(0)
            throw IllegalArgumentException(
                if (count > 1) {
                    "检测到 $count 个书源，请使用批量导入选择要添加的书源"
                } else {
                    "书源 JSON 格式不正确：请粘贴单个书源对象"
                },
            )
        }
        val root = parseObject(cleaned)
        return fromJsonObject(root)
    }

    /** 解析单个或多个书源，供批量导入使用。 */
    fun parseSources(raw: String): ParsedBookSources {
        val cleaned = clean(raw)
        if (cleaned.isBlank()) throw IllegalArgumentException("书源内容为空")

        if (cleaned.startsWith("[")) {
            val array = runCatching { JSONArray(cleaned) }.getOrElse {
                throw IllegalArgumentException("书源 JSON 数组解析失败：${it.message ?: "格式不正确"}")
            }
            return parseArray(array)
        }

        if (cleaned.startsWith("{")) {
            // 先识别 JSONL，避免 org.json 只解析第一行而悄悄丢掉后面的书源。
            val lines = jsonLines(cleaned)
            if (lines.size > 1) return parseJsonLines(lines)

            val root = runCatching { JSONObject(cleaned) }.getOrNull()
            if (root != null) {
                if (looksLikeSource(root)) {
                    return ParsedBookSources(listOf(fromJsonObject(root)))
                }
                findWrapperArray(root)?.let { return parseArray(it) }
                return ParsedBookSources(
                    sources = emptyList(),
                    failures = listOf("JSON 中没有找到书源数据（缺少 bookSourceName / bookSourceUrl）"),
                )
            }
            throw IllegalArgumentException("书源 JSON 解析失败：格式不正确")
        }

        // 纯 JSONL 文本
        val lines = jsonLines(cleaned)
        if (lines.isNotEmpty()) return parseJsonLines(lines)

        throw IllegalArgumentException("无法识别的内容格式：请粘贴书源 JSON、JSONL 或订阅链接")
    }

    private fun parseArray(array: JSONArray): ParsedBookSources {
        val sources = mutableListOf<BookSourceEntity>()
        val failures = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index)
            if (obj == null) {
                failures += "第 ${index + 1} 项不是 JSON 对象"
                continue
            }
            runCatching { fromJsonObject(obj) }
                .onSuccess { sources += it }
                .onFailure { failures += "第 ${index + 1} 个书源：${it.message ?: "格式不正确"}" }
        }
        return ParsedBookSources(sources, failures)
    }

    private fun parseJsonLines(lines: List<String>): ParsedBookSources {
        val sources = mutableListOf<BookSourceEntity>()
        val failures = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            runCatching { fromJsonObject(parseObject(line)) }
                .onSuccess { sources += it }
                .onFailure { failures += "第 ${index + 1} 行：${it.message ?: "格式不正确"}" }
        }
        if (sources.isEmpty() && failures.isNotEmpty()) {
            throw IllegalArgumentException("书源 JSON 解析失败：${failures.first()}")
        }
        return ParsedBookSources(sources, failures)
    }

    private fun jsonLines(text: String): List<String> =
        text.lineSequence()
            .map { it.trim().trimEnd(',') }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .toList()

    private fun parseObject(text: String): JSONObject =
        runCatching { JSONObject(text) }.getOrElse {
            throw IllegalArgumentException("书源 JSON 解析失败：${it.message ?: "格式不正确"}")
        }

    private fun findWrapperArray(root: JSONObject, depth: Int = 0): JSONArray? {
        if (depth > 2) return null
        for (key in wrapperKeys) {
            val array = root.optJSONArray(key)
            if (array != null && array.length() > 0) {
                val first = array.optJSONObject(0)
                if (first != null && looksLikeSource(first)) return array
            }
            val child = root.optJSONObject(key) ?: continue
            if (looksLikeSource(child)) continue
            findWrapperArray(child, depth + 1)?.let { return it }
        }
        // 兼容包装对象里用了其它键名的情况
        val keys = root.keys()
        while (keys.hasNext()) {
            val array = root.optJSONArray(keys.next()) ?: continue
            val first = array.optJSONObject(0) ?: continue
            if (looksLikeSource(first)) return array
        }
        return null
    }

    private fun looksLikeSource(obj: JSONObject): Boolean =
        firstString(obj, "bookSourceName", "name") != null &&
            firstString(obj, "bookSourceUrl", "url") != null

    private fun fromJsonObject(root: JSONObject): BookSourceEntity {
        val search = root.optJSONObject("ruleSearch")
        val info = root.optJSONObject("ruleBookInfo")
        val toc = root.optJSONObject("ruleToc")
        val content = root.optJSONObject("ruleContent")

        val name = firstString(root, "bookSourceName", "name")
            ?: throw IllegalArgumentException("缺少书源名称（bookSourceName）")
        val url = firstString(root, "bookSourceUrl", "url")
            ?.trim()
            ?.let(::normalizeSourceUrl)
            ?: throw IllegalArgumentException("缺少书源地址（bookSourceUrl）")

        return BookSourceEntity(
            name = name,
            url = url,
            searchUrl = firstString(root, "searchUrl"),
            searchListRule = firstString(search, "bookList")
                ?: firstString(root, "bookList", "searchList"),
            bookNameRule = firstString(search, "name")
                ?: firstString(root, "bookName", "nameRule"),
            bookAuthorRule = firstString(search, "author")
                ?: firstString(root, "bookAuthor", "author"),
            bookCoverRule = firstString(search, "coverUrl", "cover")
                ?: firstString(root, "bookCover", "cover"),
            bookUrlRule = firstString(search, "bookUrl")
                ?: firstString(root, "bookUrl", "detailUrl"),
            introRule = firstString(info, "intro") ?: firstString(root, "intro"),
            coverRule = firstString(info, "coverUrl", "cover") ?: firstString(root, "cover"),
            tocUrlRule = firstString(info, "tocUrl") ?: firstString(root, "tocUrl"),
            chapterListRule = firstString(toc, "chapterList")
                ?: firstString(root, "chapterList"),
            chapterNameRule = firstString(toc, "chapterName")
                ?: firstString(root, "chapterName"),
            chapterUrlRule = firstString(toc, "chapterUrl")
                ?: firstString(root, "chapterUrl"),
            contentRule = firstString(content, "content") ?: firstString(root, "content"),
            contentReplaceRules = firstString(content, "replaceRegex")
                ?: firstString(root, "contentReplace"),
        )
    }

    /** 返回书源缺少的必要字段（用于添加前的结构检查）。 */
    fun missingFields(source: BookSourceEntity): List<String> {
        val missing = mutableListOf<String>()
        if (source.searchUrl.isNullOrBlank()) missing += "搜索地址 searchUrl"
        if (source.searchListRule.isNullOrBlank()) missing += "搜索结果列表 ruleSearch.bookList"
        if (source.bookUrlRule.isNullOrBlank()) missing += "详情链接 ruleSearch.bookUrl"
        if (source.chapterListRule.isNullOrBlank()) missing += "章节目录 ruleToc.chapterList"
        if (source.chapterUrlRule.isNullOrBlank()) missing += "章节链接 ruleToc.chapterUrl"
        if (source.contentRule.isNullOrBlank()) missing += "正文规则 ruleContent.content"
        return missing
    }

    fun toJson(source: BookSourceEntity): String {
        val root = JSONObject().apply {
            put("bookSourceName", source.name)
            put("bookSourceUrl", source.url)
            put("searchUrl", source.searchUrl ?: "")
            put("bookList", source.searchListRule ?: "")
            put("bookName", source.bookNameRule ?: "")
            put("bookAuthor", source.bookAuthorRule ?: "")
            put("bookCover", source.bookCoverRule ?: "")
            put("bookUrl", source.bookUrlRule ?: "")
            put("intro", source.introRule ?: "")
            put("tocUrl", source.tocUrlRule ?: "")
            put("chapterList", source.chapterListRule ?: "")
            put("chapterName", source.chapterNameRule ?: "")
            put("chapterUrl", source.chapterUrlRule ?: "")
            put("content", source.contentRule ?: "")
            source.contentReplaceRules?.let { put("contentReplace", it) }
        }
        return root.toString(2)
    }

    private fun firstString(root: JSONObject?, vararg keys: String): String? {
        if (root == null) return null
        keys.forEach { key ->
            val value = root.optString(key)
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun normalizeSourceUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "https://${trimmed.trimStart('/')}"
        }
    }

    private fun clean(input: String): String =
        input.trim().removePrefix("\uFEFF").trim()
}
