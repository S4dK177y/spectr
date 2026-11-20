package app.spectr.mood.domain.calculator

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.ScaleType
import java.time.LocalDate

/**
 * Neutral clinical severity band for PHQ-9 (0..27).
 */
enum class Phq9Severity {
    MINIMAL,
    MILD,
    MODERATE,
    MODERATELY_SEVERE,
    SEVERE
}

/**
 * Neutral clinical severity band for GAD-7 (0..21).
 */
enum class Gad7Severity {
    MINIMAL,
    MILD,
    MODERATE,
    SEVERE
}

/**
 * Neutral clinical severity band for ASRM (0..20).
 */
enum class AsrmSeverity {
    NORMAL_BASELINE,
    ELEVATED_ACTIVATION
}

/**
 * Detailed auto-calculated result for a single clinical scale over its rolling calendar window.
 */
data class AutoScaleResult<S : Enum<S>>(
    val scaleType: ScaleType,
    val anchorDate: LocalDate,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val windowSizeDays: Int,
    val filledDaysInWindow: Int,
    val itemDayCounts: List<Int>,
    val itemScores: List<Int>,
    val totalScore: Int,
    val severity: S
)

/**
 * Combined snapshot of all three diary-derived auto-scales anchored to [anchorDate].
 */
data class AutoScalesSnapshot(
    val anchorDate: LocalDate,
    val phq9: AutoScaleResult<Phq9Severity>,
    val gad7: AutoScaleResult<Gad7Severity>,
    val asrm: AutoScaleResult<AsrmSeverity>
)

/**
 * Pure-Kotlin calculator for PHQ-9, GAD-7, and ASRM screening scales.
 *
 * Implements exact formulas from `docs/ARCHITECTURE.md`:
 * - Anchor date = latest filled active diary entry date (unless overridden).
 * - Windows: 14 calendar days `[anchorDate - 13 .. anchorDate]` for PHQ-9/GAD-7,
 *   7 calendar days `[anchorDate - 6 .. anchorDate]` for ASRM.
 * - Score mapping:
 *   - PHQ-9 / GAD-7 (0..3): `0 -> 0`, `1..6 -> 1`, `7..11 -> 2`, `12..14 -> 3`.
 *   - ASRM (0..4): `0 -> 0`, `1..2 -> 1`, `3..4 -> 2`, `5..6 -> 3`, `7 -> 4`.
 */
object ClinicalScaleCalculator {

    const val PHQ9_WINDOW_DAYS = 14
    const val GAD7_WINDOW_DAYS = 14
    const val ASRM_WINDOW_DAYS = 7

    const val PHQ9_ITEM_COUNT = 9
    const val GAD7_ITEM_COUNT = 7
    const val ASRM_ITEM_COUNT = 5

    const val PHQ9_MAX_ITEM_SCORE = 3
    const val GAD7_MAX_ITEM_SCORE = 3
    const val ASRM_MAX_ITEM_SCORE = 4

    private const val SHORT_SLEEP_THRESHOLD_HOURS = 6.0
    private const val LONG_SLEEP_THRESHOLD_HOURS = 10.0

    /**
     * Computes all three auto-scales from [entries].
     * Returns `null` when there are no active entries and no [anchorDateOverride] is supplied.
     */
    fun calculateAutoScales(entries: List<DailyEntry>, anchorDateOverride: LocalDate? = null): AutoScalesSnapshot? {
        val activeByDate = deduplicateActiveByDate(entries)
        val anchorDate = anchorDateOverride ?: activeByDate.keys.maxOrNull() ?: return null
        val normalizedEntries = activeByDate.values.toList()
        return AutoScalesSnapshot(
            anchorDate = anchorDate,
            phq9 = calculatePhq9(normalizedEntries, anchorDate),
            gad7 = calculateGad7(normalizedEntries, anchorDate),
            asrm = calculateAsrm(normalizedEntries, anchorDate)
        )
    }

    /**
     * Calculates PHQ-9 (9 items, 0..27) over the 14-day window ending at [anchorDate].
     */
    fun calculatePhq9(entries: List<DailyEntry>, anchorDate: LocalDate): AutoScaleResult<Phq9Severity> {
        val windowStart = anchorDate.minusDays((PHQ9_WINDOW_DAYS - 1).toLong())
        val windowEntries = selectWindowEntries(entries, windowStart, anchorDate)

        val itemCounts = listOf(
            windowEntries.count { it.symptoms.apathy },
            windowEntries.count { it.mood <= -2 },
            windowEntries.count { entry ->
                val sleepHours = SleepDurationCalculator.effectiveSleepHours(entry)
                (sleepHours > 0.0 && sleepHours < SHORT_SLEEP_THRESHOLD_HOURS) ||
                    sleepHours > LONG_SLEEP_THRESHOLD_HOURS
            },
            windowEntries.count { it.energy in 0..3 },
            windowEntries.count { it.symptoms.bingeEating },
            windowEntries.count { it.symptoms.selfCriticism },
            windowEntries.count { it.symptoms.brainFog },
            windowEntries.count { it.symptoms.racingThoughts || it.energy in 0..1 },
            windowEntries.count { it.symptoms.deathThoughts }
        )

        val itemScores = itemCounts.map { mapFourteenDayCountToScore(it) }
        val totalScore = itemScores.sum()
        return AutoScaleResult(
            scaleType = ScaleType.PHQ9,
            anchorDate = anchorDate,
            windowStart = windowStart,
            windowEnd = anchorDate,
            windowSizeDays = PHQ9_WINDOW_DAYS,
            filledDaysInWindow = windowEntries.size,
            itemDayCounts = itemCounts,
            itemScores = itemScores,
            totalScore = totalScore,
            severity = classifyPhq9(totalScore)
        )
    }

    /**
     * Calculates GAD-7 (7 items, 0..21) over the 14-day window ending at [anchorDate].
     */
    fun calculateGad7(entries: List<DailyEntry>, anchorDate: LocalDate): AutoScaleResult<Gad7Severity> {
        val windowStart = anchorDate.minusDays((GAD7_WINDOW_DAYS - 1).toLong())
        val windowEntries = selectWindowEntries(entries, windowStart, anchorDate)

        val itemCounts = listOf(
            windowEntries.count { it.anxiety >= 5 },
            windowEntries.count { it.anxiety >= 6 },
            windowEntries.count { it.anxiety >= 5 },
            windowEntries.count { it.anxiety >= 6 },
            windowEntries.count { it.symptoms.racingThoughts },
            windowEntries.count { it.irritability >= 5 },
            windowEntries.count { it.anxiety >= 7 }
        )

        val itemScores = itemCounts.map { mapFourteenDayCountToScore(it) }
        val totalScore = itemScores.sum()
        return AutoScaleResult(
            scaleType = ScaleType.GAD7,
            anchorDate = anchorDate,
            windowStart = windowStart,
            windowEnd = anchorDate,
            windowSizeDays = GAD7_WINDOW_DAYS,
            filledDaysInWindow = windowEntries.size,
            itemDayCounts = itemCounts,
            itemScores = itemScores,
            totalScore = totalScore,
            severity = classifyGad7(totalScore)
        )
    }

    /**
     * Calculates ASRM (5 items, 0..20) over the 7-day window ending at [anchorDate].
     */
    fun calculateAsrm(entries: List<DailyEntry>, anchorDate: LocalDate): AutoScaleResult<AsrmSeverity> {
        val windowStart = anchorDate.minusDays((ASRM_WINDOW_DAYS - 1).toLong())
        val windowEntries = selectWindowEntries(entries, windowStart, anchorDate)

        val itemCounts = listOf(
            windowEntries.count { it.mood >= 2 },
            windowEntries.count { it.mood >= 3 },
            windowEntries.count { entry ->
                val sleepHours = SleepDurationCalculator.effectiveSleepHours(entry)
                sleepHours > 0.0 && sleepHours < SHORT_SLEEP_THRESHOLD_HOURS && entry.energy >= 7
            },
            windowEntries.count { it.symptoms.racingThoughts && it.mood >= 1 },
            windowEntries.count { it.symptoms.hyperfocus }
        )

        val itemScores = itemCounts.map { mapSevenDayCountToScore(it) }
        val totalScore = itemScores.sum()
        return AutoScaleResult(
            scaleType = ScaleType.ASRM,
            anchorDate = anchorDate,
            windowStart = windowStart,
            windowEnd = anchorDate,
            windowSizeDays = ASRM_WINDOW_DAYS,
            filledDaysInWindow = windowEntries.size,
            itemDayCounts = itemCounts,
            itemScores = itemScores,
            totalScore = totalScore,
            severity = classifyAsrm(totalScore)
        )
    }

    /**
     * Maps symptom day count in a 14-day window to a 0..3 score (PHQ-9 / GAD-7).
     */
    fun mapFourteenDayCountToScore(daysCount: Int): Int = when {
        daysCount <= 0 -> 0
        daysCount <= 6 -> 1
        daysCount <= 11 -> 2
        else -> 3
    }

    /**
     * Maps symptom day count in a 7-day window to a 0..4 score (ASRM).
     */
    fun mapSevenDayCountToScore(daysCount: Int): Int = when {
        daysCount <= 0 -> 0
        daysCount <= 2 -> 1
        daysCount <= 4 -> 2
        daysCount <= 6 -> 3
        else -> 4
    }

    /**
     * Classifies total PHQ-9 score (0..27).
     */
    fun classifyPhq9(totalScore: Int): Phq9Severity = when {
        totalScore <= 4 -> Phq9Severity.MINIMAL
        totalScore <= 9 -> Phq9Severity.MILD
        totalScore <= 14 -> Phq9Severity.MODERATE
        totalScore <= 19 -> Phq9Severity.MODERATELY_SEVERE
        else -> Phq9Severity.SEVERE
    }

    /**
     * Classifies total GAD-7 score (0..21).
     */
    fun classifyGad7(totalScore: Int): Gad7Severity = when {
        totalScore <= 4 -> Gad7Severity.MINIMAL
        totalScore <= 9 -> Gad7Severity.MILD
        totalScore <= 14 -> Gad7Severity.MODERATE
        else -> Gad7Severity.SEVERE
    }

    /**
     * Classifies total ASRM score (0..20).
     */
    fun classifyAsrm(totalScore: Int): AsrmSeverity =
        if (totalScore < 6) AsrmSeverity.NORMAL_BASELINE else AsrmSeverity.ELEVATED_ACTIVATION

    /**
     * Validates and sums manually confirmed item answers for [scaleType].
     */
    fun scoreManualAssessment(scaleType: ScaleType, answers: List<Int>): Int {
        val (expectedCount, maxItemScore) = when (scaleType) {
            ScaleType.PHQ9 -> PHQ9_ITEM_COUNT to PHQ9_MAX_ITEM_SCORE
            ScaleType.GAD7 -> GAD7_ITEM_COUNT to GAD7_MAX_ITEM_SCORE
            ScaleType.ASRM -> ASRM_ITEM_COUNT to ASRM_MAX_ITEM_SCORE
        }
        require(answers.size == expectedCount) {
            "$scaleType requires $expectedCount answers, got ${answers.size}"
        }
        answers.forEachIndexed { index, score ->
            require(score in 0..maxItemScore) {
                "$scaleType answer[$index] must be in 0..$maxItemScore, got $score"
            }
        }
        return answers.sum()
    }

    private fun deduplicateActiveByDate(entries: List<DailyEntry>): Map<LocalDate, DailyEntry> {
        val byDate = LinkedHashMap<LocalDate, DailyEntry>()
        for (entry in entries) {
            if (entry.deletedAt != null) continue
            val existing = byDate[entry.date]
            if (existing == null || entry.updatedAt >= existing.updatedAt) {
                byDate[entry.date] = entry
            }
        }
        return byDate
    }

    private fun selectWindowEntries(
        entries: List<DailyEntry>,
        windowStart: LocalDate,
        windowEnd: LocalDate
    ): List<DailyEntry> = deduplicateActiveByDate(entries)
        .values
        .filter { !it.date.isBefore(windowStart) && !it.date.isAfter(windowEnd) }
}
