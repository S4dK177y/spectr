package app.spectr.mood.domain.calculator

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.SleepInterval
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDurationCalculatorTest {

    @Test
    fun `same-day interval computes exact duration`() {
        // 09:00 (540) -> 12:30 (750) = 210 min (3.5h)
        assertEquals(210, SleepDurationCalculator.intervalRawMinutes(540, 750))
        assertEquals(210, SleepDurationCalculator.intervalNetMinutes(540, 750, awakeDeductionMin = 0))
    }

    @Test
    fun `midnight crossover interval wraps across 24h boundary`() {
        // 22:00 (1320) -> 01:30 (90) = 210 min (3.5h)
        assertEquals(210, SleepDurationCalculator.intervalRawMinutes(1320, 90))
        // 23:45 (1425) -> 00:15 (15) = 30 min
        assertEquals(30, SleepDurationCalculator.intervalRawMinutes(1425, 15))
        // 22:00 (1320) -> 00:00 (0) = 120 min
        assertEquals(120, SleepDurationCalculator.intervalRawMinutes(1320, 0))
    }

    @Test
    fun `identical start and end minutes yields zero duration`() {
        assertEquals(0, SleepDurationCalculator.intervalRawMinutes(480, 480))
        assertEquals(0, SleepDurationCalculator.intervalNetMinutes(480, 480, awakeDeductionMin = 15))
    }

    @Test
    fun `awake deduction subtracts cleanly and clamps at zero`() {
        // 23:00 (1380) -> 07:00 (420) = 480 min; minus 45 min = 435 min
        assertEquals(435, SleepDurationCalculator.intervalNetMinutes(1380, 420, awakeDeductionMin = 45))
        // Deduction larger than interval clamps to 0
        assertEquals(0, SleepDurationCalculator.intervalNetMinutes(1380, 1410, awakeDeductionMin = 120))
    }

    @Test
    fun `polyphasic sleep sums active intervals and ignores soft-deleted ones`() {
        val entry = DailyEntry(
            id = "e1",
            date = LocalDate.of(2026, 11, 10),
            mood = 0,
            energy = 5,
            sleepIntervals = listOf(
                SleepInterval(id = "s1", entryId = "e1", startMinuteOfDay = 1320, endMinuteOfDay = 90),
                SleepInterval(
                    id = "s2",
                    entryId = "e1",
                    startMinuteOfDay = 540,
                    endMinuteOfDay = 750,
                    awakeDeductionMin = 30
                ),
                SleepInterval(
                    id = "s3",
                    entryId = "e1",
                    startMinuteOfDay = 900,
                    endMinuteOfDay = 960,
                    deletedAt = 12345L
                )
            )
        )

        assertTrue(SleepDurationCalculator.hasSleepData(entry))
        assertEquals(390, SleepDurationCalculator.effectiveSleepMinutes(entry))
        assertEquals(6.5, SleepDurationCalculator.effectiveSleepHours(entry), 0.0001)
    }

    @Test
    fun `manual override takes precedence over sleep intervals including explicit zero`() {
        val base = DailyEntry(
            id = "e1",
            date = LocalDate.of(2026, 11, 10),
            mood = 0,
            energy = 5,
            sleepIntervals = listOf(
                SleepInterval(id = "s1", entryId = "e1", startMinuteOfDay = 1380, endMinuteOfDay = 420)
            ),
            sleepMinutesOverride = 330
        )
        assertEquals(330, SleepDurationCalculator.effectiveSleepMinutes(base))
        assertEquals(5.5, SleepDurationCalculator.effectiveSleepHours(base), 0.0001)

        val sleeplessNight = base.copy(sleepIntervals = emptyList(), sleepMinutesOverride = 0)
        assertTrue(SleepDurationCalculator.hasSleepData(sleeplessNight))
        assertEquals(0, SleepDurationCalculator.effectiveSleepMinutes(sleeplessNight))

        val unrecorded = base.copy(sleepIntervals = emptyList(), sleepMinutesOverride = null)
        assertFalse(SleepDurationCalculator.hasSleepData(unrecorded))
        assertEquals(0, SleepDurationCalculator.effectiveSleepMinutes(unrecorded))
    }

    @Test
    fun `formatMinuteOfDay formats 24h clock values`() {
        assertEquals("00:00", SleepDurationCalculator.formatMinuteOfDay(0))
        assertEquals("01:30", SleepDurationCalculator.formatMinuteOfDay(90))
        assertEquals("23:59", SleepDurationCalculator.formatMinuteOfDay(1439))
    }

    @Test
    fun `parseSleepText parses split sleep with midnight crossover`() {
        val result = SleepDurationCalculator.parseSleepText("22:00–01:30, 09:00–12:30")

        assertTrue(result.isRecognized)
        assertNull(result.overrideMinutes)
        assertEquals(2, result.intervals.size)
        assertEquals(ParsedSleepInterval(1320, 90, 0), result.intervals[0])
        assertEquals(ParsedSleepInterval(540, 750, 0), result.intervals[1])
        assertEquals(420, result.totalMinutes)
        assertEquals(7.0, result.totalHours, 0.0001)
    }

    @Test
    fun `parseSleepText supports various delimiters dots single digit hours and 24-00`() {
        val result = SleepDurationCalculator.parseSleepText("22.00-24:00; 0:30 до 6:00")

        assertTrue(result.isRecognized)
        assertEquals(2, result.intervals.size)
        assertEquals(ParsedSleepInterval(1320, 0, 0), result.intervals[0])
        assertEquals(ParsedSleepInterval(30, 360, 0), result.intervals[1])
        assertEquals(450, result.totalMinutes)
    }

    @Test
    fun `parseSleepText extracts Russian and English awake deductions`() {
        val ruParens = SleepDurationCalculator.parseSleepText("23:00-07:00 (-30м)")
        assertTrue(ruParens.isRecognized)
        assertEquals(30, ruParens.intervals.single().awakeDeductionMin)
        assertEquals(450, ruParens.totalMinutes)

        val ruHoursAndMin = SleepDurationCalculator.parseSleepText("23:00–07:30 (-1ч 15м)")
        assertEquals(75, ruHoursAndMin.intervals.single().awakeDeductionMin)
        assertEquals(435, ruHoursAndMin.totalMinutes)

        val ruWord = SleepDurationCalculator.parseSleepText("23:00-07:00 (проснулся 40 мин)")
        assertEquals(40, ruWord.intervals.single().awakeDeductionMin)
        assertEquals(440, ruWord.totalMinutes)

        val enInline = SleepDurationCalculator.parseSleepText("23:30-07:30 -45m")
        assertEquals(45, enInline.intervals.single().awakeDeductionMin)
        assertEquals(435, enInline.totalMinutes)

        val decimalHoursDeduction = SleepDurationCalculator.parseSleepText("22:00-07:00 -1,5ч")
        assertEquals(90, decimalHoursDeduction.intervals.single().awakeDeductionMin)
        assertEquals(450, decimalHoursDeduction.totalMinutes)
    }

    @Test
    fun `parseSleepText parses direct hour and minute overrides`() {
        val decimalDot = SleepDurationCalculator.parseSleepText("7.5")
        assertTrue(decimalDot.isRecognized)
        assertTrue(decimalDot.intervals.isEmpty())
        assertEquals(450, decimalDot.overrideMinutes)

        val decimalCommaRu = SleepDurationCalculator.parseSleepText("7,5 ч")
        assertEquals(450, decimalCommaRu.overrideMinutes)

        val hoursMinutesRu = SleepDurationCalculator.parseSleepText("7ч 30м")
        assertEquals(450, hoursMinutesRu.overrideMinutes)

        val hoursMinutesEn = SleepDurationCalculator.parseSleepText("8h 15m")
        assertEquals(495, hoursMinutesEn.overrideMinutes)

        val minutesOnly = SleepDurationCalculator.parseSleepText("420 мин")
        assertEquals(420, minutesOnly.overrideMinutes)

        val zeroSleep = SleepDurationCalculator.parseSleepText("0")
        assertTrue(zeroSleep.isRecognized)
        assertEquals(0, zeroSleep.overrideMinutes)
    }

    @Test
    fun `parseSleepText rejects blank placeholders and invalid times`() {
        val invalidInputs = listOf(
            "",
            "   ",
            "-",
            "—",
            "нет",
            "н/д",
            "n/a",
            "99:99-88:88",
            "25:00-07:00",
            "24:30-07:00",
            "2026",
            "просто текст"
        )
        for (input in invalidInputs) {
            val result = SleepDurationCalculator.parseSleepText(input)
            assertFalse("Expected '$input' to be unrecognized", result.isRecognized)
            assertEquals(SleepParseResult.EMPTY, result)
        }
    }
}
