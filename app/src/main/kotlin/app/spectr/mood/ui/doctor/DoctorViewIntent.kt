package app.spectr.mood.ui.doctor

import app.spectr.mood.ui.overview.OverviewPeriodOption
import java.time.YearMonth

sealed interface DoctorViewIntent {
    data class SelectPeriod(val period: OverviewPeriodOption) : DoctorViewIntent
    data object PreviousMonth : DoctorViewIntent
    data object NextMonth : DoctorViewIntent
    data object JumpToCurrentMonth : DoctorViewIntent
    data object OpenMonthYearPicker : DoctorViewIntent
    data object DismissMonthYearPicker : DoctorViewIntent
    data class SelectMonthYear(val yearMonth: YearMonth) : DoctorViewIntent
    data class SelectChartPoint(val index: Int?) : DoctorViewIntent
    data object ToggleDailyLogExpanded : DoctorViewIntent
}
