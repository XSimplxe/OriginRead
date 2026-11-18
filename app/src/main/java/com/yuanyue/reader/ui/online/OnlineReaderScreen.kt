package com.yuanyue.reader.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.ReaderSettings
import com.yuanyue.reader.model.BookChapter
import com.yuanyue.reader.ui.reader.ReaderContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineReaderScreen(
    viewModel: OnlineReaderViewModel,
    onBack: () -> Unit,
) {
    val book = viewModel.book
    val chapters = viewModel.chapters
    val currentIndex = viewModel.currentIndex
    val currentChapter = chapters.getOrNull(currentIndex)
    val settings by viewModel.settings.collectAsState(initial = ReaderSettings())
    val annotations by viewModel.annotations.collectAsState()
    val isLoading = viewModel.isLoading
    val error = viewModel.error

    val showReader = book != null &&
        currentChapter != null &&
        !isLoading &&
        error == null &&
        viewModel.content.isNotBlank()

    if (showReader) {
        val tocChapters = remember(chapters) {
            chapters.map { BookChapter(it.title, "") }
        }
        ReaderContent(
            bookTitle = book?.title ?: "在线阅读",
            chapter = BookChapter(currentChapter?.title.orEmpty(), viewModel.content),
            chapterIndex = currentIndex,
            chapterCount = chapters.size,
            chapters = tocChapters,
            initialPage = 0,
            settings = settings,
            onBack = onBack,
            onSelectChapter = viewModel::selectChapter,
            onProgress = { _, _, _ -> },
            onFontSizeChange = viewModel::updateFontSize,
            onLineHeightChange = viewModel::updateLineHeight,
            onThemeChange = viewModel::updateTheme,
            onPageModeChange = viewModel::updatePageMode,
            onSerifFontChange = viewModel::updateSerifFont,
            onParagraphSpacingChange = viewModel::updateParagraphSpacing,
            onContentPaddingChange = viewModel::updateContentPadding,
            onIndentFirstLineChange = viewModel::updateIndentFirstLine,
            onTtsVoiceChange = viewModel::updateTtsVoice,
            onTtsRateChange = viewModel::updateTtsRate,
            onTtsEngineChange = viewModel::updateTtsEngine,
            onTtsCatalogChange = viewModel::updateTtsCatalog,
            annotations = annotations,
            onAddAnnotation = viewModel::addAnnotation,
            onDeleteAnnotation = viewModel::deleteAnnotation,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "在线阅读") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading || error == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("正在加载章节正文…")
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error.orEmpty())
                    TextButton(onClick = viewModel::retry) {
                        Text("重新加载")
                    }
                    TextButton(onClick = onBack) {
                        Text("返回目录")
                    }
                }
            }
        }
    }
}
