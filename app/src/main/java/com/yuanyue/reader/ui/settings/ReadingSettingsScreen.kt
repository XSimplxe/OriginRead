package com.yuanyue.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.yuanyue.reader.data.ReaderFont
import com.yuanyue.reader.data.ReaderSettings
import com.yuanyue.reader.data.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState(initial = ReaderSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读设置") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingHeader("字体")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReaderFont.entries) { font ->
                    FilterChip(
                        selected = settings.fontFamily == font,
                        onClick = { viewModel.updateFontFamily(font) },
                        label = { Text(font.label) },
                    )
                }
            }
            SettingSwitch(
                title = "经典衬线",
                subtitle = "使用系统衬线字体作为默认字体",
                checked = settings.serifFont,
                onCheckedChange = viewModel::updateSerifFont,
            )

            SettingHeader("字号与间距")
            SettingSlider(
                label = "字号：${"%.0f".format(settings.fontSizeSp)}",
                value = settings.fontSizeSp,
                range = 12f..30f,
                onChange = viewModel::updateFontSize,
            )
            SettingSlider(
                label = "行距：${"%.1f".format(settings.lineHeight)}",
                value = settings.lineHeight,
                range = 1.2f..2.2f,
                onChange = viewModel::updateLineHeight,
            )
            SettingSlider(
                label = "段间距：${"%.0f".format(settings.paragraphSpacing)}",
                value = settings.paragraphSpacing,
                range = 0f..24f,
                onChange = viewModel::updateParagraphSpacing,
            )
            SettingSlider(
                label = "页边距：${"%.0f".format(settings.contentPadding)}",
                value = settings.contentPadding,
                range = 8f..40f,
                onChange = viewModel::updateContentPadding,
            )
            SettingSwitch(
                title = "首行缩进",
                subtitle = "每个自然段首行空两格",
                checked = settings.indentFirstLine,
                onCheckedChange = viewModel::updateIndentFirstLine,
            )

            SettingHeader("阅读主题")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReaderTheme.entries) { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { viewModel.updateTheme(theme) },
                        label = { Text(themeLabel(theme)) },
                    )
                }
            }

            SettingHeader("翻页方式")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PageMode.entries) { mode ->
                    FilterChip(
                        selected = settings.pageMode == mode,
                        onClick = { viewModel.updatePageMode(mode) },
                        label = { Text(pageModeLabel(mode)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
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
