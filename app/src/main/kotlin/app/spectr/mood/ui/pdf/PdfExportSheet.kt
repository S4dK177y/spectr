package app.spectr.mood.ui.pdf

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import app.spectr.mood.R
import app.spectr.mood.domain.report.DoctorReportSnapshot
import app.spectr.mood.domain.report.ReportPeriodPreset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PDF_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru"))

/**
 * UI state slice for the PDF export bottom sheet.
 */
data class PdfExportSheetState(
    val isOpen: Boolean = false,
    val periodPreset: ReportPeriodPreset = ReportPeriodPreset.DAYS_30,
    val customStartDate: LocalDate = LocalDate.now().minusDays(29),
    val customEndDate: LocalDate = LocalDate.now(),
    val includeDailyTable: Boolean = false,
    val includePersonalNotesAndMoments: Boolean = false,
    val isGenerating: Boolean = false,
    val previewSnapshot: DoctorReportSnapshot? = null
) {
    val isCustomRangeValid: Boolean
        get() = !customEndDate.isBefore(customStartDate)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfExportBottomSheet(
    state: PdfExportSheetState,
    onSelectPreset: (ReportPeriodPreset) -> Unit,
    onChangeCustomStart: (LocalDate) -> Unit,
    onChangeCustomEnd: (LocalDate) -> Unit,
    onToggleDailyTable: (Boolean) -> Unit,
    onToggleNotes: (Boolean) -> Unit,
    onShareClick: () -> Unit,
    onOpenClick: () -> Unit,
    onSaveToFileClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.pdf_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Period selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.pdf_sheet_period_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReportPeriodPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = state.periodPreset == preset,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(text = pdfPresetLabel(preset)) }
                        )
                    }
                }
            }

            // Custom date range pickers
            if (state.periodPreset == ReportPeriodPreset.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DateSelectTile(
                        label = stringResource(R.string.pdf_custom_range_start),
                        date = state.customStartDate,
                        onClick = { pickingStartDate = true },
                        modifier = Modifier.weight(1f)
                    )
                    DateSelectTile(
                        label = stringResource(R.string.pdf_custom_range_end),
                        date = state.customEndDate,
                        onClick = { pickingEndDate = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!state.isCustomRangeValid) {
                    Text(
                        text = stringResource(R.string.pdf_custom_range_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Preview summary pill
            val snap = state.previewSnapshot
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (snap == null || snap.isEmpty) {
                        stringResource(R.string.pdf_preview_empty)
                    } else {
                        stringResource(
                            R.string.pdf_preview_summary,
                            snap.summary.filledDays,
                            snap.totalNotesInPeriod,
                            snap.totalMomentsInPeriod
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Option 1: Daily Table
            PdfOptionToggleRow(
                title = stringResource(R.string.pdf_option_daily_table_title),
                subtitle = stringResource(R.string.pdf_option_daily_table_subtitle),
                checked = state.includeDailyTable,
                onCheckedChange = onToggleDailyTable
            )

            // Option 2: Personal Notes & Moments
            PdfOptionToggleRow(
                title = stringResource(R.string.pdf_option_notes_title),
                subtitle = stringResource(R.string.pdf_option_notes_subtitle),
                checked = state.includePersonalNotesAndMoments,
                onCheckedChange = onToggleNotes
            )

            // Privacy hint
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                )
                Text(
                    text = stringResource(R.string.pdf_privacy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val actionsEnabled = !state.isGenerating &&
                (state.periodPreset != ReportPeriodPreset.CUSTOM || state.isCustomRangeValid)

            Button(
                onClick = onShareClick,
                enabled = actionsEnabled,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.pdf_generating_label))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.pdf_action_share))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenClick,
                    enabled = actionsEnabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.pdf_action_open))
                }

                OutlinedButton(
                    onClick = onSaveToFileClick,
                    enabled = actionsEnabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.pdf_action_save))
                }
            }
        }
    }

    if (pickingStartDate) {
        PdfDatePickerDialog(
            initialDate = state.customStartDate,
            onConfirm = { picked ->
                onChangeCustomStart(picked)
                pickingStartDate = false
            },
            onDismiss = { pickingStartDate = false }
        )
    }

    if (pickingEndDate) {
        PdfDatePickerDialog(
            initialDate = state.customEndDate,
            onConfirm = { picked ->
                onChangeCustomEnd(picked)
                pickingEndDate = false
            },
            onDismiss = { pickingEndDate = false }
        )
    }
}

@Composable
private fun DateSelectTile(label: String, date: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = date.format(PDF_DATE_FMT),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PdfOptionToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfDatePickerDialog(initialDate: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = pickerState.selectedDateMillis?.let { millis ->
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    } ?: initialDate
                    onConfirm(selected)
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
private fun pdfPresetLabel(preset: ReportPeriodPreset): String = when (preset) {
    ReportPeriodPreset.DAYS_14 -> stringResource(R.string.overview_period_14d)
    ReportPeriodPreset.DAYS_30 -> stringResource(R.string.overview_period_30d)
    ReportPeriodPreset.DAYS_90 -> stringResource(R.string.overview_period_90d)
    ReportPeriodPreset.ALL_TIME -> stringResource(R.string.overview_period_all)
    ReportPeriodPreset.CUSTOM -> stringResource(R.string.pdf_period_custom)
}
