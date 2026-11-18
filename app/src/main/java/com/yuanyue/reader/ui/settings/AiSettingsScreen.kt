package com.yuanyue.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.AiProviders
import com.yuanyue.reader.data.AiSettings
import com.yuanyue.reader.data.aiProviderForBaseUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val ai by viewModel.ai.collectAsState(initial = AiSettings())
    var apiKey by remember(ai.apiKey) { mutableStateOf(ai.apiKey) }
    var baseUrl by remember(ai.baseUrl) { mutableStateOf(ai.baseUrl) }
    var model by remember(ai.model) { mutableStateOf(ai.model) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
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
            Text(
                text = "AI 伴读与 RSS 摘要",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "兼容 OpenAI Chat Completions 接口。API Key 只保存在本机。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            val provider = aiProviderForBaseUrl(baseUrl)
            Text("服务商", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiProviders) { item ->
                    FilterChip(
                        selected = provider?.id == item.id ||
                            (item.id == "custom" && provider == null),
                        onClick = {
                            baseUrl = if (item.id == "custom") "" else item.baseUrl
                            model = item.models.firstOrNull().orEmpty()
                            saved = false
                        },
                        label = { Text(item.label) },
                    )
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    saved = false
                },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    saved = false
                },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider != null && provider.models.isNotEmpty()) {
                Text("模型", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(provider.models) { name ->
                        FilterChip(
                            selected = model == name,
                            onClick = {
                                model = name
                                saved = false
                            },
                            label = { Text(name) },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        saved = false
                    },
                    label = { Text("模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.updateAiSettings(apiKey, baseUrl, model)
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
            if (saved) {
                Text(
                    text = "已保存",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "没有 API Key 时，AI 伴读和 AI 摘要会提示配置；普通阅读、RSS、书源与听书不受影响。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
