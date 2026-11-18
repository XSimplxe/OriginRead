package com.yuanyue.reader.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.AiRepository
import com.yuanyue.reader.data.ProfileSettings
import com.yuanyue.reader.data.RssRepository
import com.yuanyue.reader.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val rssRepository: RssRepository,
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiRepository,
) : ViewModel() {
    val feeds = rssRepository.feeds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val latestArticles = rssRepository.articles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val profile = settingsRepository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileSettings(),
    )

    var isRefreshing by mutableStateOf(false)
        private set
    var aiSummary by mutableStateOf<String?>(null)
        private set
    var isAiSummarizing by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set

    init {
        refreshAll()
    }

    fun refreshAll() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            runCatching { rssRepository.refreshAll() }
            isRefreshing = false
        }
    }

    fun summarizeRss() {
        if (isAiSummarizing) return
        val articles = latestArticles.value
        if (articles.isEmpty()) {
            aiError = "还没有 RSS 资讯可以摘要"
            return
        }
        isAiSummarizing = true
        aiError = null
        viewModelScope.launch {
            aiRepository.summarizeRss(articles).fold(
                onSuccess = { aiSummary = it },
                onFailure = { aiError = it.message ?: "AI 摘要失败" },
            )
            isAiSummarizing = false
        }
    }

    fun clearAiSummary() {
        aiSummary = null
        aiError = null
    }
}
