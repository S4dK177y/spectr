package app.spectr.mood.ui.scales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spectr.mood.R
import app.spectr.mood.domain.calculator.AutoScaleResult
import app.spectr.mood.domain.chart.ChartDateTick
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.chart.ScaleTrendPoint
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.ui.components.EmptyScalesVectorIllustration
import app.spectr.mood.ui.overview.drawAxisLabelRightAligned
import app.spectr.mood.ui.overview.drawXAxisDateTicks
import app.spectr.mood.ui.overview.formatShortChartDate

private const val TREND_CHART_HEIGHT_DP = 164
private const val TREND_PAD_LEFT_DP = 34f
private const val TREND_PAD_RIGHT_DP = 14f
private const val TREND_PLOT_INSET_DP = 10f
private const val TREND_PAD_TOP_DP = 12f
private const val TREND_PAD_BOTTOM_DP = 24f
private val TREND_THRESHOLD_DASH = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))

@Composable
internal fun ScalesDisclaimerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.scales_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
internal fun AutoScaleCard(
    scaleType: ScaleType,
    autoResult: AutoScaleResult<*>?,
    latestConfirmed: ScaleAssessment?,
    isExpanded: Boolean,
    referenceYear: Int,
    onToggleExpand: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxScore = scaleMaxTotalScore(scaleType)
    val windowDays = scaleWindowDays(scaleType)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scaleCodeLabel(scaleType),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = scaleSubtitleLabel(scaleType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(R.string.scales_window_days, windowDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (autoResult != null) {
                val severityText = scaleSeverityLabel(scaleType, autoResult.totalScore)
                val elevated = isElevatedScaleScore(scaleType, autoResult.totalScore)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.scales_score_of_max, autoResult.totalScore, maxScore),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (elevated) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = severityText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (elevated) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val animatedProgress by animateFloatAsState(
                    targetValue = (autoResult.totalScore.toFloat() / maxScore.toFloat()).coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 320),
                    label = "scale_auto_progress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (elevated) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(
                        R.string.scales_coverage_summary,
                        formatShortChartDate(autoResult.windowStart, referenceYear),
                        formatShortChartDate(autoResult.windowEnd, referenceYear),
                        autoResult.filledDaysInWindow,
                        autoResult.windowSizeDays
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.scales_empty_diary_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (latestConfirmed != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.scales_latest_confirmed,
                            formatShortChartDate(latestConfirmed.date, referenceYear),
                            latestConfirmed.totalScore,
                            maxScore,
                            scaleSeverityLabel(scaleType, latestConfirmed.totalScore)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }

            if (autoResult != null) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onToggleExpand) {
                    Text(
                        text = stringResource(
                            if (isExpanded) R.string.scales_hide_breakdown else R.string.scales_show_breakdown
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        autoResult.itemScores.forEachIndexed { idx, score ->
                            val days = autoResult.itemDayCounts.getOrElse(idx) { 0 }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${scaleQuestionText(scaleType, idx)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = stringResource(R.string.scales_item_days_ratio, days, windowDays) +
                                        " · $score",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (score > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            FilledTonalButton(
                onClick = onConfirmClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.scales_confirm_button))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScalesHistoryHeader(
    selectedFilter: ScalesHistoryFilter,
    onSelectFilter: (ScalesHistoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.scales_history_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ScalesHistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(text = scaleHistoryFilterLabel(filter)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScaleTrendChartCard(
    scaleType: ScaleType,
    showScaleSwitcher: Boolean,
    points: List<ScaleTrendPoint>,
    selectedIndex: Int?,
    referenceYear: Int,
    onSelectScale: (ScaleType) -> Unit,
    onSelectPoint: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = colorScheme.onSurfaceVariant
    )
    val maxScore = scaleMaxTotalScore(scaleType)
    val selectedPoint = selectedIndex?.let { points.getOrNull(it) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(180))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.scales_trend_title, scaleCodeLabel(scaleType)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showScaleSwitcher) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ScaleType.entries.forEach { type ->
                        FilterChip(
                            selected = type == scaleType,
                            onClick = { onSelectScale(type) },
                            label = { Text(text = scaleCodeLabel(type)) }
                        )
                    }
                }
            }

            if (points.size < 2) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.scales_trend_need_more, scaleCodeLabel(scaleType)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                if (selectedPoint != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatShortChartDate(selectedPoint.assessment.date, referenceYear),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${selectedPoint.assessment.totalScore} / $maxScore · " +
                                    scaleSeverityLabel(scaleType, selectedPoint.assessment.totalScore),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val fractions = remember(points) { points.map { it.xFraction } }
                val thresholds = remember(scaleType) { scaleThresholdLines(scaleType) }
                val xTicks = remember(points) { buildTrendTicks(points) }
                val linePath = remember { Path() }
                val fillPath = remember { Path() }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TREND_CHART_HEIGHT_DP.dp)
                        .pointerInput(fractions, selectedIndex) {
                            detectTapGestures { offset ->
                                val left = (TREND_PAD_LEFT_DP + TREND_PLOT_INSET_DP).dp.toPx()
                                val right = (size.width - (TREND_PAD_RIGHT_DP + TREND_PLOT_INSET_DP).dp.toPx())
                                    .coerceAtLeast(left)
                                val nearest = ChartGeometry.findNearestPointIndex(offset.x, left, right, fractions)
                                onSelectPoint(if (nearest == selectedIndex) null else nearest)
                            }
                        }
                        .pointerInput(fractions) {
                            detectHorizontalDragGestures { change, _ ->
                                val left = (TREND_PAD_LEFT_DP + TREND_PLOT_INSET_DP).dp.toPx()
                                val right = (size.width - (TREND_PAD_RIGHT_DP + TREND_PLOT_INSET_DP).dp.toPx())
                                    .coerceAtLeast(left)
                                val nearest = ChartGeometry.findNearestPointIndex(
                                    change.position.x,
                                    left,
                                    right,
                                    fractions
                                )
                                onSelectPoint(nearest)
                            }
                        }
                ) {
                    val left = TREND_PAD_LEFT_DP.dp.toPx()
                    val right = size.width - TREND_PAD_RIGHT_DP.dp.toPx()
                    val plotInset = TREND_PLOT_INSET_DP.dp.toPx()
                    val plotLeft = left + plotInset
                    val plotRight = (right - plotInset).coerceAtLeast(plotLeft)
                    val top = TREND_PAD_TOP_DP.dp.toPx()
                    val bottom = size.height - TREND_PAD_BOTTOM_DP.dp.toPx()

                    // Base 0 and max lines
                    listOf(0, maxScore).forEach { value ->
                        val y = ChartGeometry.valueToY(value.toDouble(), 0.0, maxScore.toDouble(), top, bottom)
                        drawLine(
                            color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawAxisLabelRightAligned(
                            textMeasurer = textMeasurer,
                            text = value.toString(),
                            style = axisLabelStyle,
                            rightX = left - 6.dp.toPx(),
                            centerY = y
                        )
                    }

                    // Clinical severity threshold lines
                    thresholds.forEach { threshold ->
                        val y = ChartGeometry.valueToY(threshold.toDouble(), 0.0, maxScore.toDouble(), top, bottom)
                        drawLine(
                            color = colorScheme.outline.copy(alpha = 0.45f),
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = TREND_THRESHOLD_DASH
                        )
                        drawAxisLabelRightAligned(
                            textMeasurer = textMeasurer,
                            text = threshold.toString(),
                            style = axisLabelStyle,
                            rightX = left - 6.dp.toPx(),
                            centerY = y
                        )
                    }

                    val coords = points.map { pt ->
                        Offset(
                            x = ChartGeometry.fractionToX(pt.xFraction, plotLeft, plotRight),
                            y = ChartGeometry.valueToY(
                                pt.assessment.totalScore.toDouble(),
                                0.0,
                                maxScore.toDouble(),
                                top,
                                bottom
                            )
                        )
                    }

                    linePath.rewind()
                    fillPath.rewind()
                    linePath.moveTo(coords.first().x, coords.first().y)
                    fillPath.moveTo(coords.first().x, bottom)
                    fillPath.lineTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        linePath.lineTo(coords[i].x, coords[i].y)
                        fillPath.lineTo(coords[i].x, coords[i].y)
                    }
                    fillPath.lineTo(coords.last().x, bottom)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(colorScheme.primary.copy(alpha = 0.16f), Color.Transparent),
                            startY = top,
                            endY = bottom
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = colorScheme.primary,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    coords.forEachIndexed { idx, center ->
                        val isSelected = idx == selectedIndex
                        val r = if (isSelected) 6.dp.toPx() else 4.2.dp.toPx()
                        if (isSelected) {
                            drawCircle(
                                color = colorScheme.primary.copy(alpha = 0.22f),
                                radius = r + 5.dp.toPx(),
                                center = center
                            )
                        }
                        drawCircle(color = colorScheme.primary, radius = r, center = center)
                        drawCircle(
                            color = colorScheme.surfaceContainerLow,
                            radius = r,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    drawXAxisDateTicks(
                        textMeasurer = textMeasurer,
                        ticks = xTicks,
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
}

@Composable
internal fun ScaleHistoryEmptyCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmptyScalesVectorIllustration()
            Text(
                text = stringResource(R.string.scales_history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.scales_history_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ScaleHistoryRowCard(
    row: ScaleHistoryRow,
    referenceYear: Int,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val assessment = row.assessment
    val maxScore = scaleMaxTotalScore(assessment.scaleType)
    val severityText = scaleSeverityLabel(assessment.scaleType, assessment.totalScore)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEditClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = scaleCodeLabel(assessment.scaleType),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scales_score_of_max, assessment.totalScore, maxScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        row.deltaFromPrevious?.let { delta ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = formatSignedDelta(delta),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${formatShortChartDate(assessment.date, referenceYear)} · $severityText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.scales_delete_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildTrendTicks(points: List<ScaleTrendPoint>): List<ChartDateTick> {
    if (points.isEmpty()) return emptyList()
    if (points.size <= 3) {
        return points.map { ChartDateTick(it.xFraction, it.assessment.date) }.distinctBy { it.date }
    }
    val indices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
    return indices.map { idx ->
        ChartDateTick(points[idx].xFraction, points[idx].assessment.date)
    }.distinctBy { it.date }
}
