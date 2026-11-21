package app.spectr.mood.platform.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.spectr.mood.domain.calculator.DiaryAnalyticsSummary
import app.spectr.mood.domain.calculator.KeyMarkersSummary
import app.spectr.mood.domain.report.DoctorReportSnapshot
import app.spectr.mood.domain.report.ReportScaleOverview
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Native Android vector PDF report generator (`android.graphics.pdf.PdfDocument`) producing
 * a clinically neutral A4 summary sheet (Page 1) plus optional paginated daily table / notes pages.
 */
@Singleton
class DoctorPdfReportGenerator @Inject constructor() {

    /**
     * Generates the full vector PDF into [outputStream] and returns the number of pages written.
     */
    fun generate(snapshot: DoctorReportSnapshot, outputStream: OutputStream): Int {
        val document = PdfDocument()
        return try {
            val page1Info = PdfDocument.PageInfo.Builder(
                PdfReportStyle.PAGE_WIDTH,
                PdfReportStyle.PAGE_HEIGHT,
                1
            ).create()
            val page1 = document.startPage(page1Info)
            renderSummaryPage1(page1.canvas, snapshot)
            document.finishPage(page1)

            val totalPages = PdfDetailPagesRenderer.renderAdditionalPages(
                document = document,
                snapshot = snapshot,
                startPageNumber = 2
            ).coerceAtLeast(1)

            document.writeTo(outputStream)
            outputStream.flush()
            totalPages
        } finally {
            document.close()
        }
    }

    private fun renderSummaryPage1(canvas: Canvas, snapshot: DoctorReportSnapshot) {
        canvas.drawColor(PdfReportStyle.COLOR_PAGE_BG)
        val left = PdfReportStyle.MARGIN_H
        val width = PdfReportStyle.CONTENT_WIDTH

        // 1. Header (y: 22..68)
        drawPage1Header(canvas, snapshot)

        // 2. 14 KPI metrics in a crisp 5x3 grid (y: 74..178)
        drawKpiGrid(
            canvas = canvas,
            bounds = RectF(left, 74f, left + width, 178f),
            summary = snapshot.summary
        )

        // 3. Key Markers bar (y: 185..232)
        drawKeyMarkersCard(
            canvas = canvas,
            bounds = RectF(left, 185f, left + width, 232f),
            markers = snapshot.summary.markers
        )

        // 4. Clinical Scales (PHQ-9, GAD-7, ASRM — Auto + Latest Confirmed) (y: 239..303)
        drawScalesRow(
            canvas = canvas,
            bounds = RectF(left, 239f, left + width, 303f),
            scales = snapshot.scalesOverview
        )

        // 5. Middle split: Monthly Calendar (left 206pt) + Chart 1 Mood (right 333pt) (y: 310..468)
        val calRight = left + 206f
        PdfPage1ChartsAndCalendar.drawCalendarCard(
            canvas = canvas,
            bounds = RectF(left, 310f, calRight, 468f),
            grid = snapshot.calendarGrid
        )
        PdfPage1ChartsAndCalendar.drawMoodChartCard(
            canvas = canvas,
            bounds = RectF(calRight + 8f, 310f, left + width, 468f),
            chartData = snapshot.chartData
        )

        // 6. Chart 2: Energy & Sleep (< 6 h threshold) (y: 475..632)
        PdfPage1ChartsAndCalendar.drawEnergySleepChartCard(
            canvas = canvas,
            bounds = RectF(left, 475f, left + width, 632f),
            chartData = snapshot.chartData
        )

        // 7. Chart 3: Anxiety & Irritability (>= 7 threshold) (y: 639..796)
        PdfPage1ChartsAndCalendar.drawAnxietyIrritabilityChartCard(
            canvas = canvas,
            bounds = RectF(left, 639f, left + width, 796f),
            chartData = snapshot.chartData
        )

        // 8. Footer
        PdfDetailPagesRenderer.drawPageFooter(canvas, pageNumber = 1)
    }

    private fun drawPage1Header(canvas: Canvas, snapshot: DoctorReportSnapshot) {
        val left = PdfReportStyle.MARGIN_H
        val right = PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_ACCENT
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 7.2f
        }
        val metaRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 7.2f
            textAlign = Paint.Align.RIGHT
        }

        val periodLabel = if (snapshot.effectiveStartDate != null && snapshot.effectiveEndDate != null) {
            "${PdfTextFormatters.formatDateFull(snapshot.effectiveStartDate)} — " +
                "${PdfTextFormatters.formatDateFull(snapshot.effectiveEndDate)} · ${snapshot.summary.filledDays} дн."
        } else {
            "Записей за период нет"
        }

        canvas.drawText("Spectr — Сводка дневника настроения", left, 36f, titlePaint)
        canvas.drawText(periodLabel, right, 36f, periodPaint)

        canvas.drawText(
            "Справочная сводка самонаблюдения для обсуждения со специалистом. Не является медицинским диагнозом.",
            left,
            50f,
            metaPaint
        )
        canvas.drawText(
            "Сформировано: ${PdfTextFormatters.formatDateFull(snapshot.generatedDate)}",
            right,
            50f,
            metaRightPaint
        )

        val divider = Paint().apply {
            color = PdfReportStyle.COLOR_BORDER
            strokeWidth = 1f
        }
        canvas.drawLine(left, 60f, right, 60f, divider)
    }

    private fun drawKpiGrid(canvas: Canvas, bounds: RectF, summary: DiaryAnalyticsSummary) {
        val tiles = buildKpiCells(summary)
        val cols = 5
        val rows = 3
        val gapX = 5f
        val gapY = 5f
        val cellW = (bounds.width() - gapX * (cols - 1)) / cols
        val cellH = (bounds.height() - gapY * (rows - 1)) / rows

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = PdfReportStyle.COLOR_BORDER
            strokeWidth = 0.6f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 6.3f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 5.8f
        }

        tiles.forEachIndexed { idx, tile ->
            val r = idx / cols
            val c = idx % cols
            val x = bounds.left + c * (cellW + gapX)
            val y = bounds.top + r * (cellH + gapY)
            val rect = RectF(x, y, x + cellW, y + cellH)

            bgPaint.color = tile.tintColor ?: PdfReportStyle.COLOR_CARD_BG
            canvas.drawRoundRect(rect, 5f, 5f, bgPaint)
            canvas.drawRoundRect(rect, 5f, 5f, borderPaint)

            canvas.drawText(tile.label, x + 6f, y + 10f, labelPaint)
            valuePaint.color = tile.valueColor ?: PdfReportStyle.COLOR_INK
            canvas.drawText(tile.value, x + 6f, y + 21.5f, valuePaint)
            if (tile.sub != null) {
                canvas.drawText(tile.sub, x + 6f, y + 28.8f, subPaint)
            }
        }
    }

    private fun drawKeyMarkersCard(canvas: Canvas, bounds: RectF, markers: KeyMarkersSummary) {
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfReportStyle.COLOR_CARD_BG }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = PdfReportStyle.COLOR_BORDER
            strokeWidth = 0.7f
        }
        canvas.drawRoundRect(bounds, 7f, 7f, cardFill)
        canvas.drawRoundRect(bounds, 7f, 7f, cardStroke)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Ключевые маркеры (дней)", bounds.left + 8f, bounds.top + 12.5f, titlePaint)

        val items = listOf(
            MarkerChip("Мысли о смерти", markers.deathThoughtsDays, alert = markers.deathThoughtsDays > 0),
            MarkerChip("Пищевые срывы", markers.bingeEatingDays),
            MarkerChip("Гиперфикс", markers.hyperfocusDays),
            MarkerChip("Ускор. мысли", markers.racingThoughtsDays),
            MarkerChip("Апатия", markers.apathyDays),
            MarkerChip("Самокритика", markers.selfCriticismDays),
            MarkerChip("Туман", markers.brainFogDays),
            MarkerChip(
                label = if (markers.alcoholStressDays > 0) {
                    "Алкоголь (стр: ${markers.alcoholStressDays})"
                } else {
                    "Алкоголь"
                },
                count = markers.alcoholDays
            ),
            MarkerChip(
                label = if (markers.medicationMissedDays > 0) {
                    "Побочки (проп: ${markers.medicationMissedDays})"
                } else {
                    "Побочки лек."
                },
                count = markers.medicationSideEffectsDays
            )
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 6.7f }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val firstRow = items.take(5)
        val secondRow = items.drop(5)
        drawMarkerRow(canvas, firstRow, bounds.left + 8f, bounds.top + 17f, textPaint, countPaint, pillBg)
        drawMarkerRow(canvas, secondRow, bounds.left + 8f, bounds.top + 31.5f, textPaint, countPaint, pillBg)
    }

    private fun drawMarkerRow(
        canvas: Canvas,
        items: List<MarkerChip>,
        startX: Float,
        topY: Float,
        textPaint: Paint,
        countPaint: Paint,
        pillBg: Paint
    ) {
        var x = startX
        val pillH = 12f
        items.forEach { chip ->
            val labelText = "${chip.label}: "
            val countText = chip.count.toString()
            val w = textPaint.measureText(labelText) + countPaint.measureText(countText) + 10f
            pillBg.color = when {
                chip.alert -> PdfReportStyle.COLOR_ALERT_BG
                chip.count > 0 -> PdfReportStyle.COLOR_PILL_ACTIVE_BG
                else -> PdfReportStyle.COLOR_TILE_BG
            }
            val fg = when {
                chip.alert -> PdfReportStyle.COLOR_ALERT
                chip.count > 0 -> PdfReportStyle.COLOR_INK
                else -> PdfReportStyle.COLOR_MUTED
            }
            canvas.drawRoundRect(RectF(x, topY, x + w, topY + pillH), 4f, 4f, pillBg)
            textPaint.color = fg
            countPaint.color = fg
            val baseline = topY + 8.5f
            canvas.drawText(labelText, x + 5f, baseline, textPaint)
            canvas.drawText(countText, x + 5f + textPaint.measureText(labelText), baseline, countPaint)
            x += w + 4.5f
        }
    }

    private fun drawScalesRow(canvas: Canvas, bounds: RectF, scales: List<ReportScaleOverview>) {
        val gap = 6f
        val colW = (bounds.width() - gap * 2f) / 3f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfReportStyle.COLOR_CARD_BG }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = PdfReportStyle.COLOR_BORDER
            strokeWidth = 0.7f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 8.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 6.4f
            textAlign = Paint.Align.RIGHT
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 6.7f
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        scales.forEachIndexed { idx, scale ->
            val left = bounds.left + idx * (colW + gap)
            val rect = RectF(left, bounds.top, left + colW, bounds.bottom)
            canvas.drawRoundRect(rect, 7f, 7f, bg)
            canvas.drawRoundRect(rect, 7f, 7f, stroke)

            canvas.drawText(PdfTextFormatters.scaleCode(scale.scaleType), left + 8f, bounds.top + 14f, titlePaint)
            canvas.drawText("окно ${scale.windowDays} дн.", rect.right - 8f, bounds.top + 14f, windowPaint)

            // Auto line
            val autoText = if (scale.autoScore != null) {
                val band = PdfTextFormatters.scaleSeverityLabel(scale.scaleType, scale.autoScore)
                "${scale.autoScore}/${scale.maxScore} · $band"
            } else {
                "—"
            }
            val autoMeta = if (scale.autoFilledDays != null) {
                "Авто (${scale.autoFilledDays}/${scale.windowDays} дн.):"
            } else {
                "Авто:"
            }
            canvas.drawText(autoMeta, left + 8f, bounds.top + 27f, labelPaint)
            canvas.drawText(autoText, left + 8f, bounds.top + 36.5f, valPaint)

            // Confirmed line
            val confirmed = scale.latestConfirmed
            val confirmedMeta = if (confirmed != null) {
                "Подтв. (${PdfTextFormatters.formatDateFull(confirmed.date)}):"
            } else {
                "Подтверждённый замер:"
            }
            val confirmedText = if (confirmed != null) {
                val band = PdfTextFormatters.scaleSeverityLabel(scale.scaleType, confirmed.totalScore)
                "${confirmed.totalScore}/${scale.maxScore} · $band"
            } else {
                "Нет сохранённого замера"
            }
            canvas.drawText(confirmedMeta, left + 8f, bounds.top + 48.5f, labelPaint)
            valPaint.color = if (confirmed != null) PdfReportStyle.COLOR_ACCENT else PdfReportStyle.COLOR_MUTED
            canvas.drawText(confirmedText, left + 8f, bounds.top + 58f, valPaint)
            valPaint.color = PdfReportStyle.COLOR_INK
        }
    }

    private fun buildKpiCells(summary: DiaryAnalyticsSummary): List<KpiCell> =
        buildCoreKpiCells(summary) + buildThresholdKpiCells(summary)

    private fun buildCoreKpiCells(summary: DiaryAnalyticsSummary): List<KpiCell> {
        val rangeVal = if (summary.minMood != null && summary.maxMood != null) {
            "${PdfTextFormatters.formatSignedMood(summary.minMood)} .. " +
                PdfTextFormatters.formatSignedMood(summary.maxMood)
        } else {
            "—"
        }
        val splitVal = if (summary.filledDays > 0) {
            "${summary.lowMoodDays} / ${summary.neutralMoodDays} / ${summary.elevatedMoodDays}"
        } else {
            "—"
        }
        val avgMoodTint = summary.averageMood?.let { avg ->
            val base = PdfReportStyle.moodContainerColor(avg.roundToInt().coerceIn(-5, 5))
            android.graphics.Color.argb(
                72,
                android.graphics.Color.red(base),
                android.graphics.Color.green(base),
                android.graphics.Color.blue(base)
            )
        }

        return listOf(
            KpiCell("Заполнено дней", "${summary.filledDays} дн."),
            KpiCell(
                label = "Ср. настроение",
                value = summary.averageMood?.let(PdfTextFormatters::formatSignedAvg) ?: "—",
                tintColor = avgMoodTint
            ),
            KpiCell(
                label = "Размах мин / макс",
                value = rangeVal,
                sub = summary.moodSpan?.let { "амплитуда $it" }
            ),
            KpiCell("Спад / норма / подъём", splitVal, "≤−1 · 0 · ≥+1"),
            KpiCell("Средняя энергия", formatOutOfTenCell(summary.averageEnergy)),
            KpiCell(
                label = "Средний сон",
                value = summary.averageSleepHours?.let { "${PdfTextFormatters.formatDecimalOne(it)} ч" } ?: "—",
                sub = summary.sleepTrackedDays.takeIf { it > 0 }?.let { "указан за $it дн." }
            ),
            KpiCell("Средняя тревога", formatOutOfTenCell(summary.averageAnxiety)),
            KpiCell("Средняя раздраж.", formatOutOfTenCell(summary.averageIrritability))
        )
    }

    private fun formatOutOfTenCell(value: Double?): String =
        value?.let { "${PdfTextFormatters.formatDecimalOne(it)} / 10" } ?: "—"

    private fun buildThresholdKpiCells(summary: DiaryAnalyticsSummary): List<KpiCell> = listOf(
        KpiCell("Выраженный спад", "${summary.markedLowDays} дн.", "настроение ≤ −2"),
        KpiCell("Выраженный подъём", "${summary.markedHighDays} дн.", "настроение ≥ +2"),
        KpiCell("Смешанные дни", "${summary.mixedDays} дн.", "спад + активация"),
        KpiCell(
            label = "Недосып (< 6 ч)",
            value = "${summary.shortSleepDays} дн.",
            valueColor = if (summary.shortSleepDays > 0) PdfReportStyle.COLOR_ALERT else null
        ),
        KpiCell("Качели внутри дня", "${summary.intraDaySwingsDays} дн."),
        KpiCell(
            label = "Выс. тревога / раздр.",
            value = "${summary.highAnxietyOrIrritabilityDays} дн.",
            sub = "≥7 · Тр ${summary.highAnxietyDays} · Рз ${summary.highIrritabilityDays}"
        ),
        KpiCell(
            label = "Шкала настроения",
            value = "−5 .. +5",
            sub = "0 — ровный фон"
        )
    )

    private data class KpiCell(
        val label: String,
        val value: String,
        val sub: String? = null,
        val tintColor: Int? = null,
        val valueColor: Int? = null
    )

    private data class MarkerChip(val label: String, val count: Int, val alert: Boolean = false)
}
