package com.yuanyue.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.AppearanceSettings
import com.yuanyue.reader.data.PageMode
import com.yuanyue.reader.data.PaletteRole
import com.yuanyue.reader.data.ReaderTheme
import com.yuanyue.reader.data.SettingsRepository
import com.yuanyue.reader.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.yuanyue.reader.data.ReaderSettings(),
    )

    val appearance = settingsRepository.appearance.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearanceSettings(),
    )

    fun updateFontSize(value: Float) {
        viewModelScope.launch { settingsRepository.updateFontSize(value) }
    }

    fun updateLineHeight(value: Float) {
        viewModelScope.launch { settingsRepository.updateLineHeight(value) }
    }

    fun updateTheme(value: ReaderTheme) {
        viewModelScope.launch { settingsRepository.updateTheme(value) }
    }

    fun updatePageMode(value: PageMode) {
        viewModelScope.launch { settingsRepository.updatePageMode(value) }
    }

    fun updateSerifFont(value: Boolean) {
        viewModelScope.launch { settingsRepository.updateSerifFont(value) }
    }

    fun updateParagraphSpacing(value: Float) {
        viewModelScope.launch { settingsRepository.updateParagraphSpacing(value) }
    }

    fun updateContentPadding(value: Float) {
        viewModelScope.launch { settingsRepository.updateContentPadding(value) }
    }

    fun updateIndentFirstLine(value: Boolean) {
        viewModelScope.launch { settingsRepository.updateIndentFirstLine(value) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    }

    fun updateUseCustomColors(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateUseCustomColors(enabled) }
    }

    fun updatePaletteColor(dark: Boolean, role: PaletteRole, color: Long) {
        viewModelScope.launch { settingsRepository.updatePaletteColor(dark, role, color) }
    }

    fun resetAppearance() {
        viewModelScope.launch { settingsRepository.resetAppearance() }
    }
}
