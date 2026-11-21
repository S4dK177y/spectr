package app.spectr.mood.ui.scales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spectr.mood.R
import app.spectr.mood.domain.calculator.ClinicalScaleCalculator
import app.spectr.mood.domain.chart.ChartGeometry
import app.spectr.mood.domain.model.DailyEntry
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.domain.model.ScaleType
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.ui.theme.SpectrTheme
import java.time.LocalDate

@Composable
fun ScalesRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ScalesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarText = state.snackbarMessage?.let { scalesSnackbarText(it) }

    LaunchedEffect(state.snackbarMessage, snackbarText) {
        if (state.snackbarMessage != null && snackbarText != null) {
            snackbarHostState.showSnackbar(snackbarText)
            viewModel.onIntent(ScalesIntent.ConsumeSnackbar)
        }
    }

    ScalesScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier
    )
}

@Composable
fun ScalesScreen(state: ScalesUiState, onIntent: (ScalesIntent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "scales_disclaimer") {
            ScalesDisclaimerCard()
        }

        item(key = "scales_auto_header") {
            Text(
                text = stringResource(R.string.scales_auto_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(
            items = ScaleType.entries,
            key = { "auto_scale_${it.name}" }
        ) { scaleType ->
            AutoScaleCard(
                scaleType = scaleType,
                autoResult = state.autoSnapshot?.resultFor(scaleType),
                latestConfirmed = state.latestConfirmedByType[scaleType],
                isExpanded = scaleType in state.expandedScaleDetails,
                referenceYear = state.today.year,
                onToggleExpand = { onIntent(ScalesIntent.ToggleScaleDetails(scaleType)) },
                onConfirmClick = { onIntent(ScalesIntent.OpenConfirmScale(scaleType)) }
            )
        }

        item(key = "scales_history_header") {
            ScalesHistoryHeader(
                selectedFilter = state.historyFilter,
                onSelectFilter = { onIntent(ScalesIntent.SelectHistoryFilter(it)) }
            )
        }

        if (state.allAssessments.isNotEmpty()) {
            item(key = "scales_trend_chart") {
                ScaleTrendChartCard(
                    scaleType = state.trendScaleType,
                    showScaleSwitcher = state.historyFilter == ScalesHistoryFilter.ALL,
                    points = state.trendPoints,
                    selectedIndex = state.selectedTrendIndex,
                    referenceYear = state.today.year,
                    onSelectScale = { onIntent(ScalesIntent.SelectTrendScale(it)) },
                    onSelectPoint = { onIntent(ScalesIntent.SelectTrendPoint(it)) }
                )
            }
        }

        if (state.filteredHistory.isEmpty()) {
            item(key = "scales_history_empty") {
                ScaleHistoryEmptyCard()
            }
        } else {
            items(
                items = state.filteredHistory,
                key = { it.assessment.id }
            ) { row ->
                ScaleHistoryRowCard(
                    row = row,
                    referenceYear = state.today.year,
                    onEditClick = { onIntent(ScalesIntent.OpenEditAssessment(row.assessment)) },
                    onDeleteClick = { onIntent(ScalesIntent.RequestDeleteAssessment(row.assessment)) }
                )
            }
        }
    }

    state.editor?.let { editor ->
        val editingAssessment = editor.editingAssessmentId?.let { id ->
            state.allAssessments.firstOrNull { it.id == id }
        }
        ScaleConfirmationSheet(
            editor = editor,
            today = state.today,
            onIntent = onIntent,
            onRequestDelete = editingAssessment?.let { target ->
                { onIntent(ScalesIntent.RequestDeleteAssessment(target)) }
            }
        )
    }

    state.pendingDeleteAssessment?.let { assessment ->
        DeleteAssessmentConfirmDialog(
            assessment = assessment,
            referenceYear = state.today.year,
            onConfirm = { onIntent(ScalesIntent.ConfirmDeleteAssessment) },
            onDismiss = { onIntent(ScalesIntent.DismissDeleteAssessment) }
        )
    }
}

internal fun sampleScalesPreviewState(): ScalesUiState {
    val today = LocalDate.of(2026, 11, 21)
    val sampleEntries = (0 until 10).map { offset ->
        DailyEntry(
            id = "d$offset",
            date = today.minusDays(offset.toLong()),
            mood = if (offset % 3 == 0) -2 else 0,
            energy = if (offset % 2 == 0) 3 else 6,
            sleepMinutesOverride = if (offset % 4 == 0) 320 else 440,
            anxiety = if (offset % 2 == 0) 6 else 3,
            irritability = 5,
            symptoms = DailySymptoms(apathy = offset % 2 == 0, brainFog = offset % 3 == 0)
        )
    }
    val snapshot = ClinicalScaleCalculator.calculateAutoScales(sampleEntries, today)
    val a1 = ScaleAssessment(
        id = "a1",
        scaleType = ScaleType.PHQ9,
        date = today.minusDays(28),
        answers = listOf(2, 2, 1, 2, 1, 2, 2, 1, 0),
        totalScore = 13,
        isConfirmed = true
    )
    val a2 = ScaleAssessment(
        id = "a2",
        scaleType = ScaleType.PHQ9,
        date = today.minusDays(14),
        answers = listOf(1, 2, 1, 1, 1, 1, 1, 1, 0),
        totalScore = 9,
        isConfirmed = true
    )
    val a3 = ScaleAssessment(
        id = "a3",
        scaleType = ScaleType.GAD7,
        date = today.minusDays(7),
        answers = listOf(2, 1, 1, 1, 1, 1, 0),
        totalScore = 7,
        isConfirmed = true
    )
    val all = listOf(a3, a2, a1)
    return ScalesUiState(
        today = today,
        autoSnapshot = snapshot,
        allAssessments = all,
        filteredHistory = listOf(
            ScaleHistoryRow(a3, deltaFromPrevious = null),
            ScaleHistoryRow(a2, deltaFromPrevious = -4),
            ScaleHistoryRow(a1, deltaFromPrevious = null)
        ),
        latestConfirmedByType = mapOf(ScaleType.PHQ9 to a2, ScaleType.GAD7 to a3),
        trendScaleType = ScaleType.PHQ9,
        trendPoints = ChartGeometry.buildScaleTrendPoints(listOf(a1, a2)),
        isLoading = false
    )
}

@Preview(showBackground = true)
@Composable
private fun ScalesScreenLightPreview() {
    SpectrTheme(themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE)) {
        ScalesScreen(
            state = sampleScalesPreviewState(),
            onIntent = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScalesScreenAmoledPreview() {
    SpectrTheme(themeConfig = ThemeConfig(mode = ThemeMode.AMOLED, palette = ThemePalette.WARM_PAPER)) {
        ScalesScreen(
            state = sampleScalesPreviewState(),
            onIntent = {}
        )
    }
}
