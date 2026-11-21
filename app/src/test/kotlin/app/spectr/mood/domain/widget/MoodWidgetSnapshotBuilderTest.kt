package app.spectr.mood.domain.widget

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MomentCheckIn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodWidgetSnapshotBuilderTest {

    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun `empty diary produces 7 trailing days ending on today`() {
        val snapshot = MoodWidgetSnapshotBuilder.build(
            today = today,
            entries = emptyList(),
            moments = emptyList()
        )

        assertEquals(today, snapshot.today)
        assertNull(snapshot.todayMood)
        assertFalse(snapshot.isTodayFilled)
        assertFalse(snapshot.isTodayMixed)
        assertEquals(0, snapshot.todayMomentsCount)
        assertEquals(7, snapshot.last7Days.size)
        assertEquals(today.minusDays(6), snapshot.last7Days.first().date)
        assertEquals(today, snapshot.last7Days.last().date)
        assertTrue(snapshot.last7Days.last().isToday)
        assertTrue(snapshot.last7Days.dropLast(1).none { it.isToday })
    }

    @Test
    fun `maps active entries, mixed flag, and today's moments while ignoring tombstones`() {
        val entries = listOf(
            // Outside 7-day window
            DailyEntry(id = "old", date = today.minusDays(10), mood = 4, energy = 8),
            // Inside 7-day window
            DailyEntry(id = "d-3", date = today.minusDays(3), mood = 2, energy = 6),
            // Soft-deleted entry on yesterday must be ignored
            DailyEntry(id = "deleted", date = today.minusDays(1), mood = -4, energy = 1, deletedAt = 123L),
            // Today: mixed day (mood <= -1 and racingThoughts)
            DailyEntry(
                id = "today",
                date = today,
                mood = -2,
                energy = 7,
                symptoms = DailySymptoms(racingThoughts = true)
            )
        )
        val moments = listOf(
            MomentCheckIn(id = "m1", entryDate = today, timestampEpochMs = 1_000L, mood = -1),
            MomentCheckIn(id = "m2", entryDate = today, timestampEpochMs = 2_000L, mood = -2),
            MomentCheckIn(id = "m3-deleted", entryDate = today, timestampEpochMs = 3_000L, mood = 0, deletedAt = 99L),
            MomentCheckIn(id = "m4-other-day", entryDate = today.minusDays(1), timestampEpochMs = 4_000L, mood = 1)
        )

        val snapshot = MoodWidgetSnapshotBuilder.build(
            today = today,
            entries = entries,
            moments = moments
        )

        assertTrue(snapshot.isTodayFilled)
        assertEquals(-2, snapshot.todayMood)
        assertTrue(snapshot.isTodayMixed)
        assertEquals(2, snapshot.todayMomentsCount)

        val dayMinus3 = snapshot.last7Days.first { it.date == today.minusDays(3) }
        assertEquals(2, dayMinus3.mood)
        assertFalse(dayMinus3.isMixedDay)

        val yesterday = snapshot.last7Days.first { it.date == today.minusDays(1) }
        assertNull(yesterday.mood)

        val todayItem = snapshot.last7Days.last()
        assertEquals(-2, todayItem.mood)
        assertTrue(todayItem.isMixedDay)
    }
}
