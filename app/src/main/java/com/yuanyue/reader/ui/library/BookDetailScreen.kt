package com.yuanyue.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yuanyue.reader.data.BookTags
import com.yuanyue.reader.model.BookEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    onBack: () -> Unit,
    onRead: (Long) -> Unit,
) {
    val book by viewModel.book.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editTags by remember { mutableStateOf("") }

    fun enterEdit(current: BookEntity) {
        editTitle = current.title
        editAuthor = current.author.orEmpty()
        editDescription = current.description.orEmpty()
        editTags = BookTags.parse(current.tags).joinToString(", ")
        editing = true
    }

    LaunchedEffect(book, editing) {
        val current = book
        if (current != null && !editing) {
            editTitle = current.title
            editAuthor = current.author.orEmpty()
            editDescription = current.description.orEmpty()
            editTags = BookTags.parse(current.tags).joinToString(", ")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书籍详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    book?.let { current ->
                        IconButton(onClick = { viewModel.toggleFavorite(current) }) {
                            Icon(
                                imageVector = if (current.isFavorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = if (current.isFavorite) "取消收藏" else "收藏",
                                tint = if (current.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        IconButton(onClick = { enterEdit(current) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑图书信息")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = book
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                DetailCover(book = current)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = current.author ?: "未知作者",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (current.progressPercent > 0f) {
                        LinearProgressIndicator(
                            progress = { current.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                        Text(
                            text = "已读 ${"%.0f".format(current.progressPercent * 100)}%",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val tags = BookTags.parse(current.tags)
                    if (tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.take(3).forEach { tag ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onRead(current.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (current.progressPercent > 0f) "继续阅读" else "开始阅读")
                }
                OutlinedButton(
                    onClick = {
                        if (editing) {
                            editing = false
                        } else {
                            enterEdit(current)
                        }
                    },
                ) {
                    Text(if (editing) "取消编辑" else "编辑资料")
                }
            }

            if (editing) {
                EditBookCard(
                    title = editTitle,
                    author = editAuthor,
                    description = editDescription,
                    tags = editTags,
                    isSaving = viewModel.isSaving,
                    error = viewModel.error,
                    onTitleChange = { editTitle = it },
                    onAuthorChange = { editAuthor = it },
                    onDescriptionChange = { editDescription = it },
                    onTagsChange = { editTags = it },
                    onSave = {
                        scope.launch {
                            val success = viewModel.save(
                                title = editTitle,
                                author = editAuthor,
                                description = editDescription,
                                tags = editTags,
                            )
                            if (success) editing = false
                        }
                    },
                )
            } else {
                InfoCard(
                    description = current.description,
                    tags = BookTags.parse(current.tags),
                    lastReadAt = current.lastReadAt,
                    readingSeconds = current.readingSeconds,
                    addedAt = current.addedAt,
                )
            }
        }
    }
}

@Composable
private fun EditBookCard(
    title: String,
    author: String,
    description: String,
    tags: String,
    isSaving: Boolean,
    error: String?,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("编辑图书资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("书名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = author,
                onValueChange = onAuthorChange,
                label = { Text("作者") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("简介") },
                minLines = 3,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = onTagsChange,
                label = { Text("标签（用逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (isSaving) "保存中…" else "保存")
            }
        }
    }
}

@Composable
private fun InfoCard(
    description: String?,
    tags: List<String>,
    lastReadAt: Long,
    readingSeconds: Long,
    addedAt: Long,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = description?.takeIf { it.isNotBlank() } ?: "暂无简介，点击右上角编辑按钮添加。",
                color = if (description.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            HorizontalDivider()
            Text("阅读信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoRow("累计阅读", formatDuration(readingSeconds))
            InfoRow(
                "上次阅读",
                if (lastReadAt > 0) formatDate(lastReadAt) else "还没有开始阅读",
            )
            InfoRow("加入书架", formatDate(addedAt))
            if (tags.isNotEmpty()) {
                HorizontalDivider()
                Text("标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(tags.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DetailCover(book: BookEntity) {
    val coverFile = book.coverPath?.let { File(it) }?.takeIf { it.exists() }
    if (coverFile != null) {
        AsyncImage(
            model = coverFile,
            contentDescription = book.title,
            modifier = Modifier
                .size(width = 96.dp, height = 132.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 132.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (book.format == com.yuanyue.reader.model.BookFormat.EPUB_COMIC) {
                    "漫画"
                } else {
                    book.format.name
                },
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "还没有阅读记录"
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours} 小时 ${minutes % 60} 分钟"
        minutes > 0 -> "${minutes} 分钟"
        else -> "${seconds} 秒"
    }
}

private fun formatDate(epochMillis: Long): String = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(epochMillis))
}.getOrDefault("")
