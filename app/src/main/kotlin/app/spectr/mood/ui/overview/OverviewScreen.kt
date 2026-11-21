package app.spectr.mood.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spectr.mood.domain.calculator.AnalyticsCalculator
import app.spectr.mood.domain.calculator.AnalyticsPeriod
import app.spectr.mood.domain.calendar.CalendarMonthBuilder
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.ui.theme.SpectrTheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun OverviewRoute(
    onOpenDiaryDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OverviewScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenDiaryDate = onOpenDiaryDate,
        modifier = modifier
    )
}

@Composable
fun OverviewScreen(
    state: OverviewUiState,
    onIntent: (OverviewIntent) -> Unit,
    onOpenDiaryDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "overview_calendar", contentType = "calendar") {
            OverviewCalendarCard(
                state = state,
                onIntent = onIntent,
                onOpenDiaryDate = onOpenDiaryDate
            )
        }

        item(key = "overview_period_selector", contentType = "period_selector") {
            PeriodSelectorCard(
                state = state,
                onSelectPeriod = { onIntent(OverviewIntent.SelectPeriod(it)) }
            )
        }

        if (state.chartData.isEmpty) {
            item(key = "overview_charts_empty", contentType = "charts_empty") {
                EmptyChartsCard()
            }
        } else {
            item(key = "overview_chart_mood", contentType = "chart_mood") {
                MoodTrendChartCard(
                    chartData = state.chartData,
                    referenceYear = state.today.year,
                    selectedIndex = state.selectedMoodIndex,
                    onSelectIndex = { idx ->
                        onIntent(OverviewIntent.SelectChartPoint(OverviewChartKind.MOOD, idx))
                    },
                    onOpenDate = onOpenDiaryDate
                )
            }

            item(key = "overview_chart_energy_sleep", contentType = "chart_energy_sleep") {
                EnergySleepChartCard(
                    chartData = state.chartData,
                    referenceYear = state.today.year,
                    selectedIndex = state.selectedEnergySleepIndex,
                    onSelectIndex = { idx ->
                        onIntent(OverviewIntent.SelectChartPoint(OverviewChartKind.ENERGY_SLEEP, idx))
                    },
                    onOpenDate = onOpenDiaryDate
                )
            }

            item(
                key = "overview_chart_anxiety_irritability",
                contentType = "chart_anxiety_irritability"
            ) {
                AnxietyIrritabilityChartCard(
                    chartData = state.chartData,
                    referenceYear = state.today.year,
                    selectedIndex = state.selectedAnxietyIrritabilityIndex,
                    onSelectIndex = { idx ->
                        onIntent(OverviewIntent.SelectChartPoint(OverviewChartKind.ANXIETY_IRRITABILITY, idx))
                    },
                    onOpenDate = onOpenDiaryDate
                )
            }
        }

        item(key = "overview_kpi_summary", contentType = "kpi_summary") {
            OverviewKpiSection(summary = state.summary)
        }

        item(key = "overview_key_markers", contentType = "key_markers") {
            OverviewKeyMarkersCard(markers = state.summary.markers)
        }
    }

    if (state.isMonthYearPickerOpen) {
        MonthYearPickerDialog(
            displayedMonth = state.displayedMonth,
            currentMonth = state.currentMonth,
            availableYears = state.availableYears,
            onSelect = { onIntent(OverviewIntent.SelectMonthYear(it)) },
            onDismiss = { onIntent(OverviewIntent.DismissMonthYearPicker) }
        )
    }
}

internal fun sampleOverviewPreviewState(): OverviewUiState {
    val today = LocalDate.of(2026, 11, 21)
    val month = YearMonth.from(today)
    val sampleEntries = listOf(
        DailyEntry(
            id = "e1",
            date = today.minusDays(12),
            mood = -2,
            energy = 3,
            sleepMinutesOverride = 330,
            anxiety = 7,
            irritability = 5,
            symptoms = DailySymptoms(apathy = true, selfCriticism = true)
        ),
        DailyEntry(
            id = "e2",
            date = today.minusDays(10),
            mood = -1,
            energy = 7,
            dailyDynamics = DailyDynamics.SWINGS,
            sleepMinutesOverride = 310,
            anxiety = 6,
            irritability = 8,
            symptoms = DailySymptoms(racingThoughts = true, brainFog = true)
        ),
        DailyEntry(
            id = "e3",
            date = today.minusDays(7),
            mood = 0,
            energy = 5,
            sleepMinutesOverride = 450,
            anxiety = 3,
            irritability = 2
        ),
        DailyEntry(
            id = "e4",
            date = today.minusDays(4),
            mood = 2,
            energy = 8,
            sleepMinutesOverride = 420,
            anxiety = 2,
            irritability = 3,
            symptoms = DailySymptoms(hyperfocus = true),
            alcoholStatus = AlcoholStatus.LIGHT_OCCASION
        ),
        DailyEntry(
            id = "e5",
            date = today.minusDays(1),
            mood = 1,
            energy = 6,
            sleepMinutesOverride = 465,
            anxiety = 4,
            irritability = 3,
            medicationStatus = MedicationStatus.TAKEN_AS_PRESCRIBED
        ),
        DailyEntry(
            id = "e6",
            date = today,
            mood = 0,
            energy = 6,
            sleepMinutesOverride = 440,
            anxiety = 3,
            irritability = 2
        )
    )
    val filtered = AnalyticsCalculator.filterEntriesForPeriod(sampleEntries, AnalyticsPeriod.LAST_30_DAYS, today)
    return OverviewUiState(
        today = today,
        displayedMonth = month,
        calendarGrid = CalendarMonthBuilder.buildMonthGrid(
            yearMonth = month,
            today = today,
            entriesByDate = sampleEntries.associateBy { it.date }
        ),
        availableYears = listOf(2024, 2025, 2026),
        selectedPeriod = OverviewPeriodOption.DAYS_30,
        totalActiveEntries = sampleEntries.size,
        summary = AnalyticsCalculator.calculateSummary(sampleEntries, AnalyticsPeriod.LAST_30_DAYS, today),
        chartData = ChartGeometry.buildOverviewChartData(filtered),
        selectedMoodIndex = 1,
        isLoading = false
    )
}

@Preview(showBackground = true)
@Composable
private fun OverviewScreenLightPreview() {
    SpectrTheme(themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE)) {
        OverviewScreen(
            state = sampleOverviewPreviewState(),
            onIntent = {},
            onOpenDiaryDate = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OverviewScreenAmoledPreview() {
    SpectrTheme(themeConfig = ThemeConfig(mode = ThemeMode.AMOLED, palette = ThemePalette.WARM_PAPER)) {
        OverviewScreen(
            state = sampleOverviewPreviewState(),
            onIntent = {},
            onOpenDiaryDate = {}
        )
    }
}
