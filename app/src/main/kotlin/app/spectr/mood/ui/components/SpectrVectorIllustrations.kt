package app.spectr.mood.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.spectr.mood.ui.theme.LocalMoodColors
import app.spectr.mood.ui.theme.MoodColorScale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val BRAND_BAR_SCORES = intArrayOf(-4, -2, 0, 2, 4)
private val BRAND_BAR_TOP_FRACTIONS = floatArrayOf(0.46f, 0.28f, 0.14f, 0.24f, 0.38f)
private val FULL_SPECTRUM_SCORES = intArrayOf(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5)
private val BASE_WAVE_HEIGHTS = floatArrayOf(
    0.34f,
    0.44f,
    0.54f,
    0.66f,
    0.76f,
    0.88f,
    0.80f,
    0.70f,
    0.58f,
    0.48f,
    0.38f
)

/**
 * Compact 5-bar custom vector emblem for Spectr (strictly vector, no emojis).
 */
@Composable
fun SpectrBrandMark(modifier: Modifier = Modifier) {
    val moodColors = LocalMoodColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val curvePath = remember { Path() }

    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.125f
        val gap = (w - barWidth * BRAND_BAR_SCORES.size) / (BRAND_BAR_SCORES.size + 1)
        val bottomY = h * 0.86f
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

        BRAND_BAR_SCORES.forEachIndexed { index, score ->
            val leftX = gap + index * (barWidth + gap)
            val topY = h * BRAND_BAR_TOP_FRACTIONS[index]
            drawRoundRect(
                color = moodColors.containerColor(score),
                topLeft = Offset(leftX, topY),
                size = Size(barWidth, (bottomY - topY).coerceAtLeast(barWidth)),
                cornerRadius = corner
            )
        }

        curvePath.rewind()
        curvePath.moveTo(w * 0.10f, h * 0.50f)
        curvePath.cubicTo(
            w * 0.28f,
            h * 0.48f,
            w * 0.36f,
            h * 0.18f,
            w * 0.50f,
            h * 0.18f
        )
        curvePath.cubicTo(
            w * 0.64f,
            h * 0.18f,
            w * 0.74f,
            h * 0.42f,
            w * 0.90f,
            h * 0.30f
        )
        drawPath(
            path = curvePath,
            color = primaryColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val centerPeak = Offset(w * 0.50f, h * 0.18f)
        drawCircle(color = primaryColor, radius = 2.6.dp.toPx(), center = centerPeak)
        drawCircle(color = surfaceColor, radius = 1.1.dp.toPx(), center = centerPeak)
    }
}

/**
 * Animated custom vector hero illustration for the 4 onboarding slides (zero emojis).
 *
 * @param pageIndex Slide index in `0..3`.
 * @param pulsePhase Smooth animation phase in `0f..1f`.
 */
@Composable
fun OnboardingHeroIllustration(pageIndex: Int, pulsePhase: Float, modifier: Modifier = Modifier) {
    val moodColors = LocalMoodColors.current
    val scheme = MaterialTheme.colorScheme
    val primaryPath = remember { Path() }
    val secondaryPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }

    Canvas(modifier = modifier) {
        val angle = (pulsePhase * 2f * PI).toFloat()
        when (pageIndex) {
            0 -> drawSpectrumAndMomentsScene(moodColors, scheme, primaryPath, dashEffect, angle)
            1 -> drawSplitSleepAndSymptomsScene(scheme, primaryPath, angle)
            2 -> drawAnalyticsAndScalesScene(moodColors, scheme, primaryPath, secondaryPath, angle)
            else -> drawDoctorPdfAndPrivacyScene(moodColors, scheme, primaryPath, secondaryPath, angle)
        }
    }
}

@Suppress("LongMethod")
private fun DrawScope.drawSpectrumAndMomentsScene(
    moodColors: MoodColorScale,
    scheme: ColorScheme,
    wavePath: Path,
    dashEffect: PathEffect,
    angle: Float
) {
    val w = size.width
    val h = size.height
    val center = Offset(w * 0.5f, h * 0.5f)
    val haloRadius = minOf(w, h) * 0.44f

    drawCircle(
        color = scheme.primaryContainer.copy(alpha = 0.32f),
        radius = haloRadius,
        center = center
    )
    drawCircle(
        color = scheme.secondaryContainer.copy(alpha = 0.25f),
        radius = haloRadius * 0.74f,
        center = center
    )

    val padX = w * 0.12f
    val usableW = w - padX * 2f
    val count = FULL_SPECTRUM_SCORES.size
    val barWidth = (usableW / (count * 1.45f)).coerceAtLeast(6.dp.toPx())
    val stepX = (usableW - barWidth) / (count - 1)
    val baseLineY = h * 0.78f
    val maxBarH = h * 0.52f
    val zeroLineY = h * 0.50f

    drawLine(
        color = scheme.outline.copy(alpha = 0.45f),
        start = Offset(padX * 0.75f, zeroLineY),
        end = Offset(w - padX * 0.75f, zeroLineY),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = dashEffect
    )

    wavePath.rewind()
    for (i in 0 until count) {
        val score = FULL_SPECTRUM_SCORES[i]
        val waveShift = sin(angle + i * 0.55f) * 0.06f
        val fraction = (BASE_WAVE_HEIGHTS[i] + waveShift).coerceIn(0.22f, 0.95f)
        val barH = maxBarH * fraction
        val leftX = padX + i * stepX
        val topY = baseLineY - barH
        val barCenter = leftX + barWidth * 0.5f

        drawRoundRect(
            color = moodColors.containerColor(score),
            topLeft = Offset(leftX, topY),
            size = Size(barWidth, barH),
            cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
        )

        val curveY = topY - 6.dp.toPx()
        if (i == 0) {
            wavePath.moveTo(barCenter, curveY)
        } else {
            wavePath.lineTo(barCenter, curveY)
        }
    }

    drawPath(
        path = wavePath,
        color = scheme.primary,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Highlight 3 moment check-in nodes along the curve
    intArrayOf(2, 5, 8).forEach { idx ->
        val waveShift = sin(angle + idx * 0.55f) * 0.06f
        val fraction = (BASE_WAVE_HEIGHTS[idx] + waveShift).coerceIn(0.22f, 0.95f)
        val nodeX = padX + idx * stepX + barWidth * 0.5f
        val nodeY = baseLineY - maxBarH * fraction - 6.dp.toPx()
        drawCircle(
            color = scheme.primary.copy(alpha = 0.24f),
            radius = 9.dp.toPx(),
            center = Offset(nodeX, nodeY)
        )
        drawCircle(
            color = scheme.surface,
            radius = 5.5.dp.toPx(),
            center = Offset(nodeX, nodeY)
        )
        drawCircle(
            color = moodColors.containerColor(FULL_SPECTRUM_SCORES[idx]),
            radius = 3.8.dp.toPx(),
            center = Offset(nodeX, nodeY)
        )
    }
}

private fun DrawScope.drawSplitSleepAndSymptomsScene(scheme: ColorScheme, moonPath: Path, angle: Float) {
    val w = size.width
    val h = size.height
    val ringCenter = Offset(w * 0.34f, h * 0.48f)
    val ringRadius = minOf(w, h) * 0.28f
    val strokeW = 10.dp.toPx()

    // Background circadian dial
    drawCircle(
        color = scheme.surfaceContainerHighest,
        radius = ringRadius,
        center = ringCenter,
        style = Stroke(width = strokeW)
    )

    // Split sleep interval 1 (night crossing midnight)
    val sweep1 = 115f + sin(angle) * 6f
    drawArc(
        color = scheme.primary,
        startAngle = -110f,
        sweepAngle = sweep1,
        useCenter = false,
        topLeft = Offset(ringCenter.x - ringRadius, ringCenter.y - ringRadius),
        size = Size(ringRadius * 2f, ringRadius * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    // Split sleep interval 2 (morning nap / second segment)
    val sweep2 = 68f + cos(angle) * 5f
    drawArc(
        color = scheme.tertiary,
        startAngle = 35f,
        sweepAngle = sweep2,
        useCenter = false,
        topLeft = Offset(ringCenter.x - ringRadius, ringCenter.y - ringRadius),
        size = Size(ringRadius * 2f, ringRadius * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    // Vector geometric crescent moon in the center of the sleep ring
    moonPath.rewind()
    val moonR = ringRadius * 0.36f
    drawCircle(
        color = scheme.primaryContainer,
        radius = moonR,
        center = ringCenter
    )
    drawCircle(
        color = scheme.surfaceContainerLow,
        radius = moonR * 0.78f,
        center = Offset(ringCenter.x + moonR * 0.36f, ringCenter.y - moonR * 0.22f)
    )

    // Right side: 3 animated metric sliders + symptom chips cards
    val panelLeft = w * 0.62f
    val panelRight = w * 0.90f
    val trackW = (panelRight - panelLeft).coerceAtLeast(20.dp.toPx())
    val sliderFractions = floatArrayOf(
        0.72f + sin(angle) * 0.06f,
        0.38f + cos(angle) * 0.05f,
        0.58f + sin(angle + 1.2f) * 0.05f
    )
    val sliderColors = arrayOf(scheme.primary, scheme.secondary, scheme.tertiary)

    for (i in 0..2) {
        val y = h * (0.28f + i * 0.16f)
        drawRoundRect(
            color = scheme.surfaceContainerHighest,
            topLeft = Offset(panelLeft, y - 4.dp.toPx()),
            size = Size(trackW, 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        val activeW = trackW * sliderFractions[i].coerceIn(0.15f, 0.92f)
        drawRoundRect(
            color = sliderColors[i],
            topLeft = Offset(panelLeft, y - 4.dp.toPx()),
            size = Size(activeW, 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawCircle(
            color = scheme.surface,
            radius = 7.dp.toPx(),
            center = Offset(panelLeft + activeW, y)
        )
        drawCircle(
            color = sliderColors[i],
            radius = 5.dp.toPx(),
            center = Offset(panelLeft + activeW, y)
        )
    }

    // Two vector symptom pill badges below the sliders
    val pillY = h * 0.74f
    val pillW = (trackW - 8.dp.toPx()) * 0.5f
    drawRoundRect(
        color = scheme.secondaryContainer,
        topLeft = Offset(panelLeft, pillY),
        size = Size(pillW, 18.dp.toPx()),
        cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
    )
    drawRoundRect(
        color = scheme.tertiaryContainer,
        topLeft = Offset(panelLeft + pillW + 8.dp.toPx(), pillY),
        size = Size(pillW, 18.dp.toPx()),
        cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
    )
}

@Suppress("LongMethod")
private fun DrawScope.drawAnalyticsAndScalesScene(
    moodColors: MoodColorScale,
    scheme: ColorScheme,
    linePath: Path,
    fillPath: Path,
    angle: Float
) {
    val w = size.width
    val h = size.height

    // Left mini mood calendar card
    val calLeft = w * 0.09f
    val calTop = h * 0.18f
    val calW = w * 0.36f
    val calH = h * 0.62f
    drawRoundRect(
        color = scheme.surfaceContainerHigh,
        topLeft = Offset(calLeft, calTop),
        size = Size(calW, calH),
        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
    )

    val cols = 4
    val rows = 3
    val pad = 10.dp.toPx()
    val gap = 5.dp.toPx()
    val cellW = (calW - pad * 2f - gap * (cols - 1)) / cols
    val cellH = (calH - pad * 2f - gap * (rows - 1)) / rows
    val calScores = intArrayOf(-2, -1, 0, 1, 0, 2, 1, 0, -1, 0, 1, 2)

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val idx = r * cols + c
            val x = calLeft + pad + c * (cellW + gap)
            val y = calTop + pad + r * (cellH + gap)
            drawRoundRect(
                color = moodColors.containerColor(calScores[idx]),
                topLeft = Offset(x, y),
                size = Size(cellW, cellH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }

    // Right chart + clinical scales card
    val chartLeft = w * 0.50f
    val chartTop = h * 0.18f
    val chartW = w * 0.41f
    val chartH = h * 0.62f
    drawRoundRect(
        color = scheme.surfaceContainerHigh,
        topLeft = Offset(chartLeft, chartTop),
        size = Size(chartW, chartH),
        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
    )

    val plotLeft = chartLeft + 12.dp.toPx()
    val plotRight = chartLeft + chartW - 12.dp.toPx()
    val plotTop = chartTop + 16.dp.toPx()
    val plotBottom = chartTop + chartH - 18.dp.toPx()
    val pts = floatArrayOf(
        0.35f,
        0.52f + sin(angle) * 0.06f,
        0.44f,
        0.74f + cos(angle) * 0.06f,
        0.68f,
        0.85f
    )

    linePath.rewind()
    fillPath.rewind()
    pts.forEachIndexed { idx, norm ->
        val x = plotLeft + (plotRight - plotLeft) * (idx.toFloat() / (pts.size - 1))
        val y = plotBottom - (plotBottom - plotTop) * norm.coerceIn(0.1f, 0.95f)
        if (idx == 0) {
            linePath.moveTo(x, y)
            fillPath.moveTo(x, plotBottom)
            fillPath.lineTo(x, y)
        } else {
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }
    fillPath.lineTo(plotRight, plotBottom)
    fillPath.close()

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(scheme.primary.copy(alpha = 0.28f), Color.Transparent),
            startY = plotTop,
            endY = plotBottom
        )
    )
    drawPath(
        path = linePath,
        color = scheme.primary,
        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    pts.forEachIndexed { idx, norm ->
        val x = plotLeft + (plotRight - plotLeft) * (idx.toFloat() / (pts.size - 1))
        val y = plotBottom - (plotBottom - plotTop) * norm.coerceIn(0.1f, 0.95f)
        drawCircle(color = scheme.surface, radius = 4.dp.toPx(), center = Offset(x, y))
        drawCircle(color = scheme.primary, radius = 2.8.dp.toPx(), center = Offset(x, y))
    }
}

@Suppress("LongMethod")
private fun DrawScope.drawDoctorPdfAndPrivacyScene(
    moodColors: MoodColorScale,
    scheme: ColorScheme,
    docTrendPath: Path,
    shieldPath: Path,
    angle: Float
) {
    val w = size.width
    val h = size.height

    // A4 vector report page
    val pageW = w * 0.44f
    val pageH = h * 0.72f
    val pageLeft = w * 0.16f
    val pageTop = h * 0.14f

    drawRoundRect(
        color = scheme.surfaceContainerHighest,
        topLeft = Offset(pageLeft, pageTop),
        size = Size(pageW, pageH),
        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
    )

    // Header spectrum strip inside PDF page
    val innerLeft = pageLeft + 12.dp.toPx()
    val innerW = pageW - 24.dp.toPx()
    val stripScores = intArrayOf(-4, -2, 0, 2, 4)
    val segW = (innerW - 4 * 3.dp.toPx()) / stripScores.size
    stripScores.forEachIndexed { idx, score ->
        drawRoundRect(
            color = moodColors.containerColor(score),
            topLeft = Offset(innerLeft + idx * (segW + 3.dp.toPx()), pageTop + 12.dp.toPx()),
            size = Size(segW, 7.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }

    // Mini vector chart inside PDF page
    val chartTop = pageTop + 28.dp.toPx()
    val chartBottom = pageTop + pageH * 0.56f
    docTrendPath.rewind()
    docTrendPath.moveTo(innerLeft, chartBottom - 8.dp.toPx())
    docTrendPath.cubicTo(
        innerLeft + innerW * 0.35f,
        chartTop,
        innerLeft + innerW * 0.65f,
        chartBottom,
        innerLeft + innerW,
        chartTop + 6.dp.toPx()
    )
    drawPath(
        path = docTrendPath,
        color = scheme.primary,
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
    )

    // Redacted personal notes bars (showing Doctor Mode privacy)
    for (row in 0..2) {
        val barY = chartBottom + 12.dp.toPx() + row * 12.dp.toPx()
        val widthFactor = when (row) {
            0 -> 0.85f
            1 -> 0.65f
            else -> 0.75f
        }
        drawRoundRect(
            color = scheme.outlineVariant.copy(alpha = 0.65f),
            topLeft = Offset(innerLeft, barY),
            size = Size(innerW * widthFactor, 5.dp.toPx()),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        )
    }

    // Geometric vector privacy shield on the right overlapping the report
    val shieldCx = w * 0.68f
    val shieldCy = h * 0.54f + sin(angle) * 3.dp.toPx()
    val shieldW = minOf(w, h) * 0.34f
    val shieldH = shieldW * 1.16f

    val topY = shieldCy - shieldH * 0.5f
    val midY = shieldCy + shieldH * 0.05f
    val botY = shieldCy + shieldH * 0.5f
    val leftX = shieldCx - shieldW * 0.5f
    val rightX = shieldCx + shieldW * 0.5f

    shieldPath.rewind()
    shieldPath.moveTo(shieldCx, topY)
    shieldPath.lineTo(rightX, topY + shieldH * 0.18f)
    shieldPath.cubicTo(
        rightX,
        midY,
        shieldCx + shieldW * 0.25f,
        botY - shieldH * 0.12f,
        shieldCx,
        botY
    )
    shieldPath.cubicTo(
        shieldCx - shieldW * 0.25f,
        botY - shieldH * 0.12f,
        leftX,
        midY,
        leftX,
        topY + shieldH * 0.18f
    )
    shieldPath.close()

    drawPath(path = shieldPath, color = scheme.primaryContainer)
    drawPath(
        path = shieldPath,
        color = scheme.primary,
        style = Stroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round)
    )

    // Vector keyhole inside the shield
    drawCircle(
        color = scheme.onPrimaryContainer,
        radius = 5.dp.toPx(),
        center = Offset(shieldCx, shieldCy - 4.dp.toPx())
    )
    drawRoundRect(
        color = scheme.onPrimaryContainer,
        topLeft = Offset(shieldCx - 2.5.dp.toPx(), shieldCy - 2.dp.toPx()),
        size = Size(5.dp.toPx(), 11.dp.toPx()),
        cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
    )
}

/**
 * Custom vector illustration for empty charts state (Overview & Doctor View).
 */
@Composable
fun EmptyChartsVectorIllustration(modifier: Modifier = Modifier) {
    val moodColors = LocalMoodColors.current
    val scheme = MaterialTheme.colorScheme
    val curvePath = remember { Path() }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) }

    Canvas(modifier = modifier.size(width = 96.dp, height = 56.dp)) {
        val w = size.width
        val h = size.height
        val scores = intArrayOf(-2, 0, 2)
        val heights = floatArrayOf(0.45f, 0.72f, 0.58f)
        val barW = 10.dp.toPx()
        val gap = 10.dp.toPx()
        val startX = (w - (barW * 3 + gap * 2)) * 0.5f

        scores.forEachIndexed { idx, score ->
            val bh = h * heights[idx]
            drawRoundRect(
                color = moodColors.containerColor(score).copy(alpha = 0.65f),
                topLeft = Offset(startX + idx * (barW + gap), h - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f)
            )
        }

        curvePath.rewind()
        curvePath.moveTo(w * 0.08f, h * 0.65f)
        curvePath.cubicTo(w * 0.35f, h * 0.20f, w * 0.65f, h * 0.80f, w * 0.92f, h * 0.30f)
        drawPath(
            path = curvePath,
            color = scheme.primary,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash)
        )
    }
}

/**
 * Custom vector illustration for empty confirmed scales history state (Scales tab).
 */
@Composable
fun EmptyScalesVectorIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val checkPath = remember { Path() }

    Canvas(modifier = modifier.size(width = 88.dp, height = 56.dp)) {
        val w = size.width
        val h = size.height
        val arcSize = Size(w * 0.68f, w * 0.68f)
        val arcTopLeft = Offset((w - arcSize.width) * 0.5f, h * 0.18f)

        drawArc(
            color = scheme.surfaceContainerHighest,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = scheme.primary,
            startAngle = 180f,
            sweepAngle = 112f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        )

        val badgeCenter = Offset(w * 0.5f, h * 0.66f)
        drawCircle(color = scheme.primaryContainer, radius = 12.dp.toPx(), center = badgeCenter)

        checkPath.rewind()
        checkPath.moveTo(badgeCenter.x - 4.5.dp.toPx(), badgeCenter.y + 0.5.dp.toPx())
        checkPath.lineTo(badgeCenter.x - 1.dp.toPx(), badgeCenter.y + 4.dp.toPx())
        checkPath.lineTo(badgeCenter.x + 5.dp.toPx(), badgeCenter.y - 3.5.dp.toPx())
        drawPath(
            path = checkPath,
            color = scheme.onPrimaryContainer,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
