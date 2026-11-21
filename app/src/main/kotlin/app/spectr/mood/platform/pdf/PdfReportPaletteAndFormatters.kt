package app.spectr.mood.platform.pdf

import android.graphics.Color
import android.graphics.Paint
import app.spectr.mood.domain.calculator.ClinicalScaleCalculator
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.ScaleType
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Print-ready light vector palette and clinically neutral Russian text helpers for PDF export.
 */
internal object PdfReportStyle {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN_H = 24f
    const val MARGIN_TOP = 22f
    const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_H * 2f

    val COLOR_PAGE_BG = Color.WHITE
    val COLOR_INK = Color.rgb(26, 33, 46)
    val COLOR_MUTED = Color.rgb(92, 102, 118)
    val COLOR_CARD_BG = Color.rgb(246, 248, 251)
    val COLOR_TILE_BG = Color.rgb(255, 255, 255)
    val COLOR_BORDER = Color.rgb(218, 224, 234)
    val COLOR_GRID = Color.rgb(226, 231, 239)
    val COLOR_GRID_STRONG = Color.rgb(164, 175, 192)
    val COLOR_ACCENT = Color.rgb(52, 97, 148)
    val COLOR_SLEEP = Color.rgb(106, 142, 174)
    val COLOR_ANXIETY = Color.rgb(122, 92, 168)
    val COLOR_ALERT = Color.rgb(189, 59, 69)
    val COLOR_ALERT_BG = Color.rgb(253, 237, 238)
    val COLOR_PILL_ACTIVE_BG = Color.rgb(232, 240, 250)
    val COLOR_MIXED_RING = Color.rgb(142, 74, 158)
    val COLOR_ROW_ALT = Color.rgb(249, 250, 252)

    private val MOOD_BG_COLORS = mapOf(
        -5 to Color.rgb(57, 73, 171),
        -4 to Color.rgb(92, 107, 192),
        -3 to Color.rgb(121, 134, 203),
        -2 to Color.rgb(159, 168, 218),
        -1 to Color.rgb(197, 202, 233),
        0 to Color.rgb(220, 229, 223),
        1 to Color.rgb(255, 236, 179),
        2 to Color.rgb(255, 213, 79),
        3 to Color.rgb(255, 183, 77),
        4 to Color.rgb(255, 138, 101),
        5 to Color.rgb(229, 115, 115)
    )

    private val MOOD_FG_COLORS = mapOf(
        -5 to Color.WHITE,
        -4 to Color.WHITE,
        -3 to Color.WHITE,
        -2 to Color.rgb(26, 32, 68),
        -1 to Color.rgb(29, 35, 64),
        0 to Color.rgb(31, 43, 36),
        1 to Color.rgb(58, 44, 0),
        2 to Color.rgb(54, 39, 0),
        3 to Color.rgb(56, 32, 0),
        4 to Color.rgb(59, 21, 8),
        5 to Color.WHITE
    )

    fun moodContainerColor(score: Int): Int = MOOD_BG_COLORS.getValue(score.coerceIn(-5, 5))

    fun moodContentColor(score: Int): Int = MOOD_FG_COLORS.getValue(score.coerceIn(-5, 5))
}

internal object PdfTextFormatters {
    private val RU_LOCALE: Locale = Locale.forLanguageTag("ru")
    private val FULL_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", RU_LOCALE)
    private val SHORT_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM", RU_LOCALE)
    private val SHORT_CHART_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", RU_LOCALE)
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    private val MONTH_NAMES_RU = mapOf(
        Month.JANUARY to "Январь",
        Month.FEBRUARY to "Февраль",
        Month.MARCH to "Март",
        Month.APRIL to "Апрель",
        Month.MAY to "Май",
        Month.JUNE to "Июнь",
        Month.JULY to "Июль",
        Month.AUGUST to "Август",
        Month.SEPTEMBER to "Сентябрь",
        Month.OCTOBER to "Октябрь",
        Month.NOVEMBER to "Ноябрь",
        Month.DECEMBER to "Декабрь"
    )

    fun formatDateFull(date: LocalDate): String = date.format(FULL_DATE_FMT)

    fun formatDateShort(date: LocalDate): String = date.format(SHORT_DATE_FMT)

    fun formatChartDate(date: LocalDate): String = date.format(SHORT_CHART_DATE_FMT)

    fun formatYearMonth(yearMonth: YearMonth): String = "${MONTH_NAMES_RU.getValue(yearMonth.month)} ${yearMonth.year}"

    fun formatTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalTime().format(TIME_FMT)

    fun formatSignedMood(score: Int): String = when {
        score > 0 -> "+$score"
        score < 0 -> "−${-score}"
        else -> "0"
    }

    fun formatSignedAvg(value: Double): String {
        val formatted = String.format(RU_LOCALE, "%.1f", abs(value))
        return when {
            value > 0.05 -> "+$formatted"
            value < -0.05 -> "−$formatted"
            else -> "0,0"
        }
    }

    fun formatDecimalOne(value: Double): String = String.format(RU_LOCALE, "%.1f", value)

    fun formatSleepMinutes(minutes: Int?): String {
        if (minutes == null) return "—"
        val hours = minutes / 60
        val rem = minutes % 60
        return when {
            hours > 0 && rem > 0 -> "${hours}ч ${rem}м"
            hours > 0 -> "${hours}ч"
            else -> "${rem}м"
        }
    }

    fun scaleCode(scaleType: ScaleType): String = when (scaleType) {
        ScaleType.PHQ9 -> "PHQ-9"
        ScaleType.GAD7 -> "GAD-7"
        ScaleType.ASRM -> "ASRM"
    }

    fun scaleSeverityLabel(scaleType: ScaleType, score: Int): String = when (scaleType) {
        ScaleType.PHQ9 -> phq9SeverityText(score)
        ScaleType.GAD7 -> gad7SeverityText(score)
        ScaleType.ASRM -> asrmSeverityText(score)
    }

    private fun phq9SeverityText(score: Int): String = when (ClinicalScaleCalculator.classifyPhq9(score)) {
        app.spectr.mood.domain.calculator.Phq9Severity.MINIMAL -> "Минимальный"
        app.spectr.mood.domain.calculator.Phq9Severity.MILD -> "Лёгкий"
        app.spectr.mood.domain.calculator.Phq9Severity.MODERATE -> "Умеренный"
        app.spectr.mood.domain.calculator.Phq9Severity.MODERATELY_SEVERE -> "Умеренно-тяжёлый"
        app.spectr.mood.domain.calculator.Phq9Severity.SEVERE -> "Выраженный"
    }

    private fun gad7SeverityText(score: Int): String = when (ClinicalScaleCalculator.classifyGad7(score)) {
        app.spectr.mood.domain.calculator.Gad7Severity.MINIMAL -> "Минимальная"
        app.spectr.mood.domain.calculator.Gad7Severity.MILD -> "Лёгкая"
        app.spectr.mood.domain.calculator.Gad7Severity.MODERATE -> "Умеренная"
        app.spectr.mood.domain.calculator.Gad7Severity.SEVERE -> "Выраженная"
    }

    private fun asrmSeverityText(score: Int): String = when (ClinicalScaleCalculator.classifyAsrm(score)) {
        app.spectr.mood.domain.calculator.AsrmSeverity.NORMAL_BASELINE -> "Обычный фон"
        app.spectr.mood.domain.calculator.AsrmSeverity.ELEVATED_ACTIVATION -> "Повыш. активация"
    }

    fun dynamicsShort(dynamics: DailyDynamics): String = when (dynamics) {
        DailyDynamics.STEADY -> "Ровно"
        DailyDynamics.WORSE_MORNING -> "Утром хуже"
        DailyDynamics.WORSE_EVENING -> "Вечером хуже"
        DailyDynamics.WORSE_NIGHT -> "Ночью хуже"
        DailyDynamics.SWINGS -> "Качели"
    }

    fun formatSymptomTags(symptoms: DailySymptoms, isMixed: Boolean): String {
        val tags = ArrayList<String>(8)
        if (isMixed) tags.add("Смеш.")
        if (symptoms.deathThoughts) tags.add("Мысли о см.")
        if (symptoms.apathy) tags.add("Апатия")
        if (symptoms.selfCriticism) tags.add("Самокрит.")
        if (symptoms.brainFog) tags.add("Туман")
        if (symptoms.bingeEating) tags.add("Пищ. срыв")
        if (symptoms.hyperfocus) tags.add("Гиперфикс")
        if (symptoms.racingThoughts) tags.add("Уск. мысли")
        return tags.joinToString(", ")
    }

    fun formatDetailsSuffix(alcohol: AlcoholStatus, medication: MedicationStatus): String {
        val parts = ArrayList<String>(2)
        when (alcohol) {
            AlcoholStatus.NONE -> Unit
            AlcoholStatus.LIGHT_OCCASION -> parts.add("Алк: немн.")
            AlcoholStatus.HEAVY_OCCASION -> parts.add("Алк: много")
            AlcoholStatus.STRESS_COPING -> parts.add("Алк: стресс")
        }
        when (medication) {
            MedicationStatus.NOT_PRESCRIBED -> Unit
            MedicationStatus.TAKEN_AS_PRESCRIBED -> parts.add("Лек: по схеме")
            MedicationStatus.TAKEN_WITH_SIDE_EFFECTS -> parts.add("Лек: побочки")
            MedicationStatus.MISSED_FORGOT -> parts.add("Лек: пропуск")
            MedicationStatus.MISSED_SIDE_EFFECTS -> parts.add("Лек: проп./побоч.")
        }
        return parts.joinToString(" · ")
    }

    /**
     * Wraps [text] into lines that fit within [maxWidthPx] according to [paint].
     */
    fun wrapText(text: String, paint: Paint, maxWidthPx: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = ArrayList<String>()
        text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { paragraph ->
                wrapParagraph(paragraph, paint, maxWidthPx, lines)
            }
        return lines
    }

    private fun wrapParagraph(paragraph: String, paint: Paint, maxWidthPx: Float, lines: MutableList<String>) {
        val current = StringBuilder()
        for (word in paragraph.split(Regex("\\s+"))) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidthPx) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current.clear()
                }
                appendWordOrBreak(word, paint, maxWidthPx, lines, current)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
    }

    private fun appendWordOrBreak(
        word: String,
        paint: Paint,
        maxWidthPx: Float,
        lines: MutableList<String>,
        current: StringBuilder
    ) {
        if (paint.measureText(word) <= maxWidthPx) {
            current.append(word)
        } else {
            appendBrokenLongWord(word, paint, maxWidthPx, lines, current)
        }
    }

    private fun appendBrokenLongWord(
        word: String,
        paint: Paint,
        maxWidthPx: Float,
        lines: MutableList<String>,
        current: StringBuilder
    ) {
        var idx = 0
        while (idx < word.length) {
            val count = paint.breakText(word, idx, word.length, true, maxWidthPx, null).coerceAtLeast(1)
            val slice = word.substring(idx, idx + count)
            if (idx + count < word.length) {
                lines.add(slice)
            } else {
                current.append(slice)
            }
            idx += count
        }
    }
}
