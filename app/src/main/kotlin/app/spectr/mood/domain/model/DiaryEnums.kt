package app.spectr.mood.domain.model

/**
 * Local synchronization state for offline-first Room records.
 */
enum class SyncState {
    PENDING,
    SYNCED
}

/**
 * Intra-day mood dynamics reported in the daily summary.
 */
enum class DailyDynamics {
    STEADY,
    WORSE_MORNING,
    WORSE_EVENING,
    WORSE_NIGHT,
    SWINGS
}

/**
 * Alcohol intake context for a given day.
 */
enum class AlcoholStatus {
    NONE,
    LIGHT_OCCASION,
    HEAVY_OCCASION,
    STRESS_COPING
}

/**
 * Psychotropic medication adherence status for a given day.
 */
enum class MedicationStatus {
    NOT_PRESCRIBED,
    TAKEN_AS_PRESCRIBED,
    TAKEN_WITH_SIDE_EFFECTS,
    MISSED_FORGOT,
    MISSED_SIDE_EFFECTS
}

/**
 * Origin of a recorded sleep interval.
 */
enum class SleepSource {
    MANUAL,
    HEALTH_CONNECT
}

/**
 * Supported clinical screening scales stored in the universal assessments table.
 */
enum class ScaleType {
    PHQ9,
    GAD7,
    ASRM
}
