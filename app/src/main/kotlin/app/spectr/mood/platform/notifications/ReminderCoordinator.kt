package app.spectr.mood.platform.notifications

import app.spectr.mood.domain.reminder.DailyReminderSchedule
import app.spectr.mood.domain.reminder.ReminderSchedulerCalculator
import app.spectr.mood.domain.reminder.ScalesReminderSchedule
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ReminderSettingsRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates reminder preferences, smart suppression checks, exact alarms, and notifications.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val diaryRepository: DiaryRepository,
    private val scaleAssessmentRepository: ScaleAssessmentRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notificationPublisher: NotificationPublisher,
    private val clock: Clock
) {

    fun canScheduleExactAlarms(): Boolean = alarmScheduler.canScheduleExactAlarms()

    suspend fun syncAllReminders() {
        syncDailyReminder()
        syncScalesReminder()
    }

    suspend fun syncDailyReminder(): DailyReminderSchedule? {
        val config = reminderSettingsRepository.getReminderConfig()
        if (!config.dailyEnabled) {
            alarmScheduler.cancelDailyAlarm()
            notificationPublisher.cancelDailyReminder()
            return null
        }

        val now = ZonedDateTime.now(clock)
        val isTodayFilled = diaryRepository.getEntryByDate(now.toLocalDate()) != null
        if (isTodayFilled) {
            notificationPublisher.cancelDailyReminder()
            if (config.snoozedUntilEpochMs != null) {
                reminderSettingsRepository.setSnoozedUntil(null)
            }
        } else if (config.snoozedUntilEpochMs != null && config.snoozedUntilEpochMs <= now.toInstant().toEpochMilli()) {
            reminderSettingsRepository.setSnoozedUntil(null)
        }

        val effectiveConfig = if (isTodayFilled) config.copy(snoozedUntilEpochMs = null) else config
        val next = ReminderSchedulerCalculator.computeNextDailyTrigger(
            config = effectiveConfig,
            now = now,
            isTodayEntryFilled = isTodayFilled
        )
        if (next != null) {
            alarmScheduler.scheduleDailyAlarm(next.triggerAtEpochMs)
        } else {
            alarmScheduler.cancelDailyAlarm()
        }
        return next
    }

    suspend fun syncScalesReminder(): ScalesReminderSchedule? {
        val config = reminderSettingsRepository.getReminderConfig()
        if (!config.scalesEnabled) {
            alarmScheduler.cancelScalesAlarm()
            notificationPublisher.cancelScalesReminder()
            return null
        }

        val now = ZonedDateTime.now(clock)
        val latestConfirmedDate = latestConfirmedAssessmentDate()
        val earliestEntryDate = earliestDiaryEntryDate()

        val next = ReminderSchedulerCalculator.computeNextScalesTrigger(
            config = config,
            now = now,
            latestConfirmedAssessmentDate = latestConfirmedDate,
            earliestDiaryEntryDate = earliestEntryDate
        )
        if (next != null) {
            alarmScheduler.scheduleScalesAlarm(next.triggerAtEpochMs)
        } else {
            alarmScheduler.cancelScalesAlarm()
        }
        return next
    }

    suspend fun onDailyAlarmFired() {
        val config = reminderSettingsRepository.getReminderConfig()
        if (config.snoozedUntilEpochMs != null) {
            reminderSettingsRepository.setSnoozedUntil(null)
        }

        val now = ZonedDateTime.now(clock)
        val isTodayFilled = diaryRepository.getEntryByDate(now.toLocalDate()) != null
        if (ReminderSchedulerCalculator.shouldShowDailyReminder(config, isTodayFilled)) {
            notificationPublisher.showDailyReminder()
        }

        // Always reschedule the next daily slot (and cancel any active notification if filled/disabled).
        syncDailyReminder()
    }

    suspend fun onSnoozeDailyClicked() {
        notificationPublisher.cancelDailyReminder()
        val config = reminderSettingsRepository.getReminderConfig()
        if (!config.dailyEnabled) return

        val now = ZonedDateTime.now(clock)
        val isTodayFilled = diaryRepository.getEntryByDate(now.toLocalDate()) != null
        if (isTodayFilled) {
            reminderSettingsRepository.setSnoozedUntil(null)
            syncDailyReminder()
            return
        }

        val snoozeTargetMs = ReminderSchedulerCalculator.computeSnoozeTriggerEpochMs(
            nowEpochMs = now.toInstant().toEpochMilli()
        )
        reminderSettingsRepository.setSnoozedUntil(snoozeTargetMs)
        alarmScheduler.scheduleDailyAlarm(snoozeTargetMs)
    }

    suspend fun onDailyEntryChanged(date: LocalDate?) {
        val today = LocalDate.now(clock)
        if (date == null || date == today) {
            syncDailyReminder()
        }
        // First diary entry may also establish the baseline for the 14-day scales reminder.
        syncScalesReminder()
    }

    suspend fun onScalesAlarmFired() {
        val config = reminderSettingsRepository.getReminderConfig()
        val now = ZonedDateTime.now(clock)
        val latestConfirmedDate = latestConfirmedAssessmentDate()

        if (ReminderSchedulerCalculator.shouldShowScalesReminder(config, now, latestConfirmedDate)) {
            notificationPublisher.showScalesReminder()
            reminderSettingsRepository.setLastScalesReminder(now.toInstant().toEpochMilli())
        } else {
            notificationPublisher.cancelScalesReminder()
        }

        syncScalesReminder()
    }

    suspend fun onScaleAssessmentChanged() {
        val config = reminderSettingsRepository.getReminderConfig()
        val today = LocalDate.now(clock)
        val latestConfirmedDate = latestConfirmedAssessmentDate()
        if (latestConfirmedDate != null &&
            ChronoUnit.DAYS.between(latestConfirmedDate, today) < config.safeScalesIntervalDays
        ) {
            notificationPublisher.cancelScalesReminder()
        }
        syncScalesReminder()
    }

    suspend fun setDailyEnabled(enabled: Boolean) {
        reminderSettingsRepository.setDailyEnabled(enabled)
        syncDailyReminder()
    }

    suspend fun setDailyTime(hour: Int, minute: Int) {
        reminderSettingsRepository.setDailyTime(hour, minute)
        syncDailyReminder()
    }

    suspend fun clearSnooze() {
        reminderSettingsRepository.setSnoozedUntil(null)
        syncDailyReminder()
    }

    suspend fun setScalesEnabled(enabled: Boolean) {
        reminderSettingsRepository.setScalesEnabled(enabled)
        syncScalesReminder()
    }

    private suspend fun latestConfirmedAssessmentDate(): LocalDate? = scaleAssessmentRepository
        .getActiveAssessments()
        .asSequence()
        .filter { it.deletedAt == null && it.isConfirmed }
        .maxOfOrNull { it.date }

    private suspend fun earliestDiaryEntryDate(): LocalDate? = diaryRepository
        .getActiveEntries()
        .asSequence()
        .filter { it.deletedAt == null }
        .minOfOrNull { it.date }
}
