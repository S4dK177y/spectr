package app.spectr.mood.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `moment_check_ins` table (0..N quick timestamped entries per day).
 */
@Entity(
    tableName = "moment_check_ins",
    indices = [
        Index(value = ["entryDate"]),
        Index(value = ["timestampEpochMs"]),
        Index(value = ["updatedAt"]),
        Index(value = ["syncState"])
    ]
)
data class MomentCheckInEntity(
    @PrimaryKey
    val id: String,
    val entryDate: String,
    val timestampEpochMs: Long,
    val mood: Int,
    val energy: Int? = null,
    val anxiety: Int? = null,
    val irritability: Int? = null,
    val note: String = "",
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String = "PENDING"
)
