package dev.pausepd

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Root bridge for Samsung's kernel pass-through switch ("Pause USB PD").
 *
 * Reads go through `su` first: the node is world-readable by mode, but SELinux
 * gives `untrusted_app` no access to `vendor_sysfs_battery`, so a plain `sh`
 * read returns nothing on the target ROM. The `sh` fallback only helps on
 * permissive builds and never masks a root denial.
 */
object PassThrough {

    private const val DIR = "/sys/class/power_supply/battery"
    private const val NODE = "$DIR/pass_through"
    private const val READ_TIMEOUT_MS = 8_000L
    private const val WRITE_TIMEOUT_MS = 30_000L
    private const val PPS_A = 38
    private const val PPS_B = 41

    enum class Err { SU_DENIED, NODE_MISSING, WRITE_FAILED, NO_CHARGER, NO_PPS, NOT_CHARGING, LOW_BATTERY, TOO_FULL, TIMEOUT, UNKNOWN }

    class Failure(val err: Err, detail: String? = null) :
        Exception(err.name + (detail?.let { ": $it" } ?: ""))

    data class State(
        val passThrough: Int?,
        val dcStatus: String?,
        val status: String?,
        val currentNow: Long?,
        val capacity: Int?,
        val online: Int?,
        val rootDenied: Boolean = false,
    ) {
        val nodeMissing: Boolean get() = passThrough == null && !rootDenied
        val engaged: Boolean get() = dcStatus == "BYPASS_MODE"
        val requested: Boolean get() = passThrough != null && passThrough != 0
        val hasPps: Boolean get() = online == PPS_A || online == PPS_B
        val chargerPresent: Boolean get() = (online ?: 0) > 1
        val charging: Boolean get() = status.equals("Charging", ignoreCase = true)
        val lowBattery: Boolean get() = capacity != null && capacity <= 20
        val tooFull: Boolean get() = capacity != null && capacity > 90
    }

    private val READ_SCRIPT = """
        for f in pass_through direct_charging_chg_status status current_now capacity online
        do
          printf '%s=' "${'$'}f"
          cat "$DIR/${'$'}f" 2>/dev/null
          printf '\n'
        done
    """.trimIndent()

    /** Root read of the telemetry nodes. Never throws; unreadable fields come back null. */
    fun read(): State {
        val (code, out) = exec(listOf("su", "-c", READ_SCRIPT), READ_TIMEOUT_MS)
        if (code == 0 && out.isNotBlank()) return parse(out)
        val (_, shOut) = exec(listOf("sh", "-c", READ_SCRIPT), READ_TIMEOUT_MS)
        val st = parse(shOut)
        return if (st.passThrough == null && code != 0) st.copy(rootDenied = true) else st
    }

    /**
     * Writes the request. Refuses to arm when the kernel would ignore it (no
     * charger, no PPS, not charging, battery at or below 20 %) so the app cannot
     * leave a stale `1` that auto-engages on the next PPS plug. Cancelling (0)
     * is always allowed.
     */
    fun set(on: Boolean): Result<State> {
        if (on) {
            val now = read()
            when {
                now.rootDenied -> return fail(Err.SU_DENIED)
                now.nodeMissing -> return fail(Err.NODE_MISSING)
                !now.charging -> return fail(Err.NOT_CHARGING)
                !now.hasPps -> return fail(Err.NO_PPS)
                now.lowBattery -> return fail(Err.LOW_BATTERY)
                now.tooFull -> return fail(Err.TOO_FULL)
            }
        }
        val value = if (on) 1 else 0
        val script = """
            [ -e $NODE ] || { echo node-missing; exit 3; }
            printf '%s\n' $value > $NODE 2>/dev/null || { echo write-failed; exit 4; }
            $READ_SCRIPT
        """.trimIndent()
        val (code, out) = exec(listOf("su", "-c", script), WRITE_TIMEOUT_MS)
        mapExit(code, out)?.let { return fail(it, out.trim()) }
        return Result.success(parse(out))
    }

    private fun fail(e: Err, detail: String? = null) =
        Result.failure<State>(Failure(e, detail?.ifEmpty { null }))

    private fun parse(out: String): State {
        val m = HashMap<String, String>()
        out.lineSequence().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) m[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        fun s(k: String): String? = m[k]?.takeIf { it.isNotEmpty() }
        fun i(k: String): Int? = s(k)?.toIntOrNull()
        return State(
            passThrough = i("pass_through"),
            dcStatus = s("direct_charging_chg_status"),
            status = s("status"),
            currentNow = s("current_now")?.toLongOrNull(),
            capacity = i("capacity"),
            online = i("online"),
        )
    }

    private fun mapExit(code: Int, out: String): Err? = when {
        code == 0 -> null
        code == 3 -> Err.NODE_MISSING
        code == 4 -> Err.WRITE_FAILED
        code == -1 -> Err.TIMEOUT
        code == -2 -> Err.SU_DENIED
        out.contains("denied", true) || out.isBlank() -> Err.SU_DENIED
        else -> Err.UNKNOWN
    }

    /** Runs [cmd] with a hard timeout; returns (exit code, merged stdout+stderr). */
    private fun exec(cmd: List<String>, timeoutMs: Long): Pair<Int, String> {
        val p = try {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            return -2 to (e.message ?: "")
        }
        val out = StringBuilder()
        val reader = Thread {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') }
            }
        }
        reader.start()
        return try {
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroy()
                -1 to out.toString()
            } else {
                reader.join(1000)
                p.exitValue() to out.toString()
            }
        } catch (e: Exception) {
            p.destroy()
            -2 to (e.message ?: "")
        }
    }
}

fun Context.errText(err: PassThrough.Err): String = getString(
    when (err) {
        PassThrough.Err.SU_DENIED -> R.string.err_su_denied
        PassThrough.Err.NODE_MISSING -> R.string.err_node_missing
        PassThrough.Err.WRITE_FAILED -> R.string.err_write_failed
        PassThrough.Err.NO_CHARGER -> R.string.err_no_charger
        PassThrough.Err.NO_PPS -> R.string.err_no_pps
        PassThrough.Err.NOT_CHARGING -> R.string.err_not_charging
        PassThrough.Err.LOW_BATTERY -> R.string.err_low_battery
        PassThrough.Err.TOO_FULL -> R.string.err_too_full
        PassThrough.Err.TIMEOUT -> R.string.err_timeout
        PassThrough.Err.UNKNOWN -> R.string.err_unknown
    }
)
