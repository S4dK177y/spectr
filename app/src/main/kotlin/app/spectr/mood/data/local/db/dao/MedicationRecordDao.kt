package app.spectr.mood.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.spectr.mood.data.local.db.entity.MedicationRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for reserved `medication_records` table.
 */
@Dao
interface MedicationRecordDao {

    @Query(
        "SELECT * FROM medication_records " +
            "WHERE entryDate = :entryDate AND deletedAt IS NULL " +
            "ORDER BY takenAtEpochMs ASC"
    )
    fun observeActiveByDate(entryDate: String): Flow<List<MedicationRecordEntity>>

    @Query("SELECT * FROM medication_records WHERE deletedAt IS NULL ORDER BY entryDate DESC")
    suspend fun getActiveRecords(): List<MedicationRecordEntity>

    @Query("SELECT * FROM medication_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MedicationRecordEntity?

    @Query("SELECT * FROM medication_records WHERE syncState = 'PENDING'")
    suspend fun getPendingSync(): List<MedicationRecordEntity>

    @Upsert
    suspend fun upsert(record: MedicationRecordEntity)

    @Upsert
    suspend fun upsertAll(records: List<MedicationRecordEntity>)

    @Query(
        "UPDATE medication_records " +
            "SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncState = :syncState " +
            "WHERE id = :id"
    )
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long, syncState: String = "PENDING")

    @Query("UPDATE medication_records SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM medication_records")
    suspend fun deleteAll()
}
