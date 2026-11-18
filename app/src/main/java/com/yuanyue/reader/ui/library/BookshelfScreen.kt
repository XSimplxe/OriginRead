package com.yuanyue.reader.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yuanyue.reader.data.OperationResult
import com.yuanyue.reader.model.OnlineBookEntity
import com.yuanyue.reader.ui.comic.ComicLibraryContent
import com.yuanyue.reader.ui.comic.ComicLibraryViewModel
import com.yuanyue.reader.ui.online.OnlineLibraryContent
import com.yuanyue.reader.ui.online.OnlineLibraryViewModel
import kotlinx.coroutines.launch

private data class BookshelfTab(val title: String)

private val bookshelfTabs = listOf(
    BookshelfTab("书架"),
    BookshelfTab("在线"),
)

/**
 * 书架：把原来的「书库 / 小说 / 漫画」三个底部 Tab 合并为一个，
 * 用顶部 Tab 区分本地、在线与漫画，底部导航只保留 4 个入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    localViewModel: LibraryViewModel,
    onlineViewModel: OnlineLibraryViewModel,
    comicViewModel: ComicLibraryViewModel,
    onOpenBookDetail: (Long) -> Unit,
    onOpenOnlineBook: (Long) -> Unit,
    onOpenComic: (Long) -> Unit,
    onOpenBookSourceManager: () -> Unit,
    onOpenComicSourceManager: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var onlineSection by rememberSaveable { mutableStateOf(0) }
    val favoriteBooks by onlineViewModel.favoriteBooks.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch {
                val result = localViewModel.importBook(uri)
                val message = when (result) {
                    is OperationResult.Success -> "导入成功"
                    is OperationResult.Error -> result.message
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                actions = {
                    when (selectedTab) {
                        0 -> {
                            if (localViewModel.isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                                    Icon(Icons.Default.Add, contentDescription = "导入本地书籍")
                                }
                            }
                        }

                        else -> {
                            IconButton(onClick = onOpenBookSourceManager) {
                                Icon(Icons.Default.Cloud, contentDescription = "书源管理")
                            }
                            IconButton(onClick = onOpenComicSourceManager) {
                                Icon(Icons.Default.Image, contentDescription = "漫画源管理")
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                bookshelfTabs.forEachIndexed { index, tab ->
                    val selected = selectedTab == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = index },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                    ) {
                        Text(
                            text = tab.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (favoriteBooks.isNotEmpty()) {
                            OnlineFavoritesRow(
                                books = favoriteBooks,
                                onOpenBook = onOpenOnlineBook,
                            )
                        }
                        LocalBooksContent(
                            viewModel = localViewModel,
                            onOpenBookDetail = onOpenBookDetail,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = onlineSection == 0,
                            onClick = { onlineSection = 0 },
                            label = { Text("小说") },
                        )
                        FilterChip(
                            selected = onlineSection == 1,
                            onClick = { onlineSection = 1 },
                            label = { Text("漫画") },
                        )
                    }
                    if (onlineSection == 0) {
                        OnlineLibraryContent(
                            viewModel = onlineViewModel,
                            onOpenBook = onOpenOnlineBook,
                            onOpenSourceManager = onOpenBookSourceManager,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ComicLibraryContent(
                            viewModel = comicViewModel,
                            onOpenComic = onOpenComic,
                            onOpenSourceManager = onOpenComicSourceManager,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineFavoritesRow(
    books: List<OnlineBookEntity>,
    onOpenBook: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "在线收藏",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(books, key = { it.id }) { book ->
                Card(
                    modifier = Modifier
                        .size(width = 108.dp, height = 150.dp)
                        .clickable { onOpenBook(book.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(108.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = book.title,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
