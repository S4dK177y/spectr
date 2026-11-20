package app.spectr.mood.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemePalette

/**
 * Root Material 3 theme for Spectr with support for:
 * - Android 12+ Material You Dynamic Color
 * - 3 curated built-in palettes (Minimal, Warm Paper, Soft Slate)
 * - System / Light / Dark / Pure AMOLED Dark modes
 * - Consistent semantic mood color scale (-5 .. +5) via [LocalMoodColors]
 */
@Composable
fun SpectrTheme(themeConfig: ThemeConfig = ThemeConfig(), content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = themeConfig.isDark(systemDark)
    val context = LocalContext.current

    val baseScheme = when (themeConfig.palette) {
        ThemePalette.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) SoftSlateDarkScheme else SoftSlateLightScheme
            }
        }
        ThemePalette.MINIMAL -> if (darkTheme) MinimalDarkScheme else MinimalLightScheme
        ThemePalette.WARM_PAPER -> if (darkTheme) WarmPaperDarkScheme else WarmPaperLightScheme
        ThemePalette.SOFT_SLATE -> if (darkTheme) SoftSlateDarkScheme else SoftSlateLightScheme
    }

    val finalScheme = if (themeConfig.isPureAmoled()) {
        baseScheme.toPureAmoled()
    } else {
        baseScheme
    }

    val moodColors = if (darkTheme) MoodColorScale.Dark else MoodColorScale.Light

    CompositionLocalProvider(LocalMoodColors provides moodColors) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = SpectrTypography,
            content = content
        )
    }
}
