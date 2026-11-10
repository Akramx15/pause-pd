package dev.pausepd

import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.concurrent.Executors

/** QS tile: tap toggles, subtitle carries the state or the failure reason. */
class PauseTileService : TileService() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var last: PassThrough.State? = null

    override fun onStartListening() {
        io.execute {
            val s = PassThrough.read()
            main.post { apply(s) }
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        if (busy) return
        busy = true
        io.execute {
            val cur = last ?: PassThrough.read()
            val r = PassThrough.set(!cur.requested)
            val after = r.getOrNull() ?: PassThrough.read()
            val msg = (r.exceptionOrNull() as? PassThrough.Failure)?.let { errText(it.err) }
            main.post {
                busy = false
                apply(after, msg)
            }
        }
    }

    private fun apply(s: PassThrough.State, error: String? = null) {
        last = s
        val t = qsTile ?: return
        t.label = getString(R.string.tile_label)
        t.icon = Icon.createWithResource(this, R.drawable.ic_pause_pd)
        t.subtitle = when {
            error != null -> error
            s.rootDenied -> getString(R.string.tile_root)
            s.nodeMissing -> getString(R.string.sub_error)
            s.engaged -> getString(R.string.tile_active)
            s.requested -> getString(R.string.tile_requested)
            s.chargerPresent && !s.hasPps -> getString(R.string.tile_no_pps)
            s.lowBattery -> getString(R.string.tile_low)
            else -> getString(R.string.tile_off)
        }
        t.state = when {
            s.rootDenied -> Tile.STATE_UNAVAILABLE
            s.nodeMissing -> Tile.STATE_UNAVAILABLE
            s.engaged -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        t.updateTile()
    }
}
