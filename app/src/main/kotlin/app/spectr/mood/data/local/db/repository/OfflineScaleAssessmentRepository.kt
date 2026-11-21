package app.spectr.mood.data.local.db.repository

import app.spectr.mood.data.local.db.EpochClock
import app.spectr.mood.data.local.db.dao.ScaleAssessmentDao
import app.spectr.mood.data.local.db.mapper.toDomain
import app.spectr.mood.data.local.db.mapper.toEntity
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.domain.model.SyncState
import app.spectr.mood.domain.reminder.LocalDataChangeHook
import app.spectr.mood.domain.repository.ScaleAssessmentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Offline-first Room repository for saved PHQ-9, GAD-7, and ASRM assessments.
 */
@Singleton
class OfflineScaleAssessmentRepository @Inject constructor(
    private val scaleAssessmentDao: ScaleAssessmentDao,
    private val clock: EpochClock,
    private val dataChangeHook: LocalDataChangeHook = LocalDataChangeHook.NoOp
) : ScaleAssessmentRepository {

    override fun observeAssessments(): Flow<List<ScaleAssessment>> =
        scaleAssessmentDao.observeActiveAll().map { list -> list.map { it.toDomain() } }

    override fun observeAssessmentsByType(type: ScaleType): Flow<List<ScaleAssessment>> =
        scaleAssessmentDao.observeActiveByType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveAssessments(): List<ScaleAssessment> =
        scaleAssessmentDao.getActiveAssessments().map { it.toDomain() }

    override suspend fun saveAssessment(assessment: ScaleAssessment): ScaleAssessment {
        val now = clock.nowMillis()
        val normalized = assessment.copy(
            updatedAt = now,
            deletedAt = null,
            syncState = SyncState.PENDING
        )
        scaleAssessmentDao.upsert(normalized.toEntity())
        dataChangeHook.onScaleAssessmentChanged()
        return normalized
    }

    override suspend fun deleteAssessment(id: String) {
        val now = clock.nowMillis()
        scaleAssessmentDao.softDelete(
            id = id,
            deletedAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING.name
        )
        dataChangeHook.onScaleAssessmentChanged()
    }
}
