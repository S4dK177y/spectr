package app.spectr.mood.ui.diary

import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.MomentCheckIn
import java.time.LocalDate

sealed interface DiaryIntent {
    // Date navigation
    data class SelectDate(val date: LocalDate) : DiaryIntent
    data object SelectToday : DiaryIntent
    data object SelectYesterday : DiaryIntent
    data object PreviousDay : DiaryIntent
    data object NextDay : DiaryIntent
    data object OpenDatePicker : DiaryIntent
    data object DismissDatePicker : DiaryIntent

    // Daily entry form edits
    data class UpdateMood(val mood: Int) : DiaryIntent
    data class UpdateDynamics(val dynamics: DailyDynamics) : DiaryIntent
    data class UpdateSleepInputMode(val mode: SleepInputMode) : DiaryIntent
    data class UpdateManualSleepText(val text: String) : DiaryIntent
    data class UpdateSleepNote(val note: String) : DiaryIntent
    data object OpenNewSleepInterval : DiaryIntent
    data class OpenEditSleepInterval(val id: String) : DiaryIntent
    data class UpdateSleepIntervalDraft(
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
        val awakeDeductionMin: Int
    ) : DiaryIntent
    data object ConfirmSleepInterval : DiaryIntent
    data object DismissSleepIntervalEditor : DiaryIntent
    data class RemoveSleepInterval(val id: String) : DiaryIntent
    data class UpdateEnergy(val energy: Int) : DiaryIntent
    data class UpdateAnxiety(val anxiety: Int) : DiaryIntent
    data class UpdateIrritability(val irritability: Int) : DiaryIntent
    data class ToggleSymptom(val symptom: SymptomType) : DiaryIntent
    data class UpdateAlcohol(val status: AlcoholStatus) : DiaryIntent
    data class UpdateMedication(val status: MedicationStatus) : DiaryIntent
    data class UpdateNote(val note: String) : DiaryIntent
    data object SaveDailyEntry : DiaryIntent
    data object RequestDeleteEntry : DiaryIntent
    data object ConfirmDeleteEntry : DiaryIntent
    data object DismissDeleteEntry : DiaryIntent

    // Moment check-ins
    data object OpenNewMoment : DiaryIntent
    data class OpenEditMoment(val moment: MomentCheckIn) : DiaryIntent
    data class UpdateMomentTime(val hour: Int, val minute: Int) : DiaryIntent
    data class UpdateMomentMood(val mood: Int) : DiaryIntent
    data class UpdateMomentEnergy(val energy: Int?) : DiaryIntent
    data class UpdateMomentAnxiety(val anxiety: Int?) : DiaryIntent
    data class UpdateMomentIrritability(val irritability: Int?) : DiaryIntent
    data class UpdateMomentNote(val note: String) : DiaryIntent
    data object SaveMoment : DiaryIntent
    data class DeleteMoment(val id: String) : DiaryIntent
    data object DismissMomentEditor : DiaryIntent

    // Help & feedback
    data class OpenHelp(val topic: DiaryHelpTopic) : DiaryIntent
    data object DismissHelp : DiaryIntent
    data object ConsumeSnackbar : DiaryIntent
}
