package app.spectr.mood.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Embedded symptom flags stored as flat columns inside `daily_entries`.
 */
data class DailySymptomsEmbed(
    val apathy: Boolean = false,
    val selfCriticism: Boolean = false,
    val brainFog: Boolean = false,
    val bingeEating: Boolean = false,
    val deathThoughts: Boolean = false,
    val hyperfocus: Boolean = false,
    val racingThoughts: Boolean = false
)

/**
 * Room entity for the `daily_entries` table (1 active entry per calendar `date`).
 */
@Entity(
    tableName = "daily_entries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["updatedAt"]),
        Index(value = ["syncState"])
    ]
)
data class DailyEntryEntity(
    @PrimaryKey
    val id: String,
    val date: String,
    val mood: Int,
    val dailyDynamics: String,
    val energy: Int,
    val sleepMinutesOverride: Int? = null,
    val sleepNote: String = "",
    val anxiety: Int = 0,
    val irritability: Int = 0,
    @Embedded
    val symptoms: DailySymptomsEmbed = DailySymptomsEmbed(),
    val alcoholStatus: String = "NONE",
    val medicationStatus: String = "NOT_PRESCRIBED",
    val note: String = "",
    val stepsCount: Int? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String = "PENDING"
)

/**
 * Room relation pairing a daily entry with its child sleep intervals.
 */
data class DailyEntryWithSleep(
    @Embedded
    val entry: DailyEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val sleepIntervals: List<SleepIntervalEntity>
)
