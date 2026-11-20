package app.spectr.mood.ui.diary

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.calculator.SleepDurationCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private const val MINUTES_PER_HOUR = 60
private const val DEFAULT_OPTIONAL_METRIC_VALUE = 5
private val DEDUCTION_PRESETS = listOf(0, 15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryDatePickerDialog(
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val selectableDates = remember(maxMillis, today.year) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxMillis
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedMillis,
        selectableDates = selectableDates
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val pickedMillis = datePickerState.selectedDateMillis
                    if (pickedMillis != null) {
                        val pickedDate = Instant.ofEpochMilli(pickedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onSelectDate(pickedDate)
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(text = stringResource(R.string.diary_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.diary_action_cancel))
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = stringResource(R.string.diary_date_picker_title),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 12.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MomentEditorSheet(editor: MomentEditorState, onIntent: (DiaryIntent) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTimePicker by remember { mutableStateOf(false) }
    val localTime = Instant.ofEpochMilli(editor.timestampEpochMs).atZone(ZoneId.systemDefault()).toLocalTime()

    ModalBottomSheet(
        onDismissRequest = { onIntent(DiaryIntent.DismissMomentEditor) },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        if (editor.editingId == null) {
                            R.string.diary_moment_sheet_new_title
                        } else {
                            R.string.diary_moment_sheet_edit_title
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTimePicker = true },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = stringResource(R.string.diary_moment_time_label),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = formatMomentTime(editor.timestampEpochMs),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            MoodScaleSelector(
                selectedMood = editor.mood,
                onSelectMood = { onIntent(DiaryIntent.UpdateMomentMood(it)) }
            )

            OutlinedTextField(
                value = editor.note,
                onValueChange = { onIntent(DiaryIntent.UpdateMomentNote(it)) },
                label = { Text(text = stringResource(R.string.diary_moment_note_label)) },
                placeholder = { Text(text = stringResource(R.string.diary_moment_note_placeholder)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.diary_moment_optional_metrics),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = editor.energy != null,
                        onClick = {
                            onIntent(
                                DiaryIntent.UpdateMomentEnergy(
                                    if (editor.energy == null) DEFAULT_OPTIONAL_METRIC_VALUE else null
                                )
                            )
                        },
                        label = { Text(text = stringResource(R.string.diary_energy_label)) }
                    )
                    FilterChip(
                        selected = editor.anxiety != null,
                        onClick = {
                            onIntent(
                                DiaryIntent.UpdateMomentAnxiety(
                                    if (editor.anxiety == null) DEFAULT_OPTIONAL_METRIC_VALUE else null
                                )
                            )
                        },
                        label = { Text(text = stringResource(R.string.diary_anxiety_label)) }
                    )
                    FilterChip(
                        selected = editor.irritability != null,
                        onClick = {
                            onIntent(
                                DiaryIntent.UpdateMomentIrritability(
                                    if (editor.irritability == null) DEFAULT_OPTIONAL_METRIC_VALUE else null
                                )
                            )
                        },
                        label = { Text(text = stringResource(R.string.diary_irritability_label)) }
                    )
                }

                editor.energy?.let { value ->
                    RatingSliderBlock(
                        label = stringResource(R.string.diary_energy_label),
                        value = value,
                        minHint = stringResource(R.string.diary_energy_min_hint),
                        maxHint = stringResource(R.string.diary_energy_max_hint),
                        highlightHigh = false,
                        onValueChange = { onIntent(DiaryIntent.UpdateMomentEnergy(it)) }
                    )
                }

                editor.anxiety?.let { value ->
                    RatingSliderBlock(
                        label = stringResource(R.string.diary_anxiety_label),
                        value = value,
                        minHint = stringResource(R.string.diary_anxiety_min_hint),
                        maxHint = stringResource(R.string.diary_anxiety_max_hint),
                        highlightHigh = true,
                        onValueChange = { onIntent(DiaryIntent.UpdateMomentAnxiety(it)) }
                    )
                }

                editor.irritability?.let { value ->
                    RatingSliderBlock(
                        label = stringResource(R.string.diary_irritability_label),
                        value = value,
                        minHint = stringResource(R.string.diary_irritability_min_hint),
                        maxHint = stringResource(R.string.diary_irritability_max_hint),
                        highlightHigh = true,
                        onValueChange = { onIntent(DiaryIntent.UpdateMomentIrritability(it)) }
                    )
                }
            }

            Button(
                onClick = { onIntent(DiaryIntent.SaveMoment) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(
                        if (editor.editingId == null) {
                            R.string.diary_moment_save_button
                        } else {
                            R.string.diary_moment_update_button
                        }
                    )
                )
            }

            editor.editingId?.let { existingId ->
                TextButton(
                    onClick = { onIntent(DiaryIntent.DeleteMoment(existingId)) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = stringResource(R.string.diary_action_delete))
                }
            }
        }
    }

    if (showTimePicker) {
        ClockTimePickerDialog(
            title = stringResource(R.string.diary_moment_time_picker_title),
            initialHour = localTime.hour,
            initialMinute = localTime.minute,
            onConfirm = { hour, minute ->
                showTimePicker = false
                onIntent(DiaryIntent.UpdateMomentTime(hour, minute))
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SleepIntervalDialog(editor: SleepIntervalEditorState, onIntent: (DiaryIntent) -> Unit) {
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var customDeductionText by remember(editor.editingId) {
        mutableStateOf(
            if (editor.awakeDeductionMin > 0 && editor.awakeDeductionMin !in DEDUCTION_PRESETS) {
                editor.awakeDeductionMin.toString()
            } else {
                ""
            }
        )
    }

    AlertDialog(
        onDismissRequest = { onIntent(DiaryIntent.DismissSleepIntervalEditor) },
        title = {
            Text(
                text = stringResource(
                    if (editor.editingId == null) {
                        R.string.diary_sleep_dialog_new_title
                    } else {
                        R.string.diary_sleep_dialog_edit_title
                    }
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimeSlotCard(
                        label = stringResource(R.string.diary_sleep_start_label),
                        timeText = SleepDurationCalculator.formatMinuteOfDay(editor.startMinuteOfDay),
                        onClick = { pickingStart = true },
                        modifier = Modifier.weight(1f)
                    )
                    TimeSlotCard(
                        label = stringResource(R.string.diary_sleep_end_label),
                        timeText = SleepDurationCalculator.formatMinuteOfDay(editor.endMinuteOfDay),
                        onClick = { pickingEnd = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.diary_sleep_net_preview,
                            formatSleepDuration(editor.netMinutes)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.diary_sleep_deduction_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DEDUCTION_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = editor.awakeDeductionMin == preset && customDeductionText.isBlank(),
                                onClick = {
                                    customDeductionText = ""
                                    onIntent(
                                        DiaryIntent.UpdateSleepIntervalDraft(
                                            startMinuteOfDay = editor.startMinuteOfDay,
                                            endMinuteOfDay = editor.endMinuteOfDay,
                                            awakeDeductionMin = preset
                                        )
                                    )
                                },
                                label = { Text(text = "$preset") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customDeductionText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            customDeductionText = digits
                            val parsed = digits.toIntOrNull() ?: 0
                            onIntent(
                                DiaryIntent.UpdateSleepIntervalDraft(
                                    startMinuteOfDay = editor.startMinuteOfDay,
                                    endMinuteOfDay = editor.endMinuteOfDay,
                                    awakeDeductionMin = parsed
                                )
                            )
                        },
                        label = { Text(text = stringResource(R.string.diary_sleep_deduction_custom_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(DiaryIntent.ConfirmSleepInterval) }) {
                Text(text = stringResource(R.string.diary_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(DiaryIntent.DismissSleepIntervalEditor) }) {
                Text(text = stringResource(R.string.diary_action_cancel))
            }
        }
    )

    if (pickingStart) {
        ClockTimePickerDialog(
            title = stringResource(R.string.diary_sleep_start_label),
            initialHour = editor.startMinuteOfDay / MINUTES_PER_HOUR,
            initialMinute = editor.startMinuteOfDay % MINUTES_PER_HOUR,
            onConfirm = { hour, minute ->
                pickingStart = false
                onIntent(
                    DiaryIntent.UpdateSleepIntervalDraft(
                        startMinuteOfDay = hour * MINUTES_PER_HOUR + minute,
                        endMinuteOfDay = editor.endMinuteOfDay,
                        awakeDeductionMin = editor.awakeDeductionMin
                    )
                )
            },
            onDismiss = { pickingStart = false }
        )
    }

    if (pickingEnd) {
        ClockTimePickerDialog(
            title = stringResource(R.string.diary_sleep_end_label),
            initialHour = editor.endMinuteOfDay / MINUTES_PER_HOUR,
            initialMinute = editor.endMinuteOfDay % MINUTES_PER_HOUR,
            onConfirm = { hour, minute ->
                pickingEnd = false
                onIntent(
                    DiaryIntent.UpdateSleepIntervalDraft(
                        startMinuteOfDay = editor.startMinuteOfDay,
                        endMinuteOfDay = hour * MINUTES_PER_HOUR + minute,
                        awakeDeductionMin = editor.awakeDeductionMin
                    )
                )
            },
            onDismiss = { pickingEnd = false }
        )
    }
}

@Composable
private fun TimeSlotCard(label: String, timeText: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            TimeInput(state = timePickerState)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(text = stringResource(R.string.diary_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.diary_action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryHelpSheet(topic: DiaryHelpTopic, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (topic) {
                DiaryHelpTopic.MoodScale -> MoodHelpContent()
                is DiaryHelpTopic.SingleSymptom -> SymptomHelpContent(symptom = topic.symptom)
                DiaryHelpTopic.AllSymptoms -> AllSymptomsHelpContent()
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = stringResource(R.string.diary_action_close))
            }
        }
    }
}

@Composable
private fun MoodHelpContent() {
    Text(
        text = stringResource(R.string.diary_help_mood_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = stringResource(R.string.diary_help_mood_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.diary_help_mood_death_thoughts_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.diary_help_mood_death_thoughts_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
    Text(
        text = stringResource(R.string.diary_help_mood_scale_summary),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun SymptomHelpContent(symptom: SymptomType) {
    Text(
        text = symptomLabel(symptom),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    SymptomCriteriaBlock(
        whenToMark = symptomWhenToMark(symptom),
        whenNotToMark = symptomWhenNotToMark(symptom)
    )
}

@Composable
private fun AllSymptomsHelpContent() {
    Text(
        text = stringResource(R.string.diary_help_symptoms_all_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    SymptomType.entries.forEachIndexed { index, symptom ->
        if (index > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = symptomLabel(symptom),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SymptomCriteriaBlock(
                whenToMark = symptomWhenToMark(symptom),
                whenNotToMark = symptomWhenNotToMark(symptom)
            )
        }
    }
}

@Composable
private fun SymptomCriteriaBlock(whenToMark: String, whenNotToMark: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.diary_help_when_mark),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = whenToMark,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.diary_help_when_not_mark),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = whenNotToMark,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun DeleteEntryConfirmDialog(
    selectedDate: LocalDate,
    today: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.diary_delete_entry_dialog_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.diary_delete_entry_dialog_body,
                    formatDiaryDate(selectedDate, today)
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(text = stringResource(R.string.diary_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.diary_action_cancel))
            }
        }
    )
}
