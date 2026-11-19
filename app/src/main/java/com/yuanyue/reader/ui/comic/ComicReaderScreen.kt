package com.yuanyue.reader.ui.comic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.model.AnnotationDraft
import com.yuanyue.reader.model.AnnotationType
import com.yuanyue.reader.model.ComicImageItem
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComicReaderScreen(
    viewModel: ComicReaderViewModel,
    onBack: () -> Unit,
) {
    val chapters = viewModel.chapters
    val currentIndex = viewModel.currentIndex
    val currentChapter = chapters.getOrNull(currentIndex)
    val images = viewModel.images
    val annotations = viewModel.annotations.collectAsState().value
    var vertical by remember { mutableStateOf(true) }
    var drawMode by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var currentPage by remember(currentChapter?.id, vertical) { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentChapter?.title ?: "漫画阅读",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { drawMode = !drawMode }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = if (drawMode) "退出涂鸦" else "自由涂鸦",
                            tint = if (drawMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showNotes = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "笔记与标注")
                    }
                    TextButton(onClick = { vertical = !vertical }) {
                        Text(if (vertical) "上下" else "左右")
                    }
                },
            )
        },
        bottomBar = {
            Surface {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { chapters.getOrNull(currentIndex - 1)?.let(viewModel::openChapter) },
                        enabled = currentIndex > 0,
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一章")
                    }
                    Text(
                        text = "${currentIndex + 1} / ${chapters.size}   ·   ${
                            if (images.isEmpty()) "0 / 0" else "${currentPage + 1} / ${images.size}"
                        }",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        onClick = { chapters.getOrNull(currentIndex + 1)?.let(viewModel::openChapter) },
                        enabled = currentIndex < chapters.size - 1,
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一章")
                    }
                }
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            viewModel.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(viewModel.error.orEmpty())
                    TextButton(onClick = viewModel::retry) {
                        Text("重新加载")
                    }
                }
            }

            images.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("本章没有找到漫画图片")
                }
            }

            vertical -> {
                ComicVerticalList(
                    images = images,
                    annotations = annotations,
                    drawMode = drawMode,
                    onSaveStroke = { page, points ->
                        viewModel.addAnnotation(
                            AnnotationDraft(
                                chapterIndex = 0,
                                pageIndex = page,
                                text = "漫画第 ${page + 1} 页涂鸦",
                                type = AnnotationType.DRAW,
                                note = points,
                                color = 0xFF000000,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    currentPage = currentPage,
                    onPageChange = { currentPage = it },
                )
            }

            else -> {
                ComicHorizontalPager(
                    images = images,
                    annotations = annotations,
                    drawMode = drawMode,
                    onSaveStroke = { page, points ->
                        viewModel.addAnnotation(
                            AnnotationDraft(
                                chapterIndex = 0,
                                pageIndex = page,
                                text = "漫画第 ${page + 1} 页涂鸦",
                                type = AnnotationType.DRAW,
                                note = points,
                                color = 0xFF000000,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    currentPage = currentPage,
                    onPageChange = { currentPage = it },
                )
            }
        }
    }

    if (showNotes) {
        ComicNoteDialog(
            pageIndex = currentPage,
            existing = annotations,
            onDismiss = { showNotes = false },
            onSave = { draft ->
                viewModel.addAnnotation(
                    AnnotationDraft(
                        chapterIndex = 0,
                        pageIndex = draft.pageIndex,
                        text = "漫画第 ${draft.pageIndex + 1} 页",
                        type = draft.type,
                        note = draft.note,
                        color = draft.color,
                    ),
                )
            },
            onDelete = viewModel::deleteAnnotation,
        )
    }
}

@Composable
private fun ComicVerticalList(
    images: List<ComicImageItem>,
    annotations: List<com.yuanyue.reader.model.ReaderAnnotationEntity>,
    drawMode: Boolean,
    onSaveStroke: (Int, String) -> Unit,
    modifier: Modifier,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPageChange)
    }
    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(images, key = { index, image -> "${image.url}#$index" }) { index, image ->
            val pageAnnotations = annotations.filter { it.pageIndex == index }
            val highlight = pageAnnotations.firstOrNull {
                it.type == AnnotationType.HIGHLIGHT.name || it.type == AnnotationType.NOTE.name
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlight != null) androidx.compose.ui.graphics.Color(highlight.color)
                        else androidx.compose.ui.graphics.Color.Transparent,
                    ),
            ) {
                ComicImageView(
                    model = image.url,
                    referer = image.referer,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
                ComicDrawingOverlay(
                    annotations = annotations,
                    pageIndex = index,
                    drawMode = drawMode,
                    onSaveStroke = { onSaveStroke(index, it) },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicHorizontalPager(
    images: List<ComicImageItem>,
    annotations: List<com.yuanyue.reader.model.ReaderAnnotationEntity>,
    drawMode: Boolean,
    onSaveStroke: (Int, String) -> Unit,
    modifier: Modifier,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { images.size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect(onPageChange)
    }
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        val image = images[page]
        val pageAnnotations = annotations.filter { it.pageIndex == page }
        val highlight = pageAnnotations.firstOrNull {
            it.type == AnnotationType.HIGHLIGHT.name || it.type == AnnotationType.NOTE.name
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (highlight != null) androidx.compose.ui.graphics.Color(highlight.color)
                    else androidx.compose.ui.graphics.Color.Transparent,
                ),
        ) {
            ZoomableComicImageView(
                model = image.url,
                referer = image.referer,
                modifier = Modifier.fillMaxSize(),
            )
            ComicDrawingOverlay(
                annotations = annotations,
                pageIndex = page,
                drawMode = drawMode,
                onSaveStroke = { onSaveStroke(page, it) },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
