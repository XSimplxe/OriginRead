package com.yuanyue.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanyue.reader.data.AiSettings
import com.yuanyue.reader.data.AppearanceSettings
import com.yuanyue.reader.data.PageMode
import com.yuanyue.reader.data.PaletteRole
import com.yuanyue.reader.data.ProfileSettings
import com.yuanyue.reader.data.ReaderFont
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

    val profile = settingsRepository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileSettings(),
    )

    val ai = settingsRepository.ai.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiSettings(),
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

    fun updateFontFamily(value: ReaderFont) {
        viewModelScope.launch { settingsRepository.updateFontFamily(value) }
    }

    fun updateNickname(value: String) {
        viewModelScope.launch { settingsRepository.updateNickname(value) }
    }

    fun updateAiSettings(apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch { settingsRepository.updateAiSettings(apiKey, baseUrl, model) }
    }

    fun updateTtsVoice(name: String?) {
        viewModelScope.launch { settingsRepository.updateTtsVoice(name) }
    }

    fun updateTtsRate(value: Float) {
        viewModelScope.launch { settingsRepository.updateTtsRate(value) }
    }

    fun updateTtsEngine(packageName: String?) {
        viewModelScope.launch { settingsRepository.updateTtsEngine(packageName) }
    }

    fun updateTtsCatalog(json: String?) {
        viewModelScope.launch { settingsRepository.updateTtsCatalog(json) }
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
