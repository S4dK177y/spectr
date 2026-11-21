package app.spectr.mood.platform.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.spectr.mood.R
import app.spectr.mood.domain.repository.DiaryRepository
import app.spectr.mood.domain.widget.MoodWidgetDayItem
import app.spectr.mood.domain.widget.MoodWidgetSnapshot
import app.spectr.mood.domain.widget.MoodWidgetSnapshotBuilder
import app.spectr.mood.platform.SpectrLaunchContract
import app.spectr.mood.platform.SpectrLaunchTarget
import app.spectr.mood.ui.theme.MoodColorScale
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MoodWidgetEntryPoint {
    fun diaryRepository(): DiaryRepository
    fun clock(): Clock
}

/**
 * Jetpack Glance home-screen widget displaying today's status, a 7-day color-coded mood strip,
 * and quick-entry buttons for "+ Момент" and "Итог дня".
 */
class MoodGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MoodWidgetEntryPoint::class.java
        )
        val diaryRepository = entryPoint.diaryRepository()
        val today = LocalDate.now(entryPoint.clock())
        val startDate = today.minusDays((MoodWidgetSnapshotBuilder.WINDOW_DAYS - 1).toLong())

        val entries = diaryRepository.observeEntriesInRange(startDate, today).first()
        val todayMoments = diaryRepository.observeMomentsByDate(today).first()
        val snapshot = MoodWidgetSnapshotBuilder.build(
            today = today,
            entries = entries,
            moments = todayMoments
        )

        provideContent {
            GlanceTheme {
                MoodWidgetContent(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun MoodWidgetContent(snapshot: MoodWidgetSnapshot) {
    val context = LocalContext.current
    val openDailyAction = actionStartActivity(
        SpectrLaunchContract.createLaunchIntent(context, SpectrLaunchTarget.DAILY_SUMMARY)
    )
    val openMomentAction = actionStartActivity(
        SpectrLaunchContract.createLaunchIntent(context, SpectrLaunchTarget.NEW_MOMENT)
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openDailyAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(R.string.app_name),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = formatStatusLine(context, snapshot),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))
            TodayMoodBadge(mood = snapshot.todayMood)
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openDailyAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            snapshot.last7Days.forEachIndexed { index, item ->
                MoodStripDayCell(
                    context = context,
                    item = item,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (index < snapshot.last7Days.lastIndex) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(36.dp)
                    .cornerRadius(12.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(openMomentAction),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_action_add_moment),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(36.dp)
                    .cornerRadius(12.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(openDailyAction),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = context.getString(R.string.widget_action_daily_summary),
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TodayMoodBadge(mood: Int?) {
    val backgroundColor = if (mood != null) {
        moodContainerColorProvider(mood)
    } else {
        GlanceTheme.colors.surfaceVariant
    }
    val textColor = if (mood != null) {
        moodContentColorProvider(mood)
    } else {
        GlanceTheme.colors.onSurfaceVariant
    }

    Box(
        modifier = GlanceModifier
            .cornerRadius(10.dp)
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mood?.let(::formatSignedMood) ?: "—",
            style = TextStyle(
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun MoodStripDayCell(context: Context, item: MoodWidgetDayItem, modifier: GlanceModifier = GlanceModifier) {
    val cellBg = item.mood?.let(::moodContainerColorProvider) ?: GlanceTheme.colors.surfaceVariant
    val cellFg = item.mood?.let(::moodContentColorProvider) ?: GlanceTheme.colors.onSurfaceVariant
    val dayLabelColor = if (item.isToday) {
        GlanceTheme.colors.primary
    } else {
        GlanceTheme.colors.onSurfaceVariant
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weekdayShortLabel(context, item.dayOfWeek),
            style = TextStyle(
                color = dayLabelColor,
                fontSize = 10.sp,
                fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(26.dp)
                .cornerRadius(8.dp)
                .background(cellBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.mood?.let(::formatSignedMood) ?: "·",
                style = TextStyle(
                    color = cellFg,
                    fontSize = 11.sp,
                    fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

private fun moodContainerColorProvider(mood: Int) = ColorProvider(
    day = MoodColorScale.Light.containerColor(mood),
    night = MoodColorScale.Dark.containerColor(mood)
)

private fun moodContentColorProvider(mood: Int) = ColorProvider(
    day = MoodColorScale.Light.contentColor(mood),
    night = MoodColorScale.Dark.contentColor(mood)
)

private fun formatStatusLine(context: Context, snapshot: MoodWidgetSnapshot): String = when {
    snapshot.isTodayFilled && snapshot.todayMood != null && snapshot.isTodayMixed ->
        context.getString(R.string.widget_status_filled_mixed, formatSignedMood(snapshot.todayMood))
    snapshot.isTodayFilled && snapshot.todayMood != null && snapshot.todayMomentsCount > 0 ->
        context.getString(
            R.string.widget_status_filled_with_moments,
            formatSignedMood(snapshot.todayMood),
            snapshot.todayMomentsCount
        )
    snapshot.isTodayFilled && snapshot.todayMood != null ->
        context.getString(R.string.widget_status_filled, formatSignedMood(snapshot.todayMood))
    snapshot.todayMomentsCount > 0 ->
        context.getString(R.string.widget_status_moments_only, snapshot.todayMomentsCount)
    else -> context.getString(R.string.widget_status_empty)
}

private fun formatSignedMood(mood: Int): String = when {
    mood > 0 -> "+$mood"
    mood < 0 -> "−${-mood}"
    else -> "0"
}

private fun weekdayShortLabel(context: Context, dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> context.getString(R.string.weekday_mon_short)
    DayOfWeek.TUESDAY -> context.getString(R.string.weekday_tue_short)
    DayOfWeek.WEDNESDAY -> context.getString(R.string.weekday_wed_short)
    DayOfWeek.THURSDAY -> context.getString(R.string.weekday_thu_short)
    DayOfWeek.FRIDAY -> context.getString(R.string.weekday_fri_short)
    DayOfWeek.SATURDAY -> context.getString(R.string.weekday_sat_short)
    DayOfWeek.SUNDAY -> context.getString(R.string.weekday_sun_short)
}
