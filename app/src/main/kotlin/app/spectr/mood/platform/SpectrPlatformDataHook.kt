package app.spectr.mood.platform

import app.spectr.mood.domain.reminder.LocalDataChangeHook
import app.spectr.mood.platform.notifications.ReminderCoordinator
import app.spectr.mood.platform.widget.MoodWidgetUpdater
import dagger.Lazy
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Platform-level reaction hook that keeps alarms, smart-cancelled notifications,
 * and Jetpack Glance widgets synchronized after local Room mutations.
 */
@Singleton
class SpectrPlatformDataHook @Inject constructor(
    private val reminderCoordinator: Lazy<ReminderCoordinator>,
    private val moodWidgetUpdater: MoodWidgetUpdater
) : LocalDataChangeHook {

    override suspend fun onDailyEntryChanged(date: LocalDate?) {
        runSafely { reminderCoordinator.get().onDailyEntryChanged(date) }
        runSafely { moodWidgetUpdater.updateAllWidgets() }
    }

    override suspend fun onMomentChanged(date: LocalDate?) {
        runSafely { moodWidgetUpdater.updateAllWidgets() }
    }

    override suspend fun onScaleAssessmentChanged() {
        runSafely { reminderCoordinator.get().onScaleAssessmentChanged() }
    }

    override suspend fun onBulkDataImported() {
        runSafely { reminderCoordinator.get().syncAllReminders() }
        runSafely { moodWidgetUpdater.updateAllWidgets() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Platform side effects (alarms/widgets) must never break offline-first DB persistence.
        }
    }
}
