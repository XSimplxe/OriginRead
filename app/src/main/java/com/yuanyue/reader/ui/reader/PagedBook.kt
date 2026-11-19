package com.yuanyue.reader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.yuanyue.reader.model.BookChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class BookPage(
    val chapterIndex: Int,
    val chapterTitle: String,
    val pageIndex: Int,
    val pageCount: Int,
    val text: AnnotatedString,
)

/**
 * 把整本本地书的章节一次性排版为扁平页列表，供真正的横向自由翻阅使用。
 * 只对本地完整章节调用；在线书仍按当前章节加载。
 */
@Composable
fun rememberPagedBook(
    chapters: List<BookChapter>,
    style: TextStyle,
    pageWidth: Int,
    pageHeight: Int,
    enabled: Boolean,
    indentFirstLine: Boolean,
): List<BookPage>? {
    val textMeasurer = rememberTextMeasurer()
    var pages by remember(chapters, style, pageWidth, pageHeight, enabled, indentFirstLine) {
        mutableStateOf<List<BookPage>?>(null)
    }

    LaunchedEffect(chapters, style, pageWidth, pageHeight, enabled, indentFirstLine) {
        if (!enabled || pageWidth <= 0 || pageHeight <= 0) {
            pages = emptyList()
            return@LaunchedEffect
        }
        delay(160)
        pages = withContext(Dispatchers.Default) {
            val result = mutableListOf<BookPage>()
            for ((chapterIndex, chapter) in chapters.withIndex()) {
                coroutineContext.ensureActive()
                val content = if (indentFirstLine) {
                    chapter.content.lineSequence().joinToString("\n") { line ->
                        if (line.isBlank()) "" else "\u3000\u3000${line.trimStart()}"
                    }
                } else {
                    chapter.content
                }
                val chapterPages = paginate(content, style, pageWidth, pageHeight, textMeasurer)
                chapterPages.forEachIndexed { pageIndex, text ->
                    result += BookPage(
                        chapterIndex = chapterIndex,
                        chapterTitle = chapter.title,
                        pageIndex = pageIndex,
                        pageCount = chapterPages.size,
                        text = text,
                    )
                }
            }
            result
        }
    }

    return pages
}
