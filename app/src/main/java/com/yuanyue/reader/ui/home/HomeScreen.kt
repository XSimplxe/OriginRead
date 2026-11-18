package com.yuanyue.reader.ui.home

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.model.RssArticleEntity
import com.yuanyue.reader.model.RssFeedEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenFeedManager: () -> Unit,
    onOpenFeed: (Long) -> Unit,
    onOpenArticle: (Long) -> Unit,
    onOpenHotBoard: () -> Unit,
) {
    val feeds by viewModel.feeds.collectAsState(initial = emptyList())
    val latestArticles by viewModel.latestArticles.collectAsState(initial = emptyList())
    val profile by viewModel.profile.collectAsState(initial = com.yuanyue.reader.data.ProfileSettings())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            HomeHeader(
                nickname = profile.nickname,
                isRefreshing = viewModel.isRefreshing,
                onRefresh = viewModel::refreshAll,
            )
        }

        item {
            DailyDigestCard(
                articles = latestArticles.take(5),
                aiSummary = viewModel.aiSummary,
                aiError = viewModel.aiError,
                isAiSummarizing = viewModel.isAiSummarizing,
                onSummarize = viewModel::summarizeRss,
                onOpenArticle = onOpenArticle,
            )
        }

        item {
            HotBoardCard(onClick = onOpenHotBoard)
        }

        item {
            SectionTitle(
                title = "我的订阅",
                action = {
                    TextButton(onClick = onOpenFeedManager) {
                        Text("管理")
                    }
                },
            )
        }

        if (feeds.isEmpty()) {
            item {
                EmptyFeedHint(onOpenFeedManager)
            }
        } else {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(feeds, key = { it.id }) { feed ->
                        FeedCard(feed = feed, onClick = { onOpenFeed(feed.id) })
                    }
                }
            }
        }

        item {
            SectionTitle(title = "资讯流", action = null)
        }
        if (latestArticles.isEmpty()) {
            item {
                Text(
                    text = "订阅源更新后，这里会出现按时间排列的资讯。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(latestArticles.take(12), key = { it.id }) { article ->
                FeedArticleRow(article = article, onClick = { onOpenArticle(article.id) })
            }
        }
    }
}

@Composable
private fun HomeHeader(
    nickname: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val date = LocalDate.now().format(
        DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE),
    )
    val hour = java.time.LocalTime.now().hour
    val greeting = when (hour) {
        in 0..5 -> "夜深了"
        in 6..11 -> "早上好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$greeting，$nickname",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "原阅 · $date",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                )
            }
        }
    }
}

@Composable
private fun DailyDigestCard(
    articles: List<RssArticleEntity>,
    aiSummary: String?,
    aiError: String?,
    isAiSummarizing: Boolean,
    onSummarize: () -> Unit,
    onOpenArticle: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "每日资讯摘要",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (articles.isEmpty()) {
                Text(
                    text = "添加 RSS 订阅后，这里会聚合你关心的内容。",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            } else {
                articles.forEachIndexed { index, article ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArticle(article.id) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier.padding(end = 10.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = article.title,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            article.publishedAt?.let {
                                Text(
                                    text = formatDigestTime(it),
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            aiSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = "AI 摘要",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            aiError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onSummarize, enabled = !isAiSummarizing && articles.isNotEmpty()) {
                Text(
                    text = if (isAiSummarizing) "AI 正在阅读…" else "生成 AI 摘要",
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun HotBoardCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = "今日热榜",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "微博 · 百度 · 知乎 · 哔哩哔哩",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    action: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        action?.invoke()
    }
}

@Composable
private fun FeedCard(
    feed: RssFeedEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .size(width = 220.dp, height = 120.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = feed.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = feed.url,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedArticleRow(
    article: RssArticleEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                article.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                article.publishedAt?.let {
                    Text(
                        text = formatDigestTime(it),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyFeedHint(onOpenFeedManager: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "还没有订阅源",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenFeedManager) {
            Text("去添加 RSS 订阅")
        }
    }
}

private fun formatDigestTime(epochMillis: Long): String {
    return runCatching {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINESE).format(dateTime)
    }.getOrDefault("")
}
