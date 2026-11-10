package dev.pausepd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Defensive reset: a `1` surviving a reboot would auto-engage on the next PPS plug. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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
