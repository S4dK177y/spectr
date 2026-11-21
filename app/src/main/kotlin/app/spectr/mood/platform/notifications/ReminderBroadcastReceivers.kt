package app.spectr.mood.platform.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.spectr.mood.platform.widget.MoodWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives exact alarms for the daily evening diary reminder and 14-day scale check-in reminder.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderCoordinator: ReminderCoordinator

    @Inject
    lateinit var moodWidgetUpdater: MoodWidgetUpdater

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_DAILY_REMINDER && action != ACTION_SCALES_REMINDER) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_DAILY_REMINDER -> {
                        reminderCoordinator.onDailyAlarmFired()
                        moodWidgetUpdater.updateAllWidgets()
                    }
                    ACTION_SCALES_REMINDER -> reminderCoordinator.onScalesAlarmFired()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_REMINDER = "app.spectr.mood.alarm.DAILY_REMINDER"
        const val ACTION_SCALES_REMINDER = "app.spectr.mood.alarm.SCALES_REMINDER"
    }
}

/**
 * Handles inline notification action buttons (e.g., "Отложить (+1 ч)").
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderCoordinator: ReminderCoordinator

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SNOOZE_DAILY) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reminderCoordinator.onSnoozeDailyClicked()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_DAILY = "app.spectr.mood.action.SNOOZE_DAILY_REMINDER"
    }
}

/**
 * Restores scheduled exact alarms and refreshes widgets after reboot, app upgrade,
 * clock/timezone changes, or exact-alarm permission changes.
 */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderCoordinator: ReminderCoordinator

    @Inject
    lateinit var moodWidgetUpdater: MoodWidgetUpdater

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ALLOWED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reminderCoordinator.syncAllReminders()
                moodWidgetUpdater.updateAllWidgets()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED
        )
    }
}
