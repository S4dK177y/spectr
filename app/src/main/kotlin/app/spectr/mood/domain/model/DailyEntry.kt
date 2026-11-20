package app.spectr.mood.domain.model

import java.time.LocalDate
import java.util.UUID

/**
 * Seven core binary symptom markers tracked in the daily summary.
 */
data class DailySymptoms(
    val apathy: Boolean = false,
    val selfCriticism: Boolean = false,
    val brainFog: Boolean = false,
    val bingeEating: Boolean = false,
    val deathThoughts: Boolean = false,
    val hyperfocus: Boolean = false,
    val racingThoughts: Boolean = false
)

/**
 * Single sleep interval supporting polyphasic sleep and midnight crossover.
 *
 * [startMinuteOfDay] and [endMinuteOfDay] are in 0..1439 (minutes since 00:00).
 */
data class SleepInterval(
    val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val awakeDeductionMin: Int = 0,
    val source: SleepSource = SleepSource.MANUAL,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING
) {
    init {
        require(startMinuteOfDay in 0 until MINUTES_PER_DAY) {
            "startMinuteOfDay must be in 0..1439, got $startMinuteOfDay"
        }
        require(endMinuteOfDay in 0 until MINUTES_PER_DAY) {
            "endMinuteOfDay must be in 0..1439, got $endMinuteOfDay"
        }
        require(awakeDeductionMin >= 0) {
            "awakeDeductionMin must be >= 0, got $awakeDeductionMin"
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 1440
    }
}

/**
 * One active daily summary entry per calendar [date].
 */
data class DailyEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val mood: Int,
    val energy: Int,
    val dailyDynamics: DailyDynamics = DailyDynamics.STEADY,
    val sleepIntervals: List<SleepInterval> = emptyList(),
    val sleepMinutesOverride: Int? = null,
    val sleepNote: String = "",
    val anxiety: Int = 0,
    val irritability: Int = 0,
    val symptoms: DailySymptoms = DailySymptoms(),
    val alcoholStatus: AlcoholStatus = AlcoholStatus.NONE,
    val medicationStatus: MedicationStatus = MedicationStatus.NOT_PRESCRIBED,
    val note: String = "",
    val stepsCount: Int? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING
) {
    init {
        require(mood in MIN_MOOD..MAX_MOOD) { "mood must be in $MIN_MOOD..$MAX_MOOD, got $mood" }
        require(energy in MIN_RATING..MAX_RATING) { "energy must be in $MIN_RATING..$MAX_RATING, got $energy" }
        require(anxiety in MIN_RATING..MAX_RATING) { "anxiety must be in $MIN_RATING..$MAX_RATING, got $anxiety" }
        require(irritability in MIN_RATING..MAX_RATING) {
            "irritability must be in $MIN_RATING..$MAX_RATING, got $irritability"
        }
        require(sleepMinutesOverride == null || sleepMinutesOverride >= 0) {
            "sleepMinutesOverride must be >= 0, got $sleepMinutesOverride"
        }
        require(stepsCount == null || stepsCount >= 0) {
            "stepsCount must be >= 0, got $stepsCount"
        }
    }

    companion object {
        const val MIN_MOOD = -5
        const val MAX_MOOD = 5
        const val MIN_RATING = 0
        const val MAX_RATING = 10
    }
}
