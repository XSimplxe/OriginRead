package com.yuanyue.reader.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val MaxTtsChunkLength = 1200

class ReaderTtsController(
    context: Context,
    private val initialVoiceName: String?,
    initialRate: Float,
    private val initialEnginePackage: String?,
    private val onVoiceChanged: (String?) -> Unit,
    private val onRateChanged: (Float) -> Unit,
    private val onEngineChanged: (String?) -> Unit,
) {
    private val appContext = context.applicationContext

    var isReady by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var voices by mutableStateOf<List<Voice>>(emptyList())
        private set
    var engines by mutableStateOf<List<TextToSpeech.EngineInfo>>(emptyList())
        private set
    var selectedEnginePackage by mutableStateOf(initialEnginePackage)
        private set
    var effectiveEnginePackage by mutableStateOf(initialEnginePackage)
        private set
    var selectedVoiceName by mutableStateOf(initialVoiceName)
        private set
    var rate by mutableFloatStateOf(initialRate.coerceIn(0.5f, 2f))
        private set
    var progress by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val isActive: Boolean get() = total > 0 && (isSpeaking || isPaused)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var chunks: List<String> = emptyList()
    private var sourceText: String = ""
    private var currentIndex = 0
    private var speakingUtterance: String? = null
    private var released = false
    private var engineGeneration = 0

    init {
        createEngine(initialEnginePackage)
    }

    fun setText(text: String) {
        val normalized = text.trim()
        if (normalized == sourceText) return
        sourceText = normalized
        if (isSpeaking || isPaused) stop()
        chunks = splitForTts(normalized)
        total = chunks.size
        currentIndex = 0
        progress = 0
        error = null
    }

    fun play() {
        if (!isReady) {
            error = "系统语音引擎正在初始化"
            return
        }
        if (chunks.isEmpty()) {
            error = "当前章节没有可朗读的文字"
            return
        }
        if (currentIndex >= chunks.size) currentIndex = 0
        error = null
        isPaused = false
        isSpeaking = true
        speakCurrent()
    }

    fun pause() {
        if (!isSpeaking) return
        tts?.stop()
        speakingUtterance = null
        isSpeaking = false
        isPaused = true
    }

    fun stop() {
        tts?.stop()
        speakingUtterance = null
        isSpeaking = false
        isPaused = false
        currentIndex = 0
        progress = 0
    }

    fun changeRate(value: Float) {
        rate = value.coerceIn(0.5f, 2f)
        tts?.setSpeechRate(rate)
    }

    fun persistRate() {
        onRateChanged(rate)
    }

    fun selectVoice(voice: Voice) {
        val engine = tts ?: return
        if (engine.setVoice(voice) == TextToSpeech.SUCCESS) {
            selectedVoiceName = voice.name
            onVoiceChanged(voice.name)
        } else {
            error = "无法切换到这个音色"
        }
    }

    fun selectEngine(packageName: String?) {
        if (packageName == selectedEnginePackage) return
        stop()
        selectedEnginePackage = packageName
        onEngineChanged(packageName)
        createEngine(packageName)
    }

    fun openSystemTtsSettings() {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }.onFailure {
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun shutdown() {
        released = true
        engineGeneration++
        speakingUtterance = null
        isSpeaking = false
        isPaused = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun createEngine(packageName: String?) {
        val generation = ++engineGeneration
        tts?.shutdown()
        tts = null
        isReady = false
        isSpeaking = false
        isPaused = false
        voices = emptyList()
        selectedVoiceName = null
        error = null
        chunks = splitForTts(sourceText)
        total = chunks.size
        currentIndex = 0
        progress = 0
        val listener = TextToSpeech.OnInitListener { status ->
            onInitialized(status, generation)
        }
        tts = if (packageName.isNullOrBlank()) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, packageName)
        }
    }

    private fun onInitialized(status: Int, generation: Int) {
        mainHandler.post {
            if (released || generation != engineGeneration) return@post
            if (status != TextToSpeech.SUCCESS || tts == null) {
                error = "系统语音初始化失败，请检查 TTS 引擎"
                isReady = false
                return@post
            }
            val engine = tts ?: return@post
            engines = runCatching { engine.engines.toList() }.getOrDefault(emptyList())
            effectiveEnginePackage = selectedEnginePackage
                ?: runCatching { engine.defaultEngine }.getOrNull()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        if (utteranceId == speakingUtterance) progress = currentIndex
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        if (released || !isSpeaking || utteranceId != speakingUtterance) return@post
                        currentIndex++
                        if (currentIndex < chunks.size) {
                            speakCurrent()
                        } else {
                            isSpeaking = false
                            isPaused = false
                            progress = chunks.size
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    reportError()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    reportError()
                }
            })

            val languageResult = engine.setLanguage(Locale.CHINA)
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                error = "系统缺少中文语音数据，可以在系统语音设置中安装"
            }
            engine.setSpeechRate(rate)
            val available = runCatching { engine.voices.orEmpty().toList() }
                .getOrDefault(emptyList())
                .distinctBy { it.name }
                .sortedWith(
                    compareByDescending<Voice> { it.locale.language == "zh" }
                        .thenByDescending { it.quality }
                        .thenBy { it.locale.toLanguageTag() }
                        .thenBy { it.name },
                )
            voices = available
            val preferred = available.firstOrNull { it.name == initialVoiceName }
                ?: available.firstOrNull { it.locale.language == "zh" && !it.isNetworkConnectionRequired }
                ?: available.firstOrNull { it.locale.language == "zh" }
                ?: available.firstOrNull()
            if (preferred != null && engine.setVoice(preferred) == TextToSpeech.SUCCESS) {
                selectedVoiceName = preferred.name
            }
            isReady = true
            if (total == 0 && sourceText.isNotBlank()) {
                chunks = splitForTts(sourceText)
                total = chunks.size
            }
        }
    }

    private fun reportError() {
        mainHandler.post {
            if (released) return@post
            isSpeaking = false
            isPaused = false
            error = "语音播放失败，请尝试切换音色或检查 TTS 引擎"
        }
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        if (currentIndex !in chunks.indices) {
            isSpeaking = false
            return
        }
        progress = currentIndex
        val utteranceId = "yuanyue-tts-$currentIndex-${System.nanoTime()}"
        speakingUtterance = utteranceId
        val result = engine.speak(
            chunks[currentIndex],
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
        if (result != TextToSpeech.SUCCESS) {
            isSpeaking = false
            error = "语音播放失败"
        }
    }

    private fun splitForTts(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val normalized = text.replace("\r\n", "\n").replace('\u3000', ' ').trim()
        val result = mutableListOf<String>()
        val paragraphs = normalized.split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (paragraph in paragraphs) {
            if (paragraph.length <= MaxTtsChunkLength) {
                result += paragraph
                continue
            }
            val sentences = paragraph.split(Regex("(?<=[。！？!?；;])"))
                .filter { it.isNotBlank() }
            var buffer = StringBuilder()
            fun flushBuffer() {
                if (buffer.isNotEmpty()) {
                    result += buffer.toString().trim()
                    buffer = StringBuilder()
                }
            }
            for (sentence in sentences) {
                if (sentence.length > MaxTtsChunkLength) {
                    flushBuffer()
                    sentence.chunked(MaxTtsChunkLength).forEach { result += it.trim() }
                } else {
                    if (buffer.length + sentence.length > MaxTtsChunkLength) flushBuffer()
                    buffer.append(sentence)
                }
            }
            flushBuffer()
        }
        return result
    }
}

private fun voiceQualityLabel(quality: Int): String = when {
    quality >= Voice.QUALITY_VERY_HIGH -> "极佳"
    quality >= Voice.QUALITY_HIGH -> "高"
    quality >= Voice.QUALITY_NORMAL -> "标准"
    else -> "基础"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSheet(
    controller: ReaderTtsController,
    catalog: TtsCatalog,
    onImportCatalog: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
                onImportCatalog(json)
                importMessage = "已导入 ${parsed.engines.size} 个引擎配置"
            }
            .onFailure { importMessage = it.message ?: "JSON 导入失败" }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "听书",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    !controller.isReady && controller.error == null -> "正在初始化系统语音…"
                    controller.error != null -> controller.error.orEmpty()
                    controller.isSpeaking -> "正在朗读 · 第 ${controller.progress + 1} / ${controller.total} 段"
                    controller.isPaused -> "已暂停 · 第 ${controller.progress + 1} / ${controller.total} 段"
                    else -> "当前章节共 ${controller.total} 段"
                },
                color = if (controller.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
            )

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
                            Text(
                                catalog.engineLabel(engine.name)
                                    ?: engine.label.ifBlank { engine.name },
                            )
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "音色",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "text/json", "text/plain", "*/*"),
                        )
                    },
                ) {
                    Text("导入 JSON")
                }
            }
            importMessage?.let { message ->
                Text(
                    text = message,
                    color = if (message.startsWith("已导入")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "JSON 可配置 engines[].package、name 和 voices[].name、label、locale",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            val unavailable = catalog.engines.filter { config ->
                controller.engines.none { it.name == config.packageName }
            }
            if (controller.isReady && unavailable.isNotEmpty()) {
                Text(
                    text = "有 ${unavailable.size} 个导入引擎未安装，当前不可选",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (controller.voices.isEmpty()) {
                Text(
                    text = if (controller.isReady) "系统没有可选音色" else "正在读取系统音色…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                ) {
                    items(controller.voices, key = { it.name }) { voice ->
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
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = buildString {
                                        if (customLabel != null) {
                                            append(voice.name)
                                            append(" · ")
                                        }
                                        append(voice.locale.displayName)
                                        append(" · ")
                                        append(voiceQualityLabel(voice.quality))
                                        if (voice.isNetworkConnectionRequired) append(" · 联网")
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "语速 ${"%.1f".format(controller.rate)}×",
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (controller.isSpeaking) controller.pause() else controller.play()
                    },
                ) {
                    Icon(
                        imageVector = if (controller.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (controller.isSpeaking) "暂停" else "播放",
                    )
                }
                IconButton(onClick = controller::stop) {
                    Icon(Icons.Default.Stop, contentDescription = "停止")
                }
            }
            Slider(
                value = controller.rate,
                onValueChange = controller::changeRate,
                onValueChangeFinished = controller::persistRate,
                valueRange = 0.5f..2.0f,
                steps = 14,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (controller.isSpeaking) controller.pause() else controller.play()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (controller.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (controller.isSpeaking) "暂停" else if (controller.isPaused) "继续" else "开始朗读",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                TextButton(onClick = controller::stop) {
                    Text("停止")
                }
            }

            if (controller.error != null) {
                TextButton(onClick = controller::openSystemTtsSettings) {
                    Text("打开系统语音设置")
                }
            }
        }
    }
}

@Composable
fun TtsMiniBar(
    controller: ReaderTtsController,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (controller.isSpeaking) controller.pause() else controller.play()
                },
            ) {
                Icon(
                    imageVector = if (controller.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (controller.isSpeaking) "暂停听书" else "继续听书",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (controller.isSpeaking) "正在听书" else "听书已暂停",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${controller.progress + 1} / ${controller.total} 段",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = controller::stop) {
                Icon(Icons.Default.Stop, contentDescription = "停止听书")
            }
        }
    }
}
