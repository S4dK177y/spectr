package app.spectr.mood.data.local.db.repository

import app.spectr.mood.data.local.db.DatabaseTransactionRunner
import app.spectr.mood.data.local.db.EpochClock
import app.spectr.mood.data.local.db.dao.DailyEntryDao
import app.spectr.mood.data.local.db.dao.MedicationRecordDao
import app.spectr.mood.data.local.db.dao.MomentCheckInDao
import app.spectr.mood.data.local.db.dao.ScaleAssessmentDao
import app.spectr.mood.data.local.db.entity.DailyEntryEntity
import app.spectr.mood.data.local.db.entity.DailyEntryWithSleep
import app.spectr.mood.data.local.db.entity.MedicationRecordEntity
import app.spectr.mood.data.local.db.entity.MomentCheckInEntity
import app.spectr.mood.data.local.db.entity.ScaleAssessmentEntity
import app.spectr.mood.data.local.db.entity.SleepIntervalEntity
import app.spectr.mood.domain.backup.BackupImportMode
import app.spectr.mood.domain.backup.SpectrBackupCodec
import app.spectr.mood.domain.backup.SpectrBackupData
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.domain.model.SleepInterval
import app.spectr.mood.domain.model.SyncState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OfflineRepositoryAndBackupTest {

    private lateinit var dailyDao: FakeDailyEntryDao
    private lateinit var momentDao: FakeMomentCheckInDao
    private lateinit var scaleDao: FakeScaleAssessmentDao
    private lateinit var medDao: FakeMedicationRecordDao
    private var nowMillis = 1_000L

    private lateinit var diaryRepository: OfflineDiaryRepository
    private lateinit var scaleRepository: OfflineScaleAssessmentRepository
    private lateinit var backupRepository: LocalBackupRepository

    @Before
    fun setUp() {
        dailyDao = FakeDailyEntryDao()
        momentDao = FakeMomentCheckInDao()
        scaleDao = FakeScaleAssessmentDao()
        medDao = FakeMedicationRecordDao()
        nowMillis = 1_000L

        val txRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        val clock = EpochClock { nowMillis }

        diaryRepository = OfflineDiaryRepository(dailyDao, momentDao, txRunner, clock)
        scaleRepository = OfflineScaleAssessmentRepository(scaleDao, clock)
        backupRepository = LocalBackupRepository(dailyDao, momentDao, scaleDao, medDao, txRunner, clock)
    }

    @Test
    fun `saving entry for same date reuses active ID and soft-deletes removed sleep intervals`() = runTest {
        val date = LocalDate.of(2026, 11, 20)
        val saved1 = diaryRepository.saveEntry(
            DailyEntry(
                id = "entry-original",
                date = date,
                mood = -1,
                energy = 5,
                sleepIntervals = listOf(
                    SleepInterval(
                        id = "sleep-1",
                        entryId = "entry-original",
                        startMinuteOfDay = 1320,
                        endMinuteOfDay = 90
                    ),
                    SleepInterval(
                        id = "sleep-2",
                        entryId = "entry-original",
                        startMinuteOfDay = 540,
                        endMinuteOfDay = 720
                    )
                )
            )
        )

        nowMillis = 2_000L
        val saved2 = diaryRepository.saveEntry(
            DailyEntry(
                id = "entry-new-different-id",
                date = date,
                mood = 1,
                energy = 7,
                sleepIntervals = listOf(
                    SleepInterval(
                        id = "sleep-1",
                        entryId = "entry-new-different-id",
                        startMinuteOfDay = 1380,
                        endMinuteOfDay = 420
                    )
                )
            )
        )

        assertEquals(saved1.id, saved2.id)
        assertEquals(1, diaryRepository.getActiveEntries().size)
        assertEquals(1, diaryRepository.getEntryByDate(date)!!.sleepIntervals.size)
        assertEquals(SyncState.PENDING, saved2.syncState)

        // Removed interval sleep-2 must be soft-deleted as a tombstone, not lost
        val tombstone = dailyDao.intervals["sleep-2"]
        assertNotNull(tombstone)
        assertEquals(2_000L, tombstone!!.deletedAt)
        assertEquals("PENDING", tombstone.syncState)
    }

    @Test
    fun `deleteEntry soft-deletes both entry and all child sleep intervals`() = runTest {
        val date = LocalDate.of(2026, 11, 20)
        val saved = diaryRepository.saveEntry(
            DailyEntry(
                id = "e-del",
                date = date,
                mood = 0,
                energy = 5,
                sleepIntervals = listOf(
                    SleepInterval(id = "s-del", entryId = "e-del", startMinuteOfDay = 1380, endMinuteOfDay = 420)
                )
            )
        )

        nowMillis = 5_000L
        diaryRepository.deleteEntry(saved.id)

        assertNull(diaryRepository.getEntryByDate(date))
        assertEquals(5_000L, dailyDao.entries[saved.id]?.deletedAt)
        assertEquals(5_000L, dailyDao.intervals["s-del"]?.deletedAt)
    }

    @Test
    fun `moments and scale assessments save observe and soft-delete cleanly`() = runTest {
        val date = LocalDate.of(2026, 11, 20)
        val moment = diaryRepository.saveMoment(
            MomentCheckIn(id = "m1", entryDate = date, timestampEpochMs = 1_500L, mood = 2, note = "Хорошо")
        )
        assertEquals(1, diaryRepository.observeMomentsByDate(date).first().size)

        nowMillis = 3_000L
        diaryRepository.deleteMoment(moment.id)
        assertEquals(0, diaryRepository.observeMomentsByDate(date).first().size)
        assertEquals(3_000L, momentDao.moments["m1"]?.deletedAt)

        val scale = scaleRepository.saveAssessment(
            ScaleAssessment(
                id = "sc1",
                scaleType = ScaleType.GAD7,
                date = date,
                answers = listOf(1, 1, 1, 1, 0, 1, 0),
                totalScore = 5
            )
        )
        assertEquals(1, scaleRepository.observeAssessmentsByType(ScaleType.GAD7).first().size)
        assertEquals(0, scaleRepository.observeAssessmentsByType(ScaleType.PHQ9).first().size)

        nowMillis = 4_000L
        scaleRepository.deleteAssessment(scale.id)
        assertEquals(0, scaleRepository.getActiveAssessments().size)
    }

    @Test
    fun `backup export and MERGE import respect Last-Write-Wins and date deduplication`() = runTest {
        val date1 = LocalDate.of(2026, 11, 18)
        val date2 = LocalDate.of(2026, 11, 19)

        nowMillis = 2_000L
        diaryRepository.saveEntry(DailyEntry(id = "local-1", date = date1, mood = 0, energy = 5))
        nowMillis = 5_000L
        diaryRepository.saveEntry(DailyEntry(id = "local-2", date = date2, mood = 2, energy = 7))

        val incomingJson = SpectrBackupCodec.encode(
            SpectrBackupData(
                exportedAtEpochMs = 6_000L,
                dailyEntries = listOf(
                    // Newer than local-1 (updatedAt 3000 > 2000) -> should update local-1
                    DailyEntry(id = "remote-1", date = date1, mood = -2, energy = 3, updatedAt = 3_000L),
                    // Older than local-2 (updatedAt 4000 < 5000) -> should be skipped
                    DailyEntry(id = "remote-2", date = date2, mood = -4, energy = 2, updatedAt = 4_000L),
                    // Brand new date -> should be imported
                    DailyEntry(
                        id = "remote-3",
                        date = LocalDate.of(2026, 11, 20),
                        mood = 1,
                        energy = 6,
                        updatedAt = 6_000L
                    )
                ),
                momentCheckIns = listOf(
                    MomentCheckIn(
                        id = "m-imp",
                        entryDate = date1,
                        timestampEpochMs = 2_500L,
                        mood = -1,
                        updatedAt = 2_500L
                    )
                ),
                scaleAssessments = listOf(
                    ScaleAssessment(
                        id = "s-imp",
                        scaleType = ScaleType.ASRM,
                        date = date1,
                        answers = listOf(1, 0, 0, 0, 1),
                        totalScore = 2,
                        updatedAt = 2_600L
                    )
                ),
                medicationRecords = emptyList()
            )
        )

        val summary = backupRepository.importJson(incomingJson, BackupImportMode.MERGE)
        assertEquals(1, summary.dailyEntriesImported)
        assertEquals(1, summary.dailyEntriesUpdated)
        assertEquals(1, summary.dailyEntriesSkipped)
        assertEquals(1, summary.momentsImported)
        assertEquals(1, summary.scalesImported)

        assertEquals(-2, diaryRepository.getEntryByDate(date1)?.mood)
        assertEquals(2, diaryRepository.getEntryByDate(date2)?.mood)
        assertEquals(3, diaryRepository.getActiveEntries().size)
    }

    @Test
    fun `backup REPLACE import clears old records and restores exported state`() = runTest {
        nowMillis = 1_000L
        diaryRepository.saveEntry(DailyEntry(id = "old", date = LocalDate.of(2026, 11, 1), mood = -3, energy = 2))

        val exported = backupRepository.exportJson(exportedAtEpochMs = 1_500L)

        nowMillis = 2_000L
        diaryRepository.saveEntry(DailyEntry(id = "extra", date = LocalDate.of(2026, 11, 2), mood = 3, energy = 9))
        assertEquals(2, diaryRepository.getActiveEntries().size)

        val summary = backupRepository.importJson(exported, BackupImportMode.REPLACE)
        assertEquals(1, summary.dailyEntriesImported)
        assertEquals(1, diaryRepository.getActiveEntries().size)
        assertEquals(LocalDate.of(2026, 11, 1), diaryRepository.getActiveEntries().single().date)
    }
}

private class FakeDailyEntryDao : DailyEntryDao {
    val entries = LinkedHashMap<String, DailyEntryEntity>()
    val intervals = LinkedHashMap<String, SleepIntervalEntity>()
    private val state = MutableStateFlow(0)

    private fun notifyChanged() {
        state.value += 1
    }

    private fun toWithSleep(entity: DailyEntryEntity): DailyEntryWithSleep = DailyEntryWithSleep(
        entry = entity,
        sleepIntervals = intervals.values.filter { it.entryId == entity.id }
    )

    override fun observeActiveEntries(): Flow<List<DailyEntryWithSleep>> = state.map {
        entries.values.filter { it.deletedAt == null }.sortedByDescending { it.date }.map(::toWithSleep)
    }

    override fun observeActiveByDate(date: String): Flow<DailyEntryWithSleep?> = state.map {
        entries.values.firstOrNull { it.date == date && it.deletedAt == null }?.let(::toWithSleep)
    }

    override fun observeActiveInRange(startDate: String, endDate: String): Flow<List<DailyEntryWithSleep>> = state.map {
        entries.values
            .filter { it.deletedAt == null && it.date in startDate..endDate }
            .sortedBy { it.date }
            .map(::toWithSleep)
    }

    override suspend fun getActiveEntries(): List<DailyEntryWithSleep> =
        entries.values.filter { it.deletedAt == null }.sortedByDescending { it.date }.map(::toWithSleep)

    override suspend fun getActiveByDate(date: String): DailyEntryWithSleep? =
        entries.values.firstOrNull { it.date == date && it.deletedAt == null }?.let(::toWithSleep)

    override suspend fun getById(id: String): DailyEntryWithSleep? = entries[id]?.let(::toWithSleep)

    override suspend fun getPendingSyncEntries(): List<DailyEntryEntity> =
        entries.values.filter { it.syncState == "PENDING" }

    override suspend fun getPendingSyncIntervals(): List<SleepIntervalEntity> =
        intervals.values.filter { it.syncState == "PENDING" }

    override suspend fun getActiveIntervalsForEntry(entryId: String): List<SleepIntervalEntity> =
        intervals.values.filter { it.entryId == entryId && it.deletedAt == null }

    override suspend fun upsertEntry(entry: DailyEntryEntity) {
        entries[entry.id] = entry
        notifyChanged()
    }

    override suspend fun upsertIntervals(intervals: List<SleepIntervalEntity>) {
        intervals.forEach { this.intervals[it.id] = it }
        notifyChanged()
    }

    override suspend fun softDeleteEntry(id: String, deletedAt: Long, updatedAt: Long, syncState: String) {
        entries[id]?.let { entries[id] = it.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncState = syncState) }
        notifyChanged()
    }

    override suspend fun softDeleteIntervalsByEntryId(
        entryId: String,
        deletedAt: Long,
        updatedAt: Long,
        syncState: String
    ) {
        intervals.values.filter { it.entryId == entryId && it.deletedAt == null }.forEach {
            intervals[it.id] = it.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncState = syncState)
        }
        notifyChanged()
    }

    override suspend fun softDeleteInterval(id: String, deletedAt: Long, updatedAt: Long, syncState: String) {
        intervals[id]?.let {
            intervals[id] = it.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncState = syncState)
        }
        notifyChanged()
    }

    override suspend fun markEntrySynced(id: String, updatedAt: Long) {
        entries[id]?.takeIf { it.updatedAt == updatedAt }?.let { entries[id] = it.copy(syncState = "SYNCED") }
    }

    override suspend fun markIntervalSynced(id: String, updatedAt: Long) {
        intervals[id]?.takeIf { it.updatedAt == updatedAt }?.let { intervals[id] = it.copy(syncState = "SYNCED") }
    }

    override suspend fun deleteAllIntervals() {
        intervals.clear()
        notifyChanged()
    }

    override suspend fun deleteAllEntries() {
        entries.clear()
        notifyChanged()
    }
}

private class FakeMomentCheckInDao : MomentCheckInDao {
    val moments = LinkedHashMap<String, MomentCheckInEntity>()
    private val state = MutableStateFlow(0)

    override fun observeActiveByDate(entryDate: String): Flow<List<MomentCheckInEntity>> = state.map {
        moments.values.filter { it.entryDate == entryDate && it.deletedAt == null }.sortedBy { it.timestampEpochMs }
    }

    override fun observeActiveInRange(startDate: String, endDate: String): Flow<List<MomentCheckInEntity>> = state.map {
        moments.values
            .filter { it.deletedAt == null && it.entryDate in startDate..endDate }
            .sortedBy { it.timestampEpochMs }
    }

    override suspend fun getActiveMoments(): List<MomentCheckInEntity> =
        moments.values.filter { it.deletedAt == null }.sortedBy { it.timestampEpochMs }

    override suspend fun getById(id: String): MomentCheckInEntity? = moments[id]

    override suspend fun getPendingSync(): List<MomentCheckInEntity> =
        moments.values.filter { it.syncState == "PENDING" }

    override suspend fun upsert(moment: MomentCheckInEntity) {
        moments[moment.id] = moment
        state.value += 1
    }

    override suspend fun upsertAll(moments: List<MomentCheckInEntity>) {
        moments.forEach { this.moments[it.id] = it }
        state.value += 1
    }

    override suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long, syncState: String) {
        moments[id]?.let { moments[id] = it.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncState = syncState) }
        state.value += 1
    }

    override suspend fun markSynced(id: String, updatedAt: Long) {
        moments[id]?.takeIf { it.updatedAt == updatedAt }?.let { moments[id] = it.copy(syncState = "SYNCED") }
    }

    override suspend fun deleteAll() {
        moments.clear()
        state.value += 1
    }
}

private class FakeScaleAssessmentDao : ScaleAssessmentDao {
    val assessments = LinkedHashMap<String, ScaleAssessmentEntity>()
    private val state = MutableStateFlow(0)

    override fun observeActiveAll(): Flow<List<ScaleAssessmentEntity>> = state.map {
        assessments.values.filter { it.deletedAt == null }.sortedByDescending { it.date }
    }

    override fun observeActiveByType(scaleType: String): Flow<List<ScaleAssessmentEntity>> = state.map {
        assessments.values.filter { it.scaleType == scaleType && it.deletedAt == null }.sortedByDescending { it.date }
    }

    override suspend fun getActiveAssessments(): List<ScaleAssessmentEntity> =
        assessments.values.filter { it.deletedAt == null }.sortedByDescending { it.date }

    override suspend fun getById(id: String): ScaleAssessmentEntity? = assessments[id]

    override suspend fun getPendingSync(): List<ScaleAssessmentEntity> =
        assessments.values.filter { it.syncState == "PENDING" }

    override suspend fun upsert(assessment: ScaleAssessmentEntity) {
        assessments[assessment.id] = assessment
        state.value += 1
    }

    override suspend fun upsertAll(assessments: List<ScaleAssessmentEntity>) {
        assessments.forEach { this.assessments[it.id] = it }
        state.value += 1
    }

    override suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long, syncState: String) {
        assessments[id]?.let {
            assessments[id] = it.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncState = syncState)
        }
        state.value += 1
    }

    override suspend fun markSynced(id: String, updatedAt: Long) {
        assessments[id]?.takeIf { it.updatedAt == updatedAt }?.let {
            assessments[id] = it.copy(syncState = "SYNCED")
        }
    }

    override suspend fun deleteAll() {
        assessments.clear()
        state.value += 1
    }
}

private class FakeMedicationRecordDao : MedicationRecordDao {
    val records = LinkedHashMap<String, MedicationRecordEntity>()
    private val state = MutableStateFlow(0)

    override fun observeActiveByDate(entryDate: String): Flow<List<MedicationRecordEntity>> = state.map {
        records.values.filter { it.entryDate == entryDate && it.deletedAt == null }
    }

    override suspend fun getActiveRecords(): List<MedicationRecordEntity> =
        records.values.filter { it.deletedAt == null }

    override suspend fun getById(id: String): MedicationRecordEntity? = records[id]

    override suspend fun getPendingSync(): List<MedicationRecordEntity> =
        records.values.filter { it.syncState == "PENDING" }

    override suspend fun upsert(record: MedicationRecordEntity) {
        records[record.id] = record
        state.value += 1
    }

    override suspend fun upsertAll(records: List<MedicationRecordEntity>) {
        records.forEach { this.records[it.id] = it }
        state.value += 1
    }

    override suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long, syncState: String) {
        records[id]?.let { records[id] = it.copy(deletedAt = deletedAt, updatedAt = updatedAt, syncState = syncState) }
        state.value += 1
    }

    override suspend fun markSynced(id: String, updatedAt: Long) {
        records[id]?.takeIf { it.updatedAt == updatedAt }?.let { records[id] = it.copy(syncState = "SYNCED") }
    }

    override suspend fun deleteAll() {
        records.clear()
        state.value += 1
    }
}
