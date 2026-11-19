package com.yuanyue.reader.ui.comic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.AnnotationRepository
import com.yuanyue.reader.data.LibraryRepository
import com.yuanyue.reader.data.parser.ComicPage
import com.yuanyue.reader.model.AnnotationDraft
import com.yuanyue.reader.model.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalComicReaderViewModel(
    private val bookId: Long,
    private val repository: LibraryRepository,
    private val annotationRepository: AnnotationRepository,
) : ViewModel() {
    val annotations = annotationRepository.observe("localcomic:$bookId").stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
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

    fun addAnnotation(draft: AnnotationDraft) {
        viewModelScope.launch { annotationRepository.add("localcomic:$bookId", draft) }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch { annotationRepository.delete(id) }
    }
}
