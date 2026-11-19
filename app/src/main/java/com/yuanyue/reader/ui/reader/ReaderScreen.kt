package com.yuanyue.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanyue.reader.data.PageMode
import com.yuanyue.reader.data.ReaderFont
import com.yuanyue.reader.data.ReaderSettings
import com.yuanyue.reader.data.ReaderTheme
import com.yuanyue.reader.model.AnnotationDraft
import com.yuanyue.reader.model.AnnotationType
import com.yuanyue.reader.model.ReaderAnnotationEntity
import com.yuanyue.reader.model.BookChapter
import com.yuanyue.reader.ui.theme.LocalAppDarkMode
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val book = viewModel.book
    val chapters = viewModel.chapters
    val selectedChapter = viewModel.selectedChapter
    val loadError = viewModel.loadError
    val settings by viewModel.settings.collectAsState(initial = ReaderSettings())
    val annotations by viewModel.annotations.collectAsState()

    when {
        book == null && loadError == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "正在打开书籍…",
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        book == null -> {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(loadError.orEmpty())
                    Row {
                        TextButton(onClick = viewModel::load) {
                            Text("重试")
                        }
                        TextButton(onClick = onBack) {
                            Text("返回书架")
                        }
                    }
                }
            }
        }

        chapters.isEmpty() -> {
            ReaderEmpty(onBack = onBack, onRetry = viewModel::load)
        }

        else -> {
            val chapter = chapters.getOrNull(selectedChapter) ?: chapters.first()
            ReaderContent(
                bookTitle = book.title,
                chapter = chapter,
                chapterIndex = selectedChapter,
                chapterCount = chapters.size,
                chapters = chapters,
                initialPage = viewModel.initialPage,
                settings = settings,
                onBack = onBack,
                onSelectChapter = viewModel::selectChapter,
                onProgress = viewModel::updateProgress,
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
        }
    }
}

@Composable
private fun ReaderEmpty(onBack: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂时无法读取这本书的内容")
        Row {
            TextButton(onClick = onRetry) {
                Text("重试")
            }
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReaderContent(
    bookTitle: String,
    chapter: BookChapter,
    chapterIndex: Int,
    chapterCount: Int,
    chapters: List<BookChapter>,
    initialPage: Int,
    settings: ReaderSettings,
    onBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onProgress: (Int, Int, Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onPageModeChange: (PageMode) -> Unit,
    onSerifFontChange: (Boolean) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onContentPaddingChange: (Float) -> Unit,
    onIndentFirstLineChange: (Boolean) -> Unit,
    onTtsVoiceChange: (String?) -> Unit,
    onTtsRateChange: (Float) -> Unit,
    onTtsEngineChange: (String?) -> Unit,
    onTtsCatalogChange: (String?) -> Unit,
    annotations: List<com.yuanyue.reader.model.ReaderAnnotationEntity>,
    onAddAnnotation: (com.yuanyue.reader.model.AnnotationDraft) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
) {
    val palette = readerPalette(settings.theme, LocalAppDarkMode.current)
    val density = LocalDensity.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTts by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var annotationTarget by remember { mutableStateOf<AnnotationTarget?>(null) }
    var currentPage by remember(chapterIndex) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    val freeScroll = settings.pageMode == PageMode.FREE_SCROLL &&
        chapters.size > 1 &&
        chapters.all { it.content.isNotBlank() }
    val freeHorizontal = settings.pageMode == PageMode.FREE_HORIZONTAL &&
        chapters.size > 1 &&
        chapters.all { it.content.isNotBlank() }
    var freeChapterIndex by remember(chapterIndex) {
        mutableIntStateOf(chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
    }
    var freeHorizontalChapter by remember(chapterIndex) {
        mutableIntStateOf(chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
    }
    var freeHorizontalPage by remember { mutableIntStateOf(0) }
    val displayChapterIndex = when {
        freeHorizontal -> freeHorizontalChapter
        freeScroll -> freeChapterIndex
        else -> chapterIndex
    }
    val displayChapterTitle = chapters.getOrNull(displayChapterIndex)?.title ?: chapter.title
    val ttsText = when {
        freeHorizontal || freeScroll -> chapters.getOrNull(displayChapterIndex)?.content ?: chapter.content
        else -> chapter.content
    }
    val context = LocalContext.current
    val ttsCatalog = remember(settings.ttsCatalogJson) {
        TtsCatalog.parse(settings.ttsCatalogJson)
    }
    val ttsController = remember {
        ReaderTtsController(
            context = context,
            initialVoiceName = settings.ttsVoiceName,
            initialRate = settings.ttsRate,
            initialEnginePackage = settings.ttsEnginePackage,
            onVoiceChanged = onTtsVoiceChange,
            onRateChanged = onTtsRateChange,
            onEngineChanged = onTtsEngineChange,
        )
    }
    DisposableEffect(ttsController) {
        onDispose { ttsController.shutdown() }
    }
    LaunchedEffect(ttsText) {
        ttsController.setText(ttsText)
    }

    val textStyle = TextStyle(
        color = palette.text,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = settings.fontSizeSp.sp * settings.lineHeight,
        fontFamily = readerFontFamily(settings),
    )

    fun annotationFor(chapter: Int, page: Int, paragraph: Int = -1): ReaderAnnotationEntity? =
        annotations.lastOrNull {
            it.chapterIndex == chapter &&
                it.pageIndex == page &&
                it.paragraphIndex == paragraph &&
                it.type != AnnotationType.BOOKMARK.name
        }

    fun textStyleFor(annotation: ReaderAnnotationEntity?): TextStyle =
        if (annotation?.type == AnnotationType.UNDERLINE.name) {
            textStyle.copy(textDecoration = TextDecoration.Underline)
        } else {
            textStyle
        }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        val horizontalPadding = settings.contentPadding.coerceIn(8f, 40f).dp
        val topPadding = 88.dp
        val bottomPadding = 72.dp
        val pageWidth = (maxWidth - horizontalPadding * 2)
            .let { with(density) { it.toPx() } }
            .roundToInt()
            .coerceAtLeast(1)
        val pageHeight = (maxHeight - topPadding - bottomPadding)
            .let { with(density) { it.toPx() } }
            .roundToInt()
            .coerceAtLeast(1)

        val displayedContent = remember(chapter.content, settings.indentFirstLine) {
            if (settings.indentFirstLine) indentParagraphs(chapter.content) else chapter.content
        }
        val pages = rememberPagedText(displayedContent, textStyle, pageWidth, pageHeight)
        val freeBookPages = rememberPagedBook(
            chapters = chapters,
            style = textStyle,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            enabled = freeHorizontal,
            indentFirstLine = settings.indentFirstLine,
        )

        // 定时保存一次进度，同时让阅读摘要里的累计阅读时长能够增长。
        LaunchedEffect(chapterIndex, settings.pageMode, pages?.size) {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val index = when {
                    freeHorizontal -> freeHorizontalChapter
                    freeScroll -> freeChapterIndex
                    else -> chapterIndex
                }
                val page = if (freeScroll) 0 else currentPage
                val total = if (freeScroll) chapters.size else (pages?.size ?: 0)
                onProgress(index, page, total)
            }
        }

        if (freeHorizontal) {
            if (freeBookPages == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = palette.text,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在排版整本书…",
                            modifier = Modifier.padding(top = 12.dp),
                            color = palette.secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else if (freeBookPages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = horizontalPadding,
                            top = topPadding,
                            end = horizontalPadding,
                            bottom = bottomPadding,
                        ),
                ) {
                    Text("这本书暂无内容", color = palette.secondary)
                }
            } else {
                val startPage = remember(freeBookPages, chapterIndex, initialPage) {
                    freeBookPages.indexOfFirst {
                        it.chapterIndex == chapterIndex && it.pageIndex == initialPage.coerceAtLeast(0)
                    }.takeIf { it >= 0 }
                        ?: freeBookPages.indexOfFirst { it.chapterIndex == chapterIndex }
                            .coerceAtLeast(0)
                }
                val pagerState = rememberPagerState(
                    initialPage = startPage,
                    pageCount = { freeBookPages.size },
                )
                LaunchedEffect(chapterIndex, freeBookPages) {
                    val target = freeBookPages.indexOfFirst {
                        it.chapterIndex == chapterIndex && it.pageIndex == initialPage.coerceAtLeast(0)
                    }.takeIf { it >= 0 }
                        ?: freeBookPages.indexOfFirst { it.chapterIndex == chapterIndex }
                            .coerceAtLeast(0)
                    pagerState.scrollToPage(target.coerceIn(0, freeBookPages.lastIndex))
                }
                LaunchedEffect(pagerState, freeBookPages) {
                    snapshotFlow { pagerState.currentPage }.collect { pageIndex ->
                        val bookPage = freeBookPages.getOrNull(pageIndex) ?: return@collect
                        freeHorizontalChapter = bookPage.chapterIndex
                        freeHorizontalPage = pageIndex
                        currentPage = bookPage.pageIndex
                        onProgress(bookPage.chapterIndex, bookPage.pageIndex, bookPage.pageCount)
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    val bookPage = freeBookPages[pageIndex]
                    val annotation = annotationFor(bookPage.chapterIndex, bookPage.pageIndex)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (annotation?.type == AnnotationType.HIGHLIGHT.name ||
                                    annotation?.type == AnnotationType.NOTE.name
                                ) {
                                    Color(annotation.color)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .pointerInput(bookPage.chapterIndex, bookPage.pageIndex) {
                                detectTapGestures(
                                    onLongPress = {
                                        annotationTarget = AnnotationTarget(
                                            chapterIndex = bookPage.chapterIndex,
                                            pageIndex = bookPage.pageIndex,
                                            paragraphIndex = -1,
                                            text = bookPage.text.text,
                                        )
                                    },
                                )
                            }
                            .padding(
                                start = horizontalPadding,
                                top = topPadding,
                                end = horizontalPadding,
                                bottom = bottomPadding,
                            ),
                    ) {
                        BasicText(
                            text = bookPage.text,
                            style = textStyleFor(annotation),
                        )
                    }
                }
            }
        } else if (freeScroll) {
            key(chapterIndex) {
                val listState = remember(chapterIndex) {
                    androidx.compose.foundation.lazy.LazyListState(
                        firstVisibleItemIndex = chapterIndex.coerceIn(0, chapters.lastIndex),
                        firstVisibleItemScrollOffset = 0,
                    )
                }
                LaunchedEffect(chapterIndex) {
                    if (chapterIndex in chapters.indices) {
                        listState.scrollToItem(chapterIndex)
                    }
                }
                LaunchedEffect(listState, chapters.size) {
                    snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                        freeChapterIndex = index.coerceIn(0, chapters.lastIndex)
                        onProgress(freeChapterIndex, 0, chapters.size)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        top = topPadding,
                        end = horizontalPadding,
                        bottom = bottomPadding,
                    ),
                ) {
                    itemsIndexed(chapters, key = { index, _ -> index }) { chapterIdx, item ->
                        Column(
                            modifier = Modifier.padding(bottom = 20.dp),
                        ) {
                            Text(
                                text = item.title,
                                style = textStyle.copy(
                                    fontSize = (settings.fontSizeSp + 5).sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = Modifier.padding(bottom = 14.dp),
                            )
                            item.content.split('\n')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .forEachIndexed { paragraphIndex, paragraph ->
                                    val annotation = annotationFor(chapterIdx, 0, paragraphIndex)
                                    BasicText(
                                        text = if (settings.indentFirstLine) {
                                            "\u3000\u3000$paragraph"
                                        } else {
                                            paragraph
                                        },
                                        style = textStyleFor(annotation),
                                        modifier = Modifier
                                            .background(
                                                if (annotation?.type == AnnotationType.HIGHLIGHT.name ||
                                                    annotation?.type == AnnotationType.NOTE.name
                                                ) {
                                                    Color(annotation.color)
                                                } else {
                                                    Color.Transparent
                                                },
                                            )
                                            .pointerInput(chapterIdx, paragraphIndex) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        annotationTarget = AnnotationTarget(
                                                            chapterIndex = chapterIdx,
                                                            pageIndex = 0,
                                                            paragraphIndex = paragraphIndex,
                                                            text = paragraph,
                                                        )
                                                    },
                                                )
                                            }
                                            .padding(
                                                bottom = settings.paragraphSpacing.coerceIn(0f, 24f).dp,
                                            ),
                                    )
                                }
                        }
                    }
                }
            }
        } else if (pages == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = palette.text,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "正在排版本章…",
                        modifier = Modifier.padding(top = 12.dp),
                        color = palette.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        top = topPadding,
                        end = horizontalPadding,
                        bottom = bottomPadding,
                    ),
            ) {
                Text("本章暂无内容", color = palette.secondary)
            }
        } else if (
            settings.pageMode == PageMode.HORIZONTAL ||
            settings.pageMode == PageMode.FREE_HORIZONTAL
        ) {
            key(chapterIndex) {
                val hasNextChapter = chapterIndex < chapterCount - 1
                val startPage = initialPage.coerceIn(0, pages.lastIndex)
                val pagerState = rememberPagerState(
                    initialPage = startPage,
                    pageCount = { pages.size + if (hasNextChapter) 1 else 0 },
                )
                LaunchedEffect(pagerState, pages.size) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (page < pages.size) {
                            currentPage = page
                            onProgress(chapterIndex, page, pages.size)
                        } else if (hasNextChapter) {
                            onSelectChapter(chapterIndex + 1)
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    if (page >= pages.size) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "正在进入下一章…",
                                color = palette.secondary,
                            )
                        }
                        return@HorizontalPager
                    }
                    val annotation = annotationFor(chapterIndex, page)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (annotation?.type == AnnotationType.HIGHLIGHT.name ||
                                    annotation?.type == AnnotationType.NOTE.name
                                ) {
                                    Color(annotation.color)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .pointerInput(chapterIndex, page) {
                                detectTapGestures(
                                    onLongPress = {
                                        annotationTarget = AnnotationTarget(
                                            chapterIndex = chapterIndex,
                                            pageIndex = page,
                                            paragraphIndex = -1,
                                            text = pages[page].text,
                                        )
                                    },
                                )
                            }
                            .padding(
                                start = horizontalPadding,
                                top = topPadding,
                                end = horizontalPadding,
                                bottom = bottomPadding,
                            ),
                    ) {
                        BasicText(
                            text = pages[page],
                            style = textStyleFor(annotation),
                        )
                    }
                }
            }
        } else {
            key(chapterIndex) {
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = initialPage.coerceIn(0, pages.lastIndex),
                )
                LaunchedEffect(listState, pages.size) {
                    snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                        currentPage = index
                        onProgress(chapterIndex, index, pages.size)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        top = topPadding,
                        end = horizontalPadding,
                        bottom = bottomPadding,
                    ),
                ) {
                    itemsIndexed(pages) { pageIndex, page ->
                        val annotation = annotationFor(chapterIndex, pageIndex)
                        BasicText(
                            text = page,
                            style = textStyleFor(annotation),
                            modifier = Modifier
                                .background(
                                    if (annotation?.type == AnnotationType.HIGHLIGHT.name ||
                                        annotation?.type == AnnotationType.NOTE.name
                                    ) {
                                        Color(annotation.color)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .pointerInput(chapterIndex, pageIndex) {
                                    detectTapGestures(
                                        onLongPress = {
                                            annotationTarget = AnnotationTarget(
                                                chapterIndex = chapterIndex,
                                                pageIndex = pageIndex,
                                                paragraphIndex = -1,
                                                text = page.text,
                                            )
                                        },
                                    )
                                }
                                .padding(bottom = 10.dp),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ReaderTopBar(
                title = bookTitle,
                backgroundColor = palette.background,
                contentColor = palette.text,
                onBack = onBack,
                onToc = { showToc = true },
                onTts = { showTts = true },
                onAi = { showAi = true },
                onNotes = { showAnnotations = true },
                onSettings = { showSettings = true },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (ttsController.isActive) {
                    TtsMiniBar(controller = ttsController, modifier = Modifier.fillMaxWidth())
                }
                ReaderBottomBar(
                    chapterTitle = displayChapterTitle,
                    chapterIndex = displayChapterIndex,
                    chapterCount = chapterCount,
                    pageInfo = when {
                        freeScroll -> null
                        freeHorizontal -> "${freeHorizontalPage + 1} / ${freeBookPages?.size ?: 0}"
                        else -> pages?.takeIf { it.isNotEmpty() }?.let { pageList ->
                            "${(currentPage + 1).coerceAtMost(pageList.size)} / ${pageList.size}"
                        }
                    },
                    backgroundColor = palette.background,
                    contentColor = palette.text,
                    onSettings = { showSettings = true },
                )
            }
        }
    }

    if (showToc) {
        val tocState = rememberLazyListState()
        LaunchedEffect(displayChapterIndex) {
            if (displayChapterIndex in chapters.indices) {
                tocState.scrollToItem(displayChapterIndex)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                text = "目录（${chapters.size}）",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(
                state = tocState,
                modifier = Modifier.heightIn(max = 480.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(chapters, key = { index, _ -> index }) { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectChapter(index)
                                showToc = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = item.title,
                            fontWeight = if (index == displayChapterIndex) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (index == displayChapterIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            onDismiss = { showSettings = false },
            onFontSizeChange = onFontSizeChange,
            onLineHeightChange = onLineHeightChange,
            onThemeChange = onThemeChange,
            onPageModeChange = onPageModeChange,
            onSerifFontChange = onSerifFontChange,
            onParagraphSpacingChange = onParagraphSpacingChange,
            onContentPaddingChange = onContentPaddingChange,
            onIndentFirstLineChange = onIndentFirstLineChange,
        )
    }

    if (showTts) {
        TtsSheet(
            controller = ttsController,
            catalog = ttsCatalog,
            onImportCatalog = onTtsCatalogChange,
            onDismiss = { showTts = false },
        )
    }

    if (showAi) {
        ReaderAiSheet(
            bookTitle = bookTitle,
            chapterTitle = displayChapterTitle,
            chapterText = ttsText,
            onDismiss = { showAi = false },
        )
    }

    if (showAnnotations) {
        val annotationPage = if (freeScroll) 0 else currentPage
        val bookmarkText = chapter.content.take(80)
        AnnotationsSheet(
            annotations = annotations,
            onDismiss = { showAnnotations = false },
            onJump = {
                showAnnotations = false
                onSelectChapter(it.chapterIndex)
            },
            onDelete = onDeleteAnnotation,
            onAddBookmark = {
                onAddAnnotation(
                    AnnotationDraft(
                        chapterIndex = displayChapterIndex,
                        pageIndex = annotationPage,
                        paragraphIndex = -1,
                        text = bookmarkText.ifBlank { displayChapterTitle },
                        type = AnnotationType.BOOKMARK,
                    ),
                )
                showAnnotations = false
            },
        )
    }

    annotationTarget?.let { target ->
        AnnotationActionDialog(
            target = target,
            existing = annotationFor(target.chapterIndex, target.pageIndex, target.paragraphIndex),
            onDismiss = { annotationTarget = null },
            onSave = { draft ->
                onAddAnnotation(draft)
                annotationTarget = null
            },
            onDelete = onDeleteAnnotation,
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    backgroundColor: Color,
    contentColor: Color,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onTts: () -> Unit,
    onAi: () -> Unit,
    onNotes: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = backgroundColor.copy(alpha = 0.94f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = contentColor)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onToc) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = "目录", tint = contentColor)
            }
            IconButton(onClick = onTts) {
                Icon(Icons.Default.Headphones, contentDescription = "听书", tint = contentColor)
            }
            IconButton(onClick = onAi) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI 伴读", tint = contentColor)
            }
            IconButton(onClick = onNotes) {
                Icon(Icons.Default.Bookmarks, contentDescription = "笔记与书签", tint = contentColor)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "阅读设置", tint = contentColor)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    pageInfo: String?,
    backgroundColor: Color,
    contentColor: Color,
    onSettings: () -> Unit,
) {
    Surface(color = backgroundColor.copy(alpha = 0.94f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chapterTitle,
                modifier = Modifier.weight(1f),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${chapterIndex + 1} / $chapterCount",
                modifier = Modifier.padding(start = 12.dp),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
            )
            pageInfo?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(start = 12.dp),
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onPageModeChange: (PageMode) -> Unit,
    onSerifFontChange: (Boolean) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onContentPaddingChange: (Float) -> Unit,
    onIndentFirstLineChange: (Boolean) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "阅读设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Column {
                Text("字号：${"%.0f".format(settings.fontSizeSp)}")
                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..30f,
                )
            }
            Column {
                Text("行距：${"%.1f".format(settings.lineHeight)}")
                Slider(
                    value = settings.lineHeight,
                    onValueChange = onLineHeightChange,
                    valueRange = 1.2f..2.2f,
                )
            }
            Column {
                Text("段间距：${"%.0f".format(settings.paragraphSpacing)}")
                Slider(
                    value = settings.paragraphSpacing,
                    onValueChange = onParagraphSpacingChange,
                    valueRange = 0f..24f,
                )
            }
            Column {
                Text("页边距：${"%.0f".format(settings.contentPadding)}")
                Slider(
                    value = settings.contentPadding,
                    onValueChange = onContentPaddingChange,
                    valueRange = 8f..40f,
                )
            }
            Text("主题")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReaderTheme.entries) { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(readerThemeLabel(theme)) },
                    )
                }
            }
            Text("翻页")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PageMode.entries) { mode ->
                    FilterChip(
                        selected = settings.pageMode == mode,
                        onClick = { onPageModeChange(mode) },
                        label = { Text(pageModeLabel(mode)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("首行缩进")
                    Text(
                        text = "每个自然段首行空两格",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = settings.indentFirstLine,
                    onCheckedChange = onIndentFirstLineChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("衬线字体")
                    Text(
                        text = "使用更接近纸质书的宋体/衬线风格",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = settings.serifFont,
                    onCheckedChange = onSerifFontChange,
                )
            }
        }
    }
}

private data class ReaderPalette(
    val background: Color,
    val text: Color,
    val secondary: Color,
)

private fun readerPalette(theme: ReaderTheme, appDark: Boolean): ReaderPalette = when (theme) {
    ReaderTheme.FOLLOW_APP -> if (appDark) {
        ReaderPalette(
            background = Color(0xFF141414),
            text = Color(0xFFB8B8B8),
            secondary = Color(0xFF6D6D6D),
        )
    } else {
        ReaderPalette(
            background = Color(0xFFFAFAF8),
            text = Color(0xFF202124),
            secondary = Color(0xFF7A7A7A),
        )
    }

    ReaderTheme.WHITE -> ReaderPalette(
        background = Color(0xFFFAFAF8),
        text = Color(0xFF202124),
        secondary = Color(0xFF7A7A7A),
    )

    ReaderTheme.SEPIA -> ReaderPalette(
        background = Color(0xFFF5EBD8),
        text = Color(0xFF4A3728),
        secondary = Color(0xFF8B7564),
    )

    ReaderTheme.GREEN -> ReaderPalette(
        background = Color(0xFFE7F1E7),
        text = Color(0xFF31473A),
        secondary = Color(0xFF65786B),
    )

    ReaderTheme.DARK -> ReaderPalette(
        background = Color(0xFF141414),
        text = Color(0xFFB8B8B8),
        secondary = Color(0xFF6D6D6D),
    )
}

private fun readerFontFamily(settings: ReaderSettings): FontFamily = when (settings.fontFamily) {
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.MONOSPACE -> FontFamily.Monospace
    ReaderFont.CURSIVE -> FontFamily.Cursive
    ReaderFont.SANS_SERIF -> FontFamily.SansSerif
    ReaderFont.DEFAULT -> if (settings.serifFont) FontFamily.Serif else FontFamily.Default
}

private fun readerThemeLabel(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.FOLLOW_APP -> "跟随应用"
    ReaderTheme.WHITE -> "白纸"
    ReaderTheme.SEPIA -> "羊皮纸"
    ReaderTheme.GREEN -> "护眼绿"
    ReaderTheme.DARK -> "夜间"
}

private fun pageModeLabel(mode: PageMode): String = when (mode) {
    PageMode.FREE_HORIZONTAL -> "横向自由翻阅"
    PageMode.FREE_SCROLL -> "自由滚动"
    PageMode.HORIZONTAL -> "横向翻页"
    PageMode.VERTICAL -> "纵向滚动"
}

private fun indentParagraphs(text: String): String =
    text.lineSequence().joinToString("\n") { line ->
        if (line.isBlank()) "" else "\u3000\u3000${line.trimStart()}"
    }
