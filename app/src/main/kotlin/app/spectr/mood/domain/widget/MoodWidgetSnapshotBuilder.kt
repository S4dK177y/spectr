package app.spectr.mood.domain.widget

import app.spectr.mood.domain.calculator.AnalyticsCalculator
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.MomentCheckIn
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Single day cell in the home-screen widget's 7-day mood strip.
 */
data class MoodWidgetDayItem(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val isToday: Boolean,
    val mood: Int?,
    val isMixedDay: Boolean
)

/**
 * Pure Kotlin state snapshot rendered by the Jetpack Glance home-screen widget.
 */
data class MoodWidgetSnapshot(
    val today: LocalDate,
    val todayMood: Int?,
    val todayMomentsCount: Int,
    val isTodayFilled: Boolean,
    val isTodayMixed: Boolean,
    val last7Days: List<MoodWidgetDayItem>
)

/**
 * Builds a [MoodWidgetSnapshot] for a 7-day trailing window ending on [today].
 */
object MoodWidgetSnapshotBuilder {

    const val WINDOW_DAYS = 7

    fun build(today: LocalDate, entries: List<DailyEntry>, moments: List<MomentCheckIn>): MoodWidgetSnapshot {
        val activeByDate = entries
            .asSequence()
            .filter { it.deletedAt == null }
            .associateBy { it.date }

        val startDate = today.minusDays((WINDOW_DAYS - 1).toLong())
        val last7Days = (0 until WINDOW_DAYS).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val entry = activeByDate[date]
            MoodWidgetDayItem(
                date = date,
                dayOfWeek = date.dayOfWeek,
                isToday = date == today,
                mood = entry?.mood,
                isMixedDay = entry?.let(AnalyticsCalculator::isMixedDay) ?: false
            )
        }

        val todayEntry = activeByDate[today]
        val todayMomentsCount = moments.count { it.deletedAt == null && it.entryDate == today }

        return MoodWidgetSnapshot(
            today = today,
            todayMood = todayEntry?.mood,
            todayMomentsCount = todayMomentsCount,
            isTodayFilled = todayEntry != null,
            isTodayMixed = todayEntry?.let(AnalyticsCalculator::isMixedDay) ?: false,
            last7Days = last7Days
        )
    }
}
