package app.spectr.mood.domain.model

import java.time.LocalDate
import java.util.UUID

/**
 * Quick timestamped momentary check-in recorded at any time during [entryDate].
 */
data class MomentCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val entryDate: LocalDate,
    val timestampEpochMs: Long,
    val mood: Int,
    val energy: Int? = null,
    val anxiety: Int? = null,
    val irritability: Int? = null,
    val note: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING
) {
    init {
        require(mood in DailyEntry.MIN_MOOD..DailyEntry.MAX_MOOD) {
            "mood must be in ${DailyEntry.MIN_MOOD}..${DailyEntry.MAX_MOOD}, got $mood"
        }
        require(energy == null || energy in DailyEntry.MIN_RATING..DailyEntry.MAX_RATING) {
            "energy must be in ${DailyEntry.MIN_RATING}..${DailyEntry.MAX_RATING}, got $energy"
        }
        require(anxiety == null || anxiety in DailyEntry.MIN_RATING..DailyEntry.MAX_RATING) {
            "anxiety must be in ${DailyEntry.MIN_RATING}..${DailyEntry.MAX_RATING}, got $anxiety"
        }
        require(irritability == null || irritability in DailyEntry.MIN_RATING..DailyEntry.MAX_RATING) {
            "irritability must be in ${DailyEntry.MIN_RATING}..${DailyEntry.MAX_RATING}, got $irritability"
        }
    }
}
