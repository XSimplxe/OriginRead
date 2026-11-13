package com.yuanyue.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.yuanyue.reader.data.AppPalette
import com.yuanyue.reader.data.AppearanceSettings
import com.yuanyue.reader.data.ThemeMode

/** 阅读页用它判断应用当前是否处于深色主题。 */
val LocalAppDarkMode = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF5B5BD6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E0FF),
    onSecondaryContainer = Color(0xFF170065),
    background = PaperWhite,
    onBackground = ReaderInk,
    surface = WarmCard,
    onSurface = ReaderInk,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = SoftInk,
    outline = DividerGray,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF15448F),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFFC0C1FF),
    onSecondary = Color(0xFF2B2A70),
    secondaryContainer = Color(0xFF42418A),
    onSecondaryContainer = Color(0xFFE2E0FF),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE5E6EA),
    surface = Color(0xFF1C1D21),
    onSurface = Color(0xFFE5E6EA),
    surfaceVariant = Color(0xFF2A2C31),
    onSurfaceVariant = Color(0xFFB8B9C0),
    outline = Color(0xFF8A8C94),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun YuanyueTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) appearance.dark else appearance.light
    val colorScheme = when {
        appearance.useCustomColors -> palette.toColorScheme(dark)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalAppDarkMode provides dark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

private fun AppPalette.toColorScheme(dark: Boolean) = run {
    val bg = Color(background)
    val card = Color(surface)
    val primaryColor = Color(primary)
    val secondaryColor = Color(secondary)
    val onBg = Color(onBackground)
    val onCard = Color(onSurface)
    val primaryContainer = mix(primaryColor, bg, 0.84f)
    val secondaryContainer = mix(secondaryColor, bg, 0.84f)
    val surfaceVariant = mix(card, onCard, 0.08f)
    val outline = mix(onBg, bg, 0.55f)

    if (dark) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = contrast(primaryColor),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contrast(primaryContainer),
            secondary = secondaryColor,
            onSecondary = contrast(secondaryColor),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contrast(secondaryContainer),
            tertiary = secondaryColor,
            onTertiary = contrast(secondaryColor),
            background = bg,
            onBackground = onBg,
            surface = card,
            onSurface = onCard,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = mix(onCard, bg, 0.25f),
            outline = outline,
            outlineVariant = mix(outline, bg, 0.5f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = contrast(primaryColor),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contrast(primaryContainer),
            secondary = secondaryColor,
            onSecondary = contrast(secondaryColor),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contrast(secondaryContainer),
            tertiary = secondaryColor,
            onTertiary = contrast(secondaryColor),
            background = bg,
            onBackground = onBg,
            surface = card,
            onSurface = onCard,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = mix(onCard, bg, 0.25f),
            outline = outline,
            outlineVariant = mix(outline, bg, 0.5f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

private fun mix(start: Color, stop: Color, fraction: Float): Color = Color(
    red = start.red + (stop.red - start.red) * fraction,
    green = start.green + (stop.green - start.green) * fraction,
    blue = start.blue + (stop.blue - start.blue) * fraction,
    alpha = 1f,
)

private fun contrast(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF111111) else Color.White
