package app.spectr.mood.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.spectr.mood.domain.model.ReminderConfig
import app.spectr.mood.domain.repository.ReminderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed persistence for daily evening and periodic 14-day scale reminders.
 */
@Singleton
class ReminderPreferencesRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    ReminderSettingsRepository {

    private object Keys {
        val DAILY_ENABLED = booleanPreferencesKey("reminder_daily_enabled")
        val DAILY_HOUR = intPreferencesKey("reminder_daily_hour")
        val DAILY_MINUTE = intPreferencesKey("reminder_daily_minute")
        val SCALES_ENABLED = booleanPreferencesKey("reminder_scales_enabled")
        val SCALES_INTERVAL_DAYS = intPreferencesKey("reminder_scales_interval_days")
        val SNOOZED_UNTIL_EPOCH_MS = longPreferencesKey("reminder_snoozed_until_ms")
        val LAST_SCALES_REMINDER_EPOCH_MS = longPreferencesKey("reminder_last_scales_ms")
    }

    override val reminderConfig: Flow<ReminderConfig> = context.spectrPreferencesDataStore.data.map { prefs ->
        ReminderConfig(
            dailyEnabled = prefs[Keys.DAILY_ENABLED] ?: true,
            dailyHour = (prefs[Keys.DAILY_HOUR] ?: ReminderConfig.DEFAULT_DAILY_HOUR)
                .coerceIn(ReminderConfig.MIN_HOUR, ReminderConfig.MAX_HOUR),
            dailyMinute = (prefs[Keys.DAILY_MINUTE] ?: ReminderConfig.DEFAULT_DAILY_MINUTE)
                .coerceIn(ReminderConfig.MIN_MINUTE, ReminderConfig.MAX_MINUTE),
            scalesEnabled = prefs[Keys.SCALES_ENABLED] ?: true,
            scalesIntervalDays = (prefs[Keys.SCALES_INTERVAL_DAYS] ?: ReminderConfig.DEFAULT_SCALES_INTERVAL_DAYS)
                .coerceAtLeast(ReminderConfig.MIN_SCALES_INTERVAL_DAYS),
            snoozedUntilEpochMs = prefs[Keys.SNOOZED_UNTIL_EPOCH_MS],
            lastScalesReminderEpochMs = prefs[Keys.LAST_SCALES_REMINDER_EPOCH_MS]
        )
    }

    override suspend fun getReminderConfig(): ReminderConfig = reminderConfig.first()

    override suspend fun setDailyEnabled(enabled: Boolean) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.DAILY_ENABLED] = enabled
            if (!enabled) {
                prefs.remove(Keys.SNOOZED_UNTIL_EPOCH_MS)
            }
        }
    }

    override suspend fun setDailyTime(hour: Int, minute: Int) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.DAILY_HOUR] = hour.coerceIn(ReminderConfig.MIN_HOUR, ReminderConfig.MAX_HOUR)
            prefs[Keys.DAILY_MINUTE] = minute.coerceIn(ReminderConfig.MIN_MINUTE, ReminderConfig.MAX_MINUTE)
            prefs.remove(Keys.SNOOZED_UNTIL_EPOCH_MS)
        }
    }

    override suspend fun setScalesEnabled(enabled: Boolean) {
        context.spectrPreferencesDataStore.edit { prefs ->
            prefs[Keys.SCALES_ENABLED] = enabled
        }
    }

    override suspend fun setSnoozedUntil(epochMs: Long?) {
        context.spectrPreferencesDataStore.edit { prefs ->
            if (epochMs == null) {
                prefs.remove(Keys.SNOOZED_UNTIL_EPOCH_MS)
            } else {
                prefs[Keys.SNOOZED_UNTIL_EPOCH_MS] = epochMs
            }
        }
    }

    override suspend fun setLastScalesReminder(epochMs: Long?) {
        context.spectrPreferencesDataStore.edit { prefs ->
            if (epochMs == null) {
                prefs.remove(Keys.LAST_SCALES_REMINDER_EPOCH_MS)
            } else {
                prefs[Keys.LAST_SCALES_REMINDER_EPOCH_MS] = epochMs
            }
        }
    }
}
