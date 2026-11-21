package app.spectr.mood.ui.more

import app.spectr.mood.domain.model.ReminderConfig
import app.spectr.mood.domain.reminder.DailyReminderSchedule
import app.spectr.mood.domain.reminder.ScalesReminderSchedule
import java.time.LocalDate

/**
 * Unidirectional UI state for the «Ещё» tab (Reminders, Widget, Theme settings).
 */
data class MoreUiState(
    val today: LocalDate = LocalDate.now(),
    val reminderConfig: ReminderConfig = ReminderConfig(),
    val isTodayEntryFilled: Boolean = false,
    val nextDailySchedule: DailyReminderSchedule? = null,
    val nextScalesSchedule: ScalesReminderSchedule? = null,
    val isTimePickerOpen: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val notificationsGranted: Boolean = true
) {
    val activeSnoozeEpochMs: Long?
        get() = nextDailySchedule?.takeIf { it.isSnoozed }?.triggerAtEpochMs
}
