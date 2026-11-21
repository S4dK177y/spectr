package app.spectr.mood.platform.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidget broadcast receiver hosting [MoodGlanceWidget].
 */
class MoodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoodGlanceWidget()
}
