package app.spectr.mood.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.spectr.mood.data.local.db.entity.ScaleAssessmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for `scale_assessments`.
 */
@Dao
interface ScaleAssessmentDao {

    @Query("SELECT * FROM scale_assessments WHERE deletedAt IS NULL ORDER BY date DESC, updatedAt DESC")
    fun observeActiveAll(): Flow<List<ScaleAssessmentEntity>>

    @Query(
        "SELECT * FROM scale_assessments " +
            "WHERE scaleType = :scaleType AND deletedAt IS NULL " +
            "ORDER BY date DESC, updatedAt DESC"
    )
    fun observeActiveByType(scaleType: String): Flow<List<ScaleAssessmentEntity>>

    @Query("SELECT * FROM scale_assessments WHERE deletedAt IS NULL ORDER BY date DESC, updatedAt DESC")
    suspend fun getActiveAssessments(): List<ScaleAssessmentEntity>

    @Query("SELECT * FROM scale_assessments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScaleAssessmentEntity?

    @Query("SELECT * FROM scale_assessments WHERE syncState = 'PENDING'")
    suspend fun getPendingSync(): List<ScaleAssessmentEntity>

    @Upsert
    suspend fun upsert(assessment: ScaleAssessmentEntity)

    @Upsert
    suspend fun upsertAll(assessments: List<ScaleAssessmentEntity>)

    @Query(
        "UPDATE scale_assessments " +
            "SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncState = :syncState " +
            "WHERE id = :id"
    )
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long, syncState: String = "PENDING")

    @Query("UPDATE scale_assessments SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM scale_assessments")
    suspend fun deleteAll()
}
