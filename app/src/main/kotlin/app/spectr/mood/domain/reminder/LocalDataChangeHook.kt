package app.spectr.mood.domain.reminder

import java.time.LocalDate

/**
 * Pure Kotlin hook invoked after local diary, moment, scale, or backup mutations.
 * Platform implementations keep alarms, notifications, and home-screen widgets in sync.
 */
interface LocalDataChangeHook {
    suspend fun onDailyEntryChanged(date: LocalDate?) = Unit
    suspend fun onMomentChanged(date: LocalDate?) = Unit
    suspend fun onScaleAssessmentChanged() = Unit
    suspend fun onBulkDataImported() = Unit

    object NoOp : LocalDataChangeHook
}
