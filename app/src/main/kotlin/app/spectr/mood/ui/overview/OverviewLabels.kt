package app.spectr.mood.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.spectr.mood.R
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
private val SHORT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", RUSSIAN_LOCALE)
private val SHORT_DATE_WITH_YEAR_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yy", RUSSIAN_LOCALE)

private val MONTH_NOMINATIVE_RU: Map<Month, String> = mapOf(
    Month.JANUARY to "Январь",
    Month.FEBRUARY to "Февраль",
    Month.MARCH to "Март",
    Month.APRIL to "Апрель",
    Month.MAY to "Май",
    Month.JUNE to "Июнь",
    Month.JULY to "Июль",
    Month.AUGUST to "Август",
    Month.SEPTEMBER to "Сентябрь",
    Month.OCTOBER to "Октябрь",
    Month.NOVEMBER to "Ноябрь",
    Month.DECEMBER to "Декабрь"
)

private val MONTH_SHORT_RU: Map<Month, String> = mapOf(
    Month.JANUARY to "Янв",
    Month.FEBRUARY to "Фев",
    Month.MARCH to "Мар",
    Month.APRIL to "Апр",
    Month.MAY to "Май",
    Month.JUNE to "Июн",
    Month.JULY to "Июл",
    Month.AUGUST to "Авг",
    Month.SEPTEMBER to "Сен",
    Month.OCTOBER to "Окт",
    Month.NOVEMBER to "Ноя",
    Month.DECEMBER to "Дек"
)

internal fun formatYearMonthTitle(yearMonth: YearMonth): String {
    val monthName = MONTH_NOMINATIVE_RU.getValue(yearMonth.month)
    return "$monthName ${yearMonth.year}"
}

internal fun shortMonthLabel(month: Month): String = MONTH_SHORT_RU.getValue(month)

internal fun formatShortChartDate(date: LocalDate, referenceYear: Int): String = if (date.year == referenceYear) {
    date.format(SHORT_DATE_FORMATTER)
} else {
    date.format(SHORT_DATE_WITH_YEAR_FORMATTER)
}

internal fun formatSignedAverageMood(value: Double): String {
    val formatted = String.format(RUSSIAN_LOCALE, "%.1f", abs(value))
    return when {
        value > 0.05 -> "+$formatted"
        value < -0.05 -> "−$formatted"
        else -> "0,0"
    }
}

internal fun formatDecimalOne(value: Double): String = String.format(RUSSIAN_LOCALE, "%.1f", value)

@Composable
internal fun overviewPeriodLabel(option: OverviewPeriodOption): String = when (option) {
    OverviewPeriodOption.DAYS_14 -> stringResource(R.string.overview_period_14d)
    OverviewPeriodOption.DAYS_30 -> stringResource(R.string.overview_period_30d)
    OverviewPeriodOption.DAYS_90 -> stringResource(R.string.overview_period_90d)
    OverviewPeriodOption.ALL_TIME -> stringResource(R.string.overview_period_all)
}
