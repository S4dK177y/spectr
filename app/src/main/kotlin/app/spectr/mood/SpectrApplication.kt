package app.spectr.mood

import android.app.Application
import app.spectr.mood.platform.notifications.NotificationPublisher
import app.spectr.mood.platform.notifications.ReminderCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point initializing Hilt DI, notification channels, and reminder alarms.
 */
@HiltAndroidApp
class SpectrApplication : Application() {

    @Inject
    lateinit var notificationPublisher: NotificationPublisher

    @Inject
    lateinit var reminderCoordinator: ReminderCoordinator

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationPublisher.ensureChannelsCreated()
        appScope.launch {
            runCatching { reminderCoordinator.syncAllReminders() }
        }
    }
}
