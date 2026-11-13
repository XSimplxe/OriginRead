package com.yuanyue.reader.data.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.w3c.dom.Element as XmlElement
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class ComicPage(
    val title: String,
    val imagePath: String,
)

/**
 * 图片型 EPUB（漫画）检测与图片提取。
 * 文本型 EPUB 继续走 EpubParser；图片页占比高、正文很少的 EPUB 自动按漫画打开。
 */
object EpubComicParser {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String,
    )
    private data class OpfData(
        val baseDir: String,
        val manifest: Map<String, ManifestItem>,
        val spine: List<String>,
    )

    fun isComic(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            val opf = readOpf(zip) ?: return@use false
            val manifestImages = opf.manifest.values.count { it.mediaType.startsWith("image/") }
            var spineImages = 0
            var textLength = 0
            for (idref in opf.spine) {
                val href = opf.manifest[idref]?.href ?: continue
                val path = resolvePath(opf.baseDir, href)
                val entry = zip.getEntry(path) ?: continue
                val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val document = Jsoup.parse(html)
                spineImages += document.select("img").size
                textLength += document.body()?.text()?.trim()?.length ?: 0
            }
            val images = maxOf(manifestImages, spineImages)
            images >= 2 && textLength < 800
        }
    }.getOrDefault(false)

    fun extract(file: File, cacheDir: File): List<ComicPage> = runCatching {
        val complete = File(cacheDir, ".complete")
        if (complete.exists()) {
            val cached = imageFiles(cacheDir)
            if (cached.isNotEmpty()) {
                return@runCatching cached.mapIndexed { index, image ->
                    ComicPage("第 ${index + 1} 页", image.absolutePath)
                }
            }
        }

        ZipFile(file).use { zip ->
            val opf = readOpf(zip) ?: throw IllegalArgumentException("EPUB 缺少 OPF 文件")
            cacheDir.mkdirs()
            cacheDir.listFiles()?.forEach { it.delete() }
            val pages = mutableListOf<ComicPage>()
            val seen = mutableSetOf<String>()
            var pageNumber = 0

            fun addImage(entryPath: String, title: String) {
                if (!seen.add(entryPath)) return
                val extension = entryPath.substringAfterLast('.', "").lowercase()
                if (extension !in imageExtensions) return
                val entry = zip.getEntry(entryPath) ?: return
                val output = File(cacheDir, "page_%04d.%s".format(pageNumber + 1, extension))
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { target -> input.copyTo(target) }
                }
                pages += ComicPage(title.ifBlank { "第 ${pageNumber + 1} 页" }, output.absolutePath)
                pageNumber++
            }

            for (idref in opf.spine) {
                val item = opf.manifest[idref] ?: continue
                val htmlPath = resolvePath(opf.baseDir, item.href)
                val entry = zip.getEntry(htmlPath) ?: continue
                val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val document = Jsoup.parse(html)
                val title = document.selectFirst("h1, h2, h3")?.text()?.trim()
                    ?.ifBlank { null }
                    ?: document.title().trim().ifBlank { "第 ${pageNumber + 1} 页" }
                val baseForImage = htmlPath.substringBeforeLast('/', "")
                document.select("img").forEach { image ->
                    val raw = imageUrl(image)
                    if (raw.isNotBlank()) {
                        addImage(resolvePath(baseForImage, raw.substringBefore('#')), title)
                    }
                }
            }

            if (pages.isEmpty()) {
                val baseDir = opf.baseDir
                opf.manifest.values
                    .filter {
                        it.mediaType.startsWith("image/") &&
                            !it.properties.contains("cover-image") &&
                            !it.href.contains("cover", ignoreCase = true)
                    }
                    .sortedBy { it.href }
                    .forEach { item ->
                        addImage(resolvePath(baseDir, item.href), "第 ${pageNumber + 1} 页")
                    }
            }
            if (pages.isEmpty()) throw IllegalArgumentException("EPUB 漫画中没有找到图片")
            complete.writeText("ok")
            pages
        }
    }.getOrElse { error ->
        throw IllegalArgumentException(error.message ?: "EPUB 漫画解析失败")
    }

    private fun imageFiles(cacheDir: File): List<File> =
        cacheDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in imageExtensions }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun imageUrl(image: Element): String {
        val candidates = listOf(
            "src", "data-src", "data-original", "data-lazy-src", "data-echo", "data-url",
        )
        for (name in candidates) {
            val value = image.attr(name).trim()
            if (value.isNotBlank()) return value
        }
        val srcset = image.attr("srcset").trim()
        return srcset.substringBefore(',').substringBefore(' ').trim()
    }

    private fun readOpf(zip: ZipFile): OpfData? {
        val container = zip.getEntry("META-INF/container.xml") ?: return null
        val containerXml = zip.getInputStream(container).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val containerDoc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(InputSource(StringReader(containerXml)))
        val rootfiles = containerDoc.getElementsByTagNameNS("*", "rootfile")
        var opfPath: String? = null
        for (index in 0 until rootfiles.length) {
            val path = (rootfiles.item(index) as? XmlElement)?.getAttribute("full-path")
            if (!path.isNullOrBlank()) {
                opfPath = path
                break
            }
        }
        val opfEntry = zip.getEntry(opfPath ?: return null) ?: return null
        val opfXml = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(InputSource(StringReader(opfXml)))
        val root = document.documentElement
        val baseDir = opfPath.substringBeforeLast('/', "").let {
            if (it.isEmpty()) "" else "$it/"
        }

        val manifest = mutableMapOf<String, ManifestItem>()
        val items = root.getElementsByTagNameNS("*", "item")
        for (index in 0 until items.length) {
            val item = items.item(index) as? XmlElement ?: continue
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            if (id.isBlank() || href.isBlank()) continue
            manifest[id] = ManifestItem(
                href = href,
                mediaType = item.getAttribute("media-type").orEmpty(),
                properties = item.getAttribute("properties").orEmpty(),
            )
        }

        val spine = mutableListOf<String>()
        val refs = root.getElementsByTagNameNS("*", "itemref")
        for (index in 0 until refs.length) {
            val idref = (refs.item(index) as? XmlElement)?.getAttribute("idref")
            if (!idref.isNullOrBlank()) spine += idref
        }
        return OpfData(baseDir, manifest, spine)
    }

    private fun resolvePath(baseDir: String, href: String): String {
        var value = href
        if (value.startsWith("http://") || value.startsWith("https://")) {
            value = value.substringAfter("://").substringAfter('/')
        }
        value = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        val combined = when {
            value.startsWith("/") -> value.drop(1)
            baseDir.isEmpty() -> value
            else -> baseDir + value
        }
        val stack = ArrayDeque<String>()
        combined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }
}
