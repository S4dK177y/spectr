package app.spectr.mood.domain.report

import app.spectr.mood.domain.calculator.AnalyticsPeriod
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorReportBuilderTest {

    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun `redacts personal notes, sleep notes, and moments when personal content toggle is false`() {
        val entries = listOf(
            DailyEntry(
                id = "e1",
                date = today.minusDays(2),
                mood = -2,
                energy = 8,
                dailyDynamics = DailyDynamics.SWINGS,
                sleepMinutesOverride = 300,
                sleepNote = "Просыпался в 3 ночи",
                anxiety = 7,
                irritability = 6,
                symptoms = DailySymptoms(apathy = true, racingThoughts = true),
                alcoholStatus = AlcoholStatus.STRESS_COPING,
                medicationStatus = MedicationStatus.TAKEN_WITH_SIDE_EFFECTS,
                note = "Очень личная заметка про работу"
            ),
            DailyEntry(
                id = "e2",
                date = today,
                mood = 1,
                energy = 5,
                sleepMinutesOverride = 480,
                note = "Спокойный день"
            )
        )
        val moments = listOf(
            MomentCheckIn(
                id = "m1",
                entryDate = today.minusDays(2),
                timestampEpochMs = 1_700_000_000_000L,
                mood = -3,
                note = "Личный момент"
            )
        )

        val snapshot = DoctorReportBuilder.buildSnapshot(
            entries = entries,
            moments = moments,
            assessments = emptyList(),
            options = DoctorReportOptions(
                period = AnalyticsPeriod.LAST_14_DAYS,
                includeDailyTable = true,
                includePersonalNotesAndMoments = false
            ),
            today = today
        )

        assertEquals(2, snapshot.dailyRows.size)
        assertEquals(2, snapshot.totalNotesInPeriod)
        assertEquals(1, snapshot.totalMomentsInPeriod)
        assertFalse(snapshot.includePersonalNotesAndMoments)
        snapshot.dailyRows.forEach { row ->
            assertEquals("", row.note)
            assertEquals("", row.sleepNote)
            assertTrue(row.moments.isEmpty())
            assertFalse(row.hasPersonalContent)
        }
        assertTrue(snapshot.dailyRows.first().isMixedDay)
        assertTrue(snapshot.dailyRows.first().isShortSleep)
    }

    @Test
    fun `includes trimmed personal notes and sorted moments when toggle is enabled`() {
        val day = today.minusDays(1)
        val entries = listOf(
            DailyEntry(
                id = "e1",
                date = day,
                mood = 0,
                energy = 5,
                sleepNote = "  Ранний подъём  ",
                note = "  Важный разговор  "
            )
        )
        val moments = listOf(
            MomentCheckIn(id = "m2", entryDate = day, timestampEpochMs = 2_000L, mood = 1, note = "Позже"),
            MomentCheckIn(id = "m1", entryDate = day, timestampEpochMs = 1_000L, mood = -1, note = "Раньше"),
            MomentCheckIn(
                id = "m_del",
                entryDate = day,
                timestampEpochMs = 1_500L,
                mood = 0,
                note = "Удалено",
                deletedAt = 9_999L
            )
        )

        val snapshot = DoctorReportBuilder.buildSnapshot(
            entries = entries,
            moments = moments,
            assessments = emptyList(),
            options = DoctorReportOptions(
                period = AnalyticsPeriod.LAST_14_DAYS,
                includeDailyTable = true,
                includePersonalNotesAndMoments = true
            ),
            today = today
        )

        val row = snapshot.dailyRows.single()
        assertEquals("Важный разговор", row.note)
        assertEquals("Ранний подъём", row.sleepNote)
        assertEquals(listOf("m1", "m2"), row.moments.map { it.id })
        assertTrue(row.hasPersonalContent)
    }

    @Test
    fun `computes scales overview with auto scores and latest confirmed assessments within period`() {
        val customStart = LocalDate.of(2026, 10, 1)
        val customEnd = LocalDate.of(2026, 10, 31)
        val entries = listOf(
            DailyEntry(
                id = "e_oct",
                date = LocalDate.of(2026, 10, 20),
                mood = -2,
                energy = 3,
                anxiety = 6,
                symptoms = DailySymptoms(apathy = true)
            ),
            DailyEntry(
                id = "e_nov",
                date = LocalDate.of(2026, 11, 15),
                mood = 3,
                energy = 8
            )
        )
        val assessments = listOf(
            ScaleAssessment(
                id = "a_old",
                scaleType = ScaleType.PHQ9,
                date = LocalDate.of(2026, 10, 5),
                answers = List(9) { 1 },
                totalScore = 9,
                isConfirmed = true
            ),
            ScaleAssessment(
                id = "a_oct_latest",
                scaleType = ScaleType.PHQ9,
                date = LocalDate.of(2026, 10, 25),
                answers = List(9) { 2 },
                totalScore = 18,
                isConfirmed = true
            ),
            ScaleAssessment(
                id = "a_after_custom_end",
                scaleType = ScaleType.PHQ9,
                date = LocalDate.of(2026, 11, 10),
                answers = List(9) { 0 },
                totalScore = 0,
                isConfirmed = true
            ),
            ScaleAssessment(
                id = "a_deleted",
                scaleType = ScaleType.GAD7,
                date = LocalDate.of(2026, 10, 22),
                answers = List(7) { 1 },
                totalScore = 7,
                isConfirmed = true,
                deletedAt = 1234L
            )
        )

        val snapshot = DoctorReportBuilder.buildSnapshot(
            entries = entries,
            moments = emptyList(),
            assessments = assessments,
            options = DoctorReportOptions(period = AnalyticsPeriod.Custom(customStart, customEnd)),
            today = today
        )

        assertEquals(customStart, snapshot.effectiveStartDate)
        assertEquals(customEnd, snapshot.effectiveEndDate)
        assertEquals(1, snapshot.summary.filledDays)
        assertEquals(YearMonth.of(2026, 10), snapshot.calendarGrid.yearMonth)
        assertEquals("spectr-report-20261001-20261031.pdf", snapshot.suggestedFileName)

        val phq9 = snapshot.scalesOverview.first { it.scaleType == ScaleType.PHQ9 }
        assertNotNull(phq9.autoScore)
        assertEquals("a_oct_latest", phq9.latestConfirmed?.id)

        val gad7 = snapshot.scalesOverview.first { it.scaleType == ScaleType.GAD7 }
        assertNull(gad7.latestConfirmed)
    }

    @Test
    fun `handles empty diary gracefully`() {
        val snapshot = DoctorReportBuilder.buildSnapshot(
            entries = emptyList(),
            moments = emptyList(),
            assessments = emptyList(),
            options = DoctorReportOptions(period = AnalyticsPeriod.AllTime),
            today = today
        )

        assertTrue(snapshot.isEmpty)
        assertNull(snapshot.autoScales)
        assertEquals(3, snapshot.scalesOverview.size)
        assertEquals(YearMonth.from(today), snapshot.calendarGrid.yearMonth)
        assertEquals("spectr-report-20261121-20261121.pdf", snapshot.suggestedFileName)
    }
}
