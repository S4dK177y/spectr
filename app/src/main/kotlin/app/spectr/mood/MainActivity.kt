package app.spectr.mood

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.CancellationSignal
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spectr.mood.data.local.prefs.ThemePreferencesRepository
import app.spectr.mood.domain.model.SecurityConfig
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.repository.SecuritySettingsRepository
import app.spectr.mood.platform.SpectrLaunchContract
import app.spectr.mood.platform.SpectrLaunchRequest
import app.spectr.mood.security.BiometricAvailability
import app.spectr.mood.security.BiometricLockManager
import app.spectr.mood.security.PinHashManager
import app.spectr.mood.ui.SpectrApp
import app.spectr.mood.ui.security.AppLockScreen
import app.spectr.mood.ui.theme.SpectrTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemePreferencesRepository

    @Inject
    lateinit var securitySettingsRepository: SecuritySettingsRepository

    @Inject
    lateinit var biometricLockManager: BiometricLockManager

    @Inject
    lateinit var clock: Clock

    private val launchRequestFlow = MutableStateFlow<SpectrLaunchRequest?>(null)
    private val launchSequence = AtomicLong(1L)
    private val isLockedFlow = MutableStateFlow(false)
    private var latestSecurityConfig: SecurityConfig? = null
    private var hasEvaluatedColdStartLock = false
    private var lastBackgroundEpochMs: Long? = null
    private var wasDeviceScreenLocked = false
    private var activeBiometricSignal: CancellationSignal? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                wasDeviceScreenLocked = true
                if (latestSecurityConfig?.isLockActive == true) {
                    isLockedFlow.value = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        if (savedInstanceState == null) {
            dispatchLaunchIntent(intent)
        } else {
            hasEvaluatedColdStartLock = savedInstanceState.getBoolean(KEY_EVALUATED_COLD_START, false)
            isLockedFlow.value = savedInstanceState.getBoolean(KEY_IS_LOCKED, false)
            wasDeviceScreenLocked = savedInstanceState.getBoolean(KEY_WAS_DEVICE_SCREEN_LOCKED, false)
            if (savedInstanceState.containsKey(KEY_LAST_BACKGROUND_MS)) {
                lastBackgroundEpochMs = savedInstanceState.getLong(KEY_LAST_BACKGROUND_MS)
            }
        }

        enableEdgeToEdge()
        setContent {
            val themeConfig by themeRepository.themeConfig.collectAsStateWithLifecycle(
                initialValue = ThemeConfig()
            )
            val securityConfig by securitySettingsRepository.securityConfig.collectAsStateWithLifecycle(
                initialValue = null
            )
            val isLocked by isLockedFlow.collectAsStateWithLifecycle()
            val launchRequest by launchRequestFlow.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            val darkTheme = themeConfig.isDark(isSystemInDarkTheme())
            var biometricError by remember { mutableStateOf<String?>(null) }

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

            LaunchedEffect(securityConfig) {
                val cfg = securityConfig ?: return@LaunchedEffect
                latestSecurityConfig = cfg
                applyScreenSecurityFlag(cfg.isScreenSecurityEnabled)
                if (!hasEvaluatedColdStartLock) {
                    hasEvaluatedColdStartLock = true
                    isLockedFlow.value = cfg.isLockActive
                } else if (!cfg.isLockActive) {
                    isLockedFlow.value = false
                }
            }

            val triggerBiometric: () -> Unit = {
                biometricError = null
                activeBiometricSignal?.cancel()
                activeBiometricSignal = biometricLockManager.authenticate(
                    activity = this@MainActivity,
                    title = getString(R.string.biometric_prompt_title),
                    subtitle = getString(R.string.biometric_prompt_subtitle),
                    negativeButtonText = getString(R.string.biometric_prompt_negative),
                    onSuccess = {
                        activeBiometricSignal = null
                        biometricError = null
                        wasDeviceScreenLocked = false
                        isLockedFlow.value = false
                    },
                    onError = { message ->
                        activeBiometricSignal = null
                        biometricError = message
                    }
                )
            }

            val canUseBiometric = securityConfig?.isBiometricEnabled == true &&
                biometricLockManager.checkAvailability() == BiometricAvailability.AVAILABLE

            LaunchedEffect(isLocked, canUseBiometric) {
                if (isLocked && canUseBiometric) {
                    triggerBiometric()
                }
            }

            SpectrTheme(themeConfig = themeConfig) {
                when {
                    securityConfig == null -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {}
                    }
                    isLocked -> {
                        AppLockScreen(
                            biometricEnabled = canUseBiometric,
                            onVerifyPin = { candidate -> verifyStoredPin(candidate) },
                            onTriggerBiometric = triggerBiometric,
                            onUnlocked = {
                                biometricError = null
                                wasDeviceScreenLocked = false
                                isLockedFlow.value = false
                            },
                            biometricErrorMessage = biometricError
                        )
                    }
                    else -> {
                        SpectrApp(
                            themeConfig = themeConfig,
                            onThemeModeChange = { mode ->
                                coroutineScope.launch { themeRepository.setThemeMode(mode) }
                            },
                            onThemePaletteChange = { palette ->
                                coroutineScope.launch { themeRepository.setThemePalette(palette) }
                            },
                            launchRequest = launchRequest,
                            onLaunchRequestConsumed = { launchRequestFlow.value = null },
                            onLockAppNow = {
                                if (latestSecurityConfig?.isLockActive == true) {
                                    isLockedFlow.value = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val cfg = latestSecurityConfig
        if (hasEvaluatedColdStartLock && !isLockedFlow.value && cfg != null) {
            val shouldLock = cfg.shouldLockAfterBackground(
                lastBackgroundEpochMs = lastBackgroundEpochMs,
                nowEpochMs = clock.millis(),
                wasDeviceScreenLocked = wasDeviceScreenLocked
            )
            wasDeviceScreenLocked = false
            if (shouldLock) {
                isLockedFlow.value = true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        activeBiometricSignal?.cancel()
        activeBiometricSignal = null
        if (!isChangingConfigurations) {
            lastBackgroundEpochMs = clock.millis()
            if (isDeviceScreenOffOrLocked()) {
                wasDeviceScreenLocked = true
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_EVALUATED_COLD_START, hasEvaluatedColdStartLock)
        outState.putBoolean(KEY_IS_LOCKED, isLockedFlow.value)
        outState.putBoolean(KEY_WAS_DEVICE_SCREEN_LOCKED, wasDeviceScreenLocked)
        lastBackgroundEpochMs?.let { outState.putLong(KEY_LAST_BACKGROUND_MS, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchLaunchIntent(intent)
    }

    private fun applyScreenSecurityFlag(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun isDeviceScreenOffOrLocked(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val screenOff = powerManager != null && !powerManager.isInteractive
        val keyguardLocked = keyguardManager != null && keyguardManager.isKeyguardLocked
        return screenOff || keyguardLocked
    }

    private suspend fun verifyStoredPin(candidatePin: String): Boolean {
        val credential = securitySettingsRepository.getStoredPinCredential() ?: return false
        return withContext(Dispatchers.Default) {
            PinHashManager.verifyPin(candidatePin, credential)
        }
    }

    private fun dispatchLaunchIntent(intent: Intent?) {
        val target = SpectrLaunchContract.parseLaunchTarget(intent) ?: return
        launchRequestFlow.value = SpectrLaunchRequest(
            id = launchSequence.incrementAndGet(),
            target = target
        )
    }

    companion object {
        private const val KEY_EVALUATED_COLD_START = "spectr_evaluated_cold_start"
        private const val KEY_IS_LOCKED = "spectr_is_locked"
        private const val KEY_LAST_BACKGROUND_MS = "spectr_last_background_ms"
        private const val KEY_WAS_DEVICE_SCREEN_LOCKED = "spectr_was_device_screen_locked"
    }
}
