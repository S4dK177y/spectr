package app.spectr.mood.domain.calculator

import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MedicationStatus
import java.time.LocalDate

/**
 * Time window selector for Overview charts, KPI summary, and PDF export.
 */
sealed interface AnalyticsPeriod {
    data class LastDays(val days: Int) : AnalyticsPeriod {
        init {
            require(days >= 1) { "days must be >= 1, got $days" }
        }
    }

    data object AllTime : AnalyticsPeriod

    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : AnalyticsPeriod {
        init {
            require(!endDate.isBefore(startDate)) {
                "endDate ($endDate) must not be before startDate ($startDate)"
            }
        }
    }

    companion object {
        val LAST_14_DAYS = LastDays(14)
        val LAST_30_DAYS = LastDays(30)
        val LAST_90_DAYS = LastDays(90)
    }
}

/**
 * Aggregated symptom and behavioral markers counted across a period.
 */
data class KeyMarkersSummary(
    val deathThoughtsDays: Int = 0,
    val bingeEatingDays: Int = 0,
    val hyperfocusDays: Int = 0,
    val racingThoughtsDays: Int = 0,
    val apathyDays: Int = 0,
    val selfCriticismDays: Int = 0,
    val brainFogDays: Int = 0,
    val alcoholDays: Int = 0,
    val alcoholStressDays: Int = 0,
    val medicationSideEffectsDays: Int = 0,
    val medicationMissedDays: Int = 0
)

/**
 * Complete KPI summary (14 core metrics + key markers) for the Overview tab and PDF report.
 */
data class DiaryAnalyticsSummary(
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val filledDays: Int = 0,
    val averageMood: Double? = null,
    val minMood: Int? = null,
    val maxMood: Int? = null,
    val lowMoodDays: Int = 0,
    val neutralMoodDays: Int = 0,
    val elevatedMoodDays: Int = 0,
    val averageEnergy: Double? = null,
    val averageSleepHours: Double? = null,
    val sleepTrackedDays: Int = 0,
    val averageAnxiety: Double? = null,
    val averageIrritability: Double? = null,
    val markedLowDays: Int = 0,
    val markedHighDays: Int = 0,
    val mixedDays: Int = 0,
    val shortSleepDays: Int = 0,
    val intraDaySwingsDays: Int = 0,
    val highAnxietyDays: Int = 0,
    val highIrritabilityDays: Int = 0,
    val highAnxietyOrIrritabilityDays: Int = 0,
    val markers: KeyMarkersSummary = KeyMarkersSummary()
) {
    val moodSpan: Int?
        get() = if (minMood != null && maxMood != null) maxMood - minMood else null
}

/**
 * Pure-Kotlin analytics engine computing mixed-day flags, period filters,
 * all 14 KPI metrics, and key behavioral/symptom markers.
 */
object AnalyticsCalculator {

    private const val SHORT_SLEEP_THRESHOLD_HOURS = 6.0
    private const val HIGH_ANXIETY_OR_IRRITABILITY_THRESHOLD = 7
    private const val MIXED_ENERGY_THRESHOLD = 7
    private const val MIXED_IRRITABILITY_THRESHOLD = 7

    /**
     * Exact mixed-day rule from `docs/ARCHITECTURE.md`:
     * `(mood <= -1) && (energy >= 7 || irritability >= 7 || racingThoughts)`.
     */
    fun isMixedDay(entry: DailyEntry): Boolean = entry.mood <= -1 &&
        (
            entry.energy >= MIXED_ENERGY_THRESHOLD ||
                entry.irritability >= MIXED_IRRITABILITY_THRESHOLD ||
                entry.symptoms.racingThoughts
            )

    /**
     * Filters active entries for [period], deduplicating by calendar date (latest [DailyEntry.updatedAt] wins)
     * and returning results sorted chronologically (ascending by date).
     */
    fun filterEntriesForPeriod(
        entries: List<DailyEntry>,
        period: AnalyticsPeriod,
        anchorDate: LocalDate? = null
    ): List<DailyEntry> {
        val activeByDate = LinkedHashMap<LocalDate, DailyEntry>()
        for (entry in entries) {
            if (entry.deletedAt != null) continue
            val existing = activeByDate[entry.date]
            if (existing == null || entry.updatedAt >= existing.updatedAt) {
                activeByDate[entry.date] = entry
            }
        }
        val activeSorted = activeByDate.values.sortedBy { it.date }
        if (activeSorted.isEmpty()) {
            return emptyList()
        }

        return when (period) {
            AnalyticsPeriod.AllTime -> activeSorted
            is AnalyticsPeriod.LastDays -> {
                val endDate = anchorDate ?: activeSorted.last().date
                val startDate = endDate.minusDays((period.days - 1).toLong())
                activeSorted.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
            }

            is AnalyticsPeriod.Custom -> {
                activeSorted.filter {
                    !it.date.isBefore(period.startDate) && !it.date.isAfter(period.endDate)
                }
            }
        }
    }

    /**
     * Computes the full [DiaryAnalyticsSummary] for [entries] within [period].
     */
    fun calculateSummary(
        entries: List<DailyEntry>,
        period: AnalyticsPeriod = AnalyticsPeriod.AllTime,
        anchorDate: LocalDate? = null
    ): DiaryAnalyticsSummary {
        val filtered = filterEntriesForPeriod(entries, period, anchorDate)
        if (filtered.isEmpty()) {
            return DiaryAnalyticsSummary()
        }

        val sleepTrackedEntries = filtered.filter { SleepDurationCalculator.hasSleepData(it) }
        val sleepHoursList = sleepTrackedEntries.map { SleepDurationCalculator.effectiveSleepHours(it) }

        return DiaryAnalyticsSummary(
            periodStart = filtered.first().date,
            periodEnd = filtered.last().date,
            filledDays = filtered.size,
            averageMood = filtered.map { it.mood }.average(),
            minMood = filtered.minOf { it.mood },
            maxMood = filtered.maxOf { it.mood },
            lowMoodDays = filtered.count { it.mood <= -1 },
            neutralMoodDays = filtered.count { it.mood == 0 },
            elevatedMoodDays = filtered.count { it.mood >= 1 },
            averageEnergy = filtered.map { it.energy }.average(),
            averageSleepHours = sleepHoursList.takeIf { it.isNotEmpty() }?.average(),
            sleepTrackedDays = sleepTrackedEntries.size,
            averageAnxiety = filtered.map { it.anxiety }.average(),
            averageIrritability = filtered.map { it.irritability }.average(),
            markedLowDays = filtered.count { it.mood <= -2 },
            markedHighDays = filtered.count { it.mood >= 2 },
            mixedDays = filtered.count { isMixedDay(it) },
            shortSleepDays = sleepHoursList.count { it < SHORT_SLEEP_THRESHOLD_HOURS },
            intraDaySwingsDays = filtered.count { it.dailyDynamics == DailyDynamics.SWINGS },
            highAnxietyDays = filtered.count { it.anxiety >= HIGH_ANXIETY_OR_IRRITABILITY_THRESHOLD },
            highIrritabilityDays = filtered.count { it.irritability >= HIGH_ANXIETY_OR_IRRITABILITY_THRESHOLD },
            highAnxietyOrIrritabilityDays = filtered.count {
                it.anxiety >= HIGH_ANXIETY_OR_IRRITABILITY_THRESHOLD ||
                    it.irritability >= HIGH_ANXIETY_OR_IRRITABILITY_THRESHOLD
            },
            markers = computeKeyMarkers(filtered)
        )
    }

    private fun computeKeyMarkers(entries: List<DailyEntry>): KeyMarkersSummary = KeyMarkersSummary(
        deathThoughtsDays = entries.count { it.symptoms.deathThoughts },
        bingeEatingDays = entries.count { it.symptoms.bingeEating },
        hyperfocusDays = entries.count { it.symptoms.hyperfocus },
        racingThoughtsDays = entries.count { it.symptoms.racingThoughts },
        apathyDays = entries.count { it.symptoms.apathy },
        selfCriticismDays = entries.count { it.symptoms.selfCriticism },
        brainFogDays = entries.count { it.symptoms.brainFog },
        alcoholDays = entries.count { it.alcoholStatus != AlcoholStatus.NONE },
        alcoholStressDays = entries.count { it.alcoholStatus == AlcoholStatus.STRESS_COPING },
        medicationSideEffectsDays = entries.count {
            it.medicationStatus == MedicationStatus.TAKEN_WITH_SIDE_EFFECTS ||
                it.medicationStatus == MedicationStatus.MISSED_SIDE_EFFECTS
        },
        medicationMissedDays = entries.count {
            it.medicationStatus == MedicationStatus.MISSED_FORGOT ||
                it.medicationStatus == MedicationStatus.MISSED_SIDE_EFFECTS
        }
    )
}
