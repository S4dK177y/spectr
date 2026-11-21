package app.spectr.mood.domain.model

/**
 * Delay before re-locking the application after moving to background.
 */
enum class LockTimeout(val timeoutMillis: Long) {
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
     * Returns `true` on cold start (`lastBackgroundEpochMs == null`), when the elapsed background time
     * meets or exceeds [lockTimeout], or if the wall clock moved backwards.
     */
    fun shouldLockAfterBackground(lastBackgroundEpochMs: Long?, nowEpochMs: Long): Boolean {
        if (!isLockActive) {
            return false
        }
        if (lastBackgroundEpochMs == null) {
            return true
        }
        val elapsedMs = nowEpochMs - lastBackgroundEpochMs
        return elapsedMs < 0L || elapsedMs >= lockTimeout.timeoutMillis
    }
}
