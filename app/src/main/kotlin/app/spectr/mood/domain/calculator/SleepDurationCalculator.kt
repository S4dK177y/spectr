package app.spectr.mood.domain.calculator

import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.SleepInterval
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Raw sleep interval parsed from free-form text before persisting to Room.
 */
data class ParsedSleepInterval(val startMinuteOfDay: Int, val endMinuteOfDay: Int, val awakeDeductionMin: Int = 0)

/**
 * Result of parsing free-form sleep text (intervals or direct duration).
 */
data class SleepParseResult(
    val intervals: List<ParsedSleepInterval> = emptyList(),
    val overrideMinutes: Int? = null,
    val totalMinutes: Int = 0,
    val isRecognized: Boolean = false
) {
    val totalHours: Double
        get() = totalMinutes / MINUTES_PER_HOUR

    companion object {
        private const val MINUTES_PER_HOUR = 60.0
        val EMPTY = SleepParseResult()
    }
}

/**
 * Pure-Kotlin calculator and text parser for sleep intervals, midnight crossovers,
 * awake deductions, and manual sleep overrides.
 */
object SleepDurationCalculator {

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = SleepInterval.MINUTES_PER_DAY
    private const val MAX_DIRECT_HOURS = 24.0
    private const val MAX_DIRECT_MINUTES = MINUTES_PER_DAY

    private val EMPTY_MARKERS = setOf("-", "—", "–", "нет", "н/д", "n/a", "none", "null")

    private val INTERVAL_REGEX = Regex(
        pattern = """(\d{1,2})[:.](\d{2})\s*(?:[-–—~]|до|to)\s*(\d{1,2})[:.](\d{2})""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val DEDUCTION_REGEX = Regex(
        pattern = """(?:\(\s*(?:[-−]|минус|просн\S*|бодр\S*|вычет|awake)?\s*([^)]+?)\s*\)|""" +
            """[-−]\s*(\d+(?:[.,]\d+)?\s*(?:ч(?:ас(?:а|ов)?)?|h(?:ours?)?|м(?:ин(?:ут[аы]?)?)?|""" +
            """m(?:in(?:utes?)?)?)(?:\s*\d+\s*(?:м(?:ин(?:ут[аы]?)?)?|m(?:in(?:utes?)?)?))?))""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val HOURS_AND_MINUTES_REGEX = Regex(
        pattern = """^(\d{1,2})\s*(?:ч(?:ас(?:а|ов)?)?|h(?:ours?)?)\s*""" +
            """(\d{1,2})\s*(?:м(?:ин(?:ут[аы]?)?)?|m(?:in(?:utes?)?)?)$""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val HOURS_ONLY_REGEX = Regex(
        pattern = """^(\d{1,2}(?:[.,]\d{1,2})?)\s*(?:ч(?:ас(?:а|ов)?)?|h(?:ours?)?)?$""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val MINUTES_ONLY_REGEX = Regex(
        pattern = """^(\d{1,4})\s*(?:м(?:ин(?:ут[аы]?)?)?|m(?:in(?:utes?)?)?)$""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    /**
     * Calculates raw elapsed minutes between [startMinuteOfDay] and [endMinuteOfDay],
     * wrapping across midnight when [endMinuteOfDay] < [startMinuteOfDay].
     */
    fun intervalRawMinutes(startMinuteOfDay: Int, endMinuteOfDay: Int): Int {
        require(startMinuteOfDay in 0 until MINUTES_PER_DAY) {
            "startMinuteOfDay must be in 0..1439, got $startMinuteOfDay"
        }
        require(endMinuteOfDay in 0 until MINUTES_PER_DAY) {
            "endMinuteOfDay must be in 0..1439, got $endMinuteOfDay"
        }
        return when {
            endMinuteOfDay == startMinuteOfDay -> 0
            endMinuteOfDay > startMinuteOfDay -> endMinuteOfDay - startMinuteOfDay
            else -> (MINUTES_PER_DAY - startMinuteOfDay) + endMinuteOfDay
        }
    }

    /**
     * Calculates net sleep minutes for a single interval after subtracting awake time.
     */
    fun intervalNetMinutes(startMinuteOfDay: Int, endMinuteOfDay: Int, awakeDeductionMin: Int = 0): Int {
        val raw = intervalRawMinutes(startMinuteOfDay, endMinuteOfDay)
        val deduction = awakeDeductionMin.coerceAtLeast(0)
        return (raw - deduction).coerceAtLeast(0)
    }

    /**
     * Calculates net sleep minutes for a persisted [SleepInterval].
     */
    fun intervalNetMinutes(interval: SleepInterval): Int = intervalNetMinutes(
        startMinuteOfDay = interval.startMinuteOfDay,
        endMinuteOfDay = interval.endMinuteOfDay,
        awakeDeductionMin = interval.awakeDeductionMin
    )

    /**
     * Calculates net sleep minutes for a parsed interval.
     */
    fun intervalNetMinutes(interval: ParsedSleepInterval): Int = intervalNetMinutes(
        startMinuteOfDay = interval.startMinuteOfDay,
        endMinuteOfDay = interval.endMinuteOfDay,
        awakeDeductionMin = interval.awakeDeductionMin
    )

    /**
     * Sums net minutes across all active (non-deleted) intervals.
     */
    fun totalIntervalMinutes(intervals: List<SleepInterval>): Int = intervals
        .asSequence()
        .filter { it.deletedAt == null }
        .sumOf { intervalNetMinutes(it) }

    /**
     * Returns true when [entry] contains either a manual override or at least one active sleep interval.
     */
    fun hasSleepData(entry: DailyEntry): Boolean =
        entry.sleepMinutesOverride != null || entry.sleepIntervals.any { it.deletedAt == null }

    /**
     * Resolves effective net sleep minutes for [entry], prioritizing manual override when present.
     */
    fun effectiveSleepMinutes(entry: DailyEntry): Int =
        entry.sleepMinutesOverride?.coerceAtLeast(0) ?: totalIntervalMinutes(entry.sleepIntervals)

    /**
     * Resolves effective net sleep duration in fractional hours for clinical thresholds and charts.
     */
    fun effectiveSleepHours(entry: DailyEntry): Double = effectiveSleepMinutes(entry) / MINUTES_PER_HOUR.toDouble()

    /**
     * Formats a minute-of-day value (0..1439) as `HH:mm`.
     */
    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val normalized = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val hours = normalized / MINUTES_PER_HOUR
        val minutes = normalized % MINUTES_PER_HOUR
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
    }

    /**
     * Parses free-form sleep text supporting:
     * - One or more intervals with midnight crossover (`22:00–01:30, 09:00–12:30`)
     * - Optional awake deductions (`23:00-07:00 (-30м)`, `23:30-07:30 -1ч 15м`)
     * - Direct duration fallbacks (`7.5`, `7,5 ч`, `7ч 30м`, `450 мин`)
     */
    fun parseSleepText(raw: String): SleepParseResult {
        val trimmed = raw.trim()
        val normalizedLower = trimmed.lowercase(Locale.ROOT)
        if (trimmed.isEmpty() || normalizedLower in EMPTY_MARKERS) {
            return SleepParseResult.EMPTY
        }

        val intervalResult = parseIntervalsFromText(trimmed)
        if (intervalResult.isRecognized) {
            return intervalResult
        }

        val directMinutes = parseDurationMinutes(trimmed)
        return if (directMinutes != null && directMinutes in 0..MAX_DIRECT_MINUTES) {
            SleepParseResult(
                intervals = emptyList(),
                overrideMinutes = directMinutes,
                totalMinutes = directMinutes,
                isRecognized = true
            )
        } else {
            SleepParseResult.EMPTY
        }
    }

    private fun parseIntervalsFromText(text: String): SleepParseResult {
        val matches = INTERVAL_REGEX.findAll(text).toList()
        if (matches.isEmpty()) {
            return SleepParseResult.EMPTY
        }

        val parsed = mutableListOf<ParsedSleepInterval>()
        for (index in matches.indices) {
            val match = matches[index]
            val startMinute = parseClockTime(match.groupValues[1], match.groupValues[2])
            val endMinute = parseClockTime(match.groupValues[3], match.groupValues[4])
            if (startMinute == null || endMinute == null) {
                continue
            }

            val tailStart = match.range.last + 1
            val tailEnd = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            val tailSlice = text.substring(tailStart, tailEnd)
            val deduction = extractDeductionMinutes(tailSlice)

            parsed += ParsedSleepInterval(
                startMinuteOfDay = startMinute,
                endMinuteOfDay = endMinute,
                awakeDeductionMin = deduction
            )
        }

        if (parsed.isEmpty()) {
            return SleepParseResult.EMPTY
        }

        val total = parsed.sumOf { intervalNetMinutes(it) }
        return SleepParseResult(
            intervals = parsed,
            overrideMinutes = null,
            totalMinutes = total,
            isRecognized = true
        )
    }

    private fun parseClockTime(hourText: String, minuteText: String): Int? {
        val hour = hourText.toIntOrNull()
        val minute = minuteText.toIntOrNull()
        return when {
            hour == null || minute == null -> null
            minute !in 0 until MINUTES_PER_HOUR -> null
            hour == 24 && minute == 0 -> 0
            hour in 0..23 -> hour * MINUTES_PER_HOUR + minute
            else -> null
        }
    }

    private fun extractDeductionMinutes(tailSlice: String): Int {
        var totalDeduction = 0
        for (match in DEDUCTION_REGEX.findAll(tailSlice)) {
            val candidate = (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
            val minutes = parseDurationMinutes(candidate)
            if (minutes != null && minutes > 0) {
                totalDeduction += minutes
            }
        }
        return totalDeduction
    }

    private fun parseDurationMinutes(text: String): Int? {
        val cleaned = text.trim()
        val hmMatch = HOURS_AND_MINUTES_REGEX.matchEntire(cleaned)
        if (hmMatch != null) {
            val hours = hmMatch.groupValues[1].toIntOrNull()
            val minutes = hmMatch.groupValues[2].toIntOrNull()
            return if (hours != null && minutes != null && minutes < MINUTES_PER_HOUR) {
                hours * MINUTES_PER_HOUR + minutes
            } else {
                null
            }
        }

        val minMatch = MINUTES_ONLY_REGEX.matchEntire(cleaned)
        if (minMatch != null) {
            return minMatch.groupValues[1].toIntOrNull()
        }

        val hourMatch = HOURS_ONLY_REGEX.matchEntire(cleaned)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (hours != null && hours in 0.0..MAX_DIRECT_HOURS) {
                return (hours * MINUTES_PER_HOUR).roundToInt()
            }
        }
        return null
    }
}
