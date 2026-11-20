package app.spectr.mood.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.spectr.mood.data.local.db.entity.DailyEntryEntity
import app.spectr.mood.data.local.db.entity.DailyEntryWithSleep
import app.spectr.mood.data.local.db.entity.SleepIntervalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for `daily_entries` and child `sleep_intervals`.
 */
@Dao
interface DailyEntryDao {

    @Transaction
    @Query("SELECT * FROM daily_entries WHERE deletedAt IS NULL ORDER BY date DESC")
    fun observeActiveEntries(): Flow<List<DailyEntryWithSleep>>

    @Transaction
    @Query("SELECT * FROM daily_entries WHERE date = :date AND deletedAt IS NULL LIMIT 1")
    fun observeActiveByDate(date: String): Flow<DailyEntryWithSleep?>

    @Transaction
    @Query(
        "SELECT * FROM daily_entries " +
            "WHERE deletedAt IS NULL AND date BETWEEN :startDate AND :endDate " +
            "ORDER BY date ASC"
    )
    fun observeActiveInRange(startDate: String, endDate: String): Flow<List<DailyEntryWithSleep>>

    @Transaction
    @Query("SELECT * FROM daily_entries WHERE deletedAt IS NULL ORDER BY date DESC")
    suspend fun getActiveEntries(): List<DailyEntryWithSleep>

    @Transaction
    @Query("SELECT * FROM daily_entries WHERE date = :date AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveByDate(date: String): DailyEntryWithSleep?

    @Transaction
    @Query("SELECT * FROM daily_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DailyEntryWithSleep?

    @Query("SELECT * FROM daily_entries WHERE syncState = 'PENDING'")
    suspend fun getPendingSyncEntries(): List<DailyEntryEntity>

    @Query("SELECT * FROM sleep_intervals WHERE syncState = 'PENDING'")
    suspend fun getPendingSyncIntervals(): List<SleepIntervalEntity>

    @Query("SELECT * FROM sleep_intervals WHERE entryId = :entryId AND deletedAt IS NULL")
    suspend fun getActiveIntervalsForEntry(entryId: String): List<SleepIntervalEntity>

    @Upsert
    suspend fun upsertEntry(entry: DailyEntryEntity)

    @Upsert
    suspend fun upsertIntervals(intervals: List<SleepIntervalEntity>)

    @Query(
        "UPDATE daily_entries " +
            "SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncState = :syncState " +
            "WHERE id = :id"
    )
    suspend fun softDeleteEntry(id: String, deletedAt: Long, updatedAt: Long, syncState: String = "PENDING")

    @Query(
        "UPDATE sleep_intervals " +
            "SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncState = :syncState " +
            "WHERE entryId = :entryId AND deletedAt IS NULL"
    )
    suspend fun softDeleteIntervalsByEntryId(
        entryId: String,
        deletedAt: Long,
        updatedAt: Long,
        syncState: String = "PENDING"
    )

    @Query(
        "UPDATE sleep_intervals " +
            "SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncState = :syncState " +
            "WHERE id = :id"
    )
    suspend fun softDeleteInterval(id: String, deletedAt: Long, updatedAt: Long, syncState: String = "PENDING")

    @Query("UPDATE daily_entries SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markEntrySynced(id: String, updatedAt: Long)

    @Query("UPDATE sleep_intervals SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markIntervalSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM sleep_intervals")
    suspend fun deleteAllIntervals()

    @Query("DELETE FROM daily_entries")
    suspend fun deleteAllEntries()
}
