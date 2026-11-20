package app.spectr.mood.domain.calculator

import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsCalculatorTest {

    private val baseDate = LocalDate.of(2026, 11, 20)

    @Test
    fun `isMixedDay follows exact formula`() {
        val base = DailyEntry(date = baseDate, mood = -1, energy = 5, irritability = 5)
        assertFalse(AnalyticsCalculator.isMixedDay(base))
        assertTrue(AnalyticsCalculator.isMixedDay(base.copy(energy = 7)))
        assertTrue(AnalyticsCalculator.isMixedDay(base.copy(irritability = 7)))
        assertTrue(AnalyticsCalculator.isMixedDay(base.copy(symptoms = DailySymptoms(racingThoughts = true))))

        // Neutral or positive mood is NEVER a mixed day even with high activation
        assertFalse(
            AnalyticsCalculator.isMixedDay(
                base.copy(
                    mood = 0,
                    energy = 9,
                    irritability = 9,
                    symptoms = DailySymptoms(racingThoughts = true)
                )
            )
        )
        assertFalse(
            AnalyticsCalculator.isMixedDay(
                base.copy(
                    mood = 2,
                    energy = 9,
                    irritability = 8,
                    symptoms = DailySymptoms(racingThoughts = true)
                )
            )
        )

        // Low mood below thresholds is not mixed
        assertFalse(AnalyticsCalculator.isMixedDay(base.copy(mood = -3, energy = 6, irritability = 6)))
    }

    @Test
    fun `empty dataset yields empty summary with null averages`() {
        val summary = AnalyticsCalculator.calculateSummary(emptyList())
        assertEquals(0, summary.filledDays)
        assertNull(summary.averageMood)
        assertNull(summary.minMood)
        assertNull(summary.maxMood)
        assertNull(summary.moodSpan)
        assertNull(summary.averageSleepHours)
    }

    @Test
    fun `calculateSummary computes all 14 KPI metrics and key markers accurately`() {
        val entries = listOf(
            // Day 1: marked low + mixed (energy=7) + short sleep (5h) + swings + deathThoughts + alcohol stress
            DailyEntry(
                date = baseDate.minusDays(3),
                mood = -3,
                energy = 7,
                dailyDynamics = DailyDynamics.SWINGS,
                sleepMinutesOverride = 300,
                anxiety = 8,
                irritability = 6,
                symptoms = DailySymptoms(apathy = true, deathThoughts = true, selfCriticism = true),
                alcoholStatus = AlcoholStatus.STRESS_COPING,
                medicationStatus = MedicationStatus.TAKEN_WITH_SIDE_EFFECTS
            ),
            // Day 2: neutral mood + normal sleep (8h) + high irritability + bingeEating + missed meds (forgot)
            DailyEntry(
                date = baseDate.minusDays(2),
                mood = 0,
                energy = 5,
                dailyDynamics = DailyDynamics.STEADY,
                sleepMinutesOverride = 480,
                anxiety = 4,
                irritability = 7,
                symptoms = DailySymptoms(bingeEating = true, brainFog = true),
                alcoholStatus = AlcoholStatus.LIGHT_OCCASION,
                medicationStatus = MedicationStatus.MISSED_FORGOT
            ),
            // Day 3: marked high + unrecorded sleep (should not affect avg sleep or shortSleepDays) + hyperfocus
            DailyEntry(
                date = baseDate.minusDays(1),
                mood = 3,
                energy = 8,
                dailyDynamics = DailyDynamics.WORSE_EVENING,
                sleepMinutesOverride = null,
                anxiety = 2,
                irritability = 2,
                symptoms = DailySymptoms(hyperfocus = true, racingThoughts = true),
                alcoholStatus = AlcoholStatus.NONE,
                medicationStatus = MedicationStatus.MISSED_SIDE_EFFECTS
            ),
            // Day 4: mild low + normal sleep (7h) + normal meds
            DailyEntry(
                date = baseDate,
                mood = -1,
                energy = 4,
                dailyDynamics = DailyDynamics.WORSE_MORNING,
                sleepMinutesOverride = 420,
                anxiety = 3,
                irritability = 3,
                alcoholStatus = AlcoholStatus.NONE,
                medicationStatus = MedicationStatus.TAKEN_AS_PRESCRIBED
            )
        )

        val summary = AnalyticsCalculator.calculateSummary(entries)

        assertEquals(baseDate.minusDays(3), summary.periodStart)
        assertEquals(baseDate, summary.periodEnd)
        assertEquals(4, summary.filledDays)
        assertEquals(-0.25, summary.averageMood!!, 0.0001)
        assertEquals(-3, summary.minMood)
        assertEquals(3, summary.maxMood)
        assertEquals(6, summary.moodSpan)
        assertEquals(2, summary.lowMoodDays)
        assertEquals(1, summary.neutralMoodDays)
        assertEquals(1, summary.elevatedMoodDays)
        assertEquals(6.0, summary.averageEnergy!!, 0.0001)
        // Sleep tracked on Days 1, 2, 4: (5 + 8 + 7) / 3 = 6.6667h
        assertEquals(3, summary.sleepTrackedDays)
        assertEquals(20.0 / 3.0, summary.averageSleepHours!!, 0.0001)
        assertEquals(4.25, summary.averageAnxiety!!, 0.0001)
        assertEquals(4.5, summary.averageIrritability!!, 0.0001)
        assertEquals(1, summary.markedLowDays)
        assertEquals(1, summary.markedHighDays)
        assertEquals(1, summary.mixedDays)
        assertEquals(1, summary.shortSleepDays)
        assertEquals(1, summary.intraDaySwingsDays)
        assertEquals(1, summary.highAnxietyDays)
        assertEquals(1, summary.highIrritabilityDays)
        assertEquals(2, summary.highAnxietyOrIrritabilityDays)

        // Key markers
        assertEquals(1, summary.markers.deathThoughtsDays)
        assertEquals(1, summary.markers.bingeEatingDays)
        assertEquals(1, summary.markers.hyperfocusDays)
        assertEquals(1, summary.markers.racingThoughtsDays)
        assertEquals(1, summary.markers.apathyDays)
        assertEquals(1, summary.markers.selfCriticismDays)
        assertEquals(1, summary.markers.brainFogDays)
        assertEquals(2, summary.markers.alcoholDays)
        assertEquals(1, summary.markers.alcoholStressDays)
        assertEquals(2, summary.markers.medicationSideEffectsDays)
        assertEquals(2, summary.markers.medicationMissedDays)
    }

    @Test
    fun `filterEntriesForPeriod supports LastDays Custom AllTime and deduplication`() {
        val entries = listOf(
            DailyEntry(id = "old", date = baseDate.minusDays(20), mood = -2, energy = 3),
            DailyEntry(id = "dup-older", date = baseDate.minusDays(5), mood = -1, energy = 4, updatedAt = 100L),
            DailyEntry(id = "dup-newer", date = baseDate.minusDays(5), mood = 2, energy = 7, updatedAt = 200L),
            DailyEntry(id = "deleted", date = baseDate.minusDays(2), mood = 4, energy = 9, deletedAt = 300L),
            DailyEntry(id = "latest", date = baseDate, mood = 1, energy = 6)
        )

        val last14 = AnalyticsCalculator.filterEntriesForPeriod(entries, AnalyticsPeriod.LAST_14_DAYS)
        assertEquals(2, last14.size)
        assertEquals("dup-newer", last14[0].id)
        assertEquals("latest", last14[1].id)

        val allTime = AnalyticsCalculator.filterEntriesForPeriod(entries, AnalyticsPeriod.AllTime)
        assertEquals(3, allTime.size)

        val custom = AnalyticsCalculator.filterEntriesForPeriod(
            entries,
            AnalyticsPeriod.Custom(baseDate.minusDays(20), baseDate.minusDays(10))
        )
        assertEquals(1, custom.size)
        assertEquals("old", custom.single().id)
    }
}
