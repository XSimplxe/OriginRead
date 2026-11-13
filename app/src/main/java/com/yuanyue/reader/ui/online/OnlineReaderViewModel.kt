package com.yuanyue.reader.ui.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.OnlineBookRepository
import com.yuanyue.reader.data.OperationResult
import com.yuanyue.reader.data.PageMode
import com.yuanyue.reader.data.ReaderSettings
import com.yuanyue.reader.data.ReaderTheme
import com.yuanyue.reader.data.SettingsRepository
import com.yuanyue.reader.model.OnlineBookEntity
import com.yuanyue.reader.model.OnlineChapterEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnlineReaderViewModel(
    private val bookId: Long,
    private val chapterId: Long,
    private val repository: OnlineBookRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    var book by mutableStateOf<OnlineBookEntity?>(null)
        private set
    var chapters by mutableStateOf<List<OnlineChapterEntity>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var content by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val settings = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderSettings(),
    )

    init {
        load()
    }

    fun openChapter(chapter: OnlineChapterEntity) {
        val index = chapters.indexOfFirst { it.id == chapter.id }
        if (index >= 0) selectChapter(index)
    }

    fun selectChapter(index: Int) {
        if (index !in chapters.indices) return
        currentIndex = index
        loadContent(chapters[index])
    }

    fun retry() {
        val chapter = chapters.getOrNull(currentIndex)
        if (chapter != null && content.isBlank()) {
            loadContent(chapter)
        } else {
            load()
        }
    }

    private fun load() {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val loadedBook = repository.getBook(bookId)
                if (loadedBook == null) {
                    error = "书籍不存在"
                    return@launch
                }
                book = loadedBook
                chapters = repository.getChapters(bookId)
                if (chapters.isEmpty()) {
                    error = "没有获取到章节目录，可能是书源目录规则失效"
                    return@launch
                }
                currentIndex = chapters.indexOfFirst { it.id == chapterId }
                    .takeIf { it >= 0 }
                    ?: 0
                loadContentInternal(chapters[currentIndex])
            } catch (failure: Exception) {
                error = "打开章节失败：${failure.message ?: "未知错误"}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadContent(chapter: OnlineChapterEntity) {
        isLoading = true
        error = null
        content = ""
        viewModelScope.launch {
            try {
                loadContentInternal(chapter)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun loadContentInternal(chapter: OnlineChapterEntity) {
        when (val result = repository.content(chapter)) {
            is OperationResult.Success -> {
                if (result.data.isBlank()) {
                    error = "本章没有解析到正文，可能是书源正文规则失效"
                } else {
                    content = result.data
                }
            }

            is OperationResult.Error -> {
                error = result.message
            }
        }
    }

    fun updateFontSize(value: Float) {
        viewModelScope.launch { settingsRepository.updateFontSize(value) }
    }

    fun updateLineHeight(value: Float) {
        viewModelScope.launch { settingsRepository.updateLineHeight(value) }
    }

    fun updateTheme(value: ReaderTheme) {
        viewModelScope.launch { settingsRepository.updateTheme(value) }
    }

    fun updatePageMode(value: PageMode) {
        viewModelScope.launch { settingsRepository.updatePageMode(value) }
    }

    fun updateSerifFont(value: Boolean) {
        viewModelScope.launch { settingsRepository.updateSerifFont(value) }
    }

    fun updateParagraphSpacing(value: Float) {
        viewModelScope.launch { settingsRepository.updateParagraphSpacing(value) }
    }

    fun updateContentPadding(value: Float) {
        viewModelScope.launch { settingsRepository.updateContentPadding(value) }
    }

    fun updateIndentFirstLine(value: Boolean) {
        viewModelScope.launch { settingsRepository.updateIndentFirstLine(value) }
    }
}
