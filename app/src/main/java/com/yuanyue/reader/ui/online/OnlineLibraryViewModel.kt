package com.yuanyue.reader.ui.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.OnlineBookRepository
import com.yuanyue.reader.data.OperationResult
import com.yuanyue.reader.model.OnlineBookEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnlineLibraryViewModel(
    private val repository: OnlineBookRepository,
) : ViewModel() {
    val sources = repository.sources.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    var selectedSourceId by mutableStateOf<Long?>(null)
        private set
    var books by mutableStateOf<List<OnlineBookEntity>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<OnlineBookEntity>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var isRecommending by mutableStateOf(false)
        private set
    var recommendError by mutableStateOf<String?>(null)
        private set
    var recommendationLoaded by mutableStateOf(false)
        private set

    private var booksJob: Job? = null
    private var recommendJob: Job? = null
    private val recommendAttempted = mutableSetOf<Long>()
    private var knownSourceIds = emptySet<Long>()

    init {
        // 书源可能在页面创建之后才添加，这里持续跟随书源列表，自动选中第一个可用书源。
        viewModelScope.launch {
            repository.sources.collect { list ->
                val ids = list.map { it.id }.toSet()
                val addedIds = ids - knownSourceIds
                knownSourceIds = ids
                val current = selectedSourceId
                when {
                    list.isEmpty() -> {
                        selectedSourceId = null
                        books = emptyList()
                    }

                    current == null || current !in ids -> {
                        selectSource(list.first().id)
                    }

                    // 新导入书源后直接切到最新的书源，方便马上看到推荐书籍。
                    addedIds.isNotEmpty() -> {
                        addedIds.maxOrNull()?.let { selectSource(it) }
                    }
                }
            }
        }
    }

    fun selectSource(sourceId: Long) {
        booksJob?.cancel()
        recommendJob?.cancel()
        selectedSourceId = sourceId
        searchResults = emptyList()
        error = null
        recommendError = null
        recommendationLoaded = false
        isRecommending = false
        books = emptyList()

        booksJob = viewModelScope.launch {
            repository.books(sourceId).collect { list ->
                books = list
                // 新书源/还没有缓存时，自动抓一批书作为推荐，避免在线书架是空壳。
                if (list.isEmpty() && recommendAttempted.add(sourceId)) {
                    fetchRecommendations(sourceId)
                }
            }
        }
    }

    fun retryRecommendations() {
        val sourceId = selectedSourceId ?: return
        recommendAttempted.add(sourceId)
        fetchRecommendations(sourceId)
    }

    private fun fetchRecommendations(sourceId: Long) {
        recommendJob?.cancel()
        isRecommending = true
        recommendError = null
        recommendationLoaded = false
        recommendJob = viewModelScope.launch {
            when (val result = repository.recommend(sourceId)) {
                is OperationResult.Success -> {
                    recommendationLoaded = result.data > 0
                    if (result.data <= 0) {
                        recommendError = "没有获取到推荐书籍，可手动搜索书名"
                    }
                }

                is OperationResult.Error -> {
                    recommendError = result.message
                }
            }
            isRecommending = false
        }
    }

    fun search(keyword: String) {
        val sourceId = selectedSourceId ?: run {
            error = "请先添加并选择书源"
            return
        }
        if (keyword.isBlank()) {
            error = "请输入书名或作者"
            return
        }
        isSearching = true
        error = null
        recommendError = null
        recommendationLoaded = false
        viewModelScope.launch {
            when (val result = repository.search(sourceId, keyword)) {
                is OperationResult.Success -> {
                    searchResults = result.data
                    isSearching = false
                }

                is OperationResult.Error -> {
                    error = result.message
                    isSearching = false
                }
            }
        }
    }

    fun clearSearch() {
        searchResults = emptyList()
        error = null
    }
}
