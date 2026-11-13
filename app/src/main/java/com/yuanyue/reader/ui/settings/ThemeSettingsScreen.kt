package com.yuanyue.reader.ui.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.AppPalette
import com.yuanyue.reader.data.PaletteRole
import com.yuanyue.reader.data.ThemeMode

private data class ColorPickerTarget(
    val dark: Boolean,
    val role: PaletteRole,
    val color: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val appearance by viewModel.appearance.collectAsState()
    var pickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观与配色") },
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
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            AppearancePreview(appearance.light, appearance.dark, appearance.mode)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "深色模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = appearance.mode == mode,
                            onClick = { viewModel.updateThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text(
                    text = "深色模式会应用到首页、书架、书源、设置和阅读页。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("使用自定义配色", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "关闭时使用内置浅色/深色配色",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = appearance.useCustomColors,
                    onCheckedChange = viewModel::updateUseCustomColors,
                )
            }

            if (appearance.useCustomColors) {
                PaletteEditor(
                    title = "浅色配色",
                    dark = false,
                    palette = appearance.light,
                    onPick = { role, color ->
                        pickerTarget = ColorPickerTarget(dark = false, role = role, color = color)
                    },
                )
                PaletteEditor(
                    title = "深色配色",
                    dark = true,
                    palette = appearance.dark,
                    onPick = { role, color ->
                        pickerTarget = ColorPickerTarget(dark = true, role = role, color = color)
                    },
                )
            }

            TextButton(onClick = viewModel::resetAppearance) {
                Text("恢复默认外观")
            }

            Text(
                text = "提示：自定义背景色和文字色时，请保持足够的对比度；阅读页会跟随应用深浅色（可在阅读设置中改为独立主题）。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    pickerTarget?.let { target ->
        ColorPickerSheet(
            title = "${if (target.dark) "深色" else "浅色"} · ${target.role.label}",
            initialColor = target.color,
            onDismiss = { pickerTarget = null },
            onConfirm = { color ->
                viewModel.updatePaletteColor(target.dark, target.role, color)
                pickerTarget = null
            },
        )
    }
}

@Composable
private fun AppearancePreview(
    light: AppPalette,
    dark: AppPalette,
    mode: ThemeMode,
) {
    val preview = when (mode) {
        ThemeMode.DARK -> dark
        ThemeMode.LIGHT -> light
        ThemeMode.SYSTEM -> light
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(preview.surface)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "配色预览",
                color = Color(preview.onSurface),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "这是正文与说明文字的效果。",
                color = Color(preview.onSurface),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(preview.primary), RoundedCornerShape(10.dp)),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(preview.secondary), RoundedCornerShape(10.dp)),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(preview.background), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(preview.onBackground).copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

@Composable
private fun PaletteEditor(
    title: String,
    dark: Boolean,
    palette: AppPalette,
    onPick: (PaletteRole, Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        PaletteRole.entries.forEach { role ->
            val color = palette.colorOf(role)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(role, color) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(color), RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(role.label)
                    Text(
                        text = color.toHex(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "调整",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerSheet(
    title: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val hsv = remember(initialColor) {
        val values = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toInt(), values)
        values
    }
    var hue by remember(initialColor) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(hsv[2]) }
    val picked = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
    val presets = listOf(
        0xFF1F6FEB, 0xFF5B5BD6, 0xFF00897B, 0xFF2E7D32,
        0xFFEF6C00, 0xFFC62828, 0xFFAD1457, 0xFF6D4C41,
        0xFF424242, 0xFF000000,
    ).map { Color(it) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(picked, RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = picked.toArgb().toLong().toHex(),
                    color = if (picked.luminance() > 0.5f) Color.Black else Color.White,
                )
            }

            Text("快捷颜色", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(presets) { preset ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(preset, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable {
                                val values = FloatArray(3)
                                AndroidColor.colorToHSV(preset.toArgb(), values)
                                hue = values[0]
                                saturation = values[1]
                                value = values[2]
                            },
                    )
                }
            }

            Text("色相 ${"%.0f".format(hue)}°")
            Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
            Text("饱和度 ${"%.0f".format(saturation * 100)}%")
            Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
            Text("明度 ${"%.0f".format(value * 100)}%")
            Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(picked.toArgb().toLong() and 0xFFFFFFFFL) },
                    modifier = Modifier.weight(2f),
                ) {
                    Text("应用这个颜色")
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    fun channel(v: Float): Float =
        if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

private fun Long.toHex(): String = "#%06X".format(this and 0xFFFFFFL)
