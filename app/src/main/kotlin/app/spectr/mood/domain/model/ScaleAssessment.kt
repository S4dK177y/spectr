package app.spectr.mood.domain.model

import java.time.LocalDate
import java.util.UUID

/**
 * Saved clinical scale assessment (auto-calculated or user-confirmed) for a calendar [date].
 */
data class ScaleAssessment(
    val id: String = UUID.randomUUID().toString(),
    val scaleType: ScaleType,
    val date: LocalDate,
    val answers: List<Int>,
    val totalScore: Int,
    val isConfirmed: Boolean = true,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING
) {
    init {
        require(totalScore >= 0) { "totalScore must be >= 0, got $totalScore" }
    }
}
