package com.yuanyue.reader.ui.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.BookSourceCandidate
import com.yuanyue.reader.data.BookSourceTestResult
import com.yuanyue.reader.data.OnlineBookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface SourceTestUiState {
    data object Testing : SourceTestUiState
    data class Done(val ok: Boolean, val message: String) : SourceTestUiState
}

class BookSourceImportViewModel(
    private val repository: OnlineBookRepository,
) : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var candidates by mutableStateOf<List<BookSourceCandidate>>(emptyList())
        private set
    var warnings by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedUrls by mutableStateOf<Set<String>>(emptySet())
        private set
    var testStates by mutableStateOf<Map<String, SourceTestUiState>>(emptyMap())
        private set
    var isTesting by mutableStateOf(false)
        private set
    var isAdding by mutableStateOf(false)
        private set
    var addedCount by mutableStateOf<Int?>(null)
        private set

    private var analyzeJob: Job? = null
    private var testJob: Job? = null

    fun analyze(input: String) {
        val text = input.trim()
        if (text.isBlank()) {
            error = "请输入书源链接或 JSON 内容"
            return
        }
        analyzeJob?.cancel()
        testJob?.cancel()
        isLoading = true
        error = null
        candidates = emptyList()
        warnings = emptyList()
        selectedUrls = emptySet()
        testStates = emptyMap()
        addedCount = null

        analyzeJob = viewModelScope.launch {
            try {
                val result = repository.importCandidates(text)
                candidates = result.candidates
                warnings = result.warnings
                selectedUrls = result.candidates
                    .filter { it.importable }
                    .map { it.source.url }
                    .toSet()
                if (result.candidates.isEmpty()) {
                    error = "没有解析到可用的书源"
                } else if (result.candidates.size == 1 && result.candidates.first().importable) {
                    // 单书源时保持“添加前先检测是否可用”的旧行为。
                    testSelected()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "书源解析失败"
            } finally {
                isLoading = false
            }
        }
    }

    fun toggle(url: String) {
        val candidate = candidates.firstOrNull { it.source.url == url } ?: return
        if (!candidate.importable) return
        selectedUrls = if (url in selectedUrls) selectedUrls - url else selectedUrls + url
    }

    fun selectAll(select: Boolean = true) {
        selectedUrls = if (select) {
            candidates.filter { it.importable }.map { it.source.url }.toSet()
        } else {
            emptySet()
        }
    }

    fun testSelected() {
        if (isTesting) return
        val targets = selectedCandidates()
        if (targets.isEmpty()) {
            error = "请先选择结构完整的书源"
            return
        }
        error = null
        isTesting = true
        testStates = testStates + targets.associate {
            it.source.url to SourceTestUiState.Testing
        }

        testJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val semaphore = Semaphore(3)
                    targets.map { candidate ->
                        async {
                            semaphore.withPermit {
                                val result = runCatching {
                                    repository.testSource(candidate.source)
                                }.getOrElse { failure ->
                                    BookSourceTestResult(
                                        ok = false,
                                        message = "检测失败：${failure.message ?: "未知错误"}",
                                    )
                                }
                                testStates = testStates + (
                                    candidate.source.url to SourceTestUiState.Done(
                                        ok = result.ok,
                                        message = result.message,
                                    )
                                    )
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                isTesting = false
            }
        }
    }

    suspend fun addSelected(): Int {
        val targets = selectedCandidates()
        if (targets.isEmpty()) {
            error = "请先选择要添加的书源"
            return -1
        }
        if (isAdding) return -1
        isAdding = true
        return try {
            val count = repository.addSources(targets.map { it.source })
            addedCount = count
            count
        } catch (failure: Exception) {
            error = "添加失败：${failure.message ?: "未知错误"}"
            -1
        } finally {
            isAdding = false
        }
    }

    private fun selectedCandidates(): List<BookSourceCandidate> =
        candidates.filter { it.importable && it.source.url in selectedUrls }
}
