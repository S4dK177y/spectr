package app.spectr.mood.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reserved Room entity for `medication_records` (future detailed medication tracker).
 */
@Entity(
    tableName = "medication_records",
    indices = [
        Index(value = ["entryDate"]),
        Index(value = ["updatedAt"]),
        Index(value = ["syncState"])
    ]
)
data class MedicationRecordEntity(
    @PrimaryKey
    val id: String,
    val entryDate: String,
    val medicationName: String,
    val dosage: String = "",
    val takenAtEpochMs: Long? = null,
    val status: String = "TAKEN_AS_PRESCRIBED",
    val sideEffectsNote: String = "",
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String = "PENDING"
)
