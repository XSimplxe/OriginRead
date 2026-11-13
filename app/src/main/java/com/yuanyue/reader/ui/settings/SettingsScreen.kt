package com.yuanyue.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.PageMode
import com.yuanyue.reader.data.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenTheme: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState(
        initial = com.yuanyue.reader.data.ReaderSettings(),
    )

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
            SettingsGroup(title = "外观") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenTheme)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("外观与配色")
                        Text(
                            text = "深色模式、调色盘、完全自定义界面颜色",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            SettingsGroup(title = "阅读排版") {
                Column {
                    SettingLabel("字号：${"%.0f".format(settings.fontSizeSp)}")
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = viewModel::updateFontSize,
                        valueRange = 12f..30f,
                        steps = 17,
                    )
                }
                Column {
                    SettingLabel("行距：${"%.1f".format(settings.lineHeight)}")
                    Slider(
                        value = settings.lineHeight,
                        onValueChange = viewModel::updateLineHeight,
                        valueRange = 1.2f..2.2f,
                    )
                }
                Column {
                    SettingLabel("段间距：${"%.0f".format(settings.paragraphSpacing)}")
                    Slider(
                        value = settings.paragraphSpacing,
                        onValueChange = viewModel::updateParagraphSpacing,
                        valueRange = 0f..24f,
                    )
                }
                Column {
                    SettingLabel("页边距：${"%.0f".format(settings.contentPadding)}")
                    Slider(
                        value = settings.contentPadding,
                        onValueChange = viewModel::updateContentPadding,
                        valueRange = 8f..40f,
                    )
                }
                SettingSwitch(
                    title = "首行缩进",
                    subtitle = "每个自然段首行空两格",
                    checked = settings.indentFirstLine,
                    onCheckedChange = viewModel::updateIndentFirstLine,
                )
                SettingSwitch(
                    title = "衬线字体",
                    subtitle = "更接近纸质书的宋体/衬线风格",
                    checked = settings.serifFont,
                    onCheckedChange = viewModel::updateSerifFont,
                )
            }

            SettingsGroup(title = "阅读主题") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReaderTheme.entries) { theme ->
                        FilterChip(
                            selected = settings.theme == theme,
                            onClick = { viewModel.updateTheme(theme) },
                            label = { Text(themeLabel(theme)) },
                        )
                    }
                }
            }

            SettingsGroup(title = "阅读方式") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PageMode.entries) { mode ->
                        FilterChip(
                            selected = settings.pageMode == mode,
                            onClick = { viewModel.updatePageMode(mode) },
                            label = { Text(pageModeLabel(mode)) },
                        )
                    }
                }
                Text(
                    text = "自由滚动会连续显示整本书，可以一路滑下去，不再按章节目录翻页。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SettingsGroup(title = "关于") {
                Column {
                    Text(
                        text = "原阅",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "版本 1.2.0",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "聚合资讯与本地阅读的极简阅读器",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun themeLabel(theme: ReaderTheme): String = when (theme) {
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
