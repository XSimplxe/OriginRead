package com.yuanyue.reader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "reader_settings")

enum class ReaderTheme(val key: String) {
    FOLLOW_APP("follow_app"),
    WHITE("white"),
    SEPIA("sepia"),
    GREEN("green"),
    DARK("dark"),
}

enum class PageMode(val key: String) {
    FREE_HORIZONTAL("free_horizontal"),
    FREE_SCROLL("free_scroll"),
    HORIZONTAL("horizontal"),
    VERTICAL("vertical"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class PaletteRole(val label: String) {
    PRIMARY("主色"),
    SECONDARY("强调色"),
    BACKGROUND("背景色"),
    SURFACE("卡片色"),
    ON_BACKGROUND("背景文字"),
    ON_SURFACE("卡片文字"),
}

data class AppPalette(
    val primary: Long = 0xFF1F6FEB,
    val secondary: Long = 0xFF5B5BD6,
    val background: Long = 0xFFF8F8FA,
    val surface: Long = 0xFFFFFFFF,
    val onBackground: Long = 0xFF17181A,
    val onSurface: Long = 0xFF17181A,
) {
    fun colorOf(role: PaletteRole): Long = when (role) {
        PaletteRole.PRIMARY -> primary
        PaletteRole.SECONDARY -> secondary
        PaletteRole.BACKGROUND -> background
        PaletteRole.SURFACE -> surface
        PaletteRole.ON_BACKGROUND -> onBackground
        PaletteRole.ON_SURFACE -> onSurface
    }

    fun withColor(role: PaletteRole, color: Long): AppPalette = when (role) {
        PaletteRole.PRIMARY -> copy(primary = color)
        PaletteRole.SECONDARY -> copy(secondary = color)
        PaletteRole.BACKGROUND -> copy(background = color)
        PaletteRole.SURFACE -> copy(surface = color)
        PaletteRole.ON_BACKGROUND -> copy(onBackground = color)
        PaletteRole.ON_SURFACE -> copy(onSurface = color)
    }
}

val DefaultLightPalette = AppPalette()
val DefaultDarkPalette = AppPalette(
    primary = 0xFF9DB7FF,
    secondary = 0xFFC0C1FF,
    background = 0xFF121316,
    surface = 0xFF1C1D21,
    onBackground = 0xFFE5E6EA,
    onSurface = 0xFFE5E6EA,
)

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useCustomColors: Boolean = false,
    val light: AppPalette = DefaultLightPalette,
    val dark: AppPalette = DefaultDarkPalette,
)

data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineHeight: Float = 1.5f,
    val theme: ReaderTheme = ReaderTheme.FOLLOW_APP,
    val pageMode: PageMode = PageMode.FREE_HORIZONTAL,
    val serifFont: Boolean = false,
    val paragraphSpacing: Float = 8f,
    val contentPadding: Float = 24f,
    val indentFirstLine: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val fontSize = floatPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height")
        val theme = stringPreferencesKey("reader_theme")
        val pageMode = stringPreferencesKey("page_mode")
        val serifFont = booleanPreferencesKey("serif_font")
        val paragraphSpacing = floatPreferencesKey("paragraph_spacing")
        val contentPadding = floatPreferencesKey("content_padding")
        val indentFirstLine = booleanPreferencesKey("indent_first_line")

        val themeMode = stringPreferencesKey("app_theme_mode")
        val customColors = booleanPreferencesKey("app_custom_colors")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")

        val lightColors = mapOf(
            PaletteRole.PRIMARY to longPreferencesKey("light_primary"),
            PaletteRole.SECONDARY to longPreferencesKey("light_secondary"),
            PaletteRole.BACKGROUND to longPreferencesKey("light_background"),
            PaletteRole.SURFACE to longPreferencesKey("light_surface"),
            PaletteRole.ON_BACKGROUND to longPreferencesKey("light_on_background"),
            PaletteRole.ON_SURFACE to longPreferencesKey("light_on_surface"),
        )
        val darkColors = mapOf(
            PaletteRole.PRIMARY to longPreferencesKey("dark_primary"),
            PaletteRole.SECONDARY to longPreferencesKey("dark_secondary"),
            PaletteRole.BACKGROUND to longPreferencesKey("dark_background"),
            PaletteRole.SURFACE to longPreferencesKey("dark_surface"),
            PaletteRole.ON_BACKGROUND to longPreferencesKey("dark_on_background"),
            PaletteRole.ON_SURFACE to longPreferencesKey("dark_on_surface"),
        )
    }

    val settings: Flow<ReaderSettings> = context.settingsDataStore.data.map { prefs ->
        ReaderSettings(
            fontSizeSp = prefs[Keys.fontSize] ?: 18f,
            lineHeight = prefs[Keys.lineHeight] ?: 1.5f,
            theme = runCatching {
                ReaderTheme.valueOf(prefs[Keys.theme] ?: ReaderTheme.FOLLOW_APP.name)
            }.getOrDefault(ReaderTheme.FOLLOW_APP),
            pageMode = runCatching {
                PageMode.valueOf(prefs[Keys.pageMode] ?: PageMode.FREE_HORIZONTAL.name)
            }.getOrDefault(PageMode.FREE_HORIZONTAL),
            serifFont = prefs[Keys.serifFont] ?: false,
            paragraphSpacing = prefs[Keys.paragraphSpacing] ?: 8f,
            contentPadding = prefs[Keys.contentPadding] ?: 24f,
            indentFirstLine = prefs[Keys.indentFirstLine] ?: true,
        )
    }

    val appearance: Flow<AppearanceSettings> = context.settingsDataStore.data.map { prefs ->
        AppearanceSettings(
            mode = runCatching {
                ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            useCustomColors = prefs[Keys.customColors] ?: false,
            light = readPalette(prefs, Keys.lightColors, DefaultLightPalette),
            dark = readPalette(prefs, Keys.darkColors, DefaultDarkPalette),
        )
    }

    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.onboardingCompleted] ?: false
    }

    suspend fun setOnboardingCompleted() {
        context.settingsDataStore.edit { it[Keys.onboardingCompleted] = true }
    }

    suspend fun updateFontSize(value: Float) {
        context.settingsDataStore.edit { it[Keys.fontSize] = value }
    }

    suspend fun updateLineHeight(value: Float) {
        context.settingsDataStore.edit { it[Keys.lineHeight] = value }
    }

    suspend fun updateTheme(value: ReaderTheme) {
        context.settingsDataStore.edit { it[Keys.theme] = value.name }
    }

    suspend fun updatePageMode(value: PageMode) {
        context.settingsDataStore.edit { it[Keys.pageMode] = value.name }
    }

    suspend fun updateSerifFont(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.serifFont] = value }
    }

    suspend fun updateParagraphSpacing(value: Float) {
        context.settingsDataStore.edit { it[Keys.paragraphSpacing] = value }
    }

    suspend fun updateContentPadding(value: Float) {
        context.settingsDataStore.edit { it[Keys.contentPadding] = value }
    }

    suspend fun updateIndentFirstLine(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.indentFirstLine] = value }
    }

    suspend fun updateThemeMode(value: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = value.name }
    }

    suspend fun updateUseCustomColors(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.customColors] = value }
    }

    suspend fun updatePaletteColor(dark: Boolean, role: PaletteRole, color: Long) {
        val key = (if (dark) Keys.darkColors else Keys.lightColors)[role] ?: return
        context.settingsDataStore.edit { it[key] = color }
    }

    suspend fun resetAppearance() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.themeMode)
            prefs.remove(Keys.customColors)
            Keys.lightColors.values.forEach { prefs.remove(it) }
            Keys.darkColors.values.forEach { prefs.remove(it) }
        }
    }

    private fun readPalette(
        prefs: Preferences,
        keys: Map<PaletteRole, Preferences.Key<Long>>,
        default: AppPalette,
    ): AppPalette {
        var palette = default
        PaletteRole.entries.forEach { role ->
            val key = keys[role] ?: return@forEach
            prefs[key]?.let { palette = palette.withColor(role, it) }
        }
        return palette
    }
}
