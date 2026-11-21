package app.spectr.mood.ui.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spectr.mood.R
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.DailySymptoms
import app.spectr.mood.domain.model.MomentCheckIn
import app.spectr.mood.domain.model.ThemeConfig
import app.spectr.mood.domain.model.ThemeMode
import app.spectr.mood.domain.model.ThemePalette
import app.spectr.mood.ui.theme.SpectrTheme
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DiaryRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    requestedDate: LocalDate? = null,
    onRequestedDateConsumed: () -> Unit = {},
    openMomentRequestId: Long = 0L,
    onOpenMomentRequestConsumed: () -> Unit = {},
    viewModel: DiaryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarText = state.snackbarMessage?.let { snackbarMessageText(it) }

    LaunchedEffect(requestedDate) {
        if (requestedDate != null) {
            viewModel.onIntent(DiaryIntent.SelectDate(requestedDate))
            onRequestedDateConsumed()
        }
    }

    LaunchedEffect(openMomentRequestId) {
        if (openMomentRequestId != 0L) {
            viewModel.onIntent(DiaryIntent.OpenQuickMomentForToday)
            onOpenMomentRequestConsumed()
        }
    }

    LaunchedEffect(state.snackbarMessage, snackbarText) {
        if (state.snackbarMessage != null && snackbarText != null) {
            snackbarHostState.showSnackbar(snackbarText)
            viewModel.onIntent(DiaryIntent.ConsumeSnackbar)
        }
    }

    DiaryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier
    )
}

@Composable
fun DiaryScreen(state: DiaryUiState, onIntent: (DiaryIntent) -> Unit, modifier: Modifier = Modifier) {
    val onAddMoment = androidx.compose.runtime.remember(onIntent) {
        { onIntent(DiaryIntent.OpenNewMoment) }
    }
    val onEditMoment = androidx.compose.runtime.remember<(MomentCheckIn) -> Unit>(onIntent) {
        { onIntent(DiaryIntent.OpenEditMoment(it)) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "date_switcher", contentType = "date_switcher") {
            DateSwitcherCard(
                selectedDate = state.selectedDate,
                today = state.today,
                isToday = state.isToday,
                isYesterday = state.isYesterday,
                canGoNextDay = state.canGoNextDay,
                hasSavedEntry = state.hasSavedEntry,
                hasUnsavedChanges = state.hasUnsavedChanges,
                onIntent = onIntent
            )
        }

        item(key = "moments_section", contentType = "moments_section") {
            MomentsSectionCard(
                moments = state.moments,
                onAddMoment = onAddMoment,
                onEditMoment = onEditMoment
            )
        }

        item(key = "card_mood", contentType = "card_mood") {
            MoodCard(
                form = state.form,
                onIntent = onIntent
            )
        }

        item(key = "card_sleep_energy", contentType = "card_sleep_energy") {
            SleepAndEnergyCard(
                form = state.form,
                onIntent = onIntent
            )
        }

        item(key = "card_anxiety_symptoms", contentType = "card_anxiety_symptoms") {
            AnxietySymptomsCard(
                form = state.form,
                onIntent = onIntent
            )
        }

        item(key = "card_details_note", contentType = "card_details_note") {
            DetailsAndNoteCard(
                form = state.form,
                onIntent = onIntent
            )
        }

        item(key = "diary_footer_actions", contentType = "diary_footer_actions") {
            DiaryActionFooter(
                hasSavedEntry = state.hasSavedEntry,
                hasUnsavedChanges = state.hasUnsavedChanges,
                canSaveEntry = state.canSaveEntry,
                onIntent = onIntent
            )
        }
    }

    if (state.isDatePickerOpen) {
        DiaryDatePickerDialog(
            selectedDate = state.selectedDate,
            today = state.today,
            onSelectDate = { onIntent(DiaryIntent.SelectDate(it)) },
            onDismiss = { onIntent(DiaryIntent.DismissDatePicker) }
        )
    }

    state.momentEditor?.let { editor ->
        MomentEditorSheet(
            editor = editor,
            onIntent = onIntent
        )
    }

    state.sleepIntervalEditor?.let { editor ->
        SleepIntervalDialog(
            editor = editor,
            onIntent = onIntent
        )
    }

    state.activeHelpTopic?.let { topic ->
        DiaryHelpSheet(
            topic = topic,
            onDismiss = { onIntent(DiaryIntent.DismissHelp) }
        )
    }

    if (state.isDeleteEntryDialogOpen) {
        DeleteEntryConfirmDialog(
            selectedDate = state.selectedDate,
            today = state.today,
            onConfirm = { onIntent(DiaryIntent.ConfirmDeleteEntry) },
            onDismiss = { onIntent(DiaryIntent.DismissDeleteEntry) }
        )
    }
}

@Composable
private fun DiaryActionFooter(
    hasSavedEntry: Boolean,
    hasUnsavedChanges: Boolean,
    canSaveEntry: Boolean,
    onIntent: (DiaryIntent) -> Unit
) {
    val isUpToDateSaved = hasSavedEntry && !hasUnsavedChanges
    val buttonLabel = when {
        !hasSavedEntry -> stringResource(R.string.diary_save_button)
        hasUnsavedChanges -> stringResource(R.string.diary_update_button)
        else -> stringResource(R.string.diary_saved_button)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { onIntent(DiaryIntent.SaveDailyEntry) },
            enabled = canSaveEntry,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            AnimatedContent(
                targetState = isUpToDateSaved to buttonLabel,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "diary_save_button_state"
            ) { (savedClean, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (savedClean) Icons.Outlined.Check else Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label)
                }
            }
        }

        AnimatedVisibility(visible = hasSavedEntry) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { onIntent(DiaryIntent.RequestDeleteEntry) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.diary_delete_entry_button))
                }
            }
        }
    }
}

internal fun sampleDiaryPreviewState(): DiaryUiState {
    val previewDate = LocalDate.of(2026, 11, 21)
    val baseMillis = previewDate.atTime(10, 15).toInstant(ZoneOffset.UTC).toEpochMilli()
    return DiaryUiState(
        selectedDate = previewDate,
        today = previewDate,
        isLoading = false,
        hasSavedEntry = true,
        hasUnsavedChanges = true,
        form = DailyEntryFormState(
            mood = -2,
            dailyDynamics = DailyDynamics.SWINGS,
            sleepIntervals = listOf(
                SleepIntervalDraft(
                    id = "s1",
                    startMinuteOfDay = 22 * 60,
                    endMinuteOfDay = 90
                ),
                SleepIntervalDraft(
                    id = "s2",
                    startMinuteOfDay = 9 * 60,
                    endMinuteOfDay = 12 * 60 + 30,
                    awakeDeductionMin = 20
                )
            ),
            energy = 7,
            anxiety = 6,
            irritability = 7,
            symptoms = DailySymptoms(brainFog = true, racingThoughts = true),
            note = "Неровный день, вечером стало спокойнее."
        ),
        moments = listOf(
            MomentCheckIn(
                id = "m1",
                entryDate = previewDate,
                timestampEpochMs = baseMillis,
                mood = -2,
                energy = 4,
                note = "Тяжело раскачаться с утра"
            ),
            MomentCheckIn(
                id = "m2",
                entryDate = previewDate,
                timestampEpochMs = baseMillis + 5 * 3_600_000L,
                mood = 1,
                energy = 7,
                anxiety = 5,
                note = "После прогулки стало легче"
            )
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun DiaryScreenLightPreview() {
    SpectrTheme(
        themeConfig = ThemeConfig(mode = ThemeMode.LIGHT, palette = ThemePalette.SOFT_SLATE)
    ) {
        DiaryScreen(
            state = sampleDiaryPreviewState(),
            onIntent = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiaryScreenAmoledPreview() {
    SpectrTheme(
        themeConfig = ThemeConfig(mode = ThemeMode.AMOLED, palette = ThemePalette.WARM_PAPER)
    ) {
        DiaryScreen(
            state = sampleDiaryPreviewState(),
            onIntent = {}
        )
    }
}
