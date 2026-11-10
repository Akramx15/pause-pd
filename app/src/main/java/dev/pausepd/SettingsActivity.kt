package dev.pausepd

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var txtAccess: TextView
    private lateinit var listGames: LinearLayout
    private lateinit var txtEmpty: TextView
    private lateinit var editPkg: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        txtAccess = findViewById(R.id.txtAccess)
        listGames = findViewById(R.id.listGames)
        txtEmpty = findViewById(R.id.txtEmpty)
        editPkg = findViewById(R.id.editPkg)

        val swReset = findViewById<Switch>(R.id.swReset)
        swReset.isChecked = Prefs.resetOnUnplug(this)
        swReset.setOnCheckedChangeListener { _, v -> Prefs.setResetOnUnplug(this, v) }

        val swGame = findViewById<Switch>(R.id.swGame)
        swGame.isChecked = Prefs.gameWatch(this)
        swGame.setOnCheckedChangeListener { _, v ->
            Prefs.setGameWatch(this, v)
            if (v) {
                if (!GameWatch.hasAccess(this)) openUsageAccess()
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
                }
                startForegroundService(Intent(this, GameWatchService::class.java))
            } else {
                stopService(Intent(this, GameWatchService::class.java))
            }
            renderAccess()
        }

        findViewById<Button>(R.id.btnGrant).setOnClickListener { openUsageAccess() }
        findViewById<Button>(R.id.btnAdd).setOnClickListener { add() }
        findViewById<Button>(R.id.btnPick).setOnClickListener { pickGame() }
        renderGames()
    }

    override fun onResume() {
        super.onResume()
        renderAccess()
    }

    private fun renderAccess() {
        txtAccess.visibility = if (GameWatch.hasAccess(this)) View.GONE else View.VISIBLE
    }

    private fun add() {
        val pkg = editPkg.text.toString().trim()
        if (!pkg.contains('.')) {
            Toast.makeText(this, R.string.toast_bad_package, Toast.LENGTH_SHORT).show()
            return
        }
        addPkg(pkg)
        editPkg.setText("")
    }

    private fun addPkg(pkg: String) {
        Prefs.setGames(this, Prefs.games(this) + pkg)
        renderGames()
        Toast.makeText(this, getString(R.string.toast_added, pkg), Toast.LENGTH_SHORT).show()
    }

    private fun pickGame() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        val apps = pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val p = ri.activityInfo.packageName
                if (p == packageName) null else p to ri.loadLabel(pm).toString()
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { "${it.second}\n${it.first}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_title)
            .setItems(labels) { _, which -> addPkg(apps[which].first) }
            .show()
    }

    private fun renderGames() {
        listGames.removeAllViews()
        val games = Prefs.games(this).sorted()
        txtEmpty.visibility = if (games.isEmpty()) View.VISIBLE else View.GONE
        games.forEach { pkg ->
            listGames.addView(TextView(this).apply {
                text = pkg
                textSize = 15f
                setTextColor(getColor(R.color.text))
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener {
                    Prefs.setGames(this@SettingsActivity, Prefs.games(this@SettingsActivity) - pkg)
                    renderGames()
                }
            })
        }
    }

    private fun openUsageAccess() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
