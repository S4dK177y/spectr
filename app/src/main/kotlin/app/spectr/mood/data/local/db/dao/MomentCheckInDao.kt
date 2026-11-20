package app.spectr.mood.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.spectr.mood.data.local.db.entity.MomentCheckInEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for `moment_check_ins`.
 */
@Dao
interface MomentCheckInDao {

    @Query(
        "SELECT * FROM moment_check_ins " +
            "WHERE entryDate = :entryDate AND deletedAt IS NULL " +
            "ORDER BY timestampEpochMs ASC"
    )
    fun observeActiveByDate(entryDate: String): Flow<List<MomentCheckInEntity>>

    @Query(
        "SELECT * FROM moment_check_ins " +
            "WHERE deletedAt IS NULL AND entryDate BETWEEN :startDate AND :endDate " +
            "ORDER BY timestampEpochMs ASC"
    )
    fun observeActiveInRange(startDate: String, endDate: String): Flow<List<MomentCheckInEntity>>

    @Query("SELECT * FROM moment_check_ins WHERE deletedAt IS NULL ORDER BY timestampEpochMs ASC")
    suspend fun getActiveMoments(): List<MomentCheckInEntity>

    @Query("SELECT * FROM moment_check_ins WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MomentCheckInEntity?

    @Query("SELECT * FROM moment_check_ins WHERE syncState = 'PENDING'")
    suspend fun getPendingSync(): List<MomentCheckInEntity>

    @Upsert
    suspend fun upsert(moment: MomentCheckInEntity)

    @Upsert
    suspend fun upsertAll(moments: List<MomentCheckInEntity>)

    @Query(
        "UPDATE moment_check_ins " +
            "SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncState = :syncState " +
            "WHERE id = :id"
    )
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long, syncState: String = "PENDING")

    @Query("UPDATE moment_check_ins SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM moment_check_ins")
    suspend fun deleteAll()
}
