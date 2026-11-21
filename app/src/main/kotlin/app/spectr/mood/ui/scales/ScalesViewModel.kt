package app.spectr.mood.ui.scales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spectr.mood.domain.calculator.ClinicalScaleCalculator
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unidirectional state holder for the Scales tab (PHQ-9 / GAD-7 / ASRM auto-calculation,
 * user-confirmed assessment saving, and longitudinal history with trend chart).
 */
@HiltViewModel
class ScalesViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val scaleAssessmentRepository: ScaleAssessmentRepository,
    private val clock: Clock
) : ViewModel() {

    private val initialToday: LocalDate = LocalDate.now(clock)
    private val historyFilterFlow = MutableStateFlow(ScalesHistoryFilter.ALL)
    private val trendScaleFlow = MutableStateFlow(ScaleType.PHQ9)

    private val _uiState = MutableStateFlow(
        ScalesUiState(
            today = initialToday,
            isLoading = true
        )
    )
    val uiState: StateFlow<ScalesUiState> = _uiState.asStateFlow()

    init {
        observeScalesData()
    }

    fun onIntent(intent: ScalesIntent) {
        when (intent) {
            is ScalesIntent.ToggleScaleDetails -> toggleDetails(intent.scaleType)
            is ScalesIntent.OpenConfirmScale -> openConfirmScale(intent.scaleType)
            is ScalesIntent.OpenEditAssessment -> openEditAssessment(intent.assessment)
            is ScalesIntent.SelectHistoryFilter -> selectHistoryFilter(intent.filter)
            is ScalesIntent.SelectTrendScale -> selectTrendScale(intent.scaleType)
            is ScalesIntent.SelectTrendPoint -> selectTrendPoint(intent.index)
            ScalesIntent.ConsumeSnackbar -> _uiState.update { it.copy(snackbarMessage = null) }
            else -> handleEditorAndDeleteIntent(intent)
        }
    }

    private fun handleEditorAndDeleteIntent(intent: ScalesIntent) {
        when (intent) {
            is ScalesIntent.UpdateEditorAnswer -> updateEditorAnswer(intent.itemIndex, intent.score)
            ScalesIntent.ResetEditorToAuto -> resetEditorToAuto()
            ScalesIntent.OpenEditorDatePicker -> updateEditor { it.copy(isDatePickerOpen = true) }
            ScalesIntent.DismissEditorDatePicker -> updateEditor { it.copy(isDatePickerOpen = false) }
            is ScalesIntent.UpdateEditorDate -> updateEditorDate(intent.date)
            ScalesIntent.SaveAssessment -> saveAssessment()
            ScalesIntent.DismissEditor -> _uiState.update { it.copy(editor = null) }
            is ScalesIntent.RequestDeleteAssessment -> _uiState.update {
                it.copy(pendingDeleteAssessment = intent.assessment)
            }
            ScalesIntent.ConfirmDeleteAssessment -> confirmDeleteAssessment()
            ScalesIntent.DismissDeleteAssessment -> _uiState.update { it.copy(pendingDeleteAssessment = null) }
            else -> Unit
        }
    }

    private fun observeScalesData() {
        viewModelScope.launch {
            combine(
                diaryRepository.observeEntries(),
                scaleAssessmentRepository.observeAssessments(),
                historyFilterFlow,
                trendScaleFlow
            ) { entries, assessments, historyFilter, trendScale ->
                ScalesCombinedInput(
                    entries = entries,
                    assessments = assessments,
                    historyFilter = historyFilter,
                    trendScale = trendScale
                )
            }.collect { input ->
                val today = currentToday()
                val autoSnapshot = ClinicalScaleCalculator.calculateAutoScales(input.entries)
                val activeAssessments = input.assessments.filter { it.deletedAt == null }
                val historyRows = buildHistoryRows(activeAssessments)
                val filteredRows = when (val filterType = input.historyFilter.toScaleTypeOrNull()) {
                    null -> historyRows
                    else -> historyRows.filter { it.assessment.scaleType == filterType }
                }
                val latestByType = ScaleType.entries.mapNotNull { type ->
                    historyRows.firstOrNull { it.assessment.scaleType == type }?.assessment?.let { type to it }
                }.toMap()

                val effectiveTrendScale = input.historyFilter.toScaleTypeOrNull() ?: input.trendScale
                val trendAssessments = activeAssessments.filter { it.scaleType == effectiveTrendScale }
                val trendPoints = ChartGeometry.buildScaleTrendPoints(trendAssessments)
                val maxTrendIndex = trendPoints.lastIndex

                _uiState.update { current ->
                    current.copy(
                        today = today,
                        autoSnapshot = autoSnapshot,
                        historyFilter = input.historyFilter,
                        allAssessments = activeAssessments,
                        filteredHistory = filteredRows,
                        latestConfirmedByType = latestByType,
                        trendScaleType = effectiveTrendScale,
                        trendPoints = trendPoints,
                        selectedTrendIndex = current.selectedTrendIndex?.takeIf { it in 0..maxTrendIndex },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun toggleDetails(scaleType: ScaleType) {
        _uiState.update { current ->
            val updated = if (scaleType in current.expandedScaleDetails) {
                current.expandedScaleDetails - scaleType
            } else {
                current.expandedScaleDetails + scaleType
            }
            current.copy(expandedScaleDetails = updated)
        }
    }

    private fun openConfirmScale(scaleType: ScaleType) {
        val snapshot = _uiState.value.autoSnapshot
        val autoResult = snapshot?.resultFor(scaleType)
        val initialAnswers = autoResult?.itemScores ?: List(scaleItemCount(scaleType)) { 0 }
        val defaultDate = (snapshot?.anchorDate ?: currentToday()).let { clampToToday(it) }

        _uiState.update { current ->
            current.copy(
                editor = ScaleEditorState(
                    editingAssessmentId = null,
                    scaleType = scaleType,
                    date = defaultDate,
                    answers = initialAnswers,
                    autoBaselineScores = autoResult?.itemScores,
                    autoBaselineDayCounts = autoResult?.itemDayCounts,
                    autoWindowDays = scaleWindowDays(scaleType)
                )
            )
        }
    }

    private fun openEditAssessment(assessment: ScaleAssessment) {
        val autoResult = _uiState.value.autoSnapshot?.resultFor(assessment.scaleType)
        val expectedCount = scaleItemCount(assessment.scaleType)
        val normalizedAnswers = if (assessment.answers.size == expectedCount) {
            assessment.answers
        } else {
            List(expectedCount) { idx -> assessment.answers.getOrElse(idx) { 0 } }
        }

        _uiState.update { current ->
            current.copy(
                editor = ScaleEditorState(
                    editingAssessmentId = assessment.id,
                    scaleType = assessment.scaleType,
                    date = assessment.date,
                    answers = normalizedAnswers,
                    autoBaselineScores = autoResult?.itemScores,
                    autoBaselineDayCounts = autoResult?.itemDayCounts,
                    autoWindowDays = scaleWindowDays(assessment.scaleType)
                )
            )
        }
    }

    private fun updateEditorAnswer(itemIndex: Int, score: Int) {
        updateEditor { editor ->
            if (itemIndex !in editor.answers.indices) {
                editor
            } else {
                val clamped = score.coerceIn(0, editor.maxItemScore)
                val updated = editor.answers.toMutableList().apply { this[itemIndex] = clamped }
                editor.copy(answers = updated)
            }
        }
    }

    private fun resetEditorToAuto() {
        updateEditor { editor ->
            val baseline = editor.autoBaselineScores ?: return@updateEditor editor
            editor.copy(answers = baseline)
        }
    }

    private fun updateEditorDate(date: LocalDate) {
        val clamped = clampToToday(date)
        updateEditor { it.copy(date = clamped, isDatePickerOpen = false) }
    }

    private fun saveAssessment() {
        val editor = _uiState.value.editor ?: return
        val isEdit = editor.isEditingExisting
        val totalScore = ClinicalScaleCalculator.scoreManualAssessment(editor.scaleType, editor.answers)
        val assessment = ScaleAssessment(
            id = editor.editingAssessmentId ?: UUID.randomUUID().toString(),
            scaleType = editor.scaleType,
            date = clampToToday(editor.date),
            answers = editor.answers,
            totalScore = totalScore,
            isConfirmed = true
        )
        viewModelScope.launch {
            scaleAssessmentRepository.saveAssessment(assessment)
            _uiState.update { current ->
                current.copy(
                    editor = null,
                    snackbarMessage = if (isEdit) {
                        ScalesSnackbarMessage.ASSESSMENT_UPDATED
                    } else {
                        ScalesSnackbarMessage.ASSESSMENT_SAVED
                    }
                )
            }
        }
    }

    private fun confirmDeleteAssessment() {
        val target = _uiState.value.pendingDeleteAssessment ?: return
        viewModelScope.launch {
            scaleAssessmentRepository.deleteAssessment(target.id)
            _uiState.update { current ->
                val closeEditor = current.editor?.editingAssessmentId == target.id
                current.copy(
                    pendingDeleteAssessment = null,
                    editor = if (closeEditor) null else current.editor,
                    snackbarMessage = ScalesSnackbarMessage.ASSESSMENT_DELETED
                )
            }
        }
    }

    private fun selectHistoryFilter(filter: ScalesHistoryFilter) {
        historyFilterFlow.value = filter
        filter.toScaleTypeOrNull()?.let { trendScaleFlow.value = it }
        _uiState.update { it.copy(selectedTrendIndex = null) }
    }

    private fun selectTrendScale(scaleType: ScaleType) {
        trendScaleFlow.value = scaleType
        _uiState.update { it.copy(selectedTrendIndex = null) }
    }

    private fun selectTrendPoint(index: Int?) {
        val maxIndex = _uiState.value.trendPoints.lastIndex
        val valid = index?.takeIf { it in 0..maxIndex }
        _uiState.update { it.copy(selectedTrendIndex = valid) }
    }

    private fun updateEditor(transform: (ScaleEditorState) -> ScaleEditorState) {
        _uiState.update { current ->
            val editor = current.editor ?: return@update current
            current.copy(editor = transform(editor))
        }
    }

    private fun buildHistoryRows(assessments: List<ScaleAssessment>): List<ScaleHistoryRow> {
        val chronological = assessments.sortedWith(
            compareBy<ScaleAssessment> { it.date }.thenBy { it.updatedAt }
        )
        val previousScoreByType = HashMap<ScaleType, Int>()
        val deltaById = HashMap<String, Int?>(chronological.size)
        for (item in chronological) {
            val prev = previousScoreByType[item.scaleType]
            deltaById[item.id] = prev?.let { item.totalScore - it }
            previousScoreByType[item.scaleType] = item.totalScore
        }

        return assessments
            .sortedWith(compareByDescending<ScaleAssessment> { it.date }.thenByDescending { it.updatedAt })
            .map { item ->
                ScaleHistoryRow(
                    assessment = item,
                    deltaFromPrevious = deltaById[item.id]
                )
            }
    }

    private fun clampToToday(date: LocalDate): LocalDate {
        val today = currentToday()
        return if (date.isAfter(today)) today else date
    }

    private fun currentToday(): LocalDate = LocalDate.now(clock)

    private data class ScalesCombinedInput(
        val entries: List<app.spectr.mood.domain.model.DailyEntry>,
        val assessments: List<ScaleAssessment>,
        val historyFilter: ScalesHistoryFilter,
        val trendScale: ScaleType
    )
}

internal fun app.spectr.mood.domain.calculator.AutoScalesSnapshot.resultFor(
    scaleType: ScaleType
): app.spectr.mood.domain.calculator.AutoScaleResult<*> = when (scaleType) {
    ScaleType.PHQ9 -> phq9
    ScaleType.GAD7 -> gad7
    ScaleType.ASRM -> asrm
}
