package app.spectr.mood.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.spectr.mood.domain.model.LockTimeout
import app.spectr.mood.domain.model.SecurityConfig
import app.spectr.mood.domain.model.StoredPinCredential
import app.spectr.mood.domain.repository.SecuritySettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed persistence for local device lock (salted PIN hash, biometrics, timeout)
 * and `FLAG_SECURE` window protection.
 */
@Singleton
class SecurityPreferencesRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    SecuritySettingsRepository {

    private object Keys {
        val APP_LOCK_ENABLED = booleanPreferencesKey("security_app_lock_enabled")
        val PIN_SALT_B64 = stringPreferencesKey("security_pin_salt_b64")
        val PIN_HASH_B64 = stringPreferencesKey("security_pin_hash_b64")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("security_biometric_enabled")
        val LOCK_TIMEOUT = stringPreferencesKey("security_lock_timeout")
        val SCREEN_SECURITY_ENABLED = booleanPreferencesKey("security_screen_secure_enabled")
    }

    override val securityConfig: Flow<SecurityConfig> = context.spectrPreferencesDataStore.data.map { prefs ->
        val salt = prefs[Keys.PIN_SALT_B64]
        val hash = prefs[Keys.PIN_HASH_B64]
        val hasPin = !salt.isNullOrBlank() && !hash.isNullOrBlank()
        val timeout = prefs[Keys.LOCK_TIMEOUT]
            ?.let { raw -> runCatching { LockTimeout.valueOf(raw) }.getOrNull() }
            ?: LockTimeout.IMMEDIATE

        SecurityConfig(
            isAppLockEnabled = (prefs[Keys.APP_LOCK_ENABLED] ?: false) && hasPin,
            hasPinConfigured = hasPin,
            isBiometricEnabled = (prefs[Keys.BIOMETRIC_ENABLED] ?: false) && hasPin,
            lockTimeout = timeout,
            isScreenSecurityEnabled = prefs[Keys.SCREEN_SECURITY_ENABLED] ?: false
        )
    }

    override suspend fun getSecurityConfig(): SecurityConfig = securityConfig.first()

    override suspend fun getStoredPinCredential(): StoredPinCredential? {
        val prefs = context.spectrPreferencesDataStore.data.first()
        val salt = prefs[Keys.PIN_SALT_B64]
        val hash = prefs[Keys.PIN_HASH_B64]
        return if (!salt.isNullOrBlank() && !hash.isNullOrBlank()) {
            StoredPinCredential(saltBase64 = salt, hashBase64 = hash)
        } else {
            null
        }
    }

    override suspend fun savePinCredential(credential: StoredPinCredential, enableAppLock: Boolean) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT_B64] = credential.saltBase64
            prefs[Keys.PIN_HASH_B64] = credential.hashBase64
            prefs[Keys.APP_LOCK_ENABLED] = enableAppLock
        }
    }

    override suspend fun clearPinAndDisableLock() {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = false
            prefs[Keys.BIOMETRIC_ENABLED] = false
            prefs.remove(Keys.PIN_SALT_B64)
            prefs.remove(Keys.PIN_HASH_B64)
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    override suspend fun setLockTimeout(timeout: LockTimeout) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.LOCK_TIMEOUT] = timeout.name
        }
    }

    override suspend fun setScreenSecurityEnabled(enabled: Boolean) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.SCREEN_SECURITY_ENABLED] = enabled
        }
    }
}
