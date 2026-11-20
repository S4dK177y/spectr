package app.spectr.mood.domain.repository

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first repository contract for daily diary summaries and momentary check-ins.
 */
interface DiaryRepository {
    fun observeEntries(): Flow<List<DailyEntry>>
    fun observeEntryByDate(date: LocalDate): Flow<DailyEntry?>
    fun observeEntriesInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyEntry>>
    suspend fun getActiveEntries(): List<DailyEntry>
    suspend fun getEntryByDate(date: LocalDate): DailyEntry?
    suspend fun saveEntry(entry: DailyEntry): DailyEntry
    suspend fun deleteEntry(id: String)

    fun observeMomentsByDate(date: LocalDate): Flow<List<MomentCheckIn>>
    fun observeMomentsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MomentCheckIn>>
    suspend fun getActiveMoments(): List<MomentCheckIn>
    suspend fun saveMoment(moment: MomentCheckIn): MomentCheckIn
    suspend fun deleteMoment(id: String)
}
