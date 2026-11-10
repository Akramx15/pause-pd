package dev.pausepd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Cancels a lingering request on unplug so the next PPS charger does not auto-engage. */
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_DISCONNECTED) return
        if (!Prefs.resetOnUnplug(context)) return
        val pending = goAsync()
        Thread {
            try {
                PassThrough.set(false)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
