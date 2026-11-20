package app.spectr.mood.domain.calculator

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClinicalScaleCalculatorTest {

    private val anchor = LocalDate.of(2026, 11, 20)

    @Test
    fun `calculateAutoScales returns null when there are no active entries`() {
        assertNull(ClinicalScaleCalculator.calculateAutoScales(emptyList()))

        val deletedOnly = listOf(
            entry(date = anchor, deletedAt = 1000L)
        )
        assertNull(ClinicalScaleCalculator.calculateAutoScales(deletedOnly))
    }

    @Test
    fun `anchor date uses latest active entry and ignores newer soft-deleted entries`() {
        val entries = listOf(
            entry(date = anchor.minusDays(2), mood = -3, updatedAt = 100L),
            entry(date = anchor, mood = 2, deletedAt = 200L, updatedAt = 200L)
        )

        val snapshot = ClinicalScaleCalculator.calculateAutoScales(entries)
        assertNotNull(snapshot)
        assertEquals(anchor.minusDays(2), snapshot!!.anchorDate)
    }

    @Test
    fun `score mapping thresholds match exact PHQ-9 GAD-7 and ASRM tables`() {
        // PHQ-9 / GAD-7 (14-day window -> 0..3)
        assertEquals(0, ClinicalScaleCalculator.mapFourteenDayCountToScore(0))
        assertEquals(1, ClinicalScaleCalculator.mapFourteenDayCountToScore(1))
        assertEquals(1, ClinicalScaleCalculator.mapFourteenDayCountToScore(6))
        assertEquals(2, ClinicalScaleCalculator.mapFourteenDayCountToScore(7))
        assertEquals(2, ClinicalScaleCalculator.mapFourteenDayCountToScore(11))
        assertEquals(3, ClinicalScaleCalculator.mapFourteenDayCountToScore(12))
        assertEquals(3, ClinicalScaleCalculator.mapFourteenDayCountToScore(14))

        // ASRM (7-day window -> 0..4)
        assertEquals(0, ClinicalScaleCalculator.mapSevenDayCountToScore(0))
        assertEquals(1, ClinicalScaleCalculator.mapSevenDayCountToScore(1))
        assertEquals(1, ClinicalScaleCalculator.mapSevenDayCountToScore(2))
        assertEquals(2, ClinicalScaleCalculator.mapSevenDayCountToScore(3))
        assertEquals(2, ClinicalScaleCalculator.mapSevenDayCountToScore(4))
        assertEquals(3, ClinicalScaleCalculator.mapSevenDayCountToScore(5))
        assertEquals(3, ClinicalScaleCalculator.mapSevenDayCountToScore(6))
        assertEquals(4, ClinicalScaleCalculator.mapSevenDayCountToScore(7))
    }

    @Test
    fun `severity classification matches exact clinical boundaries`() {
        // PHQ-9
        assertEquals(Phq9Severity.MINIMAL, ClinicalScaleCalculator.classifyPhq9(0))
        assertEquals(Phq9Severity.MINIMAL, ClinicalScaleCalculator.classifyPhq9(4))
        assertEquals(Phq9Severity.MILD, ClinicalScaleCalculator.classifyPhq9(5))
        assertEquals(Phq9Severity.MILD, ClinicalScaleCalculator.classifyPhq9(9))
        assertEquals(Phq9Severity.MODERATE, ClinicalScaleCalculator.classifyPhq9(10))
        assertEquals(Phq9Severity.MODERATE, ClinicalScaleCalculator.classifyPhq9(14))
        assertEquals(Phq9Severity.MODERATELY_SEVERE, ClinicalScaleCalculator.classifyPhq9(15))
        assertEquals(Phq9Severity.MODERATELY_SEVERE, ClinicalScaleCalculator.classifyPhq9(19))
        assertEquals(Phq9Severity.SEVERE, ClinicalScaleCalculator.classifyPhq9(20))
        assertEquals(Phq9Severity.SEVERE, ClinicalScaleCalculator.classifyPhq9(27))

        // GAD-7
        assertEquals(Gad7Severity.MINIMAL, ClinicalScaleCalculator.classifyGad7(0))
        assertEquals(Gad7Severity.MINIMAL, ClinicalScaleCalculator.classifyGad7(4))
        assertEquals(Gad7Severity.MILD, ClinicalScaleCalculator.classifyGad7(5))
        assertEquals(Gad7Severity.MILD, ClinicalScaleCalculator.classifyGad7(9))
        assertEquals(Gad7Severity.MODERATE, ClinicalScaleCalculator.classifyGad7(10))
        assertEquals(Gad7Severity.MODERATE, ClinicalScaleCalculator.classifyGad7(14))
        assertEquals(Gad7Severity.SEVERE, ClinicalScaleCalculator.classifyGad7(15))
        assertEquals(Gad7Severity.SEVERE, ClinicalScaleCalculator.classifyGad7(21))

        // ASRM
        assertEquals(AsrmSeverity.NORMAL_BASELINE, ClinicalScaleCalculator.classifyAsrm(0))
        assertEquals(AsrmSeverity.NORMAL_BASELINE, ClinicalScaleCalculator.classifyAsrm(5))
        assertEquals(AsrmSeverity.ELEVATED_ACTIVATION, ClinicalScaleCalculator.classifyAsrm(6))
        assertEquals(AsrmSeverity.ELEVATED_ACTIVATION, ClinicalScaleCalculator.classifyAsrm(20))
    }

    @Test
    fun `window boundaries strictly include 14 days for PHQ-9 GAD-7 and 7 days for ASRM`() {
        val entries = listOf(
            // Outside 14-day window (anchor - 14)
            entry(date = anchor.minusDays(14), mood = -4, anxiety = 9, symptoms = DailySymptoms(apathy = true)),
            // First day inside 14-day window (anchor - 13)
            entry(date = anchor.minusDays(13), mood = -2, anxiety = 5, symptoms = DailySymptoms(apathy = true)),
            // Outside 7-day window (anchor - 7)
            entry(date = anchor.minusDays(7), mood = 4, symptoms = DailySymptoms(hyperfocus = true)),
            // First day inside 7-day window (anchor - 6)
            entry(date = anchor.minusDays(6), mood = 2, symptoms = DailySymptoms(hyperfocus = true)),
            // Anchor day
            entry(date = anchor, mood = 0, energy = 5, sleepMinutesOverride = 480),
            // After anchor (should be excluded when anchor is overridden)
            entry(date = anchor.plusDays(1), mood = 4, anxiety = 9, symptoms = DailySymptoms(deathThoughts = true))
        )

        val snapshot = ClinicalScaleCalculator.calculateAutoScales(entries, anchorDateOverride = anchor)!!

        assertEquals(anchor.minusDays(13), snapshot.phq9.windowStart)
        assertEquals(anchor, snapshot.phq9.windowEnd)
        assertEquals(4, snapshot.phq9.filledDaysInWindow)
        // Only anchor-13 had apathy & mood<=-2
        assertEquals(1, snapshot.phq9.itemDayCounts[0])
        assertEquals(1, snapshot.phq9.itemDayCounts[1])
        assertEquals(0, snapshot.phq9.itemDayCounts[8])

        assertEquals(anchor.minusDays(13), snapshot.gad7.windowStart)
        assertEquals(1, snapshot.gad7.itemDayCounts[0]) // anxiety >= 5 only on anchor-13

        assertEquals(anchor.minusDays(6), snapshot.asrm.windowStart)
        assertEquals(2, snapshot.asrm.filledDaysInWindow)
        assertEquals(1, snapshot.asrm.itemDayCounts[0]) // mood >= 2 only on anchor-6
        assertEquals(1, snapshot.asrm.itemDayCounts[4]) // hyperfocus only on anchor-6
    }

    @Test
    fun `PHQ-9 evaluates all 9 criteria including sleep bounds and dual item-8 rule`() {
        val entries = listOf(
            // Day 1: short sleep (5h59m = 359m), energy=3 (triggers item 4, not item 8), mood=-1 (does NOT trigger item 2)
            entry(date = anchor.minusDays(4), mood = -1, energy = 3, sleepMinutesOverride = 359),
            // Day 2: exact 6h (360m -> no sleep trigger), exact 10h (600m -> no sleep trigger on next day), mood=-2
            entry(date = anchor.minusDays(3), mood = -2, energy = 4, sleepMinutesOverride = 360),
            // Day 3: exact 10h (600m -> NOT > 10.0), racingThoughts=true triggers item 8 despite energy=6
            entry(
                date = anchor.minusDays(2),
                mood = 0,
                energy = 6,
                sleepMinutesOverride = 600,
                symptoms = DailySymptoms(racingThoughts = true, bingeEating = true, brainFog = true)
            ),
            // Day 4: oversleep (10h01m = 601m -> triggers item 3), energy=1 (triggers item 4 & item 8)
            entry(
                date = anchor.minusDays(1),
                mood = 0,
                energy = 1,
                sleepMinutesOverride = 601,
                symptoms = DailySymptoms(selfCriticism = true, deathThoughts = true)
            ),
            // Day 5: unrecorded sleep (0.0 -> does NOT trigger item 3), apathy=true
            entry(
                date = anchor,
                mood = 0,
                energy = 5,
                sleepMinutesOverride = null,
                symptoms = DailySymptoms(apathy = true)
            )
        )

        val phq9 = ClinicalScaleCalculator.calculatePhq9(entries, anchor)
        assertEquals(
            listOf(
                1, // 1. apathy (Day 5)
                1, // 2. mood <= -2 (Day 2)
                2, // 3. sleep < 6 or > 10 (Day 1 & Day 4)
                2, // 4. energy 0..3 (Day 1 & Day 4)
                1, // 5. bingeEating (Day 3)
                1, // 6. selfCriticism (Day 4)
                1, // 7. brainFog (Day 3)
                2, // 8. racingThoughts || energy 0..1 (Day 3 & Day 4)
                1 // 9. deathThoughts (Day 4)
            ),
            phq9.itemDayCounts
        )
        // Every item has count in 1..6 -> score 1 each -> total = 9 (MILD)
        assertEquals(List(9) { 1 }, phq9.itemScores)
        assertEquals(9, phq9.totalScore)
        assertEquals(Phq9Severity.MILD, phq9.severity)
    }

    @Test
    fun `GAD-7 evaluates all 7 criteria with exact anxiety and irritability thresholds`() {
        val entries = listOf(
            entry(date = anchor.minusDays(3), anxiety = 4, irritability = 4), // triggers nothing
            entry(date = anchor.minusDays(2), anxiety = 5, irritability = 5), // items 1, 3, 6
            entry(date = anchor.minusDays(1), anxiety = 6, irritability = 2), // items 1, 2, 3, 4
            entry(
                date = anchor,
                anxiety = 7,
                irritability = 0,
                symptoms = DailySymptoms(racingThoughts = true)
            ) // items 1, 2, 3, 4, 5, 7
        )

        val gad7 = ClinicalScaleCalculator.calculateGad7(entries, anchor)
        assertEquals(
            listOf(
                3, // 1. anxiety >= 5
                2, // 2. anxiety >= 6
                3, // 3. anxiety >= 5
                2, // 4. anxiety >= 6
                1, // 5. racingThoughts
                1, // 6. irritability >= 5
                1 // 7. anxiety >= 7
            ),
            gad7.itemDayCounts
        )
        assertEquals(List(7) { 1 }, gad7.itemScores)
        assertEquals(7, gad7.totalScore)
        assertEquals(Gad7Severity.MILD, gad7.severity)
    }

    @Test
    fun `ASRM evaluates all 5 criteria including sleep-energy and racing-mood conjunctions`() {
        val entries = listOf(
            // Day 1: mood=2, sleep=5h + energy=7 -> items 1 & 3
            entry(date = anchor.minusDays(3), mood = 2, energy = 7, sleepMinutesOverride = 300),
            // Day 2: mood=3, sleep=5h + energy=6 (no item 3), racingThoughts + mood>=1 -> items 1, 2, 4
            entry(
                date = anchor.minusDays(2),
                mood = 3,
                energy = 6,
                sleepMinutesOverride = 300,
                symptoms = DailySymptoms(racingThoughts = true)
            ),
            // Day 3: mood=0 + racingThoughts (does NOT trigger item 4), sleep=0 (does NOT trigger item 3), hyperfocus
            entry(
                date = anchor.minusDays(1),
                mood = 0,
                energy = 9,
                sleepMinutesOverride = null,
                symptoms = DailySymptoms(racingThoughts = true, hyperfocus = true)
            ),
            // Day 4: mood=1 + racingThoughts (triggers item 4), sleep=6.0h + energy=9 (does NOT trigger item 3)
            entry(
                date = anchor,
                mood = 1,
                energy = 9,
                sleepMinutesOverride = 360,
                symptoms = DailySymptoms(racingThoughts = true)
            )
        )

        val asrm = ClinicalScaleCalculator.calculateAsrm(entries, anchor)
        assertEquals(
            listOf(
                2, // 1. mood >= 2 (Days 1, 2) -> score 1
                1, // 2. mood >= 3 (Day 2) -> score 1
                1, // 3. 0 < sleep < 6 && energy >= 7 (Day 1) -> score 1
                2, // 4. racingThoughts && mood >= 1 (Days 2, 4) -> score 1
                1 // 5. hyperfocus (Day 3) -> score 1
            ),
            asrm.itemDayCounts
        )
        assertEquals(listOf(1, 1, 1, 1, 1), asrm.itemScores)
        assertEquals(5, asrm.totalScore)
        assertEquals(AsrmSeverity.NORMAL_BASELINE, asrm.severity)
    }

    @Test
    fun `full 14-day maximum symptomatic window hits maximum possible totals`() {
        val severeDepressiveDays = (0L..13L).map { offset ->
            entry(
                date = anchor.minusDays(offset),
                mood = -4,
                energy = 1,
                sleepMinutesOverride = 240,
                anxiety = 8,
                irritability = 8,
                symptoms = DailySymptoms(
                    apathy = true,
                    selfCriticism = true,
                    brainFog = true,
                    bingeEating = true,
                    deathThoughts = true,
                    racingThoughts = true
                )
            )
        }

        val phq9 = ClinicalScaleCalculator.calculatePhq9(severeDepressiveDays, anchor)
        assertEquals(27, phq9.totalScore)
        assertEquals(Phq9Severity.SEVERE, phq9.severity)

        val gad7 = ClinicalScaleCalculator.calculateGad7(severeDepressiveDays, anchor)
        assertEquals(21, gad7.totalScore)
        assertEquals(Gad7Severity.SEVERE, gad7.severity)

        val elevatedWeek = (0L..6L).map { offset ->
            entry(
                date = anchor.minusDays(offset),
                mood = 4,
                energy = 9,
                sleepMinutesOverride = 240,
                symptoms = DailySymptoms(racingThoughts = true, hyperfocus = true)
            )
        }
        val asrm = ClinicalScaleCalculator.calculateAsrm(elevatedWeek, anchor)
        assertEquals(20, asrm.totalScore)
        assertEquals(AsrmSeverity.ELEVATED_ACTIVATION, asrm.severity)
    }

    @Test
    fun `scoreManualAssessment validates counts and bounds`() {
        assertEquals(18, ClinicalScaleCalculator.scoreManualAssessment(ScaleType.PHQ9, List(9) { 2 }))
        assertEquals(14, ClinicalScaleCalculator.scoreManualAssessment(ScaleType.GAD7, List(7) { 2 }))
        assertEquals(15, ClinicalScaleCalculator.scoreManualAssessment(ScaleType.ASRM, List(5) { 3 }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scoreManualAssessment rejects wrong answer count`() {
        ClinicalScaleCalculator.scoreManualAssessment(ScaleType.PHQ9, listOf(1, 2, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scoreManualAssessment rejects out-of-range answer`() {
        ClinicalScaleCalculator.scoreManualAssessment(ScaleType.GAD7, listOf(0, 1, 2, 3, 4, 0, 0))
    }

    private fun entry(
        date: LocalDate,
        mood: Int = 0,
        energy: Int = 5,
        sleepMinutesOverride: Int? = 480,
        anxiety: Int = 0,
        irritability: Int = 0,
        symptoms: DailySymptoms = DailySymptoms(),
        updatedAt: Long = 1L,
        deletedAt: Long? = null
    ): DailyEntry = DailyEntry(
        id = "id-$date-$updatedAt",
        date = date,
        mood = mood,
        energy = energy,
        sleepMinutesOverride = sleepMinutesOverride,
        anxiety = anxiety,
        irritability = irritability,
        symptoms = symptoms,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
}
