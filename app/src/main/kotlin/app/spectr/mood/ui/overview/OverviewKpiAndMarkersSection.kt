package app.spectr.mood.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.calculator.DiaryAnalyticsSummary
import app.spectr.mood.domain.calculator.KeyMarkersSummary
import app.spectr.mood.ui.diary.formatSignedMood
import app.spectr.mood.ui.theme.LocalMoodColors
import kotlin.math.roundToInt

@Immutable
private data class KpiTileModel(
    val label: String,
    val value: String,
    val subtitle: String? = null,
    val containerColor: Color? = null,
    val valueColor: Color? = null
)

@Composable
internal fun OverviewKpiSection(summary: DiaryAnalyticsSummary, modifier: Modifier = Modifier) {
    val tiles = buildCoreKpiTiles(summary) + buildThresholdKpiTiles(summary)
    val rows = remember(tiles) { tiles.chunked(2) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_kpi_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowTiles ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            KpiTile(
                                model = tile,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun buildCoreKpiTiles(summary: DiaryAnalyticsSummary): List<KpiTileModel> {
    val dash = stringResource(R.string.overview_kpi_dash)
    val moodColors = LocalMoodColors.current
    val avgMoodBg = summary.averageMood?.let { avg ->
        moodColors.containerColor(avg.roundToInt().coerceIn(-5, 5)).copy(alpha = 0.45f)
    }
    val rangeValue = if (summary.minMood != null && summary.maxMood != null) {
        "${formatSignedMood(summary.minMood)} .. ${formatSignedMood(summary.maxMood)}"
    } else {
        dash
    }
    val splitValue = if (summary.filledDays > 0) {
        "${summary.lowMoodDays} / ${summary.neutralMoodDays} / ${summary.elevatedMoodDays}"
    } else {
        dash
    }

    return listOf(
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_filled_days),
            value = stringResource(R.string.overview_kpi_days_value, summary.filledDays)
        ),
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_avg_mood),
            value = summary.averageMood?.let { formatSignedAverageMood(it) } ?: dash,
            containerColor = avgMoodBg,
            valueColor = MaterialTheme.colorScheme.onSurface
        ),
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_mood_range),
            value = rangeValue,
            subtitle = summary.moodSpan?.let { stringResource(R.string.overview_kpi_mood_span_sub, it) }
        ),
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_mood_split),
            value = splitValue,
            subtitle = stringResource(R.string.overview_kpi_mood_split_sub)
        ),
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_avg_energy),
            value = formatOutOfTenOrDash(summary.averageEnergy, dash)
        ),
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_avg_sleep),
            value = summary.averageSleepHours?.let {
                stringResource(R.string.overview_kpi_hours_value, formatDecimalOne(it))
            } ?: dash,
            subtitle = summary.sleepTrackedDays.takeIf { it > 0 }?.let {
                stringResource(R.string.overview_kpi_sleep_tracked_sub, it)
            }
        ),
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_avg_anxiety),
            value = formatOutOfTenOrDash(summary.averageAnxiety, dash)
        ),
        KpiTileModel(
            label = stringResource(R.string.overview_kpi_avg_irritability),
            value = formatOutOfTenOrDash(summary.averageIrritability, dash)
        )
    )
}

@Composable
private fun buildThresholdKpiTiles(summary: DiaryAnalyticsSummary): List<KpiTileModel> = listOf(
    KpiTileModel(
        label = stringResource(R.string.overview_kpi_marked_low),
        value = stringResource(R.string.overview_kpi_days_value, summary.markedLowDays),
        subtitle = stringResource(R.string.overview_kpi_marked_low_sub)
    ),
    KpiTileModel(
        label = stringResource(R.string.overview_kpi_marked_high),
        value = stringResource(R.string.overview_kpi_days_value, summary.markedHighDays),
        subtitle = stringResource(R.string.overview_kpi_marked_high_sub)
    ),
    KpiTileModel(
        label = stringResource(R.string.overview_kpi_mixed_days),
        value = stringResource(R.string.overview_kpi_days_value, summary.mixedDays),
        subtitle = stringResource(R.string.overview_kpi_mixed_days_sub)
    ),
    KpiTileModel(
        label = stringResource(R.string.overview_kpi_short_sleep),
        value = stringResource(R.string.overview_kpi_days_value, summary.shortSleepDays)
    ),
    KpiTileModel(
        label = stringResource(R.string.overview_kpi_swings),
        value = stringResource(R.string.overview_kpi_days_value, summary.intraDaySwingsDays)
    ),
    KpiTileModel(
        label = stringResource(R.string.overview_kpi_high_anx_irr),
        value = stringResource(R.string.overview_kpi_days_value, summary.highAnxietyOrIrritabilityDays),
        subtitle = stringResource(
            R.string.overview_kpi_high_anx_irr_sub,
            summary.highAnxietyDays,
            summary.highIrritabilityDays
        )
    )
)

@Composable
private fun formatOutOfTenOrDash(value: Double?, dash: String): String =
    value?.let { stringResource(R.string.overview_kpi_out_of_ten, formatDecimalOne(it)) } ?: dash

@Composable
private fun KpiTile(model: KpiTileModel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = model.containerColor ?: MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = model.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = model.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = model.valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (model.subtitle != null) {
                Text(
                    text = model.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewKeyMarkersCard(markers: KeyMarkersSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_markers_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarkerPill(
                    label = stringResource(R.string.symptom_death_thoughts_label),
                    count = markers.deathThoughtsDays,
                    emphasize = markers.deathThoughtsDays > 0
                )
                MarkerPill(
                    label = stringResource(R.string.symptom_binge_eating_label),
                    count = markers.bingeEatingDays
                )
                MarkerPill(
                    label = stringResource(R.string.symptom_hyperfocus_label),
                    count = markers.hyperfocusDays
                )
                MarkerPill(
                    label = stringResource(R.string.symptom_racing_thoughts_label),
                    count = markers.racingThoughtsDays
                )
                MarkerPill(
                    label = stringResource(R.string.symptom_apathy_label),
                    count = markers.apathyDays
                )
                MarkerPill(
                    label = stringResource(R.string.symptom_self_criticism_label),
                    count = markers.selfCriticismDays
                )
                MarkerPill(
                    label = stringResource(R.string.symptom_brain_fog_label),
                    count = markers.brainFogDays
                )
                MarkerPill(
                    label = stringResource(R.string.overview_marker_alcohol),
                    count = markers.alcoholDays,
                    extra = if (markers.alcoholStressDays > 0) {
                        stringResource(R.string.overview_marker_alcohol_stress_sub, markers.alcoholStressDays)
                    } else {
                        null
                    }
                )
                MarkerPill(
                    label = stringResource(R.string.overview_marker_med_side_effects),
                    count = markers.medicationSideEffectsDays,
                    extra = if (markers.medicationMissedDays > 0) {
                        stringResource(R.string.overview_marker_med_missed_sub, markers.medicationMissedDays)
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun MarkerPill(label: String, count: Int, extra: String? = null, emphasize: Boolean = false) {
    val containerColor = when {
        emphasize -> MaterialTheme.colorScheme.errorContainer
        count > 0 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        emphasize -> MaterialTheme.colorScheme.onErrorContainer
        count > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            if (extra != null) {
                Text(
                    text = "($extra)",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}
