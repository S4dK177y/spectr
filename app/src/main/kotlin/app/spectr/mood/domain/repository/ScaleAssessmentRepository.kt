package app.spectr.mood.domain.repository

import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first repository contract for clinical scale assessments (PHQ-9, GAD-7, ASRM).
 */
interface ScaleAssessmentRepository {
    fun observeAssessments(): Flow<List<ScaleAssessment>>
    fun observeAssessmentsByType(type: ScaleType): Flow<List<ScaleAssessment>>
    suspend fun getActiveAssessments(): List<ScaleAssessment>
    suspend fun saveAssessment(assessment: ScaleAssessment): ScaleAssessment
    suspend fun deleteAssessment(id: String)
}
