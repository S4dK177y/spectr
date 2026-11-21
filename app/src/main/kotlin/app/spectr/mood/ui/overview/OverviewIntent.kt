package app.spectr.mood.ui.overview

import java.time.YearMonth

/**
 * User intents emitted from the Overview tab UI to [OverviewViewModel].
 */
sealed interface OverviewIntent {
    data object PreviousMonth : OverviewIntent
    data object NextMonth : OverviewIntent
    data object JumpToCurrentMonth : OverviewIntent
    data class SelectMonthYear(val yearMonth: YearMonth) : OverviewIntent
    data object OpenMonthYearPicker : OverviewIntent
    data object DismissMonthYearPicker : OverviewIntent

    data class SelectPeriod(val period: OverviewPeriodOption) : OverviewIntent
    data class SelectChartPoint(val chart: OverviewChartKind, val index: Int?) : OverviewIntent
}
