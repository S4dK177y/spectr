package app.spectr.mood

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spectr.mood.data.local.prefs.ThemePreferencesRepository
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.ui.SpectrApp
import app.spectr.mood.ui.theme.SpectrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemePreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val themeConfig by themeRepository.themeConfig.collectAsStateWithLifecycle(
                initialValue = ThemeConfig()
            )
            val coroutineScope = rememberCoroutineScope()
            val darkTheme = themeConfig.isDark(isSystemInDarkTheme())

            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT
                    ) { darkTheme }
                )
            }

            SpectrTheme(themeConfig = themeConfig) {
                SpectrApp(
                    themeConfig = themeConfig,
                    onThemeModeChange = { mode ->
                        coroutineScope.launch { themeRepository.setThemeMode(mode) }
                    },
                    onThemePaletteChange = { palette ->
                        coroutineScope.launch { themeRepository.setThemePalette(palette) }
                    }
                )
            }
        }
    }
}
