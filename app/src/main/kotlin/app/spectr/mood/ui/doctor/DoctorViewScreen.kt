package app.spectr.mood.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spectr.mood.R
import app.spectr.mood.domain.report.ReportDailyRow
import app.spectr.mood.domain.report.ReportScaleOverview
import app.spectr.mood.platform.pdf.PdfTextFormatters
import app.spectr.mood.ui.diary.dailyDynamicsLabel
import app.spectr.mood.ui.diary.formatSignedMood
import app.spectr.mood.ui.diary.formatSleepDuration
import app.spectr.mood.ui.overview.AnxietyIrritabilityChartCard
import app.spectr.mood.ui.overview.EmptyChartsCard
import app.spectr.mood.ui.overview.EnergySleepChartCard
import app.spectr.mood.ui.overview.MonthYearPickerDialog
import app.spectr.mood.ui.overview.MoodTrendChartCard
import app.spectr.mood.ui.overview.OverviewCalendarCard
import app.spectr.mood.ui.overview.OverviewIntent
import app.spectr.mood.ui.overview.OverviewKeyMarkersCard
import app.spectr.mood.ui.overview.OverviewKpiSection
import app.spectr.mood.ui.overview.PeriodSelectorCard
import app.spectr.mood.ui.overview.formatShortChartDate
import app.spectr.mood.ui.scales.scaleCodeLabel
import app.spectr.mood.ui.scales.scaleSeverityLabel
import app.spectr.mood.ui.scales.scaleSubtitleLabel
import app.spectr.mood.ui.theme.LocalMoodColors
import java.time.YearMonth

@Composable
fun DoctorViewRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: DoctorViewViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DoctorViewScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onClose = onClose,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorViewScreen(
    state: DoctorViewUiState,
    onIntent: (DoctorViewIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val projection = remember(state) { state.toOverviewProjection() }
    val reversedRows = remember(state.dailyRows) { state.dailyRows.asReversed() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.doctor_mode_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.doctor_mode_back_cd)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "doctor_privacy_banner", contentType = "privacy_banner") {
                DoctorPrivacyBanner()
            }

            item(key = "doctor_period_selector", contentType = "period_selector") {
                PeriodSelectorCard(
                    state = projection,
                    onSelectPeriod = { onIntent(DoctorViewIntent.SelectPeriod(it)) }
                )
            }

            item(key = "doctor_scales", contentType = "scales") {
                DoctorScalesSummaryCard(
                    scales = state.scalesOverview,
                    referenceYear = state.today.year
                )
            }

            if (projection.chartData.isEmpty) {
                item(key = "doctor_empty", contentType = "empty") {
                    EmptyChartsCard()
                }
            } else {
                item(key = "doctor_kpi", contentType = "kpi") {
                    OverviewKpiSection(summary = projection.summary)
                }

                item(key = "doctor_markers", contentType = "markers") {
                    OverviewKeyMarkersCard(markers = projection.summary.markers)
                }

                item(key = "doctor_calendar", contentType = "calendar") {
                    OverviewCalendarCard(
                        state = projection,
                        onIntent = { overviewIntent ->
                            when (overviewIntent) {
                                OverviewIntent.PreviousMonth -> onIntent(DoctorViewIntent.PreviousMonth)
                                OverviewIntent.NextMonth -> onIntent(DoctorViewIntent.NextMonth)
                                OverviewIntent.JumpToCurrentMonth -> onIntent(DoctorViewIntent.JumpToCurrentMonth)
                                OverviewIntent.OpenMonthYearPicker -> onIntent(DoctorViewIntent.OpenMonthYearPicker)
                                OverviewIntent.DismissMonthYearPicker ->
                                    onIntent(DoctorViewIntent.DismissMonthYearPicker)
                                is OverviewIntent.SelectMonthYear ->
                                    onIntent(DoctorViewIntent.SelectMonthYear(overviewIntent.yearMonth))
                                else -> Unit
                            }
                        },
                        onOpenDiaryDate = null
                    )
                }

                item(key = "doctor_chart_mood", contentType = "chart_mood") {
                    MoodTrendChartCard(
                        chartData = projection.chartData,
                        referenceYear = state.today.year,
                        selectedIndex = state.selectedChartIndex,
                        onSelectIndex = { onIntent(DoctorViewIntent.SelectChartPoint(it)) },
                        onOpenDate = null
                    )
                }

                item(key = "doctor_chart_energy_sleep", contentType = "chart_energy_sleep") {
                    EnergySleepChartCard(
                        chartData = projection.chartData,
                        referenceYear = state.today.year,
                        selectedIndex = state.selectedChartIndex,
                        onSelectIndex = { onIntent(DoctorViewIntent.SelectChartPoint(it)) },
                        onOpenDate = null
                    )
                }

                item(
                    key = "doctor_chart_anxiety_irritability",
                    contentType = "chart_anxiety_irritability"
                ) {
                    AnxietyIrritabilityChartCard(
                        chartData = projection.chartData,
                        referenceYear = state.today.year,
                        selectedIndex = state.selectedChartIndex,
                        onSelectIndex = { onIntent(DoctorViewIntent.SelectChartPoint(it)) },
                        onOpenDate = null
                    )
                }

                item(key = "doctor_daily_log", contentType = "daily_log") {
                    DoctorSanitizedDailyLogCard(
                        rows = reversedRows,
                        referenceYear = state.today.year,
                        expanded = state.isDailyLogExpanded,
                        onToggleExpanded = { onIntent(DoctorViewIntent.ToggleDailyLogExpanded) }
                    )
                }
            }
        }
    }

    if (state.isMonthPickerOpen) {
        MonthYearPickerDialog(
            displayedMonth = state.displayedMonth,
            currentMonth = YearMonth.from(state.today),
            availableYears = state.availableYears,
            onSelect = { onIntent(DoctorViewIntent.SelectMonthYear(it)) },
            onDismiss = { onIntent(DoctorViewIntent.DismissMonthYearPicker) }
        )
    }
}

@Composable
private fun DoctorPrivacyBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.doctor_mode_privacy_banner),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.scales_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun DoctorScalesSummaryCard(scales: List<ReportScaleOverview>, referenceYear: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.doctor_mode_scales_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            scales.forEachIndexed { idx, scale ->
                if (idx > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${scaleCodeLabel(scale.scaleType)} · ${scaleSubtitleLabel(scale.scaleType)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val autoLine = if (scale.autoScore != null && scale.autoFilledDays != null) {
                        stringResource(
                            R.string.doctor_mode_scale_auto,
                            scale.autoFilledDays,
                            scale.windowDays,
                            scale.autoScore,
                            scale.maxScore,
                            scaleSeverityLabel(scale.scaleType, scale.autoScore)
                        )
                    } else {
                        stringResource(R.string.doctor_mode_scale_auto_empty)
                    }
                    Text(
                        text = autoLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val confirmed = scale.latestConfirmed
                    val confirmedLine = if (confirmed != null) {
                        stringResource(
                            R.string.doctor_mode_scale_confirmed,
                            formatShortChartDate(confirmed.date, referenceYear),
                            confirmed.totalScore,
                            scale.maxScore,
                            scaleSeverityLabel(scale.scaleType, confirmed.totalScore)
                        )
                    } else {
                        stringResource(R.string.doctor_mode_scale_confirmed_empty)
                    }
                    Text(
                        text = confirmedLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (confirmed != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (confirmed != null) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun DoctorSanitizedDailyLogCard(
    rows: List<ReportDailyRow>,
    referenceYear: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val moodColors = LocalMoodColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.doctor_mode_daily_log_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onToggleExpanded) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.doctor_mode_daily_log_hide)
                        } else {
                            stringResource(R.string.doctor_mode_daily_log_show, rows.size)
                        }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    rows.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = moodColors.containerColor(row.mood)
                            ) {
                                Text(
                                    text = formatSignedMood(row.mood),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = moodColors.contentColor(row.mood),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${formatShortChartDate(row.date, referenceYear)} · " +
                                        dailyDynamicsLabel(row.dailyDynamics),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                val metricsLine = if (row.sleepMinutes != null) {
                                    stringResource(
                                        R.string.doctor_mode_daily_log_metrics,
                                        formatSleepDuration(row.sleepMinutes),
                                        row.energy,
                                        row.anxiety,
                                        row.irritability
                                    )
                                } else {
                                    stringResource(
                                        R.string.doctor_mode_daily_log_no_sleep,
                                        row.energy,
                                        row.anxiety,
                                        row.irritability
                                    )
                                }
                                Text(
                                    text = metricsLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val tags = PdfTextFormatters.formatSymptomTags(row.symptoms, row.isMixedDay)
                                val details = PdfTextFormatters.formatDetailsSuffix(
                                    row.alcoholStatus,
                                    row.medicationStatus
                                )
                                val combined = listOf(tags, details).filter { it.isNotEmpty() }.joinToString(" · ")
                                if (combined.isNotEmpty()) {
                                    Text(
                                        text = combined,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
