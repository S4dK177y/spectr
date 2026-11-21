package app.spectr.mood.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigTest {

    @Test
    fun `does not lock when app lock is disabled or PIN is missing`() {
        val disabled = SecurityConfig(isAppLockEnabled = false, hasPinConfigured = true)
        val noPin = SecurityConfig(isAppLockEnabled = true, hasPinConfigured = false)

        assertFalse(disabled.isLockActive)
        assertFalse(disabled.shouldLockAfterBackground(lastBackgroundEpochMs = null, nowEpochMs = 10_000L))
        assertFalse(noPin.isLockActive)
        assertFalse(noPin.shouldLockAfterBackground(lastBackgroundEpochMs = null, nowEpochMs = 10_000L))
    }

    @Test
    fun `locks on cold start when app lock is active`() {
        val config = SecurityConfig(
            isAppLockEnabled = true,
            hasPinConfigured = true,
            lockTimeout = LockTimeout.MINUTES_5
        )
        assertTrue(config.shouldLockAfterBackground(lastBackgroundEpochMs = null, nowEpochMs = 1_000_000L))
    }

    @Test
    fun `respects configured grace timeout and guards against clock rollback`() {
        val config = SecurityConfig(
            isAppLockEnabled = true,
            hasPinConfigured = true,
            lockTimeout = LockTimeout.SECONDS_30
        )
        val bgTime = 1_000_000L

        assertFalse(config.shouldLockAfterBackground(bgTime, bgTime + 29_999L))
        assertTrue(config.shouldLockAfterBackground(bgTime, bgTime + 30_000L))
        assertTrue(config.shouldLockAfterBackground(bgTime, bgTime + 60_000L))
        // Clock moved backwards -> fail-closed (lock)
        assertTrue(config.shouldLockAfterBackground(bgTime, bgTime - 1L))
    }

    @Test
    fun `immediate timeout locks on any non-negative background transition`() {
        val config = SecurityConfig(
            isAppLockEnabled = true,
            hasPinConfigured = true,
            lockTimeout = LockTimeout.IMMEDIATE
        )
        assertTrue(config.shouldLockAfterBackground(500_000L, 500_000L))
        assertTrue(config.shouldLockAfterBackground(500_000L, 500_001L))
    }

    @Test
    fun `disabled on minimize only locks on cold start or when smartphone screen was locked`() {
        val config = SecurityConfig(
            isAppLockEnabled = true,
            hasPinConfigured = true,
            lockTimeout = LockTimeout.DISABLED_ON_MINIMIZE
        )

        // Cold start / app restart -> locks
        assertTrue(config.shouldLockAfterBackground(lastBackgroundEpochMs = null, nowEpochMs = 1_000_000L))

        // Normal app minimize (even after 1 hour) without smartphone screen lock -> does NOT lock
        assertFalse(
            config.shouldLockAfterBackground(
                lastBackgroundEpochMs = 1_000_000L,
                nowEpochMs = 4_600_000L,
                wasDeviceScreenLocked = false
            )
        )

        // Smartphone screen was locked -> locks immediately on resume
        assertTrue(
            config.shouldLockAfterBackground(
                lastBackgroundEpochMs = 1_000_000L,
                nowEpochMs = 1_001_000L,
                wasDeviceScreenLocked = true
            )
        )
    }
}
