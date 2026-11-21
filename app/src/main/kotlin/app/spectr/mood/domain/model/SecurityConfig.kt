package app.spectr.mood.domain.model

/**
 * Delay before re-locking the application after moving to background.
 *
 * [DISABLED_ON_MINIMIZE] disables timer-based auto-lock on app minimize so the lock activates
 * only on cold app restart or when the smartphone screen itself is locked.
 */
enum class LockTimeout(val timeoutMillis: Long) {
    DISABLED_ON_MINIMIZE(-1L),
    IMMEDIATE(0L),
    SECONDS_30(30_000L),
    MINUTE_1(60_000L),
    MINUTES_5(300_000L)
}

/**
 * Persisted salted PBKDF2 PIN hash material (never plaintext).
 */
data class StoredPinCredential(val saltBase64: String, val hashBase64: String)

/**
 * Local device security settings: optional PIN/Biometric app lock and window screenshot protection.
 */
data class SecurityConfig(
    val isAppLockEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATE,
    val isScreenSecurityEnabled: Boolean = false
) {
    val isLockActive: Boolean
        get() = isAppLockEnabled && hasPinConfigured

    /**
     * Determines whether the app should require unlock upon foreground resume.
     *
     * Returns `true`:
     * - on cold start / app restart (`lastBackgroundEpochMs == null`),
     * - when the smartphone screen was locked (`wasDeviceScreenLocked == true`),
     * - or, when [lockTimeout] is not [LockTimeout.DISABLED_ON_MINIMIZE], once the elapsed
     *   background time meets/exceeds [lockTimeout] (or the wall clock moved backwards).
     */
    fun shouldLockAfterBackground(
        lastBackgroundEpochMs: Long?,
        nowEpochMs: Long,
        wasDeviceScreenLocked: Boolean = false
    ): Boolean {
        if (!isLockActive) {
            return false
        }
        if (lastBackgroundEpochMs == null || wasDeviceScreenLocked) {
            return true
        }
        if (lockTimeout == LockTimeout.DISABLED_ON_MINIMIZE) {
            return false
        }
        val elapsedMs = nowEpochMs - lastBackgroundEpochMs
        return elapsedMs < 0L || elapsedMs >= lockTimeout.timeoutMillis
    }
}
