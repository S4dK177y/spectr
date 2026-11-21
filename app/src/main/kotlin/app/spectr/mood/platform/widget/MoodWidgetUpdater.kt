package app.spectr.mood.platform.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes fresh diary and moment snapshots to all active Spectr home-screen widgets.
 */
@Singleton
class MoodWidgetUpdater @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun updateAllWidgets() {
        MoodGlanceWidget().updateAll(context)
    }
}
