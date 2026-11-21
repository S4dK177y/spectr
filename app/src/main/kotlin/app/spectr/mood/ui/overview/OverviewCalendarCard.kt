package app.spectr.mood.ui.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spectr.mood.R
import app.spectr.mood.domain.calendar.CalendarDayCell
import app.spectr.mood.ui.diary.formatSignedMood
import app.spectr.mood.ui.theme.LocalMoodColors
import app.spectr.mood.ui.theme.MoodColorScale
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

@Composable
internal fun OverviewCalendarCard(
    state: OverviewUiState,
    onIntent: (OverviewIntent) -> Unit,
    onOpenDiaryDate: (LocalDate) -> Unit,
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
                    text = stringResource(R.string.overview_calendar_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!state.isCurrentMonthDisplayed) {
                        Surface(
                            onClick = { onIntent(OverviewIntent.JumpToCurrentMonth) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.overview_calendar_today_chip),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = stringResource(
                                R.string.overview_calendar_filled_badge,
                                state.calendarGrid.filledDaysInMonth
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Month and year navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onIntent(OverviewIntent.PreviousMonth) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.overview_calendar_prev_month_cd)
                    )
                }

                Surface(
                    onClick = { onIntent(OverviewIntent.OpenMonthYearPicker) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatYearMonthTitle(state.displayedMonth),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = stringResource(R.string.overview_calendar_pick_month_cd),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onIntent(OverviewIntent.NextMonth) },
                    enabled = state.canGoNextMonth
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.overview_calendar_next_month_cd)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Monday–Sunday header row
            WeekdayHeaderRow()

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar week rows
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                state.calendarGrid.weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        week.forEach { cell ->
                            CalendarDayTile(
                                cell = cell,
                                onClick = { onOpenDiaryDate(cell.date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MoodColorLegend()

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.overview_calendar_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val labels = listOf(
        R.string.weekday_mon_short,
        R.string.weekday_tue_short,
        R.string.weekday_wed_short,
        R.string.weekday_thu_short,
        R.string.weekday_fri_short,
        R.string.weekday_sat_short,
        R.string.weekday_sun_short
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        labels.forEachIndexed { index, labelRes ->
            val isWeekend = index >= 5
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (isWeekend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CalendarDayTile(cell: CalendarDayCell, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val entry = cell.entry
    val (backgroundColor, textColor) = resolveCalendarTileColors(cell)

    val borderModifier = if (cell.isToday && cell.isCurrentMonth) {
        Modifier.border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = shape)
    } else {
        Modifier
    }
    val clickModifier = if (cell.isSelectable) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier
            .aspectRatio(0.95f)
            .clip(shape)
            .background(backgroundColor)
            .then(borderModifier)
            .then(clickModifier)
            .alpha(if (cell.isCurrentMonth) 1f else 0.45f)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isCurrentMonth && cell.isMixedDay) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (cell.isToday || entry != null) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1
            )
            if (cell.isCurrentMonth && entry != null) {
                Text(
                    text = formatSignedMood(entry.mood),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun resolveCalendarTileColors(cell: CalendarDayCell): Pair<Color, Color> {
    val moodColors = LocalMoodColors.current
    val scheme = MaterialTheme.colorScheme
    val entry = cell.entry
    return when {
        !cell.isCurrentMonth -> scheme.surfaceContainerLow to scheme.onSurfaceVariant.copy(alpha = 0.35f)
        entry != null -> moodColors.containerColor(entry.mood) to moodColors.contentColor(entry.mood)
        cell.isFuture -> scheme.surfaceContainer to scheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}

@Composable
private fun MoodColorLegend() {
    val moodColors = LocalMoodColors.current
    val scores = (MoodColorScale.MIN_MOOD..MoodColorScale.MAX_MOOD).toList()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            scores.forEach { score ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(moodColors.containerColor(score))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "−5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "0 · ${stringResource(R.string.overview_calendar_mixed_legend)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "+5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MonthYearPickerDialog(
    displayedMonth: YearMonth,
    currentMonth: YearMonth,
    availableYears: List<Int>,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val minYear = availableYears.firstOrNull() ?: currentMonth.year
    val maxYear = currentMonth.year
    var selectedYear by remember(displayedMonth) { mutableIntStateOf(displayedMonth.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.overview_month_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedYear -= 1 },
                        enabled = selectedYear > minYear
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.overview_month_picker_prev_year_cd)
                        )
                    }
                    Text(
                        text = selectedYear.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { selectedYear += 1 },
                        enabled = selectedYear < maxYear
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.overview_month_picker_next_year_cd)
                        )
                    }
                }

                val months = Month.entries.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    months.forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMonths.forEach { month ->
                                val candidate = YearMonth.of(selectedYear, month)
                                val isDisabled = candidate.isAfter(currentMonth)
                                val isSelected = candidate == displayedMonth
                                Surface(
                                    onClick = { if (!isDisabled) onSelect(candidate) },
                                    enabled = !isDisabled,
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        isDisabled -> MaterialTheme.colorScheme.surfaceContainerLow
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = shortMonthLabel(month),
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.diary_action_cancel))
            }
        }
    )
}
