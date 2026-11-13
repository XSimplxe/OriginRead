package com.yuanyue.reader.ui.online

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.BookSourceCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

private data class LoadedSourceFile(
    val name: String,
    val content: String,
)

private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceImportScreen(
    viewModel: BookSourceImportViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loadedFile by remember { mutableStateOf<LoadedSourceFile?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                localError = null
                try {
                    val content = readTextFile(context, uri)
                    val name = queryFileName(context, uri) ?: "本地书源文件"
                    loadedFile = LoadedSourceFile(name, content)
                    input = ""
                    viewModel.analyze(content)
                } catch (error: Exception) {
                    localError = "读取文件失败：${error.message ?: "无法读取"}"
                }
            }
        }
    }

    fun analyze() {
        val content = loadedFile?.content ?: input
        if (content.isBlank()) {
            localError = "请粘贴书源链接/JSON，或选择本地 JSON 文件"
            return
        }
        localError = null
        viewModel.analyze(content)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入书源") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ImportInputCard(
                        input = input,
                        loadedFile = loadedFile,
                        isLoading = viewModel.isLoading,
                        onInputChange = {
                            input = it
                            loadedFile = null
                            localError = null
                        },
                        onPickFile = { filePicker.launch(arrayOf("*/*")) },
                        onAnalyze = ::analyze,
                        onClearFile = { loadedFile = null },
                    )
                }

                if (viewModel.isLoading) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                val message = localError ?: viewModel.error
                if (message != null) {
                    item {
                        MessageCard(
                            text = message,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                if (viewModel.warnings.isNotEmpty()) {
                    item {
                        WarningCard(viewModel.warnings)
                    }
                }

                if (viewModel.candidates.isNotEmpty()) {
                    item {
                        SelectionSummary(
                            candidates = viewModel.candidates,
                            selectedCount = viewModel.selectedUrls.size,
                            onSelectAll = { viewModel.selectAll(true) },
                            onClear = { viewModel.selectAll(false) },
                        )
                    }
                }

                items(viewModel.candidates, key = { it.source.url }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        checked = candidate.source.url in viewModel.selectedUrls,
                        testState = viewModel.testStates[candidate.source.url],
                        onToggle = { viewModel.toggle(candidate.source.url) },
                    )
                }

                if (viewModel.candidates.isNotEmpty()) {
                    item {
                        Text(
                            text = "可先点「检测选中」确认书源是否可用；检测未通过的书源也可以继续添加。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (viewModel.candidates.isNotEmpty()) {
                ImportActions(
                    selectedCount = viewModel.selectedUrls.size,
                    isTesting = viewModel.isTesting,
                    isAdding = viewModel.isAdding,
                    onTest = viewModel::testSelected,
                    onAdd = {
                        scope.launch { viewModel.addSelected() }
                    },
                )
            }
        }
    }

    val addedCount = viewModel.addedCount
    if (addedCount != null) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(if (addedCount > 0) "导入完成" else "没有新增书源") },
            text = {
                Text(
                    if (addedCount > 0) {
                        "已添加 $addedCount 个书源。"
                    } else {
                        "选中的书源都已存在，或规则不完整。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("返回书源管理")
                }
            },
        )
    }
}

@Composable
private fun ImportInputCard(
    input: String,
    loadedFile: LoadedSourceFile?,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onAnalyze: () -> Unit,
    onClearFile: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "书源链接 / JSON",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                label = { Text("粘贴链接或 JSON") },
                placeholder = {
                    Text(
                        "https://example.com/bookSource.json\n" +
                            "或 legado://import/bookSource?src=...\n" +
                            "或 [ {\"bookSourceName\": ...} ]",
                    )
                },
                minLines = 4,
                maxLines = 8,
            )

            loadedFile?.let { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = file.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onClearFile, enabled = !isLoading) {
                        Text("移除")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAnalyze, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("解析")
                    }
                }
                OutlinedButton(onClick = onPickFile, enabled = !isLoading) {
                    Text("选择本地 JSON")
                }
            }

            Text(
                text = "支持单/多书源 JSON、JSONL、Legado 分享链接、订阅页面和纯链接列表。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SelectionSummary(
    candidates: List<BookSourceCandidate>,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    val importable = candidates.count { it.importable }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "共 ${candidates.size} 个，可选 $importable 个，已选 $selectedCount 个",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onSelectAll) {
            Text("全选")
        }
        TextButton(onClick = onClear) {
            Text("全不选")
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: BookSourceCandidate,
    checked: Boolean,
    testState: SourceTestUiState?,
    onToggle: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = candidate.importable,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = candidate.source.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hostOf(candidate.source.url),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    candidate.duplicate -> Text(
                        text = "已存在，添加时会跳过",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    candidate.issues.isNotEmpty() -> Text(
                        text = "规则不完整：${candidate.issues.joinToString("、")}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    else -> Text(
                        text = "结构完整，可导入",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                when (testState) {
                    SourceTestUiState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在检测可用性…",
                            modifier = Modifier.padding(start = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    is SourceTestUiState.Done -> Text(
                        text = if (testState.ok) "可用：${testState.message}" else "不可用：${testState.message}",
                        color = if (testState.ok) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun ImportActions(
    selectedCount: Int,
    isTesting: Boolean,
    isAdding: Boolean,
    onTest: () -> Unit,
    onAdd: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onTest,
                enabled = selectedCount > 0 && !isTesting && !isAdding,
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("检测中")
                } else {
                    Text("检测选中")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onAdd,
                enabled = selectedCount > 0 && !isAdding && !isTesting,
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("添加中")
                } else {
                    Text("添加选中（$selectedCount）")
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun WarningCard(warnings: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "解析提示",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
            warnings.take(8).forEach { warning ->
                Text(
                    text = "• $warning",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun hostOf(url: String): String {
    val host = runCatching { java.net.URI(url).host }.getOrNull()
    return if (host.isNullOrBlank()) url else host
}

private suspend fun readTextFile(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("文件超过 ${MAX_IMPORT_BYTES / 1024 / 1024}MB")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("无法读取该文件")

        val offset = if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        val utf8 = String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
        if (utf8.contains('\uFFFD')) {
            runCatching {
                String(bytes, offset, bytes.size - offset, Charset.forName("GBK"))
            }.getOrDefault(utf8)
        } else {
            utf8
        }
    }

private fun queryFileName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()
