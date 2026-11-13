package com.yuanyue.reader.data

import com.yuanyue.reader.model.BookSourceEntity
import com.yuanyue.reader.model.OnlineBookEntity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class BookSourceRuleEngineTest {
    @Test
    fun `resolves relative search url and parses books`() = runBlocking {
        val body = """
            <!doctype html>
            <html><body>
              <ul>
                <li class="book">
                  <a class="name" href="/book/1">斗罗大陆</a>
                  <span class="author">唐家三少</span>
                </li>
                <li class="book">
                  <a class="name" href="/book/2">完美世界</a>
                  <span class="author">辰东</span>
                </li>
              </ul>
            </body></html>
        """.trimIndent()
        val bytes = body.toByteArray(Charsets.UTF_8)
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val serverThread = thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/html; charset=utf-8\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(Charsets.US_ASCII)
                socket.getOutputStream().use { output ->
                    output.write(header)
                    output.write(bytes)
                    output.flush()
                }
            }
        }

        try {
            val source = BookSourceEntity(
                id = 1,
                name = "本地相对链接源",
                url = "http://127.0.0.1:$port",
                searchUrl = "/search?q={{key}}",
                searchListRule = "class.book",
                bookNameRule = "class.name@text",
                bookAuthorRule = "class.author@text",
                bookUrlRule = "class.name@href",
                chapterListRule = "#chapters a",
                chapterUrlRule = "href",
                contentRule = "#content",
            )

            val books = BookSourceRuleEngine(OkHttpClient()).search(source, "斗罗大陆")

            assertEquals(2, books.size)
            assertEquals("斗罗大陆", books[0].title)
            assertEquals("唐家三少", books[0].author)
            assertEquals("http://127.0.0.1:$port/book/1", books[0].bookUrl)
            assertTrue(books.all { it.sourceId == 1L })
        } finally {
            server.close()
            serverThread.join(2_000)
        }
    }

    @Test
    fun `loads chapters from toc selector and extracts real content`() = runBlocking {
        val detailHtml = """
            <html><body>
              <h1>斗罗大陆</h1>
              <a class="toc" href="/book/1/toc">目录</a>
            </body></html>
        """.trimIndent()
        val tocHtml = """
            <html><body>
              <ul class="chapter-list">
                <li><a href="/chapter/1">第一章 开端</a></li>
                <li><a href="/chapter/2">第二章 转折</a></li>
              </ul>
            </body></html>
        """.trimIndent()
        val chapterHtml = """
            <html><body>
              <div id="content">
                <p>第一段正文，不是简介。</p>
                <p>第二段正文，要能完整读出来。</p>
              </div>
            </body></html>
        """.trimIndent()

        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val serverThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    val requestLine = reader.readLine() ?: return@use
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val path = requestLine.split(" ").getOrNull(1).orEmpty()
                    val body = when {
                        path.startsWith("/book/1/toc") -> tocHtml
                        path == "/book/1" || path.startsWith("/book/1?") -> detailHtml
                        path.startsWith("/chapter/") -> chapterHtml
                        else -> ""
                    }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/html; charset=utf-8\r\n")
                        append("Content-Length: ${bytes.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(Charsets.US_ASCII)
                    it.getOutputStream().use { output ->
                        output.write(header)
                        output.write(bytes)
                        output.flush()
                    }
                }
            }
        }

        try {
            val source = BookSourceEntity(
                id = 1,
                name = "目录测试源",
                url = "http://127.0.0.1:$port",
                searchUrl = "/search?q={{key}}",
                searchListRule = "class.book",
                bookNameRule = "class.name@text",
                bookUrlRule = "class.name@href",
                tocUrlRule = "class.toc@href",
                chapterListRule = "class.chapter-list@tag.li@tag.a",
                chapterNameRule = "text",
                chapterUrlRule = "href",
                contentRule = "id.content",
            )
            val book = OnlineBookEntity(
                sourceId = 1,
                title = "斗罗大陆",
                bookUrl = "http://127.0.0.1:$port/book/1",
            )
            val engine = BookSourceRuleEngine(OkHttpClient())

            val chapters = engine.chapters(source, book)
            assertEquals(2, chapters.size)
            assertEquals("第一章 开端", chapters[0].title)
            assertEquals("http://127.0.0.1:$port/chapter/1", chapters[0].url)

            val content = engine.content(source, chapters[0].url)
            assertTrue(content.contains("第一段正文"))
            assertTrue(content.contains("第二段正文"))
        } finally {
            server.close()
            serverThread.join(2_000)
        }
    }
}
