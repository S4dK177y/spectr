package app.spectr.mood.platform.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Platform abstraction over Android [AlarmManager] for deterministic unit testing.
 */
interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun scheduleDailyAlarm(triggerAtEpochMs: Long)
    fun cancelDailyAlarm()
    fun scheduleScalesAlarm(triggerAtEpochMs: Long)
    fun cancelScalesAlarm()
}

/**
 * Schedules Doze-safe exact alarms (with safe inexact fallback if exact alarm permission is revoked).
 */
@Singleton
class SpectrAlarmScheduler @Inject constructor(@param:ApplicationContext private val context: Context) :
    AlarmScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }

    override fun scheduleDailyAlarm(triggerAtEpochMs: Long) {
        val pendingIntent = buildAlarmPendingIntent(
            action = ReminderAlarmReceiver.ACTION_DAILY_REMINDER,
            requestCode = REQUEST_CODE_DAILY_ALARM,
            create = true
        ) ?: return
        scheduleRtcWakeup(triggerAtEpochMs, pendingIntent)
    }

    override fun cancelDailyAlarm() {
        val pendingIntent = buildAlarmPendingIntent(
            action = ReminderAlarmReceiver.ACTION_DAILY_REMINDER,
            requestCode = REQUEST_CODE_DAILY_ALARM,
            create = false
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    override fun scheduleScalesAlarm(triggerAtEpochMs: Long) {
        val pendingIntent = buildAlarmPendingIntent(
            action = ReminderAlarmReceiver.ACTION_SCALES_REMINDER,
            requestCode = REQUEST_CODE_SCALES_ALARM,
            create = true
        ) ?: return
        scheduleRtcWakeup(triggerAtEpochMs, pendingIntent)
    }

    override fun cancelScalesAlarm() {
        val pendingIntent = buildAlarmPendingIntent(
            action = ReminderAlarmReceiver.ACTION_SCALES_REMINDER,
            requestCode = REQUEST_CODE_SCALES_ALARM,
            create = false
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleRtcWakeup(triggerAtEpochMs: Long, pendingIntent: PendingIntent) {
        val manager = alarmManager
        if (canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Fall back gracefully if exact-alarm permission was revoked concurrently.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pendingIntent)
    }

    private fun buildAlarmPendingIntent(action: String, requestCode: Int, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = action
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        private const val REQUEST_CODE_DAILY_ALARM = 4101
        private const val REQUEST_CODE_SCALES_ALARM = 4102
    }
}
