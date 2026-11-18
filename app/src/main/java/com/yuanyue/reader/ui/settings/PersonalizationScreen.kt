package com.yuanyue.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuanyue.reader.data.AppearanceSettings
import com.yuanyue.reader.data.PaletteRole
import com.yuanyue.reader.data.ProfileSettings
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
fun PersonalizationScreen(
    viewModel: SettingsViewModel,
    onOpenTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val profile by viewModel.profile.collectAsState(initial = ProfileSettings())
    val appearance by viewModel.appearance.collectAsState(initial = AppearanceSettings())
    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    var selectedColor by remember(appearance.light.primary) {
        mutableStateOf(appearance.light.primary)
    }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个性化设置") },
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("昵称", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    scope.launch { viewModel.updateNickname(nickname) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存昵称")
            }

            Text("界面主色", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .clickable {
                                selectedColor = color
                                scope.launch {
                                    viewModel.updateUseCustomColors(true)
                                    viewModel.updatePaletteColor(false, PaletteRole.PRIMARY, color)
                                    viewModel.updatePaletteColor(true, PaletteRole.PRIMARY, color)
                                    viewModel.updatePaletteColor(false, PaletteRole.SECONDARY, color)
                                    viewModel.updatePaletteColor(true, PaletteRole.SECONDARY, color)
                                }
                            },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTheme)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("深色模式与调色盘")
                    Text(
                        text = "浅色 / 深色 / 跟随系统，六个颜色角色",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}
