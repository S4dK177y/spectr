package app.spectr.mood.platform.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.spectr.mood.R
import app.spectr.mood.platform.SpectrLaunchContract
import app.spectr.mood.platform.SpectrLaunchTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction for posting and cancelling reminder notifications.
 */
interface NotificationPublisher {
    fun ensureChannelsCreated()
    fun showDailyReminder()
    fun cancelDailyReminder()
    fun showScalesReminder()
    fun cancelScalesReminder()
}

/**
 * Creates lockscreen-safe notification channels and posts neutral reminder notifications.
 */
@Singleton
class SpectrNotificationPublisher @Inject constructor(@param:ApplicationContext private val context: Context) :
    NotificationPublisher {

    override fun ensureChannelsCreated() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val dailyChannel = NotificationChannel(
            CHANNEL_DAILY_REMINDER,
            context.getString(R.string.notification_channel_daily_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_daily_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val scalesChannel = NotificationChannel(
            CHANNEL_SCALES_REMINDER,
            context.getString(R.string.notification_channel_scales_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_scales_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        manager.createNotificationChannels(listOf(dailyChannel, scalesChannel))
    }

    override fun showDailyReminder() {
        if (!canPostNotifications()) return
        ensureChannelsCreated()

        val openDailyIntent = buildActivityPendingIntent(
            target = SpectrLaunchTarget.DAILY_SUMMARY,
            requestCode = REQUEST_CODE_OPEN_DAILY
        )
        val snoozeIntent = buildSnoozePendingIntent()

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDER)
            .setSmallIcon(R.drawable.ic_notification_spectr)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_daily_public_body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDER)
            .setSmallIcon(R.drawable.ic_notification_spectr)
            .setContentTitle(context.getString(R.string.notification_daily_title))
            .setContentText(context.getString(R.string.notification_daily_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_daily_body))
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openDailyIntent)
            .addAction(
                0,
                context.getString(R.string.notification_daily_action_fill),
                openDailyIntent
            )
            .addAction(
                0,
                context.getString(R.string.notification_daily_action_snooze),
                snoozeIntent
            )
            .build()

        postSafely(NOTIFICATION_ID_DAILY, notification)
    }

    override fun cancelDailyReminder() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_DAILY)
    }

    override fun showScalesReminder() {
        if (!canPostNotifications()) return
        ensureChannelsCreated()

        val openScalesIntent = buildActivityPendingIntent(
            target = SpectrLaunchTarget.SCALES,
            requestCode = REQUEST_CODE_OPEN_SCALES
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_SCALES_REMINDER)
            .setSmallIcon(R.drawable.ic_notification_spectr)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_scales_public_body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_SCALES_REMINDER)
            .setSmallIcon(R.drawable.ic_notification_spectr)
            .setContentTitle(context.getString(R.string.notification_scales_title))
            .setContentText(context.getString(R.string.notification_scales_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_scales_body))
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openScalesIntent)
            .addAction(
                0,
                context.getString(R.string.notification_scales_action_open),
                openScalesIntent
            )
            .build()

        postSafely(NOTIFICATION_ID_SCALES, notification)
    }

    override fun cancelScalesReminder() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SCALES)
    }

    private fun canPostNotifications(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun postSafely(notificationId: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission was revoked concurrently; fail silently without crashing the receiver.
        }
    }

    private fun buildActivityPendingIntent(target: SpectrLaunchTarget, requestCode: Int): PendingIntent {
        val intent = SpectrLaunchContract.createLaunchIntent(context, target)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSnoozePendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_DAILY
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SNOOZE_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_DAILY_REMINDER = "spectr_daily_reminder"
        const val CHANNEL_SCALES_REMINDER = "spectr_scales_reminder"
        const val NOTIFICATION_ID_DAILY = 4001
        const val NOTIFICATION_ID_SCALES = 4002

        private const val REQUEST_CODE_OPEN_DAILY = 4201
        private const val REQUEST_CODE_SNOOZE_DAILY = 4202
        private const val REQUEST_CODE_OPEN_SCALES = 4203
    }
}
