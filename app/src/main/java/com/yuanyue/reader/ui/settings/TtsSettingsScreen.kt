package com.yuanyue.reader.ui.settings

import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.ReaderSettings
import com.yuanyue.reader.ui.reader.ReaderTtsController
import com.yuanyue.reader.ui.reader.TtsCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState(initial = ReaderSettings())
    val context = LocalContext.current
    val catalog = remember(settings.ttsCatalogJson) {
        TtsCatalog.parse(settings.ttsCatalogJson)
    }
    val controller = remember {
        ReaderTtsController(
            context = context,
            initialVoiceName = settings.ttsVoiceName,
            initialRate = settings.ttsRate,
            initialEnginePackage = settings.ttsEnginePackage,
            onVoiceChanged = viewModel::updateTtsVoice,
            onRateChanged = viewModel::updateTtsRate,
            onEngineChanged = viewModel::updateTtsEngine,
        )
    }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
        if (json.isNullOrBlank()) {
            importMessage = "读取 JSON 文件失败"
            return@rememberLauncherForActivityResult
        }
        runCatching { TtsCatalog.parseOrThrow(json) }
            .onSuccess { parsed ->
                viewModel.updateTtsCatalog(json)
                importMessage = "已导入 ${parsed.engines.size} 个引擎配置"
            }
            .onFailure { importMessage = it.message ?: "JSON 导入失败" }
    }

    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    LaunchedEffect(controller) {
        controller.setText("你好，这是原阅的听书试听。可以在阅读页打开听书，让系统语音朗读当前章节。")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("听书设置") },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("引擎", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = controller.selectedEnginePackage == null,
                        onClick = { controller.selectEngine(null) },
                        label = { Text("系统默认") },
                    )
                }
                items(controller.engines, key = { it.name }) { engine ->
                    FilterChip(
                        selected = controller.selectedEnginePackage == engine.name,
                        onClick = { controller.selectEngine(engine.name) },
                        label = {
                            Text(catalog.engineLabel(engine.name) ?: engine.label.ifBlank { engine.name })
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "音色",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) {
                    Text("导入 JSON")
                }
            }
            importMessage?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("已导入")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (controller.voices.isEmpty()) {
                Text(
                    text = if (controller.isReady) "系统没有可选音色" else "正在读取系统音色…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(controller.voices, key = { it.name }) { voice: Voice ->
                        val customLabel = catalog.voiceLabel(controller.effectiveEnginePackage, voice.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { controller.selectVoice(voice) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = voice.name == controller.selectedVoiceName,
                                onClick = { controller.selectVoice(voice) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = customLabel ?: voice.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${voice.locale.displayName} · ${voice.quality}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            Text("语速 ${"%.1f".format(controller.rate)}×", fontWeight = FontWeight.SemiBold)
            Slider(
                value = controller.rate,
                onValueChange = controller::changeRate,
                onValueChangeFinished = controller::persistRate,
                valueRange = 0.5f..2.0f,
                steps = 14,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { if (controller.isSpeaking) controller.pause() else controller.play() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (controller.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                    Text(if (controller.isSpeaking) "暂停" else "试听")
                }
                IconButton(onClick = controller::stop) {
                    Icon(Icons.Default.Stop, contentDescription = "停止")
                }
            }
            controller.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = controller::openSystemTtsSettings) {
                    Text("打开系统语音设置")
                }
            }
            Text(
                text = "音色列表来自设备已安装的 TTS 引擎；JSON 可补充引擎和音色显示名。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
