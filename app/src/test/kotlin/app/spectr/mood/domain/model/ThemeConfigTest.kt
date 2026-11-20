package app.spectr.mood.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {
    @Test
    fun `system mode follows host dark setting`() {
        val config = ThemeConfig(mode = ThemeMode.SYSTEM)
        assertFalse(config.isDark(systemInDarkTheme = false))
        assertTrue(config.isDark(systemInDarkTheme = true))
        assertFalse(config.isPureAmoled())
    }

    @Test
    fun `light mode forces light regardless of system`() {
        val config = ThemeConfig(mode = ThemeMode.LIGHT)
        assertFalse(config.isDark(systemInDarkTheme = true))
        assertFalse(config.isDark(systemInDarkTheme = false))
        assertFalse(config.isPureAmoled())
    }

    @Test
    fun `dark mode forces dark without pure amoled`() {
        val config = ThemeConfig(mode = ThemeMode.DARK)
        assertTrue(config.isDark(systemInDarkTheme = false))
        assertFalse(config.isPureAmoled())
    }

    @Test
    fun `amoled mode enables both dark and pure amoled`() {
        val config = ThemeConfig(mode = ThemeMode.AMOLED)
        assertTrue(config.isDark(systemInDarkTheme = false))
        assertTrue(config.isPureAmoled())
    }
}
