package app.spectr.mood.domain.calendar

import app.spectr.mood.domain.calculator.AnalyticsCalculator
import app.spectr.mood.domain.calculator.SleepDurationCalculator
import app.spectr.mood.domain.model.DailyEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Single calendar cell inside a Monday–Sunday monthly grid.
 */
data class CalendarDayCell(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val entry: DailyEntry?,
    val isMixedDay: Boolean,
    val isShortSleep: Boolean
) {
    val isSelectable: Boolean
        get() = isCurrentMonth && !isFuture
}

/**
 * Complete Monday–Sunday month layout used by the Overview calendar and PDF report.
 */
data class CalendarMonthGrid(
    val yearMonth: YearMonth,
    val weeks: List<List<CalendarDayCell>>,
    val filledDaysInMonth: Int
)

/**
 * Pure-Kotlin Monday–Sunday calendar grid builder.
 */
object CalendarMonthBuilder {

    private const val DAYS_IN_WEEK = 7
    private const val SHORT_SLEEP_THRESHOLD_HOURS = 6.0

    fun buildMonthGrid(
        yearMonth: YearMonth,
        today: LocalDate,
        entriesByDate: Map<LocalDate, DailyEntry>
    ): CalendarMonthGrid {
        val firstOfMonth = yearMonth.atDay(1)
        val lastOfMonth = yearMonth.atEndOfMonth()

        val leadingDays = firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
        val trailingDays = DayOfWeek.SUNDAY.value - lastOfMonth.dayOfWeek.value

        val gridStart = firstOfMonth.minusDays(leadingDays.toLong())
        val gridEnd = lastOfMonth.plusDays(trailingDays.toLong())

        val cells = ArrayList<CalendarDayCell>()
        var filledCount = 0
        var cursor = gridStart
        while (!cursor.isAfter(gridEnd)) {
            val isCurrentMonth = YearMonth.from(cursor) == yearMonth
            val entry = entriesByDate[cursor]
            if (isCurrentMonth && entry != null) {
                filledCount += 1
            }
            val isMixed = entry != null && AnalyticsCalculator.isMixedDay(entry)
            val isShortSleep = entry != null &&
                SleepDurationCalculator.hasSleepData(entry) &&
                SleepDurationCalculator.effectiveSleepHours(entry) < SHORT_SLEEP_THRESHOLD_HOURS

            cells.add(
                CalendarDayCell(
                    date = cursor,
                    isCurrentMonth = isCurrentMonth,
                    isToday = cursor == today,
                    isFuture = cursor.isAfter(today),
                    entry = entry,
                    isMixedDay = isMixed,
                    isShortSleep = isShortSleep
                )
            )
            cursor = cursor.plusDays(1)
        }

        return CalendarMonthGrid(
            yearMonth = yearMonth,
            weeks = cells.chunked(DAYS_IN_WEEK),
            filledDaysInMonth = filledCount
        )
    }
}
