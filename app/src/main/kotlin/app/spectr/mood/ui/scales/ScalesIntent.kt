package app.spectr.mood.ui.scales

import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate

/**
 * User intents emitted from the Scales tab UI to [ScalesViewModel].
 */
sealed interface ScalesIntent {
    data class ToggleScaleDetails(val scaleType: ScaleType) : ScalesIntent
    data class OpenConfirmScale(val scaleType: ScaleType) : ScalesIntent
    data class OpenEditAssessment(val assessment: ScaleAssessment) : ScalesIntent

    data class UpdateEditorAnswer(val itemIndex: Int, val score: Int) : ScalesIntent
    data object ResetEditorToAuto : ScalesIntent
    data object OpenEditorDatePicker : ScalesIntent
    data object DismissEditorDatePicker : ScalesIntent
    data class UpdateEditorDate(val date: LocalDate) : ScalesIntent
    data object SaveAssessment : ScalesIntent
    data object DismissEditor : ScalesIntent

    data class RequestDeleteAssessment(val assessment: ScaleAssessment) : ScalesIntent
    data object ConfirmDeleteAssessment : ScalesIntent
    data object DismissDeleteAssessment : ScalesIntent

    data class SelectHistoryFilter(val filter: ScalesHistoryFilter) : ScalesIntent
    data class SelectTrendScale(val scaleType: ScaleType) : ScalesIntent
    data class SelectTrendPoint(val index: Int?) : ScalesIntent
    data object ConsumeSnackbar : ScalesIntent
}
