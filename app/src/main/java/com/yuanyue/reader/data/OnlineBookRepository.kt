package com.yuanyue.reader.data

import com.yuanyue.reader.core.db.BookSourceDao
import com.yuanyue.reader.core.db.CachedContentDao
import com.yuanyue.reader.core.db.OnlineBookDao
import com.yuanyue.reader.core.db.OnlineChapterDao
import com.yuanyue.reader.model.BookSourceEntity
import com.yuanyue.reader.model.CachedChapterContentEntity
import com.yuanyue.reader.model.OnlineBookEntity
import com.yuanyue.reader.model.OnlineChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

sealed interface BookSourceAddResult {
    data class Added(
        val id: Long,
        val message: String,
        val warning: String? = null,
    ) : BookSourceAddResult

    /** 结构或网络检测未通过，用户可以确认后强制添加。 */
    data class Unavailable(val message: String) : BookSourceAddResult

    /** JSON 解析、下载等硬错误。 */
    data class Failed(val message: String) : BookSourceAddResult
}

data class BookSourceCandidate(
    val source: BookSourceEntity,
    val issues: List<String> = emptyList(),
    val duplicate: Boolean = false,
) {
    val importable: Boolean get() = issues.isEmpty() && !duplicate
}

data class BookSourceImportResult(
    val candidates: List<BookSourceCandidate>,
    val warnings: List<String> = emptyList(),
)

class OnlineBookRepository(
    private val sourceDao: BookSourceDao,
    private val bookDao: OnlineBookDao,
    private val chapterDao: OnlineChapterDao,
    private val cachedContentDao: CachedContentDao,
    private val ruleEngine: BookSourceRuleEngine,
    private val client: OkHttpClient,
) {
    val sources: Flow<List<BookSourceEntity>> = sourceDao.observeAll()
    val favoriteBooks: Flow<List<OnlineBookEntity>> = bookDao.observeFavorites()

    fun books(sourceId: Long): Flow<List<OnlineBookEntity>> =
        bookDao.observeBySource(sourceId)

    suspend fun toggleFavorite(bookId: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        bookDao.updateFavorite(bookId, favorite)
    }

    /**
     * 添加书源：先解析，再实际跑一次搜索/目录/正文检测，通过后才入库。
     * skipValidation 用于用户确认“仍然添加”时跳过网络检测。
     */
    suspend fun addSource(
        input: String,
        skipValidation: Boolean = false,
    ): BookSourceAddResult = withContext(Dispatchers.IO) {
        val json = try {
            val trimmed = input.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                fetchJson(trimmed)
            } else {
                input
            }
        } catch (error: Exception) {
            return@withContext BookSourceAddResult.Failed(
                "书源下载失败：${error.message ?: "无法访问该地址"}",
            )
        }

        val source = try {
            BookSourceImporter.fromJson(json)
        } catch (error: Exception) {
            return@withContext BookSourceAddResult.Failed(
                error.message ?: "书源 JSON 格式不正确",
            )
        }

        if (sourceDao.getByUrl(source.url) != null) {
            return@withContext BookSourceAddResult.Failed("书源「${source.name}」已经添加过了")
        }

        var message = "已添加（未检测）"
        var warning: String? = null
        if (!skipValidation) {
            val test = runCatching { ruleEngine.testSource(source) }
                .getOrElse {
                    BookSourceTestResult(
                        ok = false,
                        message = "书源不可用：${it.message ?: "检测失败"}",
                    )
                }
            if (!test.ok) {
                return@withContext BookSourceAddResult.Unavailable(test.message)
            }
            message = test.message
            warning = test.warning
        }

        val id = sourceDao.insert(source)
        BookSourceAddResult.Added(id = id, message = message, warning = warning)
    }

    /**
     * 解析用户输入的书源链接 / JSON 文本，返回可选择的候选书源。
     *
     * 支持直接 JSON、JSONL、JSON 数组、Legado 分享链接、订阅页面和纯链接列表。
     * 这里只做结构分析（缺字段、是否已存在），网络可用性由调用方按需检测。
     */
    suspend fun importCandidates(input: String): BookSourceImportResult =
        withContext(Dispatchers.IO) {
            val trimmed = input.trim()
            if (trimmed.isBlank()) throw IllegalArgumentException("请输入书源链接或 JSON 内容")

            val collected = mutableListOf<BookSourceEntity>()
            val parseFailures = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            fun collectContent(content: String) {
                val parsed = BookSourceImporter.parseSources(content)
                collected += parsed.sources
                parseFailures += parsed.failures
            }

            val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }
                ?.trimStart()
                .orEmpty()
            val looksLikeJson = firstLine.startsWith("{") || firstLine.startsWith("[")

            if (looksLikeJson) {
                collectContent(trimmed)
            } else {
                val links = BookSourceLinkParser.extractLinks(trimmed)
                if (links.isEmpty()) {
                    // 没有链接时再尝试按 JSONL 解析一次
                    collectContent(trimmed)
                } else {
                    collectFromLinks(links, collected, parseFailures, warnings)
                }
            }

            if (collected.isEmpty()) {
                val detail = (parseFailures + warnings).take(3).joinToString("；")
                throw IllegalStateException(
                    if (detail.isBlank()) "没有解析到可用的书源" else "没有解析到可用的书源：$detail",
                )
            }

            val existing = sourceDao.getAllUrls().map { it.trim() }.toHashSet()
            val uniqueSources = collected
                .filter { it.url.isNotBlank() }
                .distinctBy { it.url.trim() }
            if (collected.size > uniqueSources.size) {
                warnings += "已合并 ${collected.size - uniqueSources.size} 个重复书源"
            }

            val candidates = uniqueSources.map { source ->
                val key = source.url.trim()
                BookSourceCandidate(
                    source = source.copy(url = key),
                    issues = BookSourceImporter.missingFields(source),
                    duplicate = key in existing,
                )
            }

            if (parseFailures.isNotEmpty()) {
                warnings += "已跳过 ${parseFailures.size} 个无法解析的条目"
                warnings += parseFailures.take(3)
            }

            BookSourceImportResult(
                candidates = candidates,
                warnings = warnings.distinct(),
            )
        }

    /** 批量添加已解析的书源，跳过重复项和结构不完整的项，返回实际添加数量。 */
    suspend fun addSources(sources: List<BookSourceEntity>): Int = withContext(Dispatchers.IO) {
        val existing = sourceDao.getAllUrls().map { it.trim() }.toHashSet()
        var added = 0
        sources.forEach { source ->
            val key = source.url.trim()
            if (key.isBlank() || key in existing) return@forEach
            if (BookSourceImporter.missingFields(source).isNotEmpty()) return@forEach
            runCatching {
                sourceDao.insert(source.copy(url = key))
                existing += key
                added++
            }
        }
        added
    }

    suspend fun testSource(source: BookSourceEntity): BookSourceTestResult =
        ruleEngine.testSource(source)

    private suspend fun collectFromLinks(
        links: List<String>,
        collected: MutableList<BookSourceEntity>,
        parseFailures: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val queue = ArrayDeque<Pair<String, Int>>()
        links.take(MAX_SOURCE_LINKS).forEach { queue.add(it to 0) }
        val visited = mutableSetOf<String>()
        var fetches = 0

        while (queue.isNotEmpty() && fetches < MAX_FETCHES) {
            val (url, depth) = queue.removeFirst()
            if (!visited.add(url)) continue
            fetches++

            val body = try {
                fetchText(url)
            } catch (error: Exception) {
                warnings += "无法访问 $url：${error.message ?: "网络错误"}"
                continue
            }
            val start = body.trimStart()

            when {
                start.startsWith("[") || start.startsWith("{") -> {
                    try {
                        val parsed = BookSourceImporter.parseSources(body)
                        collected += parsed.sources
                        parseFailures += parsed.failures
                    } catch (error: Exception) {
                        warnings += "$url 解析失败：${error.message ?: "格式不正确"}"
                    }
                }

                start.startsWith("<") -> {
                    val childLinks = BookSourceLinkParser.extractHtmlJsonLinks(body, url)
                    if (childLinks.isNotEmpty()) {
                        warnings += "从订阅页面解析到 ${childLinks.size} 个书源链接"
                        if (depth < MAX_LINK_DEPTH) {
                            childLinks.take(MAX_CHILD_LINKS).forEach { queue.add(it to depth + 1) }
                        }
                    } else {
                        val embedded = BookSourceLinkParser.extractEmbeddedJson(body)
                        if (embedded != null) {
                            try {
                                val parsed = BookSourceImporter.parseSources(embedded)
                                collected += parsed.sources
                                parseFailures += parsed.failures
                            } catch (error: Exception) {
                                warnings += "$url 页面内嵌书源解析失败：${error.message ?: "格式不正确"}"
                            }
                        } else {
                            warnings += "$url 页面中没有找到 JSON 书源链接"
                        }
                    }
                }

                else -> {
                    val nestedLinks = BookSourceLinkParser.extractLinks(body)
                        .filter { it != url }
                    if (nestedLinks.isNotEmpty()) {
                        if (depth < MAX_LINK_DEPTH) {
                            nestedLinks.take(MAX_CHILD_LINKS).forEach { queue.add(it to depth + 1) }
                        }
                    } else {
                        try {
                            val parsed = BookSourceImporter.parseSources(body)
                            collected += parsed.sources
                            parseFailures += parsed.failures
                        } catch (error: Exception) {
                            warnings += "$url 内容无法解析：${error.message ?: "格式不正确"}"
                        }
                    }
                }
            }
        }
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept", "application/json, text/plain, text/html, */*")
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(20, java.util.concurrent.TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: return ""
            val contentLength = body.contentLength()
            if (contentLength > MAX_CONTENT_BYTES) {
                throw IllegalStateException("内容超过 ${MAX_CONTENT_BYTES / 1024 / 1024}MB")
            }
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_CONTENT_BYTES) {
                        throw IllegalStateException("内容超过 ${MAX_CONTENT_BYTES / 1024 / 1024}MB")
                    }
                }
                return decodeText(output.toByteArray())
            }
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val offset = if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        val utf8 = String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            runCatching {
                String(bytes, offset, bytes.size - offset, charset("GBK"))
            }.getOrDefault(utf8)
        } else {
            utf8
        }
    }

    private fun fetchJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(20, java.util.concurrent.TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    suspend fun deleteSource(source: BookSourceEntity) = withContext(Dispatchers.IO) {
        val books = bookDao.observeBySource(source.id).first()
        books.forEach { book ->
            val chapters = chapterDao.getByBook(book.id)
            if (chapters.isNotEmpty()) {
                cachedContentDao.deleteByChapters(chapters.map { it.id })
                chapterDao.deleteByBook(book.id)
            }
        }
        bookDao.deleteBySource(source.id)
        sourceDao.delete(source)
    }

    suspend fun search(
        sourceId: Long,
        keyword: String,
        page: Int = 1,
    ): OperationResult<List<OnlineBookEntity>> = withContext(Dispatchers.IO) {
        val source = sourceDao.getById(sourceId)
            ?: return@withContext OperationResult.Error("书源不存在")
        runCatching {
            val books = ruleEngine.search(source, keyword, page)
            if (books.isEmpty()) {
                emptyList()
            } else {
                val ids = bookDao.insertAll(books)
                books.mapIndexed { index, book ->
                    val id = ids.getOrNull(index)?.takeIf { it > 0 } ?: book.id
                    book.copy(id = id)
                }
            }
        }.fold(
            onSuccess = { OperationResult.Success(it) },
            onFailure = { OperationResult.Error(it.message ?: "搜索失败") },
        )
    }

    suspend fun getBook(bookId: Long): OnlineBookEntity? =
        bookDao.getById(bookId)

    /**
     * 书源没有“推荐/发现”接口时，用几个常见关键词抓一批书作为推荐。
     * 只在搜索成功但结果为空时继续换关键词；网络/规则错误直接返回，避免连续超时。
     */
    suspend fun recommend(
        sourceId: Long,
        keywords: List<String> = listOf("斗罗大陆", "我", "小说"),
    ): OperationResult<Int> = withContext(Dispatchers.IO) {
        var searched = false
        for (keyword in keywords) {
            when (val result = search(sourceId, keyword)) {
                is OperationResult.Success -> {
                    searched = true
                    if (result.data.isNotEmpty()) {
                        return@withContext OperationResult.Success(result.data.size)
                    }
                }

                is OperationResult.Error -> {
                    return@withContext OperationResult.Error("获取推荐失败：${result.message}")
                }
            }
        }
        OperationResult.Error(
            if (searched) "该书源没有搜索到推荐书籍，可手动搜索书名" else "该书源暂不支持获取推荐",
        )
    }

    suspend fun detail(book: OnlineBookEntity): OperationResult<OnlineBookEntity> =
        withContext(Dispatchers.IO) {
            val source = sourceDao.getById(book.sourceId)
                ?: return@withContext OperationResult.Error("书源不存在")
            runCatching {
                val updated = ruleEngine.detail(source, book)
                bookDao.insertAll(listOf(updated))
                updated
            }.fold(
                onSuccess = { OperationResult.Success(it) },
                onFailure = { OperationResult.Error(it.message ?: "详情获取失败") },
            )
        }

    suspend fun chapters(book: OnlineBookEntity): OperationResult<List<OnlineChapterEntity>> =
        withContext(Dispatchers.IO) {
            val source = sourceDao.getById(book.sourceId)
                ?: return@withContext OperationResult.Error("书源不存在")
            runCatching {
                val chapters = ruleEngine.chapters(source, book)
                if (chapters.isNotEmpty()) {
                    chapterDao.deleteByBook(book.id)
                    chapterDao.insertAll(chapters)
                }
                // 必须重新查询：入库后章节才有自增 id，直接返回临时对象的 id 全是 0，
                // 会导致目录 LazyColumn key 重复而崩溃、点章节也永远打开第 1 章。
                val persisted = chapterDao.getByBook(book.id)
                if (persisted.isNotEmpty()) persisted else chapters
            }.fold(
                onSuccess = { OperationResult.Success(it) },
                onFailure = { OperationResult.Error(it.message ?: "目录获取失败") },
            )
        }

    suspend fun getChapters(bookId: Long): List<OnlineChapterEntity> =
        chapterDao.getByBook(bookId)

    suspend fun content(chapter: OnlineChapterEntity): OperationResult<String> =
        withContext(Dispatchers.IO) {
            cachedContentDao.get(chapter.id)?.let {
                return@withContext OperationResult.Success(it)
            }
            val source = sourceDao.getById(chapter.sourceId)
                ?: return@withContext OperationResult.Error("书源不存在")
            runCatching {
                val content = ruleEngine.content(source, chapter.url)
                if (content.isNotBlank()) {
                    cachedContentDao.insert(
                        CachedChapterContentEntity(chapterId = chapter.id, content = content),
                    )
                }
                content
            }.fold(
                onSuccess = { OperationResult.Success(it) },
                onFailure = { OperationResult.Error(it.message ?: "正文获取失败") },
            )
        }

    private companion object {
        const val MAX_SOURCE_LINKS = 8
        const val MAX_CHILD_LINKS = 8
        const val MAX_FETCHES = 16
        const val MAX_LINK_DEPTH = 1
        const val MAX_CONTENT_BYTES = 8 * 1024 * 1024
    }
}
