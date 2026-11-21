package app.spectr.mood.ui.more

import androidx.compose.runtime.Immutable
import app.spectr.mood.domain.model.ReminderConfig
import app.spectr.mood.domain.model.SecurityConfig
import app.spectr.mood.domain.reminder.DailyReminderSchedule
import app.spectr.mood.domain.reminder.ScalesReminderSchedule
import app.spectr.mood.platform.pdf.ExportedPdfFile
import app.spectr.mood.security.BiometricAvailability
import app.spectr.mood.ui.pdf.PdfExportSheetState
import app.spectr.mood.ui.security.PinDialogMode
import java.time.LocalDate

/**
 * Unidirectional UI state for the «Ещё» tab (Doctor tools, Security, Reminders, Widget, Theme).
 */
@Immutable
data class MoreUiState(
    val today: LocalDate = LocalDate.now(),
    val reminderConfig: ReminderConfig = ReminderConfig(),
    val isTodayEntryFilled: Boolean = false,
    val nextDailySchedule: DailyReminderSchedule? = null,
    val nextScalesSchedule: ScalesReminderSchedule? = null,
    val isTimePickerOpen: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val notificationsGranted: Boolean = true,
    val securityConfig: SecurityConfig = SecurityConfig(),
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val pinDialogMode: PinDialogMode? = null,
    val pdfSheet: PdfExportSheetState = PdfExportSheetState()
) {
    val activeSnoozeEpochMs: Long?
        get() = nextDailySchedule?.takeIf { it.isSnoozed }?.triggerAtEpochMs
}

enum class MoreSnackbarMessage {
    PIN_ENABLED,
    PIN_CHANGED,
    PIN_DISABLED,
    PDF_EXPORT_ERROR
}

sealed interface MoreOneShotEvent {
    data class SharePdf(val exported: ExportedPdfFile) : MoreOneShotEvent
    data class OpenPdf(val exported: ExportedPdfFile) : MoreOneShotEvent
    data class ShowPdfSaved(val pageCount: Int) : MoreOneShotEvent
    data class ShowSnackbar(val message: MoreSnackbarMessage) : MoreOneShotEvent
}
