package app.spectr.mood.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Universal Room entity for `scale_assessments` supporting PHQ-9, GAD-7, ASRM,
 * and future questionnaires without schema migrations.
 */
@Entity(
    tableName = "scale_assessments",
    indices = [
        Index(value = ["scaleType"]),
        Index(value = ["date"]),
        Index(value = ["updatedAt"]),
        Index(value = ["syncState"])
    ]
)
data class ScaleAssessmentEntity(
    @PrimaryKey
    val id: String,
    val scaleType: String,
    val date: String,
    val answersJson: String,
    val totalScore: Int,
    val isConfirmed: Boolean = true,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String = "PENDING"
)
