package app.spectr.mood.domain.reminder

import app.spectr.mood.domain.model.ReminderConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Computed schedule target for the daily evening reminder alarm.
 */
data class DailyReminderSchedule(val triggerAtEpochMs: Long, val isSnoozed: Boolean, val targetDate: LocalDate)

/**
 * Computed schedule target for the periodic 14-day clinical scales reminder alarm.
 */
data class ScalesReminderSchedule(val triggerAtEpochMs: Long, val targetDate: LocalDate)

/**
 * Pure Kotlin calculator for exact-alarm timestamps and smart suppression rules.
 */
object ReminderSchedulerCalculator {

    const val SCALES_REMINDER_HOUR = 18
    const val SCALES_REMINDER_MINUTE = 0
    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * Computes the next trigger time for the daily evening reminder.
     *
     * Rules:
     * - Returns `null` when [ReminderConfig.dailyEnabled] is `false`.
     * - Honors an active future snooze timestamp only if today's entry is still unfilled.
     * - Automatically skips today and schedules tomorrow when [isTodayEntryFilled] is `true`
     *   or when today's configured time has already passed.
     */
    fun computeNextDailyTrigger(
        config: ReminderConfig,
        now: ZonedDateTime,
        isTodayEntryFilled: Boolean
    ): DailyReminderSchedule? {
        if (!config.dailyEnabled) return null

        val nowEpochMs = now.toInstant().toEpochMilli()
        val activeSnoozeMs = config.snoozedUntilEpochMs?.takeIf { it > nowEpochMs }
        if (activeSnoozeMs != null && !isTodayEntryFilled) {
            return DailyReminderSchedule(
                triggerAtEpochMs = activeSnoozeMs,
                isSnoozed = true,
                targetDate = Instant.ofEpochMilli(activeSnoozeMs).atZone(now.zone).toLocalDate()
            )
        }

        val today = now.toLocalDate()
        val todayTrigger = today
            .atTime(config.safeDailyHour, config.safeDailyMinute)
            .atZone(now.zone)

        val scheduledDateTime = if (!isTodayEntryFilled && todayTrigger.isAfter(now)) {
            todayTrigger
        } else {
            today.plusDays(1)
                .atTime(config.safeDailyHour, config.safeDailyMinute)
                .atZone(now.zone)
        }

        return DailyReminderSchedule(
            triggerAtEpochMs = scheduledDateTime.toInstant().toEpochMilli(),
            isSnoozed = false,
            targetDate = scheduledDateTime.toLocalDate()
        )
    }

    /**
     * Computes the snooze target epoch timestamp (+60 minutes by default).
     */
    fun computeSnoozeTriggerEpochMs(
        nowEpochMs: Long,
        snoozeMinutes: Long = ReminderConfig.SNOOZE_DURATION_MINUTES
    ): Long = nowEpochMs + snoozeMinutes.coerceAtLeast(1L) * MILLIS_PER_MINUTE

    /**
     * Smart suppression check evaluated right before posting the daily reminder notification.
     */
    fun shouldShowDailyReminder(config: ReminderConfig, isTodayEntryFilled: Boolean): Boolean =
        config.dailyEnabled && !isTodayEntryFilled

    /**
     * Computes the next trigger time for the periodic 14-day clinical scales reminder.
     *
     * Rules:
     * - Returns `null` when [ReminderConfig.scalesEnabled] is `false`.
     * - Anchors 14 days after the latest confirmed scale assessment when available,
     *   falling back to the earliest diary entry date or today for brand-new users.
     * - Respects [ReminderConfig.lastScalesReminderEpochMs] so an ignored reminder never
     *   re-fires every day, waiting a full interval instead.
     * - If the target date is already due (in the past), schedules the next upcoming 18:00 slot.
     */
    fun computeNextScalesTrigger(
        config: ReminderConfig,
        now: ZonedDateTime,
        latestConfirmedAssessmentDate: LocalDate?,
        earliestDiaryEntryDate: LocalDate?
    ): ScalesReminderSchedule? {
        if (!config.scalesEnabled) return null

        val intervalDays = config.safeScalesIntervalDays.toLong()
        val today = now.toLocalDate()

        val lastReminderDate = config.lastScalesReminderEpochMs
            ?.let { Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate() }

        val targetFromHistory = when {
            latestConfirmedAssessmentDate != null -> latestConfirmedAssessmentDate.plusDays(intervalDays)
            lastReminderDate != null -> lastReminderDate.plusDays(intervalDays)
            earliestDiaryEntryDate != null -> earliestDiaryEntryDate.plusDays(intervalDays)
            else -> today.plusDays(intervalDays)
        }

        val targetFromLastReminder = lastReminderDate?.plusDays(intervalDays)
        val candidateDate = if (targetFromLastReminder != null && targetFromLastReminder.isAfter(targetFromHistory)) {
            targetFromLastReminder
        } else {
            targetFromHistory
        }

        val candidateDateTime = candidateDate
            .atTime(SCALES_REMINDER_HOUR, SCALES_REMINDER_MINUTE)
            .atZone(now.zone)

        val scheduledDateTime = if (candidateDateTime.isAfter(now)) {
            candidateDateTime
        } else {
            val todaySlot = today.atTime(SCALES_REMINDER_HOUR, SCALES_REMINDER_MINUTE).atZone(now.zone)
            if (todaySlot.isAfter(now)) {
                todaySlot
            } else {
                today.plusDays(1).atTime(SCALES_REMINDER_HOUR, SCALES_REMINDER_MINUTE).atZone(now.zone)
            }
        }

        return ScalesReminderSchedule(
            triggerAtEpochMs = scheduledDateTime.toInstant().toEpochMilli(),
            targetDate = scheduledDateTime.toLocalDate()
        )
    }

    /**
     * Suppression check evaluated right before posting the 14-day scales reminder notification.
     */
    fun shouldShowScalesReminder(
        config: ReminderConfig,
        now: ZonedDateTime,
        latestConfirmedAssessmentDate: LocalDate?
    ): Boolean {
        if (!config.scalesEnabled) return false

        val intervalDays = config.safeScalesIntervalDays.toLong()
        val today = now.toLocalDate()

        val confirmedRecently = latestConfirmedAssessmentDate != null &&
            ChronoUnit.DAYS.between(latestConfirmedAssessmentDate, today) < intervalDays

        val remindedRecently = config.lastScalesReminderEpochMs?.let { lastMs ->
            val lastDate = Instant.ofEpochMilli(lastMs).atZone(now.zone).toLocalDate()
            ChronoUnit.DAYS.between(lastDate, today) < intervalDays
        } ?: false

        return !confirmedRecently && !remindedRecently
    }
}
