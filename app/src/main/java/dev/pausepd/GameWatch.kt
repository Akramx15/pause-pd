package dev.pausepd

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Foreground-package tracking for the optional game watcher.
 *
 * UsageStats only reports events, so a long game session sits outside any short
 * lookback window; we keep the last query time and consume only new events.
 */
class GameWatch(private val context: Context) {

    private var lastQuery = System.currentTimeMillis() - 60_000
    var foreground: String? = null
        private set

    fun refresh(): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return foreground
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQuery, now) ?: return foreground
        lastQuery = now
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> foreground = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> if (e.packageName == foreground) foreground = null
            }
        }
        return foreground
    }

    companion object {
        fun hasAccess(c: Context): Boolean {
            val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
            return ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }
}
