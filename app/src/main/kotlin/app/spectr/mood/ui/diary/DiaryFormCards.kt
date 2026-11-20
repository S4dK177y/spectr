package app.spectr.mood.ui.diary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.calculator.SleepDurationCalculator
import app.spectr.mood.domain.model.AlcoholStatus
import app.spectr.mood.domain.model.DailyDynamics
import app.spectr.mood.domain.model.MedicationStatus
import app.spectr.mood.ui.theme.LocalMoodColors
import kotlin.math.roundToInt

private const val HIGH_RATING_THRESHOLD = 7

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoodCard(form: DailyEntryFormState, onIntent: (DiaryIntent) -> Unit, modifier: Modifier = Modifier) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.diary_card_mood_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { onIntent(DiaryIntent.OpenHelp(DiaryHelpTopic.MoodScale)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.diary_mood_help_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            MoodScaleSelector(
                selectedMood = form.mood,
                onSelectMood = { onIntent(DiaryIntent.UpdateMood(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.diary_dynamics_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DailyDynamics.entries.forEach { dynamics ->
                    FilterChip(
                        selected = form.dailyDynamics == dynamics,
                        onClick = { onIntent(DiaryIntent.UpdateDynamics(dynamics)) },
                        label = { Text(text = dailyDynamicsLabel(dynamics)) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MoodScaleSelector(selectedMood: Int, onSelectMood: (Int) -> Unit, modifier: Modifier = Modifier) {
    val moodColors = LocalMoodColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = moodColors.containerColor(selectedMood),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = formatSignedMood(selectedMood),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = moodColors.contentColor(selectedMood)
                )
                Text(
                    text = moodDescription(selectedMood),
                    style = MaterialTheme.typography.bodyMedium,
                    color = moodColors.contentColor(selectedMood)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        MoodScoreRow(
            scores = listOf(-5, -4, -3, -2, -1),
            selectedMood = selectedMood,
            onSelectMood = onSelectMood
        )
        Spacer(modifier = Modifier.height(8.dp))
        MoodScoreRow(
            scores = listOf(0, 1, 2, 3, 4, 5),
            selectedMood = selectedMood,
            onSelectMood = onSelectMood
        )
    }
}

@Composable
private fun MoodScoreRow(scores: List<Int>, selectedMood: Int, onSelectMood: (Int) -> Unit) {
    val moodColors = LocalMoodColors.current
    val outlineColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        scores.forEach { score ->
            val selected = score == selectedMood
            val containerColor = moodColors.containerColor(score)
            val contentColor = moodColors.contentColor(score)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) containerColor else containerColor.copy(alpha = 0.65f))
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 2.dp,
                                color = outlineColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectMood(score) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatSignedMood(score),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
internal fun SleepAndEnergyCard(
    form: DailyEntryFormState,
    onIntent: (DiaryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.diary_card_sleep_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SleepSummaryPill(form = form)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.sleepInputMode == SleepInputMode.INTERVALS,
                    onClick = { onIntent(DiaryIntent.UpdateSleepInputMode(SleepInputMode.INTERVALS)) },
                    label = { Text(text = stringResource(R.string.diary_sleep_mode_intervals)) }
                )
                FilterChip(
                    selected = form.sleepInputMode == SleepInputMode.MANUAL_HOURS,
                    onClick = { onIntent(DiaryIntent.UpdateSleepInputMode(SleepInputMode.MANUAL_HOURS)) },
                    label = { Text(text = stringResource(R.string.diary_sleep_mode_manual)) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (form.sleepInputMode) {
                SleepInputMode.INTERVALS -> SleepIntervalsSection(
                    intervals = form.sleepIntervals,
                    onAdd = { onIntent(DiaryIntent.OpenNewSleepInterval) },
                    onEdit = { onIntent(DiaryIntent.OpenEditSleepInterval(it)) },
                    onRemove = { onIntent(DiaryIntent.RemoveSleepInterval(it)) }
                )

                SleepInputMode.MANUAL_HOURS -> ManualSleepSection(
                    form = form,
                    onTextChange = { onIntent(DiaryIntent.UpdateManualSleepText(it)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = form.sleepNote,
                onValueChange = { onIntent(DiaryIntent.UpdateSleepNote(it)) },
                label = { Text(text = stringResource(R.string.diary_sleep_note_label)) },
                placeholder = { Text(text = stringResource(R.string.diary_sleep_note_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(14.dp))

            RatingSliderBlock(
                label = stringResource(R.string.diary_energy_label),
                value = form.energy,
                minHint = stringResource(R.string.diary_energy_min_hint),
                maxHint = stringResource(R.string.diary_energy_max_hint),
                highlightHigh = false,
                onValueChange = { onIntent(DiaryIntent.UpdateEnergy(it)) }
            )
        }
    }
}

@Composable
private fun SleepSummaryPill(form: DailyEntryFormState) {
    val containerColor = when {
        !form.hasSleepData -> MaterialTheme.colorScheme.surfaceContainerHighest
        form.isShortSleep -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        !form.hasSleepData -> MaterialTheme.colorScheme.onSurfaceVariant
        form.isShortSleep -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val text = when {
        !form.hasSleepData -> stringResource(R.string.diary_sleep_total_empty)
        form.isShortSleep -> stringResource(
            R.string.diary_sleep_short_warning,
            formatSleepDuration(form.effectiveSleepMinutes)
        )
        else -> formatSleepDuration(form.effectiveSleepMinutes)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SleepIntervalsSection(
    intervals: List<SleepIntervalDraft>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (intervals.isEmpty()) {
            Text(
                text = stringResource(R.string.diary_sleep_empty_intervals),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            intervals.forEach { interval ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onEdit(interval.id) },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bedtime,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                val startStr = SleepDurationCalculator.formatMinuteOfDay(interval.startMinuteOfDay)
                                val endStr = SleepDurationCalculator.formatMinuteOfDay(interval.endMinuteOfDay)
                                Text(
                                    text = "$startStr – $endStr",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = formatSleepDuration(interval.netMinutes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (interval.awakeDeductionMin > 0) {
                                        Text(
                                            text = stringResource(
                                                R.string.diary_sleep_deduction_tag,
                                                interval.awakeDeductionMin
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(onClick = { onRemove(interval.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.diary_sleep_remove_interval_cd),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = onAdd,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.diary_sleep_add_interval).removePrefix("+ "))
        }
    }
}

@Composable
private fun ManualSleepSection(form: DailyEntryFormState, onTextChange: (String) -> Unit) {
    val parsed = form.manualSleepParseResult
    val supportingText: @Composable (() -> Unit)? = when {
        form.isManualSleepInvalid -> {
            { Text(text = stringResource(R.string.diary_sleep_manual_error)) }
        }
        parsed.isRecognized -> {
            {
                Text(
                    text = stringResource(
                        R.string.diary_sleep_manual_parsed,
                        formatSleepDuration(parsed.totalMinutes)
                    )
                )
            }
        }
        else -> null
    }

    OutlinedTextField(
        value = form.manualSleepText,
        onValueChange = onTextChange,
        label = { Text(text = stringResource(R.string.diary_sleep_manual_label)) },
        placeholder = { Text(text = stringResource(R.string.diary_sleep_manual_placeholder)) },
        isError = form.isManualSleepInvalid,
        supportingText = supportingText,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnxietySymptomsCard(
    form: DailyEntryFormState,
    onIntent: (DiaryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.diary_card_symptoms_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            AnimatedVisibility(visible = form.isMixedDay) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.diary_mixed_day_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.diary_mixed_day_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            RatingSliderBlock(
                label = stringResource(R.string.diary_anxiety_label),
                value = form.anxiety,
                minHint = stringResource(R.string.diary_anxiety_min_hint),
                maxHint = stringResource(R.string.diary_anxiety_max_hint),
                highlightHigh = true,
                onValueChange = { onIntent(DiaryIntent.UpdateAnxiety(it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            RatingSliderBlock(
                label = stringResource(R.string.diary_irritability_label),
                value = form.irritability,
                minHint = stringResource(R.string.diary_irritability_min_hint),
                maxHint = stringResource(R.string.diary_irritability_max_hint),
                highlightHigh = true,
                onValueChange = { onIntent(DiaryIntent.UpdateIrritability(it)) }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.diary_symptoms_section_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onIntent(DiaryIntent.OpenHelp(DiaryHelpTopic.AllSymptoms)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.diary_symptoms_all_help_cd),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SymptomType.entries.forEach { symptom ->
                    val label = symptomLabel(symptom)
                    FilterChip(
                        selected = form.isSymptomSelected(symptom),
                        onClick = { onIntent(DiaryIntent.ToggleSymptom(symptom)) },
                        label = { Text(text = label) },
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onIntent(DiaryIntent.OpenHelp(DiaryHelpTopic.SingleSymptom(symptom)))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                    contentDescription = stringResource(R.string.diary_symptom_help_cd, label),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsAndNoteCard(
    form: DailyEntryFormState,
    onIntent: (DiaryIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.diary_card_details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.diary_alcohol_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AlcoholStatus.entries.forEach { status ->
                    FilterChip(
                        selected = form.alcoholStatus == status,
                        onClick = { onIntent(DiaryIntent.UpdateAlcohol(status)) },
                        label = { Text(text = alcoholStatusLabel(status)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.diary_medication_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MedicationStatus.entries.forEach { status ->
                    FilterChip(
                        selected = form.medicationStatus == status,
                        onClick = { onIntent(DiaryIntent.UpdateMedication(status)) },
                        label = { Text(text = medicationStatusLabel(status)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = form.note,
                onValueChange = { onIntent(DiaryIntent.UpdateNote(it)) },
                label = { Text(text = stringResource(R.string.diary_note_label)) },
                placeholder = { Text(text = stringResource(R.string.diary_note_placeholder)) },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun RatingSliderBlock(
    label: String,
    value: Int,
    minHint: String,
    maxHint: String,
    highlightHigh: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHigh = highlightHigh && value >= HIGH_RATING_THRESHOLD
    val badgeBg = if (isHigh) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeFg = if (isHigh) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.diary_rating_out_of_ten, value),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = minHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = maxHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
