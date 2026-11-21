package app.spectr.mood.platform.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.spectr.mood.domain.report.DoctorReportSnapshot
import app.spectr.mood.domain.report.ReportDailyRow

/**
 * Paginated vector renderer for optional PDF pages 2+:
 * - Daily summary table (metrics, sleep, symptoms, alcohol/meds)
 * - Optional personal notes, sleep comments, and timestamped moments
 */
internal object PdfDetailPagesRenderer {

    private const val HEADER_TOP = 24f
    private const val FOOTER_Y = 824f
    private const val CONTENT_BOTTOM = 804f
    private const val LINE_HEIGHT = 9.8f

    // Column X offsets across 547pt width (starting at MARGIN_H = 24f)
    private const val COL_DATE_W = 42f
    private const val COL_MOOD_W = 34f
    private const val COL_DYN_W = 64f
    private const val COL_SLEEP_W = 46f
    private const val COL_NUM_W = 24f // Energy, Anxiety, Irritability (3 * 24 = 72)

    fun renderAdditionalPages(document: PdfDocument, snapshot: DoctorReportSnapshot, startPageNumber: Int): Int {
        if (!snapshot.includeDailyTable && !snapshot.includePersonalNotesAndMoments) {
            return startPageNumber - 1
        }
        return if (snapshot.includeDailyTable) {
            renderDailyTablePages(document, snapshot, startPageNumber)
        } else {
            renderPersonalNotesOnlyPages(document, snapshot, startPageNumber)
        }
    }

    private fun renderDailyTablePages(
        document: PdfDocument,
        snapshot: DoctorReportSnapshot,
        startPageNumber: Int
    ): Int {
        val rows = snapshot.dailyRows
        var pageNumber = startPageNumber
        var page = startPage(document, pageNumber)
        var cursorY = drawTablePageHeader(
            canvas = page.canvas,
            title = if (snapshot.includePersonalNotesAndMoments) {
                "Таблица по дням (с личными заметками и моментами)"
            } else {
                "Таблица по дням (без личных заметок)"
            },
            snapshot = snapshot
        )

        if (rows.isEmpty()) {
            drawEmptyMessage(page.canvas, cursorY + 18f, "За выбранный период нет записей.")
            drawPageFooter(page.canvas, pageNumber)
            document.finishPage(page)
            return pageNumber
        }

        val detailColLeft = PdfReportStyle.MARGIN_H + COL_DATE_W + COL_MOOD_W + COL_DYN_W +
            COL_SLEEP_W + COL_NUM_W * 3f + 4f
        val detailColWidth = (PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H - detailColLeft)
            .coerceAtLeast(100f)

        val bodyPaint = createBodyPaint()
        val notePaint = createNotePaint()

        rows.forEachIndexed { index, row ->
            val detailText = buildRowDetailsText(row)
            val detailLines = PdfTextFormatters.wrapText(detailText.ifEmpty { "—" }, bodyPaint, detailColWidth)
            val personalLines = if (snapshot.includePersonalNotesAndMoments) {
                buildPersonalLines(row, notePaint, PdfReportStyle.CONTENT_WIDTH - 16f)
            } else {
                emptyList()
            }

            val baseRowH = (detailLines.size.coerceAtLeast(1) * LINE_HEIGHT + 7f).coerceAtLeast(18f)
            val personalBlockH = if (personalLines.isNotEmpty()) {
                personalLines.size * (LINE_HEIGHT - 0.6f) + 6f
            } else {
                0f
            }
            val totalRowH = baseRowH + personalBlockH

            if (cursorY + totalRowH > CONTENT_BOTTOM) {
                drawPageFooter(page.canvas, pageNumber)
                document.finishPage(page)
                pageNumber += 1
                page = startPage(document, pageNumber)
                cursorY = drawTablePageHeader(
                    canvas = page.canvas,
                    title = "Таблица по дням (продолжение)",
                    snapshot = snapshot
                )
            }

            drawDailyTableRow(
                canvas = page.canvas,
                row = row,
                topY = cursorY,
                totalHeight = totalRowH,
                baseRowHeight = baseRowH,
                detailColLeft = detailColLeft,
                detailLines = detailLines,
                personalLines = personalLines,
                isAlternate = index % 2 == 1,
                bodyPaint = bodyPaint,
                notePaint = notePaint
            )
            cursorY += totalRowH
        }

        drawPageFooter(page.canvas, pageNumber)
        document.finishPage(page)
        return pageNumber
    }

    private fun renderPersonalNotesOnlyPages(
        document: PdfDocument,
        snapshot: DoctorReportSnapshot,
        startPageNumber: Int
    ): Int {
        val rowsWithNotes = snapshot.dailyRows.filter { it.hasPersonalContent }
        var pageNumber = startPageNumber
        var page = startPage(document, pageNumber)
        var cursorY = drawNotesSectionHeader(page.canvas, "Личные заметки и отметки момента", snapshot)

        if (rowsWithNotes.isEmpty()) {
            drawEmptyMessage(page.canvas, cursorY + 18f, "За выбранный период нет заметок и отметок момента.")
            drawPageFooter(page.canvas, pageNumber)
            document.finishPage(page)
            return pageNumber
        }

        val notePaint = createNotePaint()
        val cardTextWidth = PdfReportStyle.CONTENT_WIDTH - 16f

        rowsWithNotes.forEach { row ->
            val lines = buildPersonalLines(row, notePaint, cardTextWidth)
            val cardHeight = 20f + lines.size * (LINE_HEIGHT + 0.5f) + 8f
            if (cursorY + cardHeight > CONTENT_BOTTOM) {
                drawPageFooter(page.canvas, pageNumber)
                document.finishPage(page)
                pageNumber += 1
                page = startPage(document, pageNumber)
                cursorY = drawNotesSectionHeader(
                    page.canvas,
                    "Личные заметки и отметки момента (продолжение)",
                    snapshot
                )
            }
            drawPersonalNoteCard(page.canvas, row, cursorY, cardHeight, lines, notePaint)
            cursorY += cardHeight + 6f
        }

        drawPageFooter(page.canvas, pageNumber)
        document.finishPage(page)
        return pageNumber
    }

    private fun drawTablePageHeader(canvas: Canvas, title: String, snapshot: DoctorReportSnapshot): Float {
        var y = drawSectionTitleBar(canvas, title, snapshot)
        val headerRect = RectF(PdfReportStyle.MARGIN_H, y, PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H, y + 16f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfReportStyle.COLOR_PILL_ACTIVE_BG }
        canvas.drawRoundRect(headerRect, 4f, 4f, bgPaint)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val baseline = y + 11f
        var x = PdfReportStyle.MARGIN_H + 4f
        canvas.drawText("Дата", x, baseline, headerPaint)
        x += COL_DATE_W
        canvas.drawText("Настр.", x, baseline, headerPaint)
        x += COL_MOOD_W
        canvas.drawText("Динамика", x, baseline, headerPaint)
        x += COL_DYN_W
        canvas.drawText("Сон", x, baseline, headerPaint)
        x += COL_SLEEP_W
        canvas.drawText("Эн", x + 3f, baseline, headerPaint)
        x += COL_NUM_W
        canvas.drawText("Тр", x + 3f, baseline, headerPaint)
        x += COL_NUM_W
        canvas.drawText("Рз", x + 3f, baseline, headerPaint)
        x += COL_NUM_W
        canvas.drawText("Симптомы и детали", x + 2f, baseline, headerPaint)

        y += 18f
        return y
    }

    private fun drawNotesSectionHeader(canvas: Canvas, title: String, snapshot: DoctorReportSnapshot): Float =
        drawSectionTitleBar(canvas, title, snapshot) + 4f

    private fun drawSectionTitleBar(canvas: Canvas, title: String, snapshot: DoctorReportSnapshot): Float {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 7.5f
            textAlign = Paint.Align.RIGHT
        }
        val range = if (snapshot.effectiveStartDate != null && snapshot.effectiveEndDate != null) {
            "${PdfTextFormatters.formatDateFull(snapshot.effectiveStartDate)} — " +
                PdfTextFormatters.formatDateFull(snapshot.effectiveEndDate)
        } else {
            PdfTextFormatters.formatDateFull(snapshot.generatedDate)
        }
        canvas.drawText(title, PdfReportStyle.MARGIN_H, HEADER_TOP + 12f, titlePaint)
        canvas.drawText(range, PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H, HEADER_TOP + 12f, periodPaint)
        val divider = Paint().apply {
            color = PdfReportStyle.COLOR_BORDER
            strokeWidth = 0.8f
        }
        canvas.drawLine(
            PdfReportStyle.MARGIN_H,
            HEADER_TOP + 18f,
            PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H,
            HEADER_TOP + 18f,
            divider
        )
        return HEADER_TOP + 24f
    }

    @Suppress("LongParameterList")
    private fun drawDailyTableRow(
        canvas: Canvas,
        row: ReportDailyRow,
        topY: Float,
        totalHeight: Float,
        baseRowHeight: Float,
        detailColLeft: Float,
        detailLines: List<String>,
        personalLines: List<String>,
        isAlternate: Boolean,
        bodyPaint: Paint,
        notePaint: Paint
    ) {
        val rowRect = RectF(
            PdfReportStyle.MARGIN_H,
            topY,
            PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H,
            topY + totalHeight
        )
        if (isAlternate) {
            val bg = Paint().apply { color = PdfReportStyle.COLOR_ROW_ALT }
            canvas.drawRect(rowRect, bg)
        }

        val baseline = topY + 11.5f
        var x = PdfReportStyle.MARGIN_H + 4f

        // Date
        bodyPaint.color = PdfReportStyle.COLOR_INK
        canvas.drawText(PdfTextFormatters.formatDateShort(row.date), x, baseline, bodyPaint)
        x += COL_DATE_W

        // Mood pill
        val pillRect = RectF(x, topY + 3f, x + 24f, topY + 14.5f)
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfReportStyle.moodContainerColor(row.mood) }
        canvas.drawRoundRect(pillRect, 3f, 3f, pillBg)
        val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.moodContentColor(row.mood)
            textSize = 7f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(PdfTextFormatters.formatSignedMood(row.mood), pillRect.centerX(), baseline - 0.3f, pillText)
        x += COL_MOOD_W

        // Dynamics
        bodyPaint.color = PdfReportStyle.COLOR_INK
        canvas.drawText(PdfTextFormatters.dynamicsShort(row.dailyDynamics), x, baseline, bodyPaint)
        x += COL_DYN_W

        // Sleep
        bodyPaint.color = if (row.isShortSleep) PdfReportStyle.COLOR_ALERT else PdfReportStyle.COLOR_INK
        canvas.drawText(PdfTextFormatters.formatSleepMinutes(row.sleepMinutes), x, baseline, bodyPaint)
        x += COL_SLEEP_W

        // Energy, Anxiety, Irritability
        bodyPaint.color = PdfReportStyle.COLOR_INK
        canvas.drawText(row.energy.toString(), x + 4f, baseline, bodyPaint)
        x += COL_NUM_W
        bodyPaint.color = if (row.anxiety >= 7) PdfReportStyle.COLOR_ALERT else PdfReportStyle.COLOR_INK
        canvas.drawText(row.anxiety.toString(), x + 4f, baseline, bodyPaint)
        x += COL_NUM_W
        bodyPaint.color = if (row.irritability >= 7) PdfReportStyle.COLOR_ALERT else PdfReportStyle.COLOR_INK
        canvas.drawText(row.irritability.toString(), x + 4f, baseline, bodyPaint)

        // Symptoms & details wrapped
        bodyPaint.color = PdfReportStyle.COLOR_MUTED
        detailLines.forEachIndexed { idx, line ->
            canvas.drawText(line, detailColLeft, baseline + idx * LINE_HEIGHT, bodyPaint)
        }

        // Optional personal notes & moments beneath row
        if (personalLines.isNotEmpty()) {
            var noteY = topY + baseRowHeight + 6f
            personalLines.forEach { line ->
                canvas.drawText(line, PdfReportStyle.MARGIN_H + 10f, noteY, notePaint)
                noteY += LINE_HEIGHT - 0.6f
            }
        }

        val divider = Paint().apply {
            color = PdfReportStyle.COLOR_GRID
            strokeWidth = 0.5f
        }
        canvas.drawLine(rowRect.left, rowRect.bottom, rowRect.right, rowRect.bottom, divider)
    }

    private fun drawPersonalNoteCard(
        canvas: Canvas,
        row: ReportDailyRow,
        topY: Float,
        cardHeight: Float,
        lines: List<String>,
        notePaint: Paint
    ) {
        val rect = RectF(
            PdfReportStyle.MARGIN_H,
            topY,
            PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H,
            topY + cardHeight
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfReportStyle.COLOR_CARD_BG }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = PdfReportStyle.COLOR_BORDER
            strokeWidth = 0.6f
        }
        canvas.drawRoundRect(rect, 6f, 6f, fill)
        canvas.drawRoundRect(rect, 6f, 6f, stroke)

        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerText = "${PdfTextFormatters.formatDateFull(row.date)} · Настроение " +
            "${PdfTextFormatters.formatSignedMood(row.mood)} · ${PdfTextFormatters.dynamicsShort(row.dailyDynamics)}"
        canvas.drawText(headerText, rect.left + 8f, rect.top + 13f, headPaint)

        var lineY = rect.top + 25f
        lines.forEach { line ->
            canvas.drawText(line, rect.left + 8f, lineY, notePaint)
            lineY += LINE_HEIGHT + 0.5f
        }
    }

    private fun buildRowDetailsText(row: ReportDailyRow): String {
        val parts = ArrayList<String>(2)
        val symptoms = PdfTextFormatters.formatSymptomTags(row.symptoms, row.isMixedDay)
        if (symptoms.isNotEmpty()) parts.add(symptoms)
        val details = PdfTextFormatters.formatDetailsSuffix(row.alcoholStatus, row.medicationStatus)
        if (details.isNotEmpty()) parts.add(details)
        return parts.joinToString(" | ")
    }

    private fun buildPersonalLines(row: ReportDailyRow, paint: Paint, maxWidth: Float): List<String> {
        val result = ArrayList<String>()
        if (row.note.isNotBlank()) {
            result.addAll(PdfTextFormatters.wrapText("Заметка: ${row.note}", paint, maxWidth))
        }
        if (row.sleepNote.isNotBlank()) {
            result.addAll(PdfTextFormatters.wrapText("Сон: ${row.sleepNote}", paint, maxWidth))
        }
        row.moments.forEach { moment ->
            val time = PdfTextFormatters.formatTime(moment.timestampEpochMs)
            val mood = PdfTextFormatters.formatSignedMood(moment.mood)
            val extras = buildList {
                moment.energy?.let { add("Эн $it") }
                moment.anxiety?.let { add("Тр $it") }
                moment.irritability?.let { add("Рз $it") }
            }.joinToString(", ")
            val prefix = buildString {
                append("Момент $time ($mood")
                if (extras.isNotEmpty()) append(", $extras")
                append(")")
                if (moment.note.isNotBlank()) append(": ${moment.note.trim()}")
            }
            result.addAll(PdfTextFormatters.wrapText(prefix, paint, maxWidth))
        }
        return result
    }

    private fun startPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(
            PdfReportStyle.PAGE_WIDTH,
            PdfReportStyle.PAGE_HEIGHT,
            pageNumber
        ).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawColor(PdfReportStyle.COLOR_PAGE_BG)
        return page
    }

    private fun drawEmptyMessage(canvas: Canvas, y: Float, text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 9f
        }
        canvas.drawText(text, PdfReportStyle.MARGIN_H, y, paint)
    }

    fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 6.8f
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 6.8f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "Spectr · Справочный отчёт самонаблюдения (не является медицинским диагнозом)",
            PdfReportStyle.MARGIN_H,
            FOOTER_Y,
            leftPaint
        )
        canvas.drawText(
            "Стр. $pageNumber",
            PdfReportStyle.PAGE_WIDTH - PdfReportStyle.MARGIN_H,
            FOOTER_Y,
            rightPaint
        )
    }

    private fun createBodyPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PdfReportStyle.COLOR_INK
        textSize = 7.3f
    }

    private fun createNotePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PdfReportStyle.COLOR_MUTED
        textSize = 7.1f
    }
}
