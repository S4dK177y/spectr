package app.spectr.mood.domain.chart

import app.spectr.mood.domain.calculator.AnalyticsCalculator
import app.spectr.mood.domain.calculator.SleepDurationCalculator
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.ScaleAssessment
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Single chronological data point rendered across the 3 Overview vector charts.
 *
 * [xFraction] is normalized to `0f..1f` based on calendar-day distance between
 * [OverviewChartData.rangeStart] and [OverviewChartData.rangeEnd] so missing diary days
 * preserve proportional spacing on the horizontal timeline.
 */
data class OverviewChartPoint(
    val date: LocalDate,
    val xFraction: Float,
    val mood: Int,
    val isMixedDay: Boolean,
    val dailyDynamics: DailyDynamics,
    val energy: Int,
    val sleepHours: Double?,
    val sleepMinutes: Int?,
    val isShortSleep: Boolean,
    val anxiety: Int,
    val irritability: Int
)

/**
 * Tick mark along the horizontal date axis.
 */
data class ChartDateTick(val xFraction: Float, val date: LocalDate)

/**
 * Complete chart series model shared by the Overview Canvas charts and future PDF export.
 */
data class OverviewChartData(
    val points: List<OverviewChartPoint> = emptyList(),
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
    val sleepAxisMaxHours: Double = ChartGeometry.DEFAULT_SLEEP_AXIS_MAX_HOURS,
    val xTicks: List<ChartDateTick> = emptyList()
) {
    val isEmpty: Boolean
        get() = points.isEmpty()
}

/**
 * Single point on a clinical scale confirmation trend chart.
 */
data class ScaleTrendPoint(val assessment: ScaleAssessment, val xFraction: Float)

/**
 * Pure-Kotlin geometry and coordinate-mapping helper for vector Canvas charts (and PDF export).
 */
object ChartGeometry {

    const val DEFAULT_SLEEP_AXIS_MAX_HOURS = 12.0
    const val SHORT_SLEEP_THRESHOLD_HOURS = 6.0
    const val HIGH_ANXIETY_THRESHOLD = 7.0
    private const val MAX_X_TICKS = 4

    /**
     * Converts chronologically sorted [entries] into [OverviewChartData].
     */
    fun buildOverviewChartData(entries: List<DailyEntry>): OverviewChartData {
        if (entries.isEmpty()) {
            return OverviewChartData()
        }

        val sorted = entries.sortedBy { it.date }
        val start = sorted.first().date
        val end = sorted.last().date
        val spanDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0L)

        var maxObservedSleep = 0.0
        val points = sorted.map { entry ->
            val fraction = if (spanDays == 0L) {
                0.5f
            } else {
                (ChronoUnit.DAYS.between(start, entry.date).toFloat() / spanDays.toFloat()).coerceIn(0f, 1f)
            }
            val hasSleep = SleepDurationCalculator.hasSleepData(entry)
            val sleepMinutes = if (hasSleep) SleepDurationCalculator.effectiveSleepMinutes(entry) else null
            val sleepHours = if (hasSleep) SleepDurationCalculator.effectiveSleepHours(entry) else null
            if (sleepHours != null && sleepHours > maxObservedSleep) {
                maxObservedSleep = sleepHours
            }
            OverviewChartPoint(
                date = entry.date,
                xFraction = fraction,
                mood = entry.mood,
                isMixedDay = AnalyticsCalculator.isMixedDay(entry),
                dailyDynamics = entry.dailyDynamics,
                energy = entry.energy,
                sleepHours = sleepHours,
                sleepMinutes = sleepMinutes,
                isShortSleep = sleepHours != null && sleepHours < SHORT_SLEEP_THRESHOLD_HOURS,
                anxiety = entry.anxiety,
                irritability = entry.irritability
            )
        }

        val sleepAxisMax = computeSleepAxisMax(maxObservedSleep)
        val xTicks = buildDateTicks(start, spanDays)

        return OverviewChartData(
            points = points,
            rangeStart = start,
            rangeEnd = end,
            sleepAxisMaxHours = sleepAxisMax,
            xTicks = xTicks
        )
    }

    /**
     * Builds chronologically sorted trend points for confirmed [assessments] of a single scale.
     */
    fun buildScaleTrendPoints(assessments: List<ScaleAssessment>): List<ScaleTrendPoint> {
        if (assessments.isEmpty()) {
            return emptyList()
        }
        val sorted = assessments.sortedWith(compareBy<ScaleAssessment> { it.date }.thenBy { it.updatedAt })
        if (sorted.size == 1) {
            return listOf(ScaleTrendPoint(assessment = sorted.first(), xFraction = 0.5f))
        }
        val start = sorted.first().date
        val end = sorted.last().date
        val spanDays = ChronoUnit.DAYS.between(start, end)
        return sorted.mapIndexed { index, assessment ->
            val fraction = if (spanDays <= 0L) {
                index.toFloat() / (sorted.size - 1).toFloat()
            } else {
                (ChronoUnit.DAYS.between(start, assessment.date).toFloat() / spanDays.toFloat()).coerceIn(0f, 1f)
            }
            ScaleTrendPoint(assessment = assessment, xFraction = fraction)
        }
    }

    /**
     * Maps normalized X [fraction] (`0f..1f`) into horizontal pixel space `[leftPx, rightPx]`.
     */
    fun fractionToX(fraction: Float, leftPx: Float, rightPx: Float): Float =
        leftPx + fraction.coerceIn(0f, 1f) * (rightPx - leftPx)

    /**
     * Maps domain [value] in `[minValue, maxValue]` into vertical pixel space `[topPx, bottomPx]`,
     * where [maxValue] sits at [topPx] and [minValue] sits at [bottomPx].
     */
    fun valueToY(value: Double, minValue: Double, maxValue: Double, topPx: Float, bottomPx: Float): Float {
        if (maxValue <= minValue) {
            return (topPx + bottomPx) / 2f
        }
        val normalized = ((value - minValue) / (maxValue - minValue)).toFloat().coerceIn(0f, 1f)
        return bottomPx - normalized * (bottomPx - topPx)
    }

    /**
     * Finds the index of the point horizontally closest to [touchX].
     * Returns `null` when [fractions] is empty.
     */
    fun findNearestPointIndex(touchX: Float, leftPx: Float, rightPx: Float, fractions: List<Float>): Int? {
        if (fractions.isEmpty()) {
            return null
        }
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        fractions.forEachIndexed { index, fraction ->
            val x = fractionToX(fraction, leftPx, rightPx)
            val distance = abs(touchX - x)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun computeSleepAxisMax(maxObservedHours: Double): Double {
        if (maxObservedHours <= DEFAULT_SLEEP_AXIS_MAX_HOURS) {
            return DEFAULT_SLEEP_AXIS_MAX_HOURS
        }
        val roundedUp = ceil(maxObservedHours).toInt()
        val evenCeiling = if (roundedUp % 2 == 0) roundedUp else roundedUp + 1
        return evenCeiling.toDouble()
    }

    private fun buildDateTicks(start: LocalDate, spanDays: Long): List<ChartDateTick> {
        if (spanDays == 0L) {
            return listOf(ChartDateTick(xFraction = 0.5f, date = start))
        }
        val tickCount = when {
            spanDays <= 2L -> (spanDays + 1L).toInt()
            else -> MAX_X_TICKS
        }
        val seenDates = LinkedHashSet<LocalDate>()
        val result = ArrayList<ChartDateTick>(tickCount)
        for (i in 0 until tickCount) {
            val fraction = i.toFloat() / (tickCount - 1).toFloat()
            val offsetDays = kotlin.math.round(spanDays * fraction.toDouble()).toLong()
            val date = start.plusDays(offsetDays)
            if (seenDates.add(date)) {
                result.add(ChartDateTick(xFraction = fraction, date = date))
            }
        }
        return result
    }
}
