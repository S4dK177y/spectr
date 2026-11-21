package app.spectr.mood.domain.repository

import app.spectr.mood.domain.model.ReminderConfig
import kotlinx.coroutines.flow.Flow

/**
 * Local settings repository contract for reminder preferences and scheduling state.
 */
interface ReminderSettingsRepository {
    val reminderConfig: Flow<ReminderConfig>

    suspend fun getReminderConfig(): ReminderConfig
    suspend fun setDailyEnabled(enabled: Boolean)
    suspend fun setDailyTime(hour: Int, minute: Int)
    suspend fun setScalesEnabled(enabled: Boolean)
    suspend fun setSnoozedUntil(epochMs: Long?)
    suspend fun setLastScalesReminder(epochMs: Long?)
}
