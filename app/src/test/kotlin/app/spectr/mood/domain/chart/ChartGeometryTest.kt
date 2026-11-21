package app.spectr.mood.domain.chart

import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGeometryTest {

    @Test
    fun `empty entries produce empty chart data`() {
        val data = ChartGeometry.buildOverviewChartData(emptyList())
        assertTrue(data.isEmpty)
        assertNull(data.rangeStart)
        assertNull(data.rangeEnd)
        assertEquals(12.0, data.sleepAxisMaxHours, 0.001)
    }

    @Test
    fun `single entry centers xFraction at 0_5 and keeps flags accurate`() {
        val date = LocalDate.of(2026, 11, 21)
        val entry = DailyEntry(
            id = "e1",
            date = date,
            mood = -2,
            energy = 8,
            dailyDynamics = DailyDynamics.SWINGS,
            sleepMinutesOverride = 300,
            anxiety = 7,
            irritability = 6,
            symptoms = DailySymptoms(racingThoughts = true)
        )
        val data = ChartGeometry.buildOverviewChartData(listOf(entry))

        assertEquals(1, data.points.size)
        val pt = data.points.single()
        assertEquals(0.5f, pt.xFraction, 0.001f)
        assertTrue(pt.isMixedDay)
        assertTrue(pt.isShortSleep)
        assertEquals(5.0, pt.sleepHours!!, 0.001)
        assertEquals(300, pt.sleepMinutes)
        assertEquals(1, data.xTicks.size)
        assertEquals(date, data.xTicks.single().date)
    }

    @Test
    fun `gapped dates preserve proportional calendar spacing and auto-scale sleep axis above 12h`() {
        val d0 = LocalDate.of(2026, 11, 1)
        val d2 = LocalDate.of(2026, 11, 3)
        val d10 = LocalDate.of(2026, 11, 11)
        val entries = listOf(
            DailyEntry(id = "3", date = d10, mood = 2, energy = 7, sleepMinutesOverride = 810), // 13.5h -> ceiling 14
            DailyEntry(id = "1", date = d0, mood = -1, energy = 4), // no sleep
            DailyEntry(id = "2", date = d2, mood = 0, energy = 5, sleepMinutesOverride = 420) // 7h
        )

        val data = ChartGeometry.buildOverviewChartData(entries)
        assertEquals(listOf(d0, d2, d10), data.points.map { it.date })
        assertEquals(0.0f, data.points[0].xFraction, 0.001f)
        assertEquals(0.2f, data.points[1].xFraction, 0.001f)
        assertEquals(1.0f, data.points[2].xFraction, 0.001f)

        assertNull(data.points[0].sleepHours)
        assertFalse(data.points[0].isShortSleep)
        assertEquals(7.0, data.points[1].sleepHours!!, 0.001)
        assertEquals(14.0, data.sleepAxisMaxHours, 0.001)
        assertTrue(data.xTicks.size in 2..4)
    }

    @Test
    fun `valueToY fractionToX and findNearestPointIndex map coordinates accurately`() {
        assertEquals(100f, ChartGeometry.valueToY(-5.0, -5.0, 5.0, 0f, 100f), 0.001f)
        assertEquals(50f, ChartGeometry.valueToY(0.0, -5.0, 5.0, 0f, 100f), 0.001f)
        assertEquals(0f, ChartGeometry.valueToY(5.0, -5.0, 5.0, 0f, 100f), 0.001f)

        assertEquals(20f, ChartGeometry.fractionToX(0f, 20f, 220f), 0.001f)
        assertEquals(120f, ChartGeometry.fractionToX(0.5f, 20f, 220f), 0.001f)
        assertEquals(220f, ChartGeometry.fractionToX(1f, 20f, 220f), 0.001f)

        val fractions = listOf(0f, 0.25f, 0.8f, 1f)
        assertNull(ChartGeometry.findNearestPointIndex(50f, 0f, 100f, emptyList()))
        assertEquals(0, ChartGeometry.findNearestPointIndex(5f, 0f, 100f, fractions))
        assertEquals(1, ChartGeometry.findNearestPointIndex(30f, 0f, 100f, fractions))
        assertEquals(2, ChartGeometry.findNearestPointIndex(78f, 0f, 100f, fractions))
        assertEquals(3, ChartGeometry.findNearestPointIndex(98f, 0f, 100f, fractions))
    }

    @Test
    fun `buildScaleTrendPoints handles single same-day and multi-date confirmed assessments`() {
        val d1 = LocalDate.of(2026, 11, 1)
        val d2 = LocalDate.of(2026, 11, 11)
        val a1 = ScaleAssessment(
            id = "1",
            scaleType = ScaleType.PHQ9,
            date = d1,
            answers = List(9) { 1 },
            totalScore = 9,
            updatedAt = 100L
        )
        val a2 = ScaleAssessment(
            id = "2",
            scaleType = ScaleType.PHQ9,
            date = d2,
            answers = List(9) { 0 },
            totalScore = 0,
            updatedAt = 200L
        )

        val single = ChartGeometry.buildScaleTrendPoints(listOf(a1))
        assertEquals(0.5f, single.single().xFraction, 0.001f)

        val two = ChartGeometry.buildScaleTrendPoints(listOf(a2, a1))
        assertEquals(listOf("1", "2"), two.map { it.assessment.id })
        assertEquals(0.0f, two[0].xFraction, 0.001f)
        assertEquals(1.0f, two[1].xFraction, 0.001f)

        // Same-date fallback evenly distributes points
        val sameDay = ChartGeometry.buildScaleTrendPoints(listOf(a1, a1.copy(id = "3", updatedAt = 300L)))
        assertEquals(0.0f, sameDay[0].xFraction, 0.001f)
        assertEquals(1.0f, sameDay[1].xFraction, 0.001f)
    }
}
