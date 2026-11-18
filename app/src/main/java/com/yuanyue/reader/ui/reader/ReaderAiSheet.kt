package com.yuanyue.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.YuanyueApplication
import kotlinx.coroutines.launch

private val aiPresets = listOf(
    "总结本章",
    "解释这段内容",
    "分析主要人物",
    "帮我梳理时间线",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderAiSheet(
    bookTitle: String,
    chapterTitle: String,
    chapterText: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as YuanyueApplication).container.aiRepository
    }
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun ask(prompt: String) {
        if (loading || prompt.isBlank()) return
        loading = true
        error = null
        answer = null
        scope.launch {
            repository.companion(
                chapterTitle = "$bookTitle · $chapterTitle",
                chapterText = chapterText,
                question = prompt,
            ).fold(
                onSuccess = { answer = it },
                onFailure = { error = it.message ?: "AI 请求失败" },
            )
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "AI 伴读",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$bookTitle · $chapterTitle",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aiPresets) { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            question = preset
                            ask(preset)
                        },
                        label = { Text(preset) },
                    )
                }
            }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("想问什么？") },
                placeholder = { Text("例如：这一章发生了什么？") },
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = { ask(question) },
                enabled = !loading && question.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "AI 正在阅读…" else "提问")
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
            answer?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(top = 4.dp),
                )
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { ask(question) }) {
                    Text("重试")
                }
            }
        }
    }
}
