package app.spectr.mood.ui.more

sealed interface MoreIntent {
    data class SetDailyReminderEnabled(val enabled: Boolean) : MoreIntent
    data object OpenDailyTimePicker : MoreIntent
    data object DismissDailyTimePicker : MoreIntent
    data class UpdateDailyReminderTime(val hour: Int, val minute: Int) : MoreIntent
    data object ClearDailySnooze : MoreIntent
    data class SetScalesReminderEnabled(val enabled: Boolean) : MoreIntent
    data class RefreshSystemPermissions(val notificationsGranted: Boolean, val canScheduleExactAlarms: Boolean) :
        MoreIntent
}
