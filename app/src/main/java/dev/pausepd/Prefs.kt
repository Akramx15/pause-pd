package dev.pausepd

import android.content.Context

object Prefs {
    private const val FILE = "pause-pd"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun resetOnUnplug(c: Context) = sp(c).getBoolean("reset_on_unplug", true)
    fun setResetOnUnplug(c: Context, v: Boolean) = sp(c).edit().putBoolean("reset_on_unplug", v).apply()

    fun gameWatch(c: Context) = sp(c).getBoolean("game_watch", false)
    fun setGameWatch(c: Context, v: Boolean) = sp(c).edit().putBoolean("game_watch", v).apply()

    /** True while the game watcher owns the switch, so it never undoes a manual enable. */
    fun armed(c: Context) = sp(c).getBoolean("game_armed", false)
    fun setArmed(c: Context, v: Boolean) = sp(c).edit().putBoolean("game_armed", v).apply()

    fun games(c: Context): Set<String> =
        sp(c).getStringSet("games", emptySet<String>())?.toSet() ?: emptySet()

    fun setGames(c: Context, v: Set<String>) = sp(c).edit().putStringSet("games", v).apply()
}
