package dev.pausepd

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {

    private enum class Status { OFF, REQUESTED, ACTIVE, NO_PPS, LOW_BATTERY, TOO_FULL, ROOT, ERROR }

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var progress: ProgressBar
    private lateinit var errText: TextView
    private lateinit var toggle: Button
    private lateinit var checkPps: TextView
    private lateinit var checkCharging: TextView
    private lateinit var checkCapacity: TextView
    private lateinit var valBattery: TextView
    private lateinit var valCurrent: TextView
    private lateinit var valCharger: TextView
    private lateinit var valDc: TextView

    private var state: PassThrough.State? = null
    private var polling = false
    private var busy = false

    private val hideError = Runnable { errText.visibility = View.GONE }

    private val refresh = object : Runnable {
        override fun run() {
            poll()
            ui.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTitle = findViewById(R.id.statusTitle)
        statusSub = findViewById(R.id.statusSub)
        progress = findViewById(R.id.progress)
        errText = findViewById(R.id.errText)
        toggle = findViewById(R.id.toggle)
        checkPps = findViewById(R.id.checkPps)
        checkCharging = findViewById(R.id.checkCharging)
        checkCapacity = findViewById(R.id.checkCapacity)
        valBattery = findViewById(R.id.valBattery)
        valCurrent = findViewById(R.id.valCurrent)
        valCharger = findViewById(R.id.valCharger)
        valDc = findViewById(R.id.valDc)

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        toggle.setOnClickListener { onToggle() }
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresh)
    }

    override fun onPause() {
        ui.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun poll() {
        if (polling) return
        polling = true
        io.execute {
            val s = PassThrough.read()
            ui.post {
                polling = false
                if (!isDestroyed) render(s)
            }
        }
    }

    private fun onToggle() {
        val s = state ?: return
        if (busy) return
        busy = true
        showError(null)
        render(s)
        val want = !s.requested
        io.execute {
            val r = PassThrough.set(want)
            val after = r.getOrNull() ?: PassThrough.read()
            val msg = (r.exceptionOrNull() as? PassThrough.Failure)?.let { errText(it.err) }
            ui.post {
                busy = false
                if (isDestroyed) return@post
                render(after)
                showError(msg)
            }
        }
    }

    private fun render(s: PassThrough.State) {
        state = s
        val status = when {
            s.rootDenied -> Status.ROOT
            s.nodeMissing -> Status.ERROR
            s.engaged -> Status.ACTIVE
            s.requested -> Status.REQUESTED
            s.chargerPresent && !s.hasPps -> Status.NO_PPS
            s.lowBattery -> Status.LOW_BATTERY
            s.chargerPresent && s.tooFull -> Status.TOO_FULL
            else -> Status.OFF
        }

        statusTitle.setText(titleOf(status))
        statusTitle.setTextColor(getColor(colorOf(status)))
        statusSub.setText(subOf(status))

        check(checkPps, s.hasPps, getString(R.string.chk_pps))
        check(checkCharging, s.charging, getString(R.string.chk_charging))
        check(checkCapacity, s.capacity?.let { it > 20 } == true, getString(R.string.chk_capacity))

        valBattery.text = s.capacity?.let { getString(R.string.fmt_percent, it) }
            ?: getString(R.string.value_na)
        valCurrent.text = s.currentNow?.let { getString(R.string.fmt_ma, abs(it)) }
            ?: getString(R.string.value_na)
        valCharger.text = when {
            !s.chargerPresent -> getString(R.string.charger_none)
            s.hasPps -> getString(R.string.charger_pps)
            else -> getString(R.string.charger_other, s.online ?: 0)
        }
        valDc.text = s.dcStatus ?: getString(R.string.value_na)

        toggle.isEnabled = !busy && !s.nodeMissing
        toggle.setText(
            when {
                s.nodeMissing -> R.string.btn_unsupported
                busy -> R.string.btn_working
                s.requested -> R.string.btn_off
                else -> R.string.btn_on
            }
        )
        progress.visibility = if (s.requested && !s.engaged) View.VISIBLE else View.GONE
    }

    private fun check(tv: TextView, ok: Boolean, label: String) {
        tv.text = getString(if (ok) R.string.check_on else R.string.check_off, label)
        tv.setTextColor(getColor(if (ok) R.color.accent else R.color.muted))
    }

    private fun showError(msg: String?) {
        ui.removeCallbacks(hideError)
        if (msg == null) {
            errText.visibility = View.GONE
        } else {
            errText.text = msg
            errText.visibility = View.VISIBLE
            ui.postDelayed(hideError, 30_000)
        }
    }

    private fun titleOf(s: Status) = when (s) {
        Status.OFF -> R.string.status_off
        Status.REQUESTED -> R.string.status_requested
        Status.ACTIVE -> R.string.status_active
        Status.NO_PPS -> R.string.status_no_pps
        Status.LOW_BATTERY -> R.string.status_low_battery
        Status.TOO_FULL -> R.string.status_too_full
        Status.ROOT -> R.string.status_root
        Status.ERROR -> R.string.status_error
    }

    private fun subOf(s: Status) = when (s) {
        Status.OFF -> R.string.sub_off
        Status.REQUESTED -> R.string.sub_requested
        Status.ACTIVE -> R.string.sub_active
        Status.NO_PPS -> R.string.sub_no_pps
        Status.LOW_BATTERY -> R.string.sub_low_battery
        Status.TOO_FULL -> R.string.sub_too_full
        Status.ROOT -> R.string.sub_root
        Status.ERROR -> R.string.sub_error
    }

    private fun colorOf(s: Status) = when (s) {
        Status.ACTIVE -> R.color.accent
        Status.REQUESTED, Status.NO_PPS, Status.LOW_BATTERY, Status.TOO_FULL -> R.color.warn
        Status.ROOT, Status.ERROR -> R.color.danger
        Status.OFF -> R.color.muted
    }

    private companion object {
        const val POLL_MS = 2_000L
    }
}
