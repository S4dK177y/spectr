package app.spectr.mood.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.report.DoctorReportBuilder
import app.spectr.mood.domain.report.DoctorReportOptions
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import app.spectr.mood.ui.overview.OverviewPeriodOption
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for «Режим показа врачу» — strictly read-only, never loads personal moments
 * and always builds [DoctorReportBuilder] snapshots with personal notes redacted.
 */
@HiltViewModel
class DoctorViewViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val scaleAssessmentRepository: ScaleAssessmentRepository,
    private val clock: Clock
) : ViewModel() {

    private val initialToday = LocalDate.now(clock)
    private val initialMonth = YearMonth.from(initialToday)

    private val _uiState = MutableStateFlow(
        DoctorViewUiState(
            today = initialToday,
            displayedMonth = initialMonth,
            availableYears = listOf(initialToday.year)
        )
    )
    val uiState: StateFlow<DoctorViewUiState> = _uiState.asStateFlow()

    private var cachedEntries: List<DailyEntry> = emptyList()
    private var cachedAssessments: List<ScaleAssessment> = emptyList()

    init {
        observeData()
    }

    fun onIntent(intent: DoctorViewIntent) {
        when (intent) {
            is DoctorViewIntent.SelectPeriod -> updateAndRecompute(
                selectedPeriod = intent.period,
                clearSelection = true
            )
            DoctorViewIntent.PreviousMonth -> updateAndRecompute(
                displayedMonth = _uiState.value.displayedMonth.minusMonths(1)
            )
            DoctorViewIntent.NextMonth -> {
                val todayMonth = YearMonth.from(LocalDate.now(clock))
                val next = _uiState.value.displayedMonth.plusMonths(1)
                if (!next.isAfter(todayMonth)) {
                    updateAndRecompute(displayedMonth = next)
                }
            }
            DoctorViewIntent.JumpToCurrentMonth -> updateAndRecompute(
                displayedMonth = YearMonth.from(LocalDate.now(clock))
            )
            DoctorViewIntent.OpenMonthYearPicker -> _uiState.update { it.copy(isMonthPickerOpen = true) }
            DoctorViewIntent.DismissMonthYearPicker -> _uiState.update { it.copy(isMonthPickerOpen = false) }
            is DoctorViewIntent.SelectMonthYear -> {
                val todayMonth = YearMonth.from(LocalDate.now(clock))
                val clamped = if (intent.yearMonth.isAfter(todayMonth)) todayMonth else intent.yearMonth
                updateAndRecompute(displayedMonth = clamped, closeMonthPicker = true)
            }
            is DoctorViewIntent.SelectChartPoint -> _uiState.update { it.copy(selectedChartIndex = intent.index) }
            DoctorViewIntent.ToggleDailyLogExpanded -> _uiState.update {
                it.copy(isDailyLogExpanded = !it.isDailyLogExpanded)
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                diaryRepository.observeEntries(),
                scaleAssessmentRepository.observeAssessments()
            ) { entries, assessments ->
                entries to assessments
            }.collect { (entries, assessments) ->
                cachedEntries = entries
                cachedAssessments = assessments
                recomputeState()
            }
        }
    }

    private fun updateAndRecompute(
        selectedPeriod: OverviewPeriodOption = _uiState.value.selectedPeriod,
        displayedMonth: YearMonth = _uiState.value.displayedMonth,
        clearSelection: Boolean = false,
        closeMonthPicker: Boolean = false
    ) {
        _uiState.update { state ->
            state.copy(
                selectedPeriod = selectedPeriod,
                displayedMonth = displayedMonth,
                selectedChartIndex = if (clearSelection) null else state.selectedChartIndex,
                isMonthPickerOpen = if (closeMonthPicker) false else state.isMonthPickerOpen
            )
        }
        recomputeState()
    }

    private fun recomputeState() {
        val current = _uiState.value
        val today = LocalDate.now(clock)
        val currentMonth = YearMonth.from(today)
        val safeDisplayedMonth = if (current.displayedMonth.isAfter(currentMonth)) {
            currentMonth
        } else {
            current.displayedMonth
        }

        val activeEntries = cachedEntries.filter { it.deletedAt == null }
        val minYear = activeEntries.minOfOrNull { it.date.year } ?: today.year
        val availableYears = (minYear..today.year).toList().ifEmpty { listOf(today.year) }

        val snapshot = DoctorReportBuilder.buildSnapshot(
            entries = activeEntries,
            moments = emptyList(),
            assessments = cachedAssessments,
            options = DoctorReportOptions(
                period = current.selectedPeriod.toAnalyticsPeriod(),
                includeDailyTable = true,
                includePersonalNotesAndMoments = false
            ),
            today = today,
            calendarMonthOverride = safeDisplayedMonth
        )

        val validSelection = current.selectedChartIndex?.takeIf { it in snapshot.chartData.points.indices }

        _uiState.update { state ->
            state.copy(
                today = today,
                displayedMonth = safeDisplayedMonth,
                availableYears = availableYears,
                snapshot = snapshot,
                selectedChartIndex = validSelection
            )
        }
    }
}
