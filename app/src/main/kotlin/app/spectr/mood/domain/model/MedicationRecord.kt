package app.spectr.mood.domain.model

import java.time.LocalDate
import java.util.UUID

/**
 * Reserved model for future detailed medication intake tracking (post-v1.0).
 */
data class MedicationRecord(
    val id: String = UUID.randomUUID().toString(),
    val entryDate: LocalDate,
    val medicationName: String,
    val dosage: String = "",
    val takenAtEpochMs: Long? = null,
    val status: MedicationStatus = MedicationStatus.TAKEN_AS_PRESCRIBED,
    val sideEffectsNote: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING
)
