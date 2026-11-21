package app.spectr.mood.ui.more

import app.spectr.mood.domain.model.LockTimeout
import app.spectr.mood.domain.report.ReportPeriodPreset
import java.time.LocalDate

sealed interface MoreIntent {
    // Reminders
    data class SetDailyReminderEnabled(val enabled: Boolean) : MoreIntent
    data object OpenDailyTimePicker : MoreIntent
    data object DismissDailyTimePicker : MoreIntent
    data class UpdateDailyReminderTime(val hour: Int, val minute: Int) : MoreIntent
    data object ClearDailySnooze : MoreIntent
    data class SetScalesReminderEnabled(val enabled: Boolean) : MoreIntent
    data class RefreshSystemPermissions(val notificationsGranted: Boolean, val canScheduleExactAlarms: Boolean) :
        MoreIntent

    // Local security (PIN / Biometrics / FLAG_SECURE)
    data class ToggleAppLock(val enabled: Boolean) : MoreIntent
    data object OpenChangePinDialog : MoreIntent
    data object DismissPinDialog : MoreIntent
    data class SaveNewPin(val pin: String) : MoreIntent
    data object ConfirmDisablePinLock : MoreIntent
    data class SetBiometricEnabled(val enabled: Boolean) : MoreIntent
    data class SetLockTimeout(val timeout: LockTimeout) : MoreIntent
    data class SetScreenSecurityEnabled(val enabled: Boolean) : MoreIntent

    // PDF Export sheet
    data object OpenPdfSheet : MoreIntent
    data object DismissPdfSheet : MoreIntent
    data class SelectPdfPeriodPreset(val preset: ReportPeriodPreset) : MoreIntent
    data class UpdatePdfCustomStart(val date: LocalDate) : MoreIntent
    data class UpdatePdfCustomEnd(val date: LocalDate) : MoreIntent
    data class SetPdfIncludeDailyTable(val include: Boolean) : MoreIntent
    data class SetPdfIncludePersonalNotes(val include: Boolean) : MoreIntent
    data object GenerateAndSharePdf : MoreIntent
    data object GenerateAndOpenPdf : MoreIntent
}
