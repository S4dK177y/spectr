package app.spectr.mood.data.local.db.repository

import app.spectr.mood.data.local.db.DatabaseTransactionRunner
import app.spectr.mood.data.local.db.EpochClock
import app.spectr.mood.data.local.db.dao.DailyEntryDao
import app.spectr.mood.data.local.db.dao.MomentCheckInDao
import app.spectr.mood.data.local.db.mapper.toDomain
import app.spectr.mood.data.local.db.mapper.toEntity
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.SyncState
import app.spectr.mood.domain.reminder.LocalDataChangeHook
import app.spectr.mood.domain.repository.DiaryRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Offline-first implementation of [DiaryRepository] backed by Room SQLite.
 * Enforces one active [DailyEntry] per calendar date and preserves soft-delete tombstones.
 */
@Singleton
class OfflineDiaryRepository @Inject constructor(
    private val dailyEntryDao: DailyEntryDao,
    private val momentCheckInDao: MomentCheckInDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val clock: EpochClock,
    private val dataChangeHook: LocalDataChangeHook = LocalDataChangeHook.NoOp
) : DiaryRepository {

    override fun observeEntries(): Flow<List<DailyEntry>> =
        dailyEntryDao.observeActiveEntries().map { list -> list.map { it.toDomain() } }

    override fun observeEntryByDate(date: LocalDate): Flow<DailyEntry?> =
        dailyEntryDao.observeActiveByDate(date.toString()).map { it?.toDomain() }

    override fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyEntry>> =
        dailyEntryDao.observeActiveInRange(startDate.toString(), endDate.toString())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveEntries(): List<DailyEntry> = dailyEntryDao.getActiveEntries().map { it.toDomain() }

    override suspend fun getEntryByDate(date: LocalDate): DailyEntry? =
        dailyEntryDao.getActiveByDate(date.toString())?.toDomain()

    override suspend fun saveEntry(entry: DailyEntry): DailyEntry {
        val saved = transactionRunner.runInTransaction {
            val now = clock.nowMillis()
            val existingForDate = dailyEntryDao.getActiveByDate(entry.date.toString())
            val effectiveId = existingForDate?.entry?.id ?: entry.id

            val normalizedIntervals = entry.sleepIntervals
                .filter { it.deletedAt == null }
                .map { interval ->
                    interval.copy(
                        entryId = effectiveId,
                        updatedAt = now,
                        deletedAt = null,
                        syncState = SyncState.PENDING
                    )
                }

            val normalizedEntry = entry.copy(
                id = effectiveId,
                sleepIntervals = normalizedIntervals,
                updatedAt = now,
                deletedAt = null,
                syncState = SyncState.PENDING
            )

            dailyEntryDao.upsertEntry(normalizedEntry.toEntity())

            val previousActiveIds = dailyEntryDao.getActiveIntervalsForEntry(effectiveId).map { it.id }.toSet()
            val incomingIds = normalizedIntervals.map { it.id }.toSet()
            val removedIds = previousActiveIds - incomingIds
            for (removedId in removedIds) {
                dailyEntryDao.softDeleteInterval(
                    id = removedId,
                    deletedAt = now,
                    updatedAt = now,
                    syncState = SyncState.PENDING.name
                )
            }
            if (normalizedIntervals.isNotEmpty()) {
                dailyEntryDao.upsertIntervals(normalizedIntervals.map { it.toEntity() })
            }

            normalizedEntry
        }
        dataChangeHook.onDailyEntryChanged(saved.date)
        return saved
    }

    override suspend fun deleteEntry(id: String) {
        transactionRunner.runInTransaction {
            val now = clock.nowMillis()
            dailyEntryDao.softDeleteIntervalsByEntryId(
                entryId = id,
                deletedAt = now,
                updatedAt = now,
                syncState = SyncState.PENDING.name
            )
            dailyEntryDao.softDeleteEntry(
                id = id,
                deletedAt = now,
                updatedAt = now,
                syncState = SyncState.PENDING.name
            )
        }
        dataChangeHook.onDailyEntryChanged(date = null)
    }

    override fun observeMomentsByDate(date: LocalDate): Flow<List<MomentCheckIn>> =
        momentCheckInDao.observeActiveByDate(date.toString()).map { list -> list.map { it.toDomain() } }

    override fun observeMomentsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MomentCheckIn>> =
        momentCheckInDao.observeActiveInRange(startDate.toString(), endDate.toString())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveMoments(): List<MomentCheckIn> =
        momentCheckInDao.getActiveMoments().map { it.toDomain() }

    override suspend fun saveMoment(moment: MomentCheckIn): MomentCheckIn {
        val now = clock.nowMillis()
        val normalized = moment.copy(
            updatedAt = now,
            deletedAt = null,
            syncState = SyncState.PENDING
        )
        momentCheckInDao.upsert(normalized.toEntity())
        dataChangeHook.onMomentChanged(normalized.entryDate)
        return normalized
    }

    override suspend fun deleteMoment(id: String) {
        val now = clock.nowMillis()
        momentCheckInDao.softDelete(
            id = id,
            deletedAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING.name
        )
        dataChangeHook.onMomentChanged(date = null)
    }
}
