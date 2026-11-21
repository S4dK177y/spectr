package app.spectr.mood.platform

import android.content.Context
import android.content.Intent
import app.spectr.mood.MainActivity

/**
 * High-level deep-link target triggered from a local notification or home-screen widget.
 */
enum class SpectrLaunchTarget(val actionName: String) {
    DAILY_SUMMARY("app.spectr.mood.action.OPEN_DAILY_SUMMARY"),
    NEW_MOMENT("app.spectr.mood.action.OPEN_NEW_MOMENT"),
    SCALES("app.spectr.mood.action.OPEN_SCALES");

    companion object {
        fun fromAction(action: String?): SpectrLaunchTarget? = entries.firstOrNull { it.actionName == action }
    }
}

/**
 * One-shot launch request dispatched from [MainActivity] into the Compose shell.
 */
data class SpectrLaunchRequest(val id: Long, val target: SpectrLaunchTarget)

/**
 * Builds and parses [Intent]s for opening specific screens from notifications and Glance widgets.
 */
object SpectrLaunchContract {
    private const val EXTRA_LAUNCH_TARGET = "app.spectr.mood.extra.LAUNCH_TARGET"

    fun createLaunchIntent(context: Context, target: SpectrLaunchTarget): Intent =
        Intent(context, MainActivity::class.java).apply {
            // Distinct action ensures PendingIntents for different targets never collide.
            action = target.actionName
            putExtra(EXTRA_LAUNCH_TARGET, target.actionName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun parseLaunchTarget(intent: Intent?): SpectrLaunchTarget? {
        if (intent == null) return null
        val fromExtra = intent.getStringExtra(EXTRA_LAUNCH_TARGET)
        return SpectrLaunchTarget.fromAction(fromExtra) ?: SpectrLaunchTarget.fromAction(intent.action)
    }
}
