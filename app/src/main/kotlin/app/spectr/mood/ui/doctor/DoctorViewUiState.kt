package app.spectr.mood.ui.doctor

import app.spectr.mood.domain.calendar.CalendarMonthGrid
import app.spectr.mood.domain.report.DoctorReportSnapshot
import app.spectr.mood.domain.report.ReportDailyRow
import app.spectr.mood.domain.report.ReportScaleOverview
import app.spectr.mood.ui.overview.OverviewPeriodOption
import app.spectr.mood.ui.overview.OverviewUiState
import java.time.LocalDate
import java.time.YearMonth

/**
 * Strictly read-only UI state for «Режим показа врачу».
 *
 * By construction, all [dailyRows] are produced with `includePersonalNotesAndMoments = false`
 * so private text notes, sleep comments, and momentary check-ins are never exposed in Doctor Mode.
 */
data class DoctorViewUiState(
    val today: LocalDate = LocalDate.now(),
    val selectedPeriod: OverviewPeriodOption = OverviewPeriodOption.DAYS_30,
    val displayedMonth: YearMonth = YearMonth.now(),
    val availableYears: List<Int> = listOf(LocalDate.now().year),
    val isMonthPickerOpen: Boolean = false,
    val snapshot: DoctorReportSnapshot? = null,
    val selectedChartIndex: Int? = null,
    val isDailyLogExpanded: Boolean = false
) {
    val calendarGrid: CalendarMonthGrid
        get() = snapshot?.calendarGrid ?: CalendarMonthGrid(
            yearMonth = displayedMonth,
            weeks = emptyList(),
            filledDaysInMonth = 0
        )

    val scalesOverview: List<ReportScaleOverview>
        get() = snapshot?.scalesOverview.orEmpty()

    val dailyRows: List<ReportDailyRow>
        get() = snapshot?.dailyRows.orEmpty()

    /**
     * Projection used to reuse read-only calendar and period widgets from Overview without duplication.
     */
    fun toOverviewProjection(): OverviewUiState {
        val snap = snapshot
        return OverviewUiState(
            today = today,
            displayedMonth = displayedMonth,
            calendarGrid = calendarGrid,
            availableYears = availableYears,
            isMonthYearPickerOpen = isMonthPickerOpen,
            selectedPeriod = selectedPeriod,
            summary = snap?.summary ?: app.spectr.mood.domain.calculator.DiaryAnalyticsSummary(),
            chartData = snap?.chartData ?: app.spectr.mood.domain.chart.OverviewChartData(),
            selectedMoodIndex = selectedChartIndex,
            selectedEnergySleepIndex = selectedChartIndex,
            selectedAnxietyIrritabilityIndex = selectedChartIndex,
            isLoading = false
        )
    }
}
