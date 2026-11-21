package app.spectr.mood.ui.overview

import app.spectr.mood.domain.calculator.AnalyticsPeriod
import app.spectr.mood.domain.calculator.DiaryAnalyticsSummary
import app.spectr.mood.domain.calendar.CalendarMonthGrid
import app.spectr.mood.domain.chart.OverviewChartData
import java.time.LocalDate
import java.time.YearMonth

/**
 * Period presets for Overview charts, 14 KPI metrics, and key markers.
 */
enum class OverviewPeriodOption {
    DAYS_14,
    DAYS_30,
    DAYS_90,
    ALL_TIME;

    fun toAnalyticsPeriod(): AnalyticsPeriod = when (this) {
        DAYS_14 -> AnalyticsPeriod.LAST_14_DAYS
        DAYS_30 -> AnalyticsPeriod.LAST_30_DAYS
        DAYS_90 -> AnalyticsPeriod.LAST_90_DAYS
        ALL_TIME -> AnalyticsPeriod.AllTime
    }
}

/**
 * Identifies each of the three interactive vector charts on the Overview tab.
 */
enum class OverviewChartKind {
    MOOD,
    ENERGY_SLEEP,
    ANXIETY_IRRITABILITY
}

/**
 * Unidirectional UI state for the Overview tab.
 */
data class OverviewUiState(
    val today: LocalDate,
    val displayedMonth: YearMonth,
    val calendarGrid: CalendarMonthGrid,
    val availableYears: List<Int>,
    val isMonthYearPickerOpen: Boolean = false,
    val selectedPeriod: OverviewPeriodOption = OverviewPeriodOption.DAYS_30,
    val totalActiveEntries: Int = 0,
    val summary: DiaryAnalyticsSummary = DiaryAnalyticsSummary(),
    val chartData: OverviewChartData = OverviewChartData(),
    val selectedMoodIndex: Int? = null,
    val selectedEnergySleepIndex: Int? = null,
    val selectedAnxietyIrritabilityIndex: Int? = null,
    val isLoading: Boolean = true
) {
    val currentMonth: YearMonth
        get() = YearMonth.from(today)

    val canGoNextMonth: Boolean
        get() = displayedMonth.isBefore(currentMonth)

    val isCurrentMonthDisplayed: Boolean
        get() = displayedMonth == currentMonth
}
