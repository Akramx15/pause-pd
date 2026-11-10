package dev.pausepd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors

/** Optional game watcher: arms pass-through while a listed game is foreground and charging. */
class GameWatchService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var watch: GameWatch
    private lateinit var tick: Runnable
    private var stopped = false
    private var powerRegistered = false

    private val powerOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Prefs.resetOnUnplug(c)) {
                Prefs.setArmed(c, false)
                io.execute { PassThrough.set(false) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        watch = GameWatch(this)
        tick = Runnable {
            if (stopped) return@Runnable
            io.execute {
                try {
                    check()
                } catch (_: Throwable) {
                }
                main.postDelayed(tick, POLL_MS)
            }
        }
        createChannel()
        val f = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerOff, f, RECEIVER_NOT_EXPORTED)
        else registerReceiver(powerOff, f)
        powerRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, notification())
        if (!Prefs.gameWatch(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        main.removeCallbacks(tick)
        main.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopped = true
        if (powerRegistered) runCatching { unregisterReceiver(powerOff) }
        main.removeCallbacks(tick)
        if (Prefs.armed(this)) {
            Prefs.setArmed(this, false)
            io.execute { PassThrough.set(false) }
        }
        io.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun check() {
        if (!Prefs.gameWatch(this)) {
            main.post { stopSelf() }
            return
        }
        val st = PassThrough.read()
        if (!st.chargerPresent) {
            disarm()
            return
        }
        val fg = watch.refresh()
        if (fg != null && Prefs.games(this).contains(fg)) arm(st) else disarm()
    }

    private fun arm(st: PassThrough.State) {
        if (stopped) return
        if (st.requested) {
            if (!Prefs.armed(this)) Prefs.setArmed(this, true)
            return // already on, leave it to the user
        }
        if (PassThrough.set(true).isSuccess) Prefs.setArmed(this, true)
    }

    private fun disarm() {
        if (!Prefs.armed(this)) return
        Prefs.setArmed(this, false)
        io.execute { PassThrough.set(false) }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW
        )
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_pause_pd)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private companion object {
        const val CHANNEL = "game_watch"
        const val NOTIF_ID = 42
        const val POLL_MS = 15_000L
    }
}
