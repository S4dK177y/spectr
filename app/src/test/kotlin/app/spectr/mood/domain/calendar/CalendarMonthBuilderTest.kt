package app.spectr.mood.domain.calendar

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthBuilderTest {

    @Test
    fun `november 2026 starts on sunday and produces monday-to-sunday grid with accurate markers`() {
        val ym = YearMonth.of(2026, 11) // Nov 1 2026 is Sunday
        val today = LocalDate.of(2026, 11, 21)
        val mixedDate = LocalDate.of(2026, 11, 10)
        val shortSleepDate = LocalDate.of(2026, 11, 15)
        val prevMonthDate = LocalDate.of(2026, 10, 31)

        val entries = mapOf(
            mixedDate to DailyEntry(
                id = "m1",
                date = mixedDate,
                mood = -2,
                energy = 8,
                symptoms = DailySymptoms(racingThoughts = true)
            ),
            shortSleepDate to DailyEntry(
                id = "m2",
                date = shortSleepDate,
                mood = 1,
                energy = 6,
                sleepMinutesOverride = 300
            ),
            prevMonthDate to DailyEntry(
                id = "prev",
                date = prevMonthDate,
                mood = 0,
                energy = 5
            )
        )

        val grid = CalendarMonthBuilder.buildMonthGrid(ym, today, entries)
        assertEquals(ym, grid.yearMonth)
        assertEquals(2, grid.filledDaysInMonth)
        assertTrue(grid.weeks.all { it.size == 7 })
        assertEquals(DayOfWeek.MONDAY, grid.weeks.first().first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, grid.weeks.last().last().date.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 26), grid.weeks.first().first().date)

        val flat = grid.weeks.flatten()
        val mixedCell = flat.first { it.date == mixedDate }
        assertTrue(mixedCell.isCurrentMonth)
        assertTrue(mixedCell.isMixedDay)
        assertFalse(mixedCell.isShortSleep)
        assertTrue(mixedCell.isSelectable)

        val shortSleepCell = flat.first { it.date == shortSleepDate }
        assertTrue(shortSleepCell.isShortSleep)
        assertFalse(shortSleepCell.isMixedDay)

        val todayCell = flat.first { it.date == today }
        assertTrue(todayCell.isToday)
        assertFalse(todayCell.isFuture)
        assertTrue(todayCell.isSelectable)

        val futureCell = flat.first { it.date == LocalDate.of(2026, 11, 25) }
        assertTrue(futureCell.isFuture)
        assertFalse(futureCell.isSelectable)

        val prevCell = flat.first { it.date == prevMonthDate }
        assertFalse(prevCell.isCurrentMonth)
        assertFalse(prevCell.isSelectable)
    }

    @Test
    fun `february 2024 leap year contains 29 days aligned Monday to Sunday`() {
        val ym = YearMonth.of(2024, 2) // Feb 1 2024 is Thursday, 29 days
        val grid = CalendarMonthBuilder.buildMonthGrid(
            yearMonth = ym,
            today = LocalDate.of(2024, 3, 1),
            entriesByDate = emptyMap()
        )
        val currentMonthDays = grid.weeks.flatten().filter { it.isCurrentMonth }
        assertEquals(29, currentMonthDays.size)
        assertEquals(LocalDate.of(2024, 2, 1), currentMonthDays.first().date)
        assertEquals(LocalDate.of(2024, 2, 29), currentMonthDays.last().date)
    }
}
