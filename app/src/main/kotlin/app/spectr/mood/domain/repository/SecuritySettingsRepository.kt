package app.spectr.mood.domain.repository

import app.spectr.mood.domain.model.LockTimeout
import app.spectr.mood.domain.model.SecurityConfig
import app.spectr.mood.domain.model.StoredPinCredential
import kotlinx.coroutines.flow.Flow

/**
 * Local preference repository for PIN/biometric app lock and `FLAG_SECURE` screen protection.
 */
interface SecuritySettingsRepository {
    val securityConfig: Flow<SecurityConfig>

    suspend fun getSecurityConfig(): SecurityConfig
    suspend fun getStoredPinCredential(): StoredPinCredential?
    suspend fun savePinCredential(credential: StoredPinCredential, enableAppLock: Boolean)
    suspend fun clearPinAndDisableLock()
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setLockTimeout(timeout: LockTimeout)
    suspend fun setScreenSecurityEnabled(enabled: Boolean)
}
