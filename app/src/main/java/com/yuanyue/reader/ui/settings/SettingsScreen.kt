package com.yuanyue.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.AiSettings
import com.yuanyue.reader.data.ProfileSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenReading: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenTts: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val profile by viewModel.profile.collectAsState(initial = ProfileSettings())
    val ai by viewModel.ai.collectAsState(initial = AiSettings())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsGroup(title = "阅读") {
                SettingsRow(
                    title = "阅读设置",
                    subtitle = "字体、字号、行距、段间距、主题与翻页方式",
                    onClick = onOpenReading,
                )
            }

            SettingsGroup(title = "个性化") {
                SettingsRow(
                    title = "个性化设置",
                    subtitle = "${profile.nickname} · 昵称、界面颜色、深色模式与调色盘",
                    onClick = onOpenPersonalization,
                )
            }

            SettingsGroup(title = "听书") {
                SettingsRow(
                    title = "听书设置",
                    subtitle = "音色、引擎、语速与音色 JSON",
                    onClick = onOpenTts,
                )
            }

            SettingsGroup(title = "AI") {
                SettingsRow(
                    title = "AI 设置",
                    subtitle = if (ai.isConfigured) {
                        "已配置 · ${ai.model}"
                    } else {
                        "未配置 API Key，AI 伴读与 AI 摘要不可用"
                    },
                    onClick = onOpenAi,
                )
            }

            SettingsGroup(title = "关于") {
                SettingsRow(
                    title = "关于原阅",
                    subtitle = "版本、开源许可与 GitHub",
                    onClick = onOpenAbout,
                )
            }

            Text(
                text = "原阅 · 安静地读一点东西",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    }
}
