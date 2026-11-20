package app.spectr.mood.domain.backup

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MedicationRecord
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import kotlinx.serialization.Serializable

/**
 * Thrown when a JSON v1 backup payload cannot be decoded or fails schema/range validation.
 */
class BackupCodecException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Strategy used when importing a JSON v1 backup into local storage.
 */
enum class BackupImportMode {
    /**
     * Merges imported records into existing local data using Last-Write-Wins on `updatedAt`
     * and deduplicating daily entries by calendar date.
     */
    MERGE,

    /**
     * Replaces all local records with the imported backup inside a single database transaction.
     */
    REPLACE
}

/**
 * Result statistics returned after importing a JSON v1 backup.
 */
data class BackupImportSummary(
    val dailyEntriesImported: Int = 0,
    val dailyEntriesUpdated: Int = 0,
    val dailyEntriesSkipped: Int = 0,
    val sleepIntervalsImported: Int = 0,
    val momentsImported: Int = 0,
    val scalesImported: Int = 0,
    val medicationsImported: Int = 0
)

/**
 * Decoded and validated domain bundle extracted from a JSON v1 backup.
 */
data class SpectrBackupData(
    val exportedAtEpochMs: Long,
    val dailyEntries: List<DailyEntry>,
    val momentCheckIns: List<MomentCheckIn>,
    val scaleAssessments: List<ScaleAssessment>,
    val medicationRecords: List<MedicationRecord>
)

@Serializable
data class BackupSymptoms(
    val apathy: Boolean = false,
    val selfCriticism: Boolean = false,
    val brainFog: Boolean = false,
    val bingeEating: Boolean = false,
    val deathThoughts: Boolean = false,
    val hyperfocus: Boolean = false,
    val racingThoughts: Boolean = false
)

@Serializable
data class BackupSleepInterval(
    val id: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val awakeDeductionMin: Int = 0,
    val source: String = "MANUAL",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Serializable
data class BackupDailyEntry(
    val id: String,
    val date: String,
    val mood: Int,
    val energy: Int,
    val dailyDynamics: String = "STEADY",
    val sleepIntervals: List<BackupSleepInterval> = emptyList(),
    val sleepMinutesOverride: Int? = null,
    val sleepNote: String = "",
    val anxiety: Int = 0,
    val irritability: Int = 0,
    val symptoms: BackupSymptoms = BackupSymptoms(),
    val alcoholStatus: String = "NONE",
    val medicationStatus: String = "NOT_PRESCRIBED",
    val note: String = "",
    val stepsCount: Int? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Serializable
data class BackupMomentCheckIn(
    val id: String,
    val entryDate: String,
    val timestampEpochMs: Long,
    val mood: Int,
    val energy: Int? = null,
    val anxiety: Int? = null,
    val irritability: Int? = null,
    val note: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Serializable
data class BackupScaleAssessment(
    val id: String,
    val scaleType: String,
    val date: String,
    val answers: List<Int>,
    val totalScore: Int,
    val isConfirmed: Boolean = true,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Serializable
data class BackupMedicationRecord(
    val id: String,
    val entryDate: String,
    val medicationName: String,
    val dosage: String = "",
    val takenAtEpochMs: Long? = null,
    val status: String = "TAKEN_AS_PRESCRIBED",
    val sideEffectsNote: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Serializable
data class SpectrBackupV1(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val app: String = APP_IDENTIFIER,
    val exportedAtEpochMs: Long,
    val dailyEntries: List<BackupDailyEntry> = emptyList(),
    val momentCheckIns: List<BackupMomentCheckIn> = emptyList(),
    val scaleAssessments: List<BackupScaleAssessment> = emptyList(),
    val medicationRecords: List<BackupMedicationRecord> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val APP_IDENTIFIER = "spectr"
    }
}
