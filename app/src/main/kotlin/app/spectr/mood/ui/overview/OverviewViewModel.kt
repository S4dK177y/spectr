package app.spectr.mood.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spectr.mood.domain.calculator.AnalyticsCalculator
import app.spectr.mood.domain.calendar.CalendarMonthBuilder
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.repository.DiaryRepository
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
 * Unidirectional state holder for the Overview tab (monthly mood calendar,
 * 3 interactive vector charts, 14 KPI metrics, and key behavioral/symptom markers).
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(private val diaryRepository: DiaryRepository, private val clock: Clock) :
    ViewModel() {

    private val initialToday: LocalDate = LocalDate.now(clock)
    private val initialMonth: YearMonth = YearMonth.from(initialToday)

    private val displayedMonthFlow = MutableStateFlow(initialMonth)
    private val selectedPeriodFlow = MutableStateFlow(OverviewPeriodOption.DAYS_30)

    private val _uiState = MutableStateFlow(
        OverviewUiState(
            today = initialToday,
            displayedMonth = initialMonth,
            calendarGrid = CalendarMonthBuilder.buildMonthGrid(
                yearMonth = initialMonth,
                today = initialToday,
                entriesByDate = emptyMap()
            ),
            availableYears = buildAvailableYears(initialToday, emptyList()),
            isLoading = true
        )
    )
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        observeOverviewData()
    }

    fun onIntent(intent: OverviewIntent) {
        when (intent) {
            OverviewIntent.PreviousMonth -> selectMonth(displayedMonthFlow.value.minusMonths(1))
            OverviewIntent.NextMonth -> selectMonth(displayedMonthFlow.value.plusMonths(1))
            OverviewIntent.JumpToCurrentMonth -> selectMonth(YearMonth.from(currentToday()))
            is OverviewIntent.SelectMonthYear -> {
                selectMonth(intent.yearMonth)
                _uiState.update { it.copy(isMonthYearPickerOpen = false) }
            }
            OverviewIntent.OpenMonthYearPicker -> _uiState.update { it.copy(isMonthYearPickerOpen = true) }
            OverviewIntent.DismissMonthYearPicker -> _uiState.update { it.copy(isMonthYearPickerOpen = false) }
            is OverviewIntent.SelectPeriod -> selectPeriod(intent.period)
            is OverviewIntent.SelectChartPoint -> selectChartPoint(intent.chart, intent.index)
        }
    }

    private fun observeOverviewData() {
        viewModelScope.launch {
            combine(
                diaryRepository.observeEntries(),
                displayedMonthFlow,
                selectedPeriodFlow
            ) { entries, displayedMonth, periodOption ->
                Triple(entries, displayedMonth, periodOption)
            }.collect { (entries, displayedMonth, periodOption) ->
                val today = currentToday()
                val clampedMonth = clampMonth(displayedMonth, today)
                val activeByDate = deduplicateActiveByDate(entries)
                val calendarGrid = CalendarMonthBuilder.buildMonthGrid(
                    yearMonth = clampedMonth,
                    today = today,
                    entriesByDate = activeByDate
                )

                val analyticsPeriod = periodOption.toAnalyticsPeriod()
                val filteredEntries = AnalyticsCalculator.filterEntriesForPeriod(
                    entries = entries,
                    period = analyticsPeriod,
                    anchorDate = today
                )
                val summary = AnalyticsCalculator.calculateSummary(
                    entries = entries,
                    period = analyticsPeriod,
                    anchorDate = today
                )
                val chartData = ChartGeometry.buildOverviewChartData(filteredEntries)
                val maxIndex = chartData.points.lastIndex

                _uiState.update { current ->
                    current.copy(
                        today = today,
                        displayedMonth = clampedMonth,
                        calendarGrid = calendarGrid,
                        availableYears = buildAvailableYears(today, activeByDate.values),
                        selectedPeriod = periodOption,
                        totalActiveEntries = activeByDate.size,
                        summary = summary,
                        chartData = chartData,
                        selectedMoodIndex = current.selectedMoodIndex?.takeIf { it in 0..maxIndex },
                        selectedEnergySleepIndex = current.selectedEnergySleepIndex?.takeIf { it in 0..maxIndex },
                        selectedAnxietyIrritabilityIndex =
                        current.selectedAnxietyIrritabilityIndex?.takeIf { it in 0..maxIndex },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun selectMonth(target: YearMonth) {
        val clamped = clampMonth(target, currentToday())
        displayedMonthFlow.value = clamped
    }

    private fun selectPeriod(period: OverviewPeriodOption) {
        if (period == selectedPeriodFlow.value) return
        _uiState.update {
            it.copy(
                selectedMoodIndex = null,
                selectedEnergySleepIndex = null,
                selectedAnxietyIrritabilityIndex = null
            )
        }
        selectedPeriodFlow.value = period
    }

    private fun selectChartPoint(chart: OverviewChartKind, index: Int?) {
        val maxIndex = _uiState.value.chartData.points.lastIndex
        val validIndex = index?.takeIf { it in 0..maxIndex }
        _uiState.update { current ->
            when (chart) {
                OverviewChartKind.MOOD -> current.copy(selectedMoodIndex = validIndex)
                OverviewChartKind.ENERGY_SLEEP -> current.copy(selectedEnergySleepIndex = validIndex)
                OverviewChartKind.ANXIETY_IRRITABILITY -> current.copy(selectedAnxietyIrritabilityIndex = validIndex)
            }
        }
    }

    private fun clampMonth(target: YearMonth, today: LocalDate): YearMonth {
        val currentMonth = YearMonth.from(today)
        return if (target.isAfter(currentMonth)) currentMonth else target
    }

    private fun deduplicateActiveByDate(entries: List<DailyEntry>): Map<LocalDate, DailyEntry> {
        val byDate = LinkedHashMap<LocalDate, DailyEntry>()
        for (entry in entries) {
            if (entry.deletedAt != null) continue
            val existing = byDate[entry.date]
            if (existing == null || entry.updatedAt >= existing.updatedAt) {
                byDate[entry.date] = entry
            }
        }
        return byDate
    }

    private fun buildAvailableYears(today: LocalDate, entries: Collection<DailyEntry>): List<Int> {
        val minEntryYear = entries.minOfOrNull { it.date.year } ?: today.year
        val startYear = minOf(minEntryYear, today.year - DEFAULT_YEAR_LOOKBACK)
        return (startYear..today.year).toList()
    }

    private fun currentToday(): LocalDate = LocalDate.now(clock)

    companion object {
        private const val DEFAULT_YEAR_LOOKBACK = 2
    }
}
