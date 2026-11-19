package com.yuanyue.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yuanyue.reader.data.BookTags
import com.yuanyue.reader.model.BookEntity
import com.yuanyue.reader.model.OnlineBookEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocalBooksContent(
    viewModel: LibraryViewModel,
    onOpenBookDetail: (Long) -> Unit,
    onlineFavorites: List<com.yuanyue.reader.model.OnlineBookEntity> = emptyList(),
    onOpenOnlineBook: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val books by viewModel.books.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    val allTags = remember(books) {
        books.flatMap { BookTags.parse(it.tags) }.distinct().sorted()
    }
    val filteredBooks = remember(books, query, favoritesOnly, selectedTag) {
        val keyword = query.trim()
        books.filter { book ->
            val matchesKeyword = keyword.isBlank() ||
                book.title.contains(keyword, ignoreCase = true) ||
                book.author.orEmpty().contains(keyword, ignoreCase = true) ||
                book.description.orEmpty().contains(keyword, ignoreCase = true) ||
                BookTags.parse(book.tags).any { it.contains(keyword, ignoreCase = true) }
            val matchesFavorite = !favoritesOnly || book.isFavorite
            val matchesTag = selectedTag == null || selectedTag in BookTags.parse(book.tags)
            matchesKeyword && matchesFavorite && matchesTag
        }
    }
    val filteredOnline = remember(onlineFavorites, query, favoritesOnly, selectedTag) {
        val keyword = query.trim()
        onlineFavorites.filter { book ->
            selectedTag == null &&
                (keyword.isBlank() ||
                    book.title.contains(keyword, ignoreCase = true) ||
                    book.author.orEmpty().contains(keyword, ignoreCase = true))
        }
    }

    if (books.isEmpty() && onlineFavorites.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "书架还是空的",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击右上角导入 TXT、EPUB 或 MOBI 文件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ReadingSummaryCard(books, onlineFavorites.size)
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索书名、作者、标签") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = !favoritesOnly && selectedTag == null,
                            onClick = {
                                favoritesOnly = false
                                selectedTag = null
                            },
                            label = { Text("全部") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = {
                                favoritesOnly = !favoritesOnly
                                selectedTag = null
                            },
                            label = { Text("收藏") },
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = {
                                selectedTag = if (selectedTag == tag) null else tag
                                favoritesOnly = false
                            },
                            label = { Text(tag) },
                        )
                    }
                }
            }
            if (filteredBooks.isEmpty() && filteredOnline.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("没有匹配的书籍", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = {
                            query = ""
                            favoritesOnly = false
                            selectedTag = null
                        }) {
                            Text("清除筛选")
                        }
                    }
                }
            } else {
                items(filteredBooks, key = { "local-${it.id}" }) { book ->
                    BookCard(
                        book = book,
                        onClick = { onOpenBookDetail(book.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(book) },
                        onDelete = { pendingDelete = book },
                    )
                }
                items(filteredOnline, key = { "online-${it.id}" }) { book ->
                    OnlineShelfCard(book = book, onClick = { onOpenOnlineBook(book.id) })
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("移除书籍") },
            text = { Text("确定从书架移除「${book.title}」吗？本地文件也会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteBook(book)
                            pendingDelete = null
                        }
                    },
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ReadingSummaryCard(books: List<BookEntity>, onlineCount: Int) {
    val reading = books.count { it.progressPercent > 0.01f && it.progressPercent < 0.99f }
    val finished = books.count { it.progressPercent >= 0.99f }
    val favorites = books.count { it.isFavorite }
    val totalSeconds = books.sumOf { it.readingSeconds }
    val recent = books.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "阅读摘要",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "共 ${books.size + onlineCount} 本 · 在线收藏 $onlineCount · 在读 $reading · 已读完 $finished · 收藏 $favorites",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "累计阅读 ${formatDuration(totalSeconds)}",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
            recent?.let {
                Text(
                    text = "最近在读《${it.title}》，已读 ${"%.0f".format(it.progressPercent * 100)}%",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookCard(
    book: BookEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = book)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author ?: "未知作者",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tags = BookTags.parse(book.tags)
                if (tags.isNotEmpty()) {
                    Text(
                        text = tags.take(4).joinToString(" · "),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (book.progressPercent > 0f) {
                    LinearProgressIndicator(
                        progress = { book.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                val status = buildString {
                    if (book.progressPercent > 0f) {
                        append("已读 ${"%.0f".format(book.progressPercent * 100)}%")
                    }
                    if (book.lastReadAt > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("上次 ${formatShortDate(book.lastReadAt)}")
                    }
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (book.isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = if (book.isFavorite) "取消收藏" else "收藏",
                        tint = if (book.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "移除")
                }
            }
        }
    }
}

@Composable
private fun OnlineShelfCard(book: OnlineBookEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp),
                    ),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author.orEmpty().ifBlank { "未知作者" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "在线收藏",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BookCover(book: BookEntity) {
    val coverFile = book.coverPath?.let { File(it) }?.takeIf { it.exists() }
    if (coverFile != null) {
        AsyncImage(
            model = coverFile,
            contentDescription = book.title,
            modifier = Modifier
                .size(58.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                ),
        )
    } else {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = book.format.name.take(1),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0 分钟"
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours} 小时 ${minutes % 60} 分钟"
        minutes > 0 -> "${minutes} 分钟"
        else -> "${seconds} 秒"
    }
}

private fun formatShortDate(epochMillis: Long): String = runCatching {
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epochMillis))
}.getOrDefault("")
