package app.spectr.mood.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `sleep_intervals` table linked to [DailyEntryEntity].
 */
@Entity(
    tableName = "sleep_intervals",
    foreignKeys = [
        ForeignKey(
            entity = DailyEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entryId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["syncState"])
    ]
)
data class SleepIntervalEntity(
    @PrimaryKey
    val id: String,
    val entryId: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val awakeDeductionMin: Int = 0,
    val source: String = "MANUAL",
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String = "PENDING"
)
