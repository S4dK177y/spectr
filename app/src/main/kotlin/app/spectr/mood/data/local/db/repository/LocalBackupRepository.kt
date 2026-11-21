package app.spectr.mood.data.local.db.repository

import app.spectr.mood.data.local.db.DatabaseTransactionRunner
import app.spectr.mood.data.local.db.EpochClock
import app.spectr.mood.data.local.db.dao.DailyEntryDao
import app.spectr.mood.data.local.db.dao.MedicationRecordDao
import app.spectr.mood.data.local.db.dao.MomentCheckInDao
import app.spectr.mood.data.local.db.dao.ScaleAssessmentDao
import app.spectr.mood.data.local.db.mapper.toDomain
import app.spectr.mood.data.local.db.mapper.toEntity
import app.spectr.mood.domain.backup.BackupImportMode
import app.spectr.mood.domain.backup.BackupImportSummary
import app.spectr.mood.domain.backup.SpectrBackupCodec
import app.spectr.mood.domain.backup.SpectrBackupData
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MedicationRecord
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.SyncState
import app.spectr.mood.domain.reminder.LocalDataChangeHook
import app.spectr.mood.domain.repository.BackupRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Room-backed JSON v1 export/import manager supporting both MERGE (LWW) and REPLACE modes.
 */
@Singleton
class LocalBackupRepository @Inject constructor(
    private val dailyEntryDao: DailyEntryDao,
    private val momentCheckInDao: MomentCheckInDao,
    private val scaleAssessmentDao: ScaleAssessmentDao,
    private val medicationRecordDao: MedicationRecordDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val clock: EpochClock,
    private val dataChangeHook: LocalDataChangeHook = LocalDataChangeHook.NoOp
) : BackupRepository {

    override suspend fun exportJson(exportedAtEpochMs: Long): String {
        val data = SpectrBackupData(
            exportedAtEpochMs = exportedAtEpochMs,
            dailyEntries = dailyEntryDao.getActiveEntries().map { it.toDomain() },
            momentCheckIns = momentCheckInDao.getActiveMoments().map { it.toDomain() },
            scaleAssessments = scaleAssessmentDao.getActiveAssessments().map { it.toDomain() },
            medicationRecords = medicationRecordDao.getActiveRecords().map { it.toDomain() }
        )
        return SpectrBackupCodec.encode(data)
    }

    override suspend fun importJson(rawJson: String, mode: BackupImportMode): BackupImportSummary {
        val backup = SpectrBackupCodec.decode(rawJson)
        val summary = transactionRunner.runInTransaction {
            when (mode) {
                BackupImportMode.REPLACE -> replaceAll(backup)
                BackupImportMode.MERGE -> mergeAll(backup)
            }
        }
        dataChangeHook.onBulkDataImported()
        return summary
    }

    private suspend fun replaceAll(backup: SpectrBackupData): BackupImportSummary {
        val now = clock.nowMillis()
        dailyEntryDao.deleteAllIntervals()
        dailyEntryDao.deleteAllEntries()
        momentCheckInDao.deleteAll()
        scaleAssessmentDao.deleteAll()
        medicationRecordDao.deleteAll()

        var intervalCount = 0
        for (entry in backup.dailyEntries) {
            val stamped = stampEntry(entry, now)
            dailyEntryDao.upsertEntry(stamped.toEntity())
            if (stamped.sleepIntervals.isNotEmpty()) {
                dailyEntryDao.upsertIntervals(stamped.sleepIntervals.map { it.toEntity() })
                intervalCount += stamped.sleepIntervals.size
            }
        }

        val moments = backup.momentCheckIns.map { stampMoment(it, now).toEntity() }
        if (moments.isNotEmpty()) momentCheckInDao.upsertAll(moments)

        val scales = backup.scaleAssessments.map { stampScale(it, now).toEntity() }
        if (scales.isNotEmpty()) scaleAssessmentDao.upsertAll(scales)

        val meds = backup.medicationRecords.map { stampMedication(it, now).toEntity() }
        if (meds.isNotEmpty()) medicationRecordDao.upsertAll(meds)

        return BackupImportSummary(
            dailyEntriesImported = backup.dailyEntries.size,
            sleepIntervalsImported = intervalCount,
            momentsImported = moments.size,
            scalesImported = scales.size,
            medicationsImported = meds.size
        )
    }

    private suspend fun mergeAll(backup: SpectrBackupData): BackupImportSummary {
        val now = clock.nowMillis()
        val entryStats = mergeDailyEntries(backup.dailyEntries, now)
        val momentsImported = mergeMoments(backup.momentCheckIns, now)
        val scalesImported = mergeScales(backup.scaleAssessments, now)
        val medsImported = mergeMedications(backup.medicationRecords, now)

        return BackupImportSummary(
            dailyEntriesImported = entryStats.imported,
            dailyEntriesUpdated = entryStats.updated,
            dailyEntriesSkipped = entryStats.skipped,
            sleepIntervalsImported = entryStats.intervals,
            momentsImported = momentsImported,
            scalesImported = scalesImported,
            medicationsImported = medsImported
        )
    }

    private suspend fun mergeDailyEntries(entries: List<DailyEntry>, now: Long): EntryMergeStats {
        var imported = 0
        var updated = 0
        var skipped = 0
        var intervalsImported = 0

        for (rawEntry in entries) {
            val stamped = stampEntry(rawEntry, now)
            val existingById = dailyEntryDao.getById(stamped.id)
            val existingByDate = dailyEntryDao.getActiveByDate(stamped.date.toString())
            val existing = existingById ?: existingByDate

            if (existing == null) {
                dailyEntryDao.upsertEntry(stamped.toEntity())
                if (stamped.sleepIntervals.isNotEmpty()) {
                    dailyEntryDao.upsertIntervals(stamped.sleepIntervals.map { it.toEntity() })
                    intervalsImported += stamped.sleepIntervals.size
                }
                imported++
            } else if (stamped.updatedAt > existing.entry.updatedAt) {
                val targetId = existing.entry.id
                val aligned = stamped.copy(
                    id = targetId,
                    sleepIntervals = stamped.sleepIntervals.map { it.copy(entryId = targetId) }
                )
                dailyEntryDao.upsertEntry(aligned.toEntity())
                reconcileIntervalsForMergedEntry(targetId, aligned, now)
                intervalsImported += aligned.sleepIntervals.size
                updated++
            } else {
                skipped++
            }
        }
        return EntryMergeStats(imported, updated, skipped, intervalsImported)
    }

    private suspend fun reconcileIntervalsForMergedEntry(entryId: String, entry: DailyEntry, now: Long) {
        val previousActiveIds = dailyEntryDao.getActiveIntervalsForEntry(entryId).map { it.id }.toSet()
        val incomingIds = entry.sleepIntervals.map { it.id }.toSet()
        for (removedId in previousActiveIds - incomingIds) {
            dailyEntryDao.softDeleteInterval(
                id = removedId,
                deletedAt = now,
                updatedAt = now,
                syncState = SyncState.PENDING.name
            )
        }
        if (entry.sleepIntervals.isNotEmpty()) {
            dailyEntryDao.upsertIntervals(entry.sleepIntervals.map { it.toEntity() })
        }
    }

    private suspend fun mergeMoments(moments: List<MomentCheckIn>, now: Long): Int {
        var count = 0
        for (moment in moments) {
            val stamped = stampMoment(moment, now)
            val existing = momentCheckInDao.getById(stamped.id)
            if (existing == null || stamped.updatedAt > existing.updatedAt) {
                momentCheckInDao.upsert(stamped.toEntity())
                count++
            }
        }
        return count
    }

    private suspend fun mergeScales(scales: List<ScaleAssessment>, now: Long): Int {
        var count = 0
        for (scale in scales) {
            val stamped = stampScale(scale, now)
            val existing = scaleAssessmentDao.getById(stamped.id)
            if (existing == null || stamped.updatedAt > existing.updatedAt) {
                scaleAssessmentDao.upsert(stamped.toEntity())
                count++
            }
        }
        return count
    }

    private suspend fun mergeMedications(records: List<MedicationRecord>, now: Long): Int {
        var count = 0
        for (record in records) {
            val stamped = stampMedication(record, now)
            val existing = medicationRecordDao.getById(stamped.id)
            if (existing == null || stamped.updatedAt > existing.updatedAt) {
                medicationRecordDao.upsert(stamped.toEntity())
                count++
            }
        }
        return count
    }

    private fun stampEntry(entry: DailyEntry, fallbackNow: Long): DailyEntry {
        val effectiveUpdatedAt = if (entry.updatedAt > 0L) entry.updatedAt else fallbackNow
        val stampedIntervals = entry.sleepIntervals.map { interval ->
            interval.copy(
                entryId = entry.id,
                updatedAt = if (interval.updatedAt > 0L) interval.updatedAt else effectiveUpdatedAt,
                syncState = SyncState.PENDING
            )
        }
        return entry.copy(
            sleepIntervals = stampedIntervals,
            updatedAt = effectiveUpdatedAt,
            syncState = SyncState.PENDING
        )
    }

    private fun stampMoment(moment: MomentCheckIn, fallbackNow: Long): MomentCheckIn = moment.copy(
        updatedAt = if (moment.updatedAt > 0L) moment.updatedAt else fallbackNow,
        syncState = SyncState.PENDING
    )

    private fun stampScale(scale: ScaleAssessment, fallbackNow: Long): ScaleAssessment = scale.copy(
        updatedAt = if (scale.updatedAt > 0L) scale.updatedAt else fallbackNow,
        syncState = SyncState.PENDING
    )

    private fun stampMedication(record: MedicationRecord, fallbackNow: Long): MedicationRecord = record.copy(
        updatedAt = if (record.updatedAt > 0L) record.updatedAt else fallbackNow,
        syncState = SyncState.PENDING
    )

    private data class EntryMergeStats(val imported: Int, val updated: Int, val skipped: Int, val intervals: Int)
}
