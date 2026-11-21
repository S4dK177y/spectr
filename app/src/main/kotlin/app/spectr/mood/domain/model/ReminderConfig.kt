package app.spectr.mood.domain.model

/**
 * User preferences and runtime scheduling metadata for local reminders.
 *
 * @property dailyEnabled Whether the daily evening diary reminder is active.
 * @property dailyHour Local hour of day (0..23) for the daily reminder (default 22:00).
 * @property dailyMinute Local minute of hour (0..59) for the daily reminder.
 * @property scalesEnabled Whether the periodic clinical scale check-in reminder is active.
 * @property scalesIntervalDays Interval in calendar days between scale reminders (default 14).
 * @property snoozedUntilEpochMs Active one-shot snooze target epoch ms, if the user tapped "+1 h".
 * @property lastScalesReminderEpochMs Last time a periodic scale reminder was shown (prevents daily spam).
 */
data class ReminderConfig(
    val dailyEnabled: Boolean = true,
    val dailyHour: Int = DEFAULT_DAILY_HOUR,
    val dailyMinute: Int = DEFAULT_DAILY_MINUTE,
    val scalesEnabled: Boolean = true,
    val scalesIntervalDays: Int = DEFAULT_SCALES_INTERVAL_DAYS,
    val snoozedUntilEpochMs: Long? = null,
    val lastScalesReminderEpochMs: Long? = null
) {
    val safeDailyHour: Int
        get() = dailyHour.coerceIn(MIN_HOUR, MAX_HOUR)

    val safeDailyMinute: Int
        get() = dailyMinute.coerceIn(MIN_MINUTE, MAX_MINUTE)

    val safeScalesIntervalDays: Int
        get() = scalesIntervalDays.coerceAtLeast(MIN_SCALES_INTERVAL_DAYS)

    companion object {
        const val MIN_HOUR = 0
        const val MAX_HOUR = 23
        const val MIN_MINUTE = 0
        const val MAX_MINUTE = 59
        const val DEFAULT_DAILY_HOUR = 22
        const val DEFAULT_DAILY_MINUTE = 0
        const val DEFAULT_SCALES_INTERVAL_DAYS = 14
        const val MIN_SCALES_INTERVAL_DAYS = 1
        const val SNOOZE_DURATION_MINUTES = 60L
    }
}
