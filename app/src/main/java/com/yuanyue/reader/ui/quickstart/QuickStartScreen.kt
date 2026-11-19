package com.yuanyue.reader.ui.quickstart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.YuanyueApplication
import com.yuanyue.reader.core.AppContainer
import com.yuanyue.reader.data.AiProviders
import com.yuanyue.reader.data.AiSettings
import com.yuanyue.reader.data.AppearanceSettings
import com.yuanyue.reader.data.OperationResult
import com.yuanyue.reader.data.PaletteRole
import com.yuanyue.reader.data.ProfileSettings
import com.yuanyue.reader.data.aiProviderForBaseUrl
import kotlinx.coroutines.launch

private val accentPresets = listOf(
    0xFF1F6FEB,
    0xFF5B5BD6,
    0xFF00897B,
    0xFF2E7D32,
    0xFFEF6C00,
    0xFFC62828,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStartScreen(
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    container: AppContainer? = null,
) {
    val context = LocalContext.current
    val appContainer = container
        ?: (context.applicationContext as YuanyueApplication).container
    val profile by appContainer.settingsRepository.profile.collectAsState(
        initial = ProfileSettings(),
    )
    val ai by appContainer.settingsRepository.ai.collectAsState(initial = AiSettings())
    val appearance by appContainer.settingsRepository.appearance.collectAsState(
        initial = AppearanceSettings(),
    )
    val scope = rememberCoroutineScope()

    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    var apiKey by remember(ai.apiKey) { mutableStateOf(ai.apiKey) }
    var baseUrl by remember(ai.baseUrl) { mutableStateOf(ai.baseUrl) }
    var model by remember(ai.model) { mutableStateOf(ai.model) }
    var rssUrl by remember { mutableStateOf("") }
    var sourceInput by remember { mutableStateOf("") }
    var selectedColor by remember(appearance.light.primary) {
        mutableStateOf(appearance.light.primary)
    }
    var rssMessage by remember { mutableStateOf<String?>(null) }
    var sourceMessage by remember { mutableStateOf<String?>(null) }
    var rssLoading by remember { mutableStateOf(false) }
    var sourceLoading by remember { mutableStateOf(false) }

    fun saveAndClose(callback: () -> Unit) {
        scope.launch {
            appContainer.settingsRepository.updateNickname(nickname)
            appContainer.settingsRepository.updateAiSettings(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
            )
            appContainer.settingsRepository.updateUseCustomColors(true)
            appContainer.settingsRepository.updatePaletteColor(
                dark = false,
                role = PaletteRole.PRIMARY,
                color = selectedColor,
            )
            appContainer.settingsRepository.updatePaletteColor(
                dark = true,
                role = PaletteRole.PRIMARY,
                color = selectedColor,
            )
            appContainer.settingsRepository.updatePaletteColor(
                dark = false,
                role = PaletteRole.SECONDARY,
                color = selectedColor,
            )
            appContainer.settingsRepository.updatePaletteColor(
                dark = true,
                role = PaletteRole.SECONDARY,
                color = selectedColor,
            )
            callback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("第一次使用") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "欢迎使用原阅",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "花一分钟把阅读环境准备好。没有填写的项目都可以稍后在「设置」里补充。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                Text(
                    text = "1. 先添加书源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "在书架「在线」中搜索和阅读网络小说、漫画。支持 Legado 书源链接、JSON 或 JSONL。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                Text(
                    text = "2. 再订阅 RSS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "RSS 会聚合到首页的每日摘要和资讯流。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("你的昵称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text("界面主色", fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(accentPresets) { color ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(color), CircleShape)
                                .border(
                                    width = if (selectedColor == color) 3.dp else 1.dp,
                                    color = if (selectedColor == color) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { selectedColor = color },
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = rssUrl,
                    onValueChange = { rssUrl = it },
                    label = { Text("RSS 订阅地址") },
                    placeholder = { Text("https://example.com/feed.xml") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rssMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = {
                            if (rssUrl.isBlank() || rssLoading) return@TextButton
                            rssLoading = true
                            rssMessage = null
                            scope.launch {
                                val result = appContainer.rssRepository.addFeed(rssUrl.trim())
                                rssMessage = when (result) {
                                    is OperationResult.Success -> "RSS 已添加"
                                    is OperationResult.Error -> result.message
                                }
                                rssLoading = false
                            }
                        },
                        enabled = !rssLoading && rssUrl.isNotBlank(),
                    ) {
                        if (rssLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("添加 RSS")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = sourceInput,
                    onValueChange = { sourceInput = it },
                    label = { Text("书源 JSON 或链接") },
                    placeholder = { Text("粘贴 Legado 书源链接、JSON 或 JSONL") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sourceMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = {
                            if (sourceInput.isBlank() || sourceLoading) return@TextButton
                            sourceLoading = true
                            sourceMessage = null
                            scope.launch {
                                runCatching {
                                    val candidates = appContainer.onlineBookRepository
                                        .importCandidates(sourceInput.trim())
                                    val added = appContainer.onlineBookRepository.addSources(
                                        candidates.candidates.map { it.source },
                                    )
                                    added
                                }.onSuccess { added ->
                                    sourceMessage = "已导入 $added 个书源"
                                    sourceInput = ""
                                }.onFailure {
                                    sourceMessage = it.message ?: "书源导入失败"
                                }
                                sourceLoading = false
                            }
                        },
                        enabled = !sourceLoading && sourceInput.isNotBlank(),
                    ) {
                        if (sourceLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("导入书源")
                        }
                    }
                }
            }

            item {
                val provider = aiProviderForBaseUrl(baseUrl)
                Text("AI 服务商", fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(AiProviders) { item ->
                        FilterChip(
                            selected = provider?.id == item.id ||
                                (item.id == "custom" && provider == null),
                            onClick = {
                                baseUrl = if (item.id == "custom") "" else item.baseUrl
                                model = item.models.firstOrNull().orEmpty()
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
                if (provider != null && provider.models.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(provider.models) { name ->
                            FilterChip(
                                selected = model == name,
                                onClick = { model = name },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("AI API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    text = "用于 AI 伴读和 RSS 摘要；选择服务商后会自动填入 Base URL 和常用模型。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { saveAndClose(onFinish) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("进入原阅")
                    }
                    TextButton(onClick = { saveAndClose(onSkip) }) {
                        Text("稍后设置")
                    }
                }
            }
                }
            }
        }
