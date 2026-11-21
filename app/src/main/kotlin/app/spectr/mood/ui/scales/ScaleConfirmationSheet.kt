package app.spectr.mood.ui.scales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.model.ScaleAssessment
import app.spectr.mood.ui.overview.formatShortChartDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScaleConfirmationSheet(
    editor: ScaleEditorState,
    today: LocalDate,
    onIntent: (ScalesIntent) -> Unit,
    onRequestDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scaleCode = scaleCodeLabel(editor.scaleType)
    val severityLabel = scaleSeverityLabel(editor.scaleType, editor.totalScore)

    ModalBottomSheet(
        onDismissRequest = { onIntent(ScalesIntent.DismissEditor) },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editor.isEditingExisting) {
                    stringResource(R.string.scales_sheet_edit_title, scaleCode)
                } else {
                    stringResource(R.string.scales_sheet_new_title, scaleCode)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Date selector & reset-to-auto row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onIntent(ScalesIntent.OpenEditorDatePicker) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.scales_sheet_date_label)}: " +
                            formatShortChartDate(editor.date, today.year)
                    )
                }

                if (editor.differsFromAutoBaseline) {
                    TextButton(onClick = { onIntent(ScalesIntent.ResetEditorToAuto) }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.scales_sheet_reset_auto))
                    }
                }
            }

            // Live total score preview banner
            val elevated = isElevatedScaleScore(editor.scaleType, editor.totalScore)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (elevated) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        R.string.scales_sheet_total_label,
                        editor.totalScore,
                        editor.maxTotalScore,
                        severityLabel
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (elevated) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // Answer legend
            Text(
                text = scaleAnswerLegend(editor.scaleType),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Question cards
            editor.answers.forEachIndexed { idx, selectedScore ->
                ScaleQuestionItemCard(
                    editor = editor,
                    itemIndex = idx,
                    selectedScore = selectedScore,
                    onSelectScore = { score -> onIntent(ScalesIntent.UpdateEditorAnswer(idx, score)) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { onIntent(ScalesIntent.SaveAssessment) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(
                        if (editor.isEditingExisting) {
                            R.string.scales_sheet_update_button
                        } else {
                            R.string.scales_sheet_save_button
                        }
                    )
                )
            }

            if (onRequestDelete != null) {
                TextButton(
                    onClick = onRequestDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.scales_sheet_delete_button))
                }
            }
        }
    }

    if (editor.isDatePickerOpen) {
        ScaleDatePickerDialog(
            selectedDate = editor.date,
            today = today,
            onSelectDate = { onIntent(ScalesIntent.UpdateEditorDate(it)) },
            onDismiss = { onIntent(ScalesIntent.DismissEditorDatePicker) }
        )
    }
}

@Composable
private fun ScaleQuestionItemCard(
    editor: ScaleEditorState,
    itemIndex: Int,
    selectedScore: Int,
    onSelectScore: (Int) -> Unit
) {
    val autoScore = editor.autoBaselineScores?.getOrNull(itemIndex)
    val autoDays = editor.autoBaselineDayCounts?.getOrNull(itemIndex)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${itemIndex + 1}. ${scaleQuestionText(editor.scaleType, itemIndex)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (autoScore != null && autoDays != null) {
                Text(
                    text = stringResource(
                        R.string.scales_sheet_auto_hint,
                        autoScore,
                        autoDays,
                        editor.autoWindowDays
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (scoreOption in 0..editor.maxItemScore) {
                    val isSelected = scoreOption == selectedScore
                    Surface(
                        onClick = { onSelectScore(scoreOption) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (!isSelected && scoreOption == autoScore) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = scoreOption.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleDatePickerDialog(
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxMillis
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onSelectDate(picked)
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
        DatePicker(state = pickerState)
    }
}

@Composable
internal fun DeleteAssessmentConfirmDialog(
    assessment: ScaleAssessment,
    referenceYear: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scaleCode = scaleCodeLabel(assessment.scaleType)
    val maxScore = scaleMaxTotalScore(assessment.scaleType)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.scales_delete_dialog_title, scaleCode)) },
        text = {
            Text(
                text = stringResource(
                    R.string.scales_delete_dialog_body,
                    formatShortChartDate(assessment.date, referenceYear),
                    assessment.totalScore,
                    maxScore
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
