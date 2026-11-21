package app.spectr.mood.ui.scales

import app.spectr.mood.domain.calculator.AutoScalesSnapshot
import app.spectr.mood.domain.calculator.ClinicalScaleCalculator
import app.spectr.mood.domain.chart.ScaleTrendPoint
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate

/**
 * Filter option for the confirmed assessments history section.
 */
enum class ScalesHistoryFilter {
    ALL,
    PHQ9,
    GAD7,
    ASRM;

    fun toScaleTypeOrNull(): ScaleType? = when (this) {
        ALL -> null
        PHQ9 -> ScaleType.PHQ9
        GAD7 -> ScaleType.GAD7
        ASRM -> ScaleType.ASRM
    }
}

/**
 * Transient snackbar messages emitted by [ScalesViewModel].
 */
enum class ScalesSnackbarMessage {
    ASSESSMENT_SAVED,
    ASSESSMENT_UPDATED,
    ASSESSMENT_DELETED
}

/**
 * State for the confirmation / manual editing sheet of a PHQ-9, GAD-7, or ASRM assessment.
 */
data class ScaleEditorState(
    val editingAssessmentId: String? = null,
    val scaleType: ScaleType,
    val date: LocalDate,
    val answers: List<Int>,
    val autoBaselineScores: List<Int>? = null,
    val autoBaselineDayCounts: List<Int>? = null,
    val autoWindowDays: Int,
    val isDatePickerOpen: Boolean = false
) {
    val totalScore: Int
        get() = answers.sum()

    val isEditingExisting: Boolean
        get() = editingAssessmentId != null

    val maxItemScore: Int
        get() = when (scaleType) {
            ScaleType.PHQ9 -> ClinicalScaleCalculator.PHQ9_MAX_ITEM_SCORE
            ScaleType.GAD7 -> ClinicalScaleCalculator.GAD7_MAX_ITEM_SCORE
            ScaleType.ASRM -> ClinicalScaleCalculator.ASRM_MAX_ITEM_SCORE
        }

    val maxTotalScore: Int
        get() = scaleMaxTotalScore(scaleType)

    val differsFromAutoBaseline: Boolean
        get() = autoBaselineScores != null && autoBaselineScores != answers
}

/**
 * Single confirmed assessment entry enriched with delta from the chronologically previous
 * assessment of the same scale.
 */
data class ScaleHistoryRow(val assessment: ScaleAssessment, val deltaFromPrevious: Int?)

/**
 * Unidirectional UI state for the Scales tab.
 */
data class ScalesUiState(
    val today: LocalDate,
    val autoSnapshot: AutoScalesSnapshot? = null,
    val expandedScaleDetails: Set<ScaleType> = emptySet(),
    val historyFilter: ScalesHistoryFilter = ScalesHistoryFilter.ALL,
    val allAssessments: List<ScaleAssessment> = emptyList(),
    val filteredHistory: List<ScaleHistoryRow> = emptyList(),
    val latestConfirmedByType: Map<ScaleType, ScaleAssessment> = emptyMap(),
    val trendScaleType: ScaleType = ScaleType.PHQ9,
    val trendPoints: List<ScaleTrendPoint> = emptyList(),
    val selectedTrendIndex: Int? = null,
    val editor: ScaleEditorState? = null,
    val pendingDeleteAssessment: ScaleAssessment? = null,
    val snackbarMessage: ScalesSnackbarMessage? = null,
    val isLoading: Boolean = true
)

internal fun scaleMaxTotalScore(scaleType: ScaleType): Int = when (scaleType) {
    ScaleType.PHQ9 -> ClinicalScaleCalculator.PHQ9_ITEM_COUNT * ClinicalScaleCalculator.PHQ9_MAX_ITEM_SCORE
    ScaleType.GAD7 -> ClinicalScaleCalculator.GAD7_ITEM_COUNT * ClinicalScaleCalculator.GAD7_MAX_ITEM_SCORE
    ScaleType.ASRM -> ClinicalScaleCalculator.ASRM_ITEM_COUNT * ClinicalScaleCalculator.ASRM_MAX_ITEM_SCORE
}

internal fun scaleWindowDays(scaleType: ScaleType): Int = when (scaleType) {
    ScaleType.PHQ9 -> ClinicalScaleCalculator.PHQ9_WINDOW_DAYS
    ScaleType.GAD7 -> ClinicalScaleCalculator.GAD7_WINDOW_DAYS
    ScaleType.ASRM -> ClinicalScaleCalculator.ASRM_WINDOW_DAYS
}

internal fun scaleItemCount(scaleType: ScaleType): Int = when (scaleType) {
    ScaleType.PHQ9 -> ClinicalScaleCalculator.PHQ9_ITEM_COUNT
    ScaleType.GAD7 -> ClinicalScaleCalculator.GAD7_ITEM_COUNT
    ScaleType.ASRM -> ClinicalScaleCalculator.ASRM_ITEM_COUNT
}
