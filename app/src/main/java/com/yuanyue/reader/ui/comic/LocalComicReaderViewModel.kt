package com.yuanyue.reader.ui.comic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.LibraryRepository
import com.yuanyue.reader.data.parser.ComicPage
import com.yuanyue.reader.model.BookEntity
import kotlinx.coroutines.launch

class LocalComicReaderViewModel(
    private val bookId: Long,
    private val repository: LibraryRepository,
) : ViewModel() {
    var book by mutableStateOf<BookEntity?>(null)
        private set
    var pages by mutableStateOf<List<ComicPage>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val loaded = repository.getBook(bookId)
                if (loaded == null) {
                    error = "书籍不存在"
                } else {
                    book = loaded
                    val result = repository.loadComicPages(loaded)
                    if (result.isEmpty()) {
                        error = "这本 EPUB 中没有找到漫画图片"
                    } else {
                        pages = result
                    }
                }
            } catch (exception: Exception) {
                error = exception.message ?: "漫画加载失败"
            } finally {
                isLoading = false
            }
        }
    }
}
