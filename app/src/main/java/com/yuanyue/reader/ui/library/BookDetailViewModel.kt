package com.yuanyue.reader.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.BookTags
import com.yuanyue.reader.data.LibraryRepository
import com.yuanyue.reader.model.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val bookId: Long,
    private val repository: LibraryRepository,
) : ViewModel() {
    val book = repository.observeBook(bookId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            repository.updateFavorite(book.id, !book.isFavorite)
        }
    }

    suspend fun save(
        title: String,
        author: String,
        description: String,
        tags: String,
    ): Boolean {
        if (title.isBlank()) {
            error = "书名不能为空"
            return false
        }
        isSaving = true
        error = null
        return try {
            repository.updateBookInfo(
                bookId = bookId,
                title = title,
                author = author,
                description = description,
                tags = BookTags.normalize(tags),
            )
            true
        } catch (failure: Exception) {
            error = "保存失败：${failure.message ?: "未知错误"}"
            false
        } finally {
            isSaving = false
        }
    }
}
