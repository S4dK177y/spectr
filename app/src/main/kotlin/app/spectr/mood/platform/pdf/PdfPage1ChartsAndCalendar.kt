package app.spectr.mood.platform.pdf

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import app.spectr.mood.domain.calendar.CalendarMonthGrid
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.chart.OverviewChartData
import app.spectr.mood.ui.theme.MoodColorScale

/**
 * Vector renderers for the monthly mood calendar and the 3 clinical charts on PDF Page 1,
 * reusing pure-Kotlin [CalendarMonthGrid] and [ChartGeometry].
 */
internal object PdfPage1ChartsAndCalendar {

    private val WEEKDAY_HEADERS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    fun drawCalendarCard(canvas: Canvas, bounds: RectF, grid: CalendarMonthGrid) {
        drawCardFrame(canvas, bounds)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 7f
            textAlign = Paint.Align.RIGHT
        }

        val pad = 8f
        canvas.drawText(
            PdfTextFormatters.formatYearMonth(grid.yearMonth),
            bounds.left + pad,
            bounds.top + 15f,
            titlePaint
        )
        canvas.drawText(
            "${grid.filledDaysInMonth} дн.",
            bounds.right - pad,
            bounds.top + 15f,
            subPaint
        )

        val headerY = bounds.top + 26f
        val innerLeft = bounds.left + pad
        val innerWidth = bounds.width() - pad * 2f
        val gap = 2.5f
        val cellW = (innerWidth - gap * 6f) / 7f

        val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.2f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        WEEKDAY_HEADERS.forEachIndexed { idx, label ->
            weekdayPaint.color = if (idx >= 5) PdfReportStyle.COLOR_ACCENT else PdfReportStyle.COLOR_MUTED
            val cx = innerLeft + idx * (cellW + gap) + cellW / 2f
            canvas.drawText(label, cx, headerY, weekdayPaint)
        }

        val gridTop = headerY + 4f
        val legendHeight = 17f
        val availableGridH = (bounds.bottom - pad - legendHeight - gridTop).coerceAtLeast(60f)
        val rowCount = grid.weeks.size.coerceAtLeast(1)
        val cellH = ((availableGridH - gap * (rowCount - 1)) / rowCount).coerceIn(13f, 22f)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.4f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 5.6f
            textAlign = Paint.Align.CENTER
        }

        grid.weeks.forEachIndexed { rowIdx, week ->
            val top = gridTop + rowIdx * (cellH + gap)
            week.forEachIndexed { colIdx, cell ->
                val left = innerLeft + colIdx * (cellW + gap)
                drawCalendarDayCell(
                    canvas = canvas,
                    rect = RectF(left, top, left + cellW, top + cellH),
                    cell = cell,
                    fillPaint = fillPaint,
                    dayPaint = dayPaint,
                    moodPaint = moodPaint
                )
            }
        }

        drawCalendarMoodLegend(canvas, innerLeft, bounds.bottom - pad - 11f, innerWidth)
    }

    private fun drawCalendarDayCell(
        canvas: Canvas,
        rect: RectF,
        cell: app.spectr.mood.domain.calendar.CalendarDayCell,
        fillPaint: Paint,
        dayPaint: Paint,
        moodPaint: Paint
    ) {
        val entry = cell.entry
        val (bg, fg) = when {
            !cell.isCurrentMonth -> PdfReportStyle.COLOR_CARD_BG to PdfReportStyle.COLOR_GRID_STRONG
            entry != null -> PdfReportStyle.moodContainerColor(entry.mood) to
                PdfReportStyle.moodContentColor(entry.mood)
            else -> PdfReportStyle.COLOR_TILE_BG to PdfReportStyle.COLOR_MUTED
        }
        fillPaint.color = bg
        canvas.drawRoundRect(rect, 3f, 3f, fillPaint)

        if (cell.isCurrentMonth && entry == null) {
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                color = PdfReportStyle.COLOR_BORDER
            }
            canvas.drawRoundRect(rect, 3f, 3f, border)
        }

        if (cell.isCurrentMonth && cell.isMixedDay) {
            fillPaint.color = fg
            canvas.drawCircle(rect.right - 3.2f, rect.top + 3.2f, 1.5f, fillPaint)
        }

        if (!cell.isCurrentMonth) return
        val cx = rect.centerX()
        val cellH = rect.height()
        dayPaint.color = fg
        if (entry != null) {
            canvas.drawText(cell.date.dayOfMonth.toString(), cx, rect.top + cellH * 0.46f, dayPaint)
            moodPaint.color = fg
            canvas.drawText(
                PdfTextFormatters.formatSignedMood(entry.mood),
                cx,
                rect.top + cellH * 0.85f,
                moodPaint
            )
        } else {
            canvas.drawText(cell.date.dayOfMonth.toString(), cx, rect.centerY() + 2.2f, dayPaint)
        }
    }

    fun drawMoodChartCard(canvas: Canvas, bounds: RectF, chartData: OverviewChartData) {
        drawCardFrame(canvas, bounds)
        drawChartTitle(canvas, bounds, "Настроение (−5 .. +5)", "Кольцо — смеш. день")
        if (chartData.isEmpty) {
            drawChartEmptyNotice(canvas, bounds)
            return
        }

        val left = bounds.left + 26f
        val right = bounds.right - 10f
        val plotLeft = left + 6f
        val plotRight = (right - 6f).coerceAtLeast(plotLeft)
        val top = bounds.top + 24f
        val bottom = bounds.bottom - 18f

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_GRID
            strokeWidth = 0.6f
        }
        val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_GRID_STRONG
            strokeWidth = 0.9f
        }
        val axisPaint = createAxisLabelPaint(Paint.Align.RIGHT)

        listOf(-5, -2, 0, 2, 5).forEach { tick ->
            val y = ChartGeometry.valueToY(tick.toDouble(), -5.0, 5.0, top, bottom)
            canvas.drawLine(left, y, right, y, if (tick == 0) zeroPaint else gridPaint)
            canvas.drawText(PdfTextFormatters.formatSignedMood(tick), left - 4f, y + 2.2f, axisPaint)
        }

        val coords = chartData.points.map { pt ->
            ChartGeometry.fractionToX(pt.xFraction, plotLeft, plotRight) to
                ChartGeometry.valueToY(pt.mood.toDouble(), -5.0, 5.0, top, bottom)
        }
        drawPolyline(canvas, coords, PdfReportStyle.COLOR_ACCENT, 1.5f)

        val dotRadius = if (coords.size > 35) 2.2f else 3.1f
        val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = PdfReportStyle.COLOR_TILE_BG
            strokeWidth = 0.7f
        }
        val mixedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = PdfReportStyle.COLOR_MIXED_RING
            strokeWidth = 1f
        }

        chartData.points.forEachIndexed { idx, pt ->
            val (x, y) = coords[idx]
            if (pt.isMixedDay) {
                canvas.drawCircle(x, y, dotRadius + 1.6f, mixedStroke)
            }
            dotFill.color = PdfReportStyle.moodContainerColor(pt.mood)
            canvas.drawCircle(x, y, dotRadius, dotFill)
            canvas.drawCircle(x, y, dotRadius, dotStroke)
        }

        drawXDateTicks(canvas, chartData, plotLeft, plotRight, bottom + 10f)
    }

    fun drawEnergySleepChartCard(canvas: Canvas, bounds: RectF, chartData: OverviewChartData) {
        drawCardFrame(canvas, bounds)
        drawChartTitle(
            canvas,
            bounds,
            "Энергия (0–10) и сон (ч)",
            "Линия — энергия · Столбцы — сон · Пунктир — < 6 ч"
        )
        if (chartData.isEmpty) {
            drawChartEmptyNotice(canvas, bounds)
            return
        }

        val left = bounds.left + 26f
        val right = bounds.right - 26f
        val plotLeft = left + 7f
        val plotRight = (right - 7f).coerceAtLeast(plotLeft)
        val top = bounds.top + 24f
        val bottom = bounds.bottom - 18f
        val sleepMax = chartData.sleepAxisMaxHours

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_GRID
            strokeWidth = 0.6f
        }
        val leftAxis = createAxisLabelPaint(Paint.Align.RIGHT)
        val rightAxis = createAxisLabelPaint(Paint.Align.LEFT)

        listOf(0, 5, 10).forEach { tick ->
            val y = ChartGeometry.valueToY(tick.toDouble(), 0.0, 10.0, top, bottom)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(tick.toString(), left - 4f, y + 2.2f, leftAxis)
        }
        listOf(0.0, ChartGeometry.SHORT_SLEEP_THRESHOLD_HOURS, sleepMax).forEach { hours ->
            val y = ChartGeometry.valueToY(hours, 0.0, sleepMax, top, bottom)
            canvas.drawText("${hours.toInt()}ч", right + 4f, y + 2.2f, rightAxis)
        }

        val barWidth = ((plotRight - plotLeft) / (chartData.points.size.coerceAtLeast(1) * 1.8f))
            .coerceIn(2.2f, 9.5f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        chartData.points.forEach { pt ->
            val hours = pt.sleepHours ?: return@forEach
            val x = ChartGeometry.fractionToX(pt.xFraction, plotLeft, plotRight)
            val barTop = ChartGeometry.valueToY(hours, 0.0, sleepMax, top, bottom)
            barPaint.color = if (pt.isShortSleep) PdfReportStyle.COLOR_ALERT else PdfReportStyle.COLOR_SLEEP
            barPaint.alpha = if (pt.isShortSleep) 135 else 92
            canvas.drawRoundRect(
                RectF(x - barWidth / 2f, barTop, x + barWidth / 2f, bottom),
                2f,
                2f,
                barPaint
            )
        }

        val thresholdY = ChartGeometry.valueToY(ChartGeometry.SHORT_SLEEP_THRESHOLD_HOURS, 0.0, sleepMax, top, bottom)
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_ALERT
            alpha = 185
            strokeWidth = 0.9f
            pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
        }
        canvas.drawLine(left, thresholdY, right, thresholdY, dashPaint)

        val energyCoords = chartData.points.map { pt ->
            ChartGeometry.fractionToX(pt.xFraction, plotLeft, plotRight) to
                ChartGeometry.valueToY(pt.energy.toDouble(), 0.0, 10.0, top, bottom)
        }
        drawPolylineWithDots(canvas, energyCoords, PdfReportStyle.COLOR_ACCENT)
        drawXDateTicks(canvas, chartData, plotLeft, plotRight, bottom + 10f)
    }

    fun drawAnxietyIrritabilityChartCard(canvas: Canvas, bounds: RectF, chartData: OverviewChartData) {
        drawCardFrame(canvas, bounds)
        drawChartTitle(
            canvas,
            bounds,
            "Тревога и раздражительность (0–10)",
            "Фиол. — тревога · Красн. — раздраж. · Пунктир — порог ≥ 7"
        )
        if (chartData.isEmpty) {
            drawChartEmptyNotice(canvas, bounds)
            return
        }

        val left = bounds.left + 26f
        val right = bounds.right - 12f
        val plotLeft = left + 7f
        val plotRight = (right - 7f).coerceAtLeast(plotLeft)
        val top = bounds.top + 24f
        val bottom = bounds.bottom - 18f

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_GRID
            strokeWidth = 0.6f
        }
        val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_GRID_STRONG
            strokeWidth = 0.9f
            pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
        }
        val axisPaint = createAxisLabelPaint(Paint.Align.RIGHT)

        listOf(0, 3, 7, 10).forEach { tick ->
            val y = ChartGeometry.valueToY(tick.toDouble(), 0.0, 10.0, top, bottom)
            canvas.drawLine(left, y, right, y, if (tick == 7) thresholdPaint else gridPaint)
            canvas.drawText(tick.toString(), left - 4f, y + 2.2f, axisPaint)
        }

        val anxietyCoords = chartData.points.map { pt ->
            ChartGeometry.fractionToX(pt.xFraction, plotLeft, plotRight) to
                ChartGeometry.valueToY(pt.anxiety.toDouble(), 0.0, 10.0, top, bottom)
        }
        val irritCoords = chartData.points.map { pt ->
            ChartGeometry.fractionToX(pt.xFraction, plotLeft, plotRight) to
                ChartGeometry.valueToY(pt.irritability.toDouble(), 0.0, 10.0, top, bottom)
        }

        drawPolylineWithDots(canvas, anxietyCoords, PdfReportStyle.COLOR_ANXIETY)
        drawPolylineWithDots(canvas, irritCoords, PdfReportStyle.COLOR_ALERT)
        drawXDateTicks(canvas, chartData, plotLeft, plotRight, bottom + 10f)
    }

    private fun drawCalendarMoodLegend(canvas: Canvas, left: Float, top: Float, width: Float) {
        val scores = (MoodColorScale.MIN_MOOD..MoodColorScale.MAX_MOOD).toList()
        val gap = 1.2f
        val segW = (width - gap * (scores.size - 1)) / scores.size
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        scores.forEachIndexed { idx, score ->
            val x = left + idx * (segW + gap)
            paint.color = PdfReportStyle.moodContainerColor(score)
            canvas.drawRoundRect(RectF(x, top, x + segW, top + 4.5f), 1.5f, 1.5f, paint)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 5.6f
        }
        canvas.drawText("−5", left, top + 10.5f, labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("0 · точка — смеш.", left + width / 2f, top + 10.5f, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("+5", left + width, top + 10.5f, labelPaint)
    }

    private fun drawCardFrame(canvas: Canvas, bounds: RectF) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = PdfReportStyle.COLOR_CARD_BG
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = PdfReportStyle.COLOR_BORDER
            strokeWidth = 0.7f
        }
        canvas.drawRoundRect(bounds, 8f, 8f, fill)
        canvas.drawRoundRect(bounds, 8f, 8f, stroke)
    }

    private fun drawChartTitle(canvas: Canvas, bounds: RectF, title: String, subtitle: String) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_INK
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 6.3f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(title, bounds.left + 8f, bounds.top + 14.5f, titlePaint)
        canvas.drawText(subtitle, bounds.right - 8f, bounds.top + 14.5f, subPaint)
    }

    private fun drawChartEmptyNotice(canvas: Canvas, bounds: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PdfReportStyle.COLOR_MUTED
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Нет данных за выбранный период", bounds.centerX(), bounds.centerY() + 4f, paint)
    }

    private fun drawPolyline(canvas: Canvas, coords: List<Pair<Float, Float>>, color: Int, strokeWidth: Float) {
        if (coords.size < 2) return
        val path = Path().apply {
            moveTo(coords.first().first, coords.first().second)
            for (i in 1 until coords.size) {
                lineTo(coords[i].first, coords[i].second)
            }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPolylineWithDots(canvas: Canvas, coords: List<Pair<Float, Float>>, color: Int) {
        drawPolyline(canvas, coords, color, 1.4f)
        val radius = if (coords.size > 35) 1.7f else 2.4f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = PdfReportStyle.COLOR_TILE_BG
            strokeWidth = 0.6f
        }
        coords.forEach { (x, y) ->
            canvas.drawCircle(x, y, radius, fill)
            canvas.drawCircle(x, y, radius, border)
        }
    }

    private fun drawXDateTicks(
        canvas: Canvas,
        chartData: OverviewChartData,
        plotLeft: Float,
        plotRight: Float,
        baselineY: Float
    ) {
        val tickPaint = createAxisLabelPaint(Paint.Align.CENTER)
        val count = chartData.xTicks.size
        chartData.xTicks.forEachIndexed { idx, tick ->
            val x = ChartGeometry.fractionToX(tick.xFraction, plotLeft, plotRight)
            tickPaint.textAlign = when {
                count <= 1 -> Paint.Align.CENTER
                idx == 0 -> Paint.Align.LEFT
                idx == count - 1 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(PdfTextFormatters.formatChartDate(tick.date), x, baselineY, tickPaint)
        }
    }

    private fun createAxisLabelPaint(align: Paint.Align): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PdfReportStyle.COLOR_MUTED
        textSize = 6.3f
        textAlign = align
    }
}
