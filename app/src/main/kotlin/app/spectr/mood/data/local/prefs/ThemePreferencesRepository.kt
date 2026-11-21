package app.spectr.mood.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local DataStore repository for visual theme settings.
 */
class ThemePreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
    }

    val themeConfig: Flow<ThemeConfig> = context.spectrPreferencesDataStore.data.map { prefs ->
        val mode = prefs[Keys.THEME_MODE]
            ?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val palette = prefs[Keys.THEME_PALETTE]
            ?.let { raw -> runCatching { ThemePalette.valueOf(raw) }.getOrNull() }
            ?: ThemePalette.DYNAMIC
        ThemeConfig(mode = mode, palette = palette)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_PALETTE] = palette.name
        }
    }
}
