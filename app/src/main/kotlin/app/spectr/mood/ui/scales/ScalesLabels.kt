package app.spectr.mood.ui.scales

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.spectr.mood.R
import app.spectr.mood.domain.calculator.AsrmSeverity
import app.spectr.mood.domain.calculator.ClinicalScaleCalculator
import app.spectr.mood.domain.calculator.Gad7Severity
import app.spectr.mood.domain.calculator.Phq9Severity
import app.spectr.mood.domain.model.ScaleType

@Composable
internal fun scaleCodeLabel(scaleType: ScaleType): String = when (scaleType) {
    ScaleType.PHQ9 -> stringResource(R.string.scale_phq9_code)
    ScaleType.GAD7 -> stringResource(R.string.scale_gad7_code)
    ScaleType.ASRM -> stringResource(R.string.scale_asrm_code)
}

@Composable
internal fun scaleSubtitleLabel(scaleType: ScaleType): String = when (scaleType) {
    ScaleType.PHQ9 -> stringResource(R.string.scale_phq9_subtitle)
    ScaleType.GAD7 -> stringResource(R.string.scale_gad7_subtitle)
    ScaleType.ASRM -> stringResource(R.string.scale_asrm_subtitle)
}

@Composable
internal fun scaleHistoryFilterLabel(filter: ScalesHistoryFilter): String = when (filter) {
    ScalesHistoryFilter.ALL -> stringResource(R.string.scales_filter_all)
    ScalesHistoryFilter.PHQ9 -> stringResource(R.string.scale_phq9_code)
    ScalesHistoryFilter.GAD7 -> stringResource(R.string.scale_gad7_code)
    ScalesHistoryFilter.ASRM -> stringResource(R.string.scale_asrm_code)
}

@Composable
internal fun phq9SeverityLabel(severity: Phq9Severity): String = when (severity) {
    Phq9Severity.MINIMAL -> stringResource(R.string.severity_phq9_minimal)
    Phq9Severity.MILD -> stringResource(R.string.severity_phq9_mild)
    Phq9Severity.MODERATE -> stringResource(R.string.severity_phq9_moderate)
    Phq9Severity.MODERATELY_SEVERE -> stringResource(R.string.severity_phq9_moderately_severe)
    Phq9Severity.SEVERE -> stringResource(R.string.severity_phq9_severe)
}

@Composable
internal fun gad7SeverityLabel(severity: Gad7Severity): String = when (severity) {
    Gad7Severity.MINIMAL -> stringResource(R.string.severity_gad7_minimal)
    Gad7Severity.MILD -> stringResource(R.string.severity_gad7_mild)
    Gad7Severity.MODERATE -> stringResource(R.string.severity_gad7_moderate)
    Gad7Severity.SEVERE -> stringResource(R.string.severity_gad7_severe)
}

@Composable
internal fun asrmSeverityLabel(severity: AsrmSeverity): String = when (severity) {
    AsrmSeverity.NORMAL_BASELINE -> stringResource(R.string.severity_asrm_normal)
    AsrmSeverity.ELEVATED_ACTIVATION -> stringResource(R.string.severity_asrm_elevated)
}

@Composable
internal fun scaleSeverityLabel(scaleType: ScaleType, totalScore: Int): String = when (scaleType) {
    ScaleType.PHQ9 -> phq9SeverityLabel(ClinicalScaleCalculator.classifyPhq9(totalScore))
    ScaleType.GAD7 -> gad7SeverityLabel(ClinicalScaleCalculator.classifyGad7(totalScore))
    ScaleType.ASRM -> asrmSeverityLabel(ClinicalScaleCalculator.classifyAsrm(totalScore))
}

internal fun isElevatedScaleScore(scaleType: ScaleType, totalScore: Int): Boolean = when (scaleType) {
    ScaleType.PHQ9 -> totalScore >= 10
    ScaleType.GAD7 -> totalScore >= 10
    ScaleType.ASRM -> totalScore >= 6
}

internal fun scaleThresholdLines(scaleType: ScaleType): List<Int> = when (scaleType) {
    ScaleType.PHQ9 -> listOf(5, 10, 15, 20)
    ScaleType.GAD7 -> listOf(5, 10, 15)
    ScaleType.ASRM -> listOf(6)
}

@Composable
internal fun scaleQuestionText(scaleType: ScaleType, index: Int): String = when (scaleType) {
    ScaleType.PHQ9 -> stringResource(PHQ9_QUESTION_RES.getOrElse(index) { R.string.phq9_q1 })
    ScaleType.GAD7 -> stringResource(GAD7_QUESTION_RES.getOrElse(index) { R.string.gad7_q1 })
    ScaleType.ASRM -> stringResource(ASRM_QUESTION_RES.getOrElse(index) { R.string.asrm_q1 })
}

@Composable
internal fun scaleAnswerLegend(scaleType: ScaleType): String = when (scaleType) {
    ScaleType.PHQ9, ScaleType.GAD7 -> stringResource(R.string.scales_legend_phq_gad)
    ScaleType.ASRM -> stringResource(R.string.scales_legend_asrm)
}

@Composable
internal fun scalesSnackbarText(message: ScalesSnackbarMessage): String = when (message) {
    ScalesSnackbarMessage.ASSESSMENT_SAVED -> stringResource(R.string.scales_snackbar_saved)
    ScalesSnackbarMessage.ASSESSMENT_UPDATED -> stringResource(R.string.scales_snackbar_updated)
    ScalesSnackbarMessage.ASSESSMENT_DELETED -> stringResource(R.string.scales_snackbar_deleted)
}

internal fun formatSignedDelta(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "0"
}

private val PHQ9_QUESTION_RES = listOf(
    R.string.phq9_q1,
    R.string.phq9_q2,
    R.string.phq9_q3,
    R.string.phq9_q4,
    R.string.phq9_q5,
    R.string.phq9_q6,
    R.string.phq9_q7,
    R.string.phq9_q8,
    R.string.phq9_q9
)

private val GAD7_QUESTION_RES = listOf(
    R.string.gad7_q1,
    R.string.gad7_q2,
    R.string.gad7_q3,
    R.string.gad7_q4,
    R.string.gad7_q5,
    R.string.gad7_q6,
    R.string.gad7_q7
)

private val ASRM_QUESTION_RES = listOf(
    R.string.asrm_q1,
    R.string.asrm_q2,
    R.string.asrm_q3,
    R.string.asrm_q4,
    R.string.asrm_q5
)
