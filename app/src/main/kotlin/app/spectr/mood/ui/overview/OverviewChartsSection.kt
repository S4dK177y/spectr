package app.spectr.mood.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spectr.mood.R
import app.spectr.mood.domain.chart.ChartDateTick
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.chart.OverviewChartData
import app.spectr.mood.domain.chart.OverviewChartPoint
import app.spectr.mood.ui.diary.dailyDynamicsLabel
import app.spectr.mood.ui.diary.formatSignedMood
import app.spectr.mood.ui.diary.formatSleepDuration
import app.spectr.mood.ui.theme.LocalMoodColors
import java.time.LocalDate

private const val CHART_HEIGHT_DP = 188
private const val CHART_PAD_LEFT_DP = 36f
private const val CHART_PAD_RIGHT_DP = 14f
private const val CHART_PAD_RIGHT_DUAL_DP = 38f
private const val CHART_PLOT_INSET_DP = 10f
private const val CHART_PAD_TOP_DP = 12f
private const val CHART_PAD_BOTTOM_DP = 24f

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodSelectorCard(
    state: OverviewUiState,
    onSelectPeriod: (OverviewPeriodOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val start = state.summary.periodStart
    val end = state.summary.periodEnd
    val rangeText = when {
        start == null || end == null -> stringResource(R.string.overview_period_empty_summary)
        start == end -> stringResource(
            R.string.overview_period_single_day_summary,
            formatShortChartDate(start, state.today.year)
        )
        else -> stringResource(
            R.string.overview_period_range_summary,
            formatShortChartDate(start, state.today.year),
            formatShortChartDate(end, state.today.year),
            state.summary.filledDays
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_period_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OverviewPeriodOption.entries.forEach { option ->
                    FilterChip(
                        selected = state.selectedPeriod == option,
                        onClick = { onSelectPeriod(option) },
                        label = { Text(text = overviewPeriodLabel(option)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = rangeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.chartData.isEmpty) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.overview_charts_touch_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
internal fun EmptyChartsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_charts_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.overview_charts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MoodTrendChartCard(
    chartData: OverviewChartData,
    referenceYear: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    onOpenDate: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val moodColors = LocalMoodColors.current
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = colorScheme.onSurfaceVariant
    )
    val selectedPoint = selectedIndex?.let { chartData.points.getOrNull(it) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_chart_mood_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (selectedPoint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                MoodTooltipBanner(
                    point = selectedPoint,
                    referenceYear = referenceYear,
                    onOpenDate = onOpenDate?.let { cb -> { cb(selectedPoint.date) } }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val fractions = chartData.points.map { it.xFraction }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT_DP.dp)
                    .chartTouchInteraction(
                        fractions = fractions,
                        selectedIndex = selectedIndex,
                        padLeftDp = CHART_PAD_LEFT_DP,
                        padRightDp = CHART_PAD_RIGHT_DP,
                        onSelectIndex = onSelectIndex
                    )
            ) {
                drawMoodTrendCanvas(
                    chartData = chartData,
                    referenceYear = referenceYear,
                    selectedIndex = selectedIndex,
                    selectedPoint = selectedPoint,
                    textMeasurer = textMeasurer,
                    axisLabelStyle = axisLabelStyle,
                    moodColors = moodColors,
                    colorScheme = colorScheme
                )
            }
        }
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawMoodTrendCanvas(
    chartData: OverviewChartData,
    referenceYear: Int,
    selectedIndex: Int?,
    selectedPoint: OverviewChartPoint?,
    textMeasurer: TextMeasurer,
    axisLabelStyle: TextStyle,
    moodColors: app.spectr.mood.ui.theme.MoodColorScale,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    val left = CHART_PAD_LEFT_DP.dp.toPx()
    val right = size.width - CHART_PAD_RIGHT_DP.dp.toPx()
    val plotInset = CHART_PLOT_INSET_DP.dp.toPx()
    val plotLeft = left + plotInset
    val plotRight = (right - plotInset).coerceAtLeast(plotLeft)
    val top = CHART_PAD_TOP_DP.dp.toPx()
    val bottom = size.height - CHART_PAD_BOTTOM_DP.dp.toPx()

    listOf(-5, -2, 0, 2, 5).forEach { tick ->
        val y = ChartGeometry.valueToY(tick.toDouble(), -5.0, 5.0, top, bottom)
        val isZero = tick == 0
        drawLine(
            color = if (isZero) {
                colorScheme.outline.copy(alpha = 0.55f)
            } else {
                colorScheme.outlineVariant.copy(alpha = 0.45f)
            },
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = if (isZero) 1.5.dp.toPx() else 1.dp.toPx()
        )
        drawAxisLabelRightAligned(
            textMeasurer = textMeasurer,
            text = formatSignedMood(tick),
            style = axisLabelStyle,
            rightX = left - 6.dp.toPx(),
            centerY = y
        )
    }

    selectedPoint?.let { point ->
        val selX = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight)
        drawLine(
            color = colorScheme.primary.copy(alpha = 0.45f),
            start = Offset(selX, top),
            end = Offset(selX, bottom),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
    }

    val coords = chartData.points.map { point ->
        Offset(
            x = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight),
            y = ChartGeometry.valueToY(point.mood.toDouble(), -5.0, 5.0, top, bottom)
        )
    }
    if (coords.size >= 2) {
        val linePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 1 until coords.size) {
                lineTo(coords[i].x, coords[i].y)
            }
        }
        drawPath(
            path = linePath,
            color = colorScheme.primary,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    val baseRadius = if (coords.size > 35) 3.dp.toPx() else 4.5.dp.toPx()
    chartData.points.forEachIndexed { idx, point ->
        val center = coords[idx]
        val isSelected = idx == selectedIndex
        val radius = if (isSelected) 6.5.dp.toPx() else baseRadius
        if (point.isMixedDay) {
            drawCircle(
                color = colorScheme.tertiary,
                radius = radius + 2.5.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        if (isSelected) {
            drawCircle(
                color = colorScheme.primary.copy(alpha = 0.22f),
                radius = radius + 5.dp.toPx(),
                center = center
            )
        }
        drawCircle(
            color = moodColors.containerColor(point.mood),
            radius = radius,
            center = center
        )
        drawCircle(
            color = colorScheme.surfaceContainerLow,
            radius = radius,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }

    drawXAxisDateTicks(
        textMeasurer = textMeasurer,
        ticks = chartData.xTicks,
        referenceYear = referenceYear,
        style = axisLabelStyle,
        left = plotLeft,
        right = plotRight,
        baselineY = bottom + 6.dp.toPx()
    )
}

@Composable
internal fun EnergySleepChartCard(
    chartData: OverviewChartData,
    referenceYear: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    onOpenDate: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = colorScheme.onSurfaceVariant
    )
    val energyColor = colorScheme.primary
    val selectedPoint = selectedIndex?.let { chartData.points.getOrNull(it) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_chart_energy_sleep_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            ChartLegendRow(
                items = listOf(
                    energyColor to stringResource(R.string.overview_chart_legend_energy),
                    colorScheme.secondary to stringResource(R.string.overview_chart_legend_sleep),
                    colorScheme.error to stringResource(R.string.overview_chart_legend_short_sleep)
                )
            )

            if (selectedPoint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                EnergySleepTooltipBanner(
                    point = selectedPoint,
                    referenceYear = referenceYear,
                    onOpenDate = onOpenDate?.let { cb -> { cb(selectedPoint.date) } }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val fractions = chartData.points.map { it.xFraction }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT_DP.dp)
                    .chartTouchInteraction(
                        fractions = fractions,
                        selectedIndex = selectedIndex,
                        padLeftDp = CHART_PAD_LEFT_DP,
                        padRightDp = CHART_PAD_RIGHT_DUAL_DP,
                        onSelectIndex = onSelectIndex
                    )
            ) {
                drawEnergySleepCanvas(
                    chartData = chartData,
                    referenceYear = referenceYear,
                    selectedIndex = selectedIndex,
                    textMeasurer = textMeasurer,
                    axisLabelStyle = axisLabelStyle,
                    palette = EnergySleepPalette(
                        energy = energyColor,
                        sleep = colorScheme.secondary,
                        shortSleep = colorScheme.error,
                        grid = colorScheme.outlineVariant.copy(alpha = 0.45f),
                        surface = colorScheme.surfaceContainerLow
                    )
                )
            }
        }
    }
}

private data class EnergySleepPalette(
    val energy: Color,
    val sleep: Color,
    val shortSleep: Color,
    val grid: Color,
    val surface: Color
)

private fun DrawScope.drawEnergySleepCanvas(
    chartData: OverviewChartData,
    referenceYear: Int,
    selectedIndex: Int?,
    textMeasurer: TextMeasurer,
    axisLabelStyle: TextStyle,
    palette: EnergySleepPalette
) {
    val left = CHART_PAD_LEFT_DP.dp.toPx()
    val right = size.width - CHART_PAD_RIGHT_DUAL_DP.dp.toPx()
    val plotInset = CHART_PLOT_INSET_DP.dp.toPx()
    val plotLeft = left + plotInset
    val plotRight = (right - plotInset).coerceAtLeast(plotLeft)
    val top = CHART_PAD_TOP_DP.dp.toPx()
    val bottom = size.height - CHART_PAD_BOTTOM_DP.dp.toPx()
    val sleepMax = chartData.sleepAxisMaxHours

    listOf(0, 5, 10).forEach { energyTick ->
        val y = ChartGeometry.valueToY(energyTick.toDouble(), 0.0, 10.0, top, bottom)
        drawLine(color = palette.grid, start = Offset(left, y), end = Offset(right, y), strokeWidth = 1.dp.toPx())
        drawAxisLabelRightAligned(textMeasurer, energyTick.toString(), axisLabelStyle, left - 6.dp.toPx(), y)
    }

    listOf(0.0, ChartGeometry.SHORT_SLEEP_THRESHOLD_HOURS, sleepMax).forEach { hoursTick ->
        val y = ChartGeometry.valueToY(hoursTick, 0.0, sleepMax, top, bottom)
        drawAxisLabelLeftAligned(textMeasurer, "${hoursTick.toInt()}ч", axisLabelStyle, right + 6.dp.toPx(), y)
    }

    val barWidth = ((plotRight - plotLeft) / (chartData.points.size.coerceAtLeast(1) * 1.8f))
        .coerceIn(3.dp.toPx(), 14.dp.toPx())
    chartData.points.forEachIndexed { idx, point ->
        val hours = point.sleepHours ?: return@forEachIndexed
        val x = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight)
        val barTop = ChartGeometry.valueToY(hours, 0.0, sleepMax, top, bottom)
        val barHeight = (bottom - barTop).coerceAtLeast(2.dp.toPx())
        val isSelected = idx == selectedIndex
        val baseColor = if (point.isShortSleep) palette.shortSleep else palette.sleep
        val alpha = when {
            point.isShortSleep && isSelected -> 0.85f
            point.isShortSleep -> 0.55f
            isSelected -> 0.65f
            else -> 0.36f
        }
        drawRoundRect(
            color = baseColor.copy(alpha = alpha),
            topLeft = Offset(x - barWidth / 2f, barTop),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }

    val thresholdY = ChartGeometry.valueToY(ChartGeometry.SHORT_SLEEP_THRESHOLD_HOURS, 0.0, sleepMax, top, bottom)
    drawLine(
        color = palette.shortSleep.copy(alpha = 0.75f),
        start = Offset(left, thresholdY),
        end = Offset(right, thresholdY),
        strokeWidth = 1.4.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    )

    selectedIndex?.let { chartData.points.getOrNull(it) }?.let { point ->
        val selX = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight)
        drawLine(
            color = palette.energy.copy(alpha = 0.45f),
            start = Offset(selX, top),
            end = Offset(selX, bottom),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
    }

    val energyCoords = chartData.points.map { point ->
        Offset(
            x = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight),
            y = ChartGeometry.valueToY(point.energy.toDouble(), 0.0, 10.0, top, bottom)
        )
    }
    drawLineSeries(energyCoords, palette.energy, selectedIndex, palette.surface)

    drawXAxisDateTicks(
        textMeasurer,
        chartData.xTicks,
        referenceYear,
        axisLabelStyle,
        plotLeft,
        plotRight,
        bottom + 6.dp.toPx()
    )
}

@Composable
internal fun AnxietyIrritabilityChartCard(
    chartData: OverviewChartData,
    referenceYear: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    onOpenDate: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = colorScheme.onSurfaceVariant
    )
    val anxietyColor = colorScheme.tertiary
    val irritabilityColor = colorScheme.error
    val thresholdColor = colorScheme.outline.copy(alpha = 0.6f)
    val selectedPoint = selectedIndex?.let { chartData.points.getOrNull(it) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_chart_anxiety_irritability_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            ChartLegendRow(
                items = listOf(
                    anxietyColor to stringResource(R.string.overview_chart_legend_anxiety),
                    irritabilityColor to stringResource(R.string.overview_chart_legend_irritability),
                    thresholdColor to stringResource(R.string.overview_chart_legend_high_threshold)
                )
            )

            if (selectedPoint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AnxietyIrritabilityTooltipBanner(
                    point = selectedPoint,
                    referenceYear = referenceYear,
                    onOpenDate = onOpenDate?.let { cb -> { cb(selectedPoint.date) } }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val fractions = chartData.points.map { it.xFraction }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT_DP.dp)
                    .chartTouchInteraction(
                        fractions = fractions,
                        selectedIndex = selectedIndex,
                        padLeftDp = CHART_PAD_LEFT_DP,
                        padRightDp = CHART_PAD_RIGHT_DP,
                        onSelectIndex = onSelectIndex
                    )
            ) {
                val left = CHART_PAD_LEFT_DP.dp.toPx()
                val right = size.width - CHART_PAD_RIGHT_DP.dp.toPx()
                val plotInset = CHART_PLOT_INSET_DP.dp.toPx()
                val plotLeft = left + plotInset
                val plotRight = (right - plotInset).coerceAtLeast(plotLeft)
                val top = CHART_PAD_TOP_DP.dp.toPx()
                val bottom = size.height - CHART_PAD_BOTTOM_DP.dp.toPx()

                // Y-axis ticks (0, 3, 7, 10)
                listOf(0, 3, 7, 10).forEach { tick ->
                    val y = ChartGeometry.valueToY(tick.toDouble(), 0.0, 10.0, top, bottom)
                    val isThreshold = tick == 7
                    drawLine(
                        color = if (isThreshold) thresholdColor else colorScheme.outlineVariant.copy(alpha = 0.45f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = if (isThreshold) 1.3.dp.toPx() else 1.dp.toPx(),
                        pathEffect = if (isThreshold) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                    )
                    drawAxisLabelRightAligned(
                        textMeasurer = textMeasurer,
                        text = tick.toString(),
                        style = axisLabelStyle,
                        rightX = left - 6.dp.toPx(),
                        centerY = y
                    )
                }

                // Crosshair
                selectedPoint?.let { point ->
                    val selX = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight)
                    drawLine(
                        color = colorScheme.primary.copy(alpha = 0.45f),
                        start = Offset(selX, top),
                        end = Offset(selX, bottom),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                }

                val anxietyCoords = chartData.points.map { point ->
                    Offset(
                        x = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight),
                        y = ChartGeometry.valueToY(point.anxiety.toDouble(), 0.0, 10.0, top, bottom)
                    )
                }
                val irritabilityCoords = chartData.points.map { point ->
                    Offset(
                        x = ChartGeometry.fractionToX(point.xFraction, plotLeft, plotRight),
                        y = ChartGeometry.valueToY(point.irritability.toDouble(), 0.0, 10.0, top, bottom)
                    )
                }

                drawLineSeries(
                    coords = anxietyCoords,
                    color = anxietyColor,
                    selectedIndex = selectedIndex,
                    surfaceColor = colorScheme.surfaceContainerLow
                )
                drawLineSeries(
                    coords = irritabilityCoords,
                    color = irritabilityColor,
                    selectedIndex = selectedIndex,
                    surfaceColor = colorScheme.surfaceContainerLow
                )

                drawXAxisDateTicks(
                    textMeasurer = textMeasurer,
                    ticks = chartData.xTicks,
                    referenceYear = referenceYear,
                    style = axisLabelStyle,
                    left = plotLeft,
                    right = plotRight,
                    baselineY = bottom + 6.dp.toPx()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegendRow(items: List<Pair<Color, String>>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MoodTooltipBanner(point: OverviewChartPoint, referenceYear: Int, onOpenDate: (() -> Unit)?) {
    val moodColors = LocalMoodColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = moodColors.containerColor(point.mood)
                ) {
                    Text(
                        text = formatSignedMood(point.mood),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = moodColors.contentColor(point.mood),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Column {
                    Text(
                        text = formatShortChartDate(point.date, referenceYear),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val subtitle = if (point.isMixedDay) {
                        "${dailyDynamicsLabel(point.dailyDynamics)} · " +
                            stringResource(R.string.overview_chart_mood_mixed_tag)
                    } else {
                        dailyDynamicsLabel(point.dailyDynamics)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onOpenDate != null) {
                TextButton(onClick = onOpenDate) {
                    Text(text = stringResource(R.string.overview_chart_open_day))
                }
            }
        }
    }
}

@Composable
private fun EnergySleepTooltipBanner(point: OverviewChartPoint, referenceYear: Int, onOpenDate: (() -> Unit)?) {
    val sleepLabel = point.sleepMinutes?.let { formatSleepDuration(it) }
        ?: stringResource(R.string.overview_chart_sleep_missing)
    val detailLine = buildString {
        append(stringResource(R.string.overview_chart_tooltip_energy, point.energy))
        append(" · ")
        if (point.sleepMinutes != null) {
            append(stringResource(R.string.overview_chart_tooltip_sleep, sleepLabel))
            if (point.isShortSleep) {
                append(" (< 6 ч)")
            }
        } else {
            append(sleepLabel)
        }
    }

    TooltipBannerShell(
        dateLabel = formatShortChartDate(point.date, referenceYear),
        detailLine = detailLine,
        onOpenDate = onOpenDate
    )
}

@Composable
private fun AnxietyIrritabilityTooltipBanner(point: OverviewChartPoint, referenceYear: Int, onOpenDate: (() -> Unit)?) {
    val detailLine = stringResource(R.string.overview_chart_tooltip_anxiety, point.anxiety) +
        " · " +
        stringResource(R.string.overview_chart_tooltip_irritability, point.irritability)

    TooltipBannerShell(
        dateLabel = formatShortChartDate(point.date, referenceYear),
        detailLine = detailLine,
        onOpenDate = onOpenDate
    )
}

@Composable
private fun TooltipBannerShell(dateLabel: String, detailLine: String, onOpenDate: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onOpenDate != null) {
                TextButton(onClick = onOpenDate) {
                    Text(text = stringResource(R.string.overview_chart_open_day))
                }
            }
        }
    }
}

private fun Modifier.chartTouchInteraction(
    fractions: List<Float>,
    selectedIndex: Int?,
    padLeftDp: Float,
    padRightDp: Float,
    onSelectIndex: (Int?) -> Unit
): Modifier = this
    .pointerInput(fractions, selectedIndex) {
        detectTapGestures { offset ->
            val left = (padLeftDp + CHART_PLOT_INSET_DP).dp.toPx()
            val right = (size.width - (padRightDp + CHART_PLOT_INSET_DP).dp.toPx()).coerceAtLeast(left)
            val nearest = ChartGeometry.findNearestPointIndex(offset.x, left, right, fractions)
            onSelectIndex(if (nearest == selectedIndex) null else nearest)
        }
    }
    .pointerInput(fractions) {
        detectHorizontalDragGestures { change, _ ->
            val left = (padLeftDp + CHART_PLOT_INSET_DP).dp.toPx()
            val right = (size.width - (padRightDp + CHART_PLOT_INSET_DP).dp.toPx()).coerceAtLeast(left)
            val nearest = ChartGeometry.findNearestPointIndex(change.position.x, left, right, fractions)
            onSelectIndex(nearest)
        }
    }

private fun DrawScope.drawLineSeries(coords: List<Offset>, color: Color, selectedIndex: Int?, surfaceColor: Color) {
    if (coords.size >= 2) {
        val path = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 1 until coords.size) {
                lineTo(coords[i].x, coords[i].y)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
    val compact = coords.size > 35
    coords.forEachIndexed { idx, center ->
        val isSelected = idx == selectedIndex
        val r = if (isSelected) {
            5.5.dp.toPx()
        } else if (compact) {
            2.5.dp.toPx()
        } else {
            3.8.dp.toPx()
        }
        if (isSelected) {
            drawCircle(color = color.copy(alpha = 0.22f), radius = r + 4.dp.toPx(), center = center)
        }
        drawCircle(color = color, radius = r, center = center)
        drawCircle(color = surfaceColor, radius = r, center = center, style = Stroke(width = 1.dp.toPx()))
    }
}

internal fun DrawScope.drawAxisLabelRightAligned(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    rightX: Float,
    centerY: Float
) {
    val layout = textMeasurer.measure(text = text, style = style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            x = (rightX - layout.size.width).coerceAtLeast(0f),
            y = centerY - layout.size.height / 2f
        )
    )
}

internal fun DrawScope.drawAxisLabelLeftAligned(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    leftX: Float,
    centerY: Float
) {
    val layout = textMeasurer.measure(text = text, style = style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            x = leftX.coerceAtMost((size.width - layout.size.width).coerceAtLeast(0f)),
            y = centerY - layout.size.height / 2f
        )
    )
}

internal fun DrawScope.drawXAxisDateTicks(
    textMeasurer: TextMeasurer,
    ticks: List<ChartDateTick>,
    referenceYear: Int,
    style: TextStyle,
    left: Float,
    right: Float,
    baselineY: Float
) {
    ticks.forEach { tick ->
        val label = formatShortChartDate(tick.date, referenceYear)
        val layout = textMeasurer.measure(text = label, style = style)
        val rawX = ChartGeometry.fractionToX(tick.xFraction, left, right) - layout.size.width / 2f
        val clampedX = rawX.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(x = clampedX, y = baselineY)
        )
    }
}
