package app.spectr.mood.domain.model

/**
 * Display brightness mode chosen by the user.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

/**
 * Color palette preset for Spectr.
 * [DYNAMIC] uses Android 12+ Material You wallpaper colors when available,
 * gracefully falling back to [SOFT_SLATE] on Android 8–11.
 */
enum class ThemePalette {
    DYNAMIC,
    MINIMAL,
    WARM_PAPER,
    SOFT_SLATE
}

/**
 * Complete theme configuration persisted in DataStore.
 */
data class ThemeConfig(val mode: ThemeMode = ThemeMode.SYSTEM, val palette: ThemePalette = ThemePalette.DYNAMIC) {
    /**
     * Resolves whether the UI should render in dark mode given the current system state.
     */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    /**
     * Returns true only when pure black OLED surfaces should be enforced.
     */
    fun isPureAmoled(): Boolean = mode == ThemeMode.AMOLED
}
