package gr.wsol.gamenight.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    /** Bolder words and questions (slang, teasing), never sexual content. */
    val adult: Boolean = false,
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    val updateCheck: Boolean = true,
    val skipVersion: String = "",
    val english: Boolean = false,
)

/**
 * Everything the app keeps, in one SharedPreferences file on this phone: tonight (players,
 * teams, points), the settings, the records of the arcade games and which cards were already seen.
 * Observable from Compose (the getters read state).
 */
object Store {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var nightState by mutableStateOf(Night())
    private var settingsState by mutableStateOf(Settings())

    var night: Night
        get() = nightState
        set(v) { nightState = v; if (::prefs.isInitialized) prefs.edit().putString("night", json.encodeToString(Night.serializer(), v)).apply() }

    var settings: Settings
        get() = settingsState
        set(v) { settingsState = v; Lang.english = v.english; if (::prefs.isInitialized) prefs.edit().putString("settings", json.encodeToString(Settings.serializer(), v)).apply() }

    var lastGame: String?
        get() = if (::prefs.isInitialized) prefs.getString("lastGame", null) else null
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("lastGame", v).apply() }

    var lastUpdateCheck: Long
        get() = if (::prefs.isInitialized) prefs.getLong("lastUpdateCheck", 0) else 0
        set(v) { if (::prefs.isInitialized) prefs.edit().putLong("lastUpdateCheck", v).apply() }

    fun init(c: Context) {
        prefs = c.getSharedPreferences("gamenight", Context.MODE_PRIVATE)
        prefs.getString("night", null)?.let { s -> runCatching { json.decodeFromString(Night.serializer(), s) }.getOrNull()?.let { nightState = it } }
        prefs.getString("settings", null)?.let { s -> runCatching { json.decodeFromString(Settings.serializer(), s) }.getOrNull()?.let { settingsState = it } }
        Lang.english = settingsState.english
    }

    /** For tests and previews: no file, just memory. */
    fun inMemory(n: Night = Night(), s: Settings = Settings()) { nightState = n; settingsState = s; Lang.english = s.english }

    // ---- cards already seen, per deck (so a card doesn't come back before the deck runs out) ----
    fun seen(deck: String): Set<Int> =
        if (::prefs.isInitialized) prefs.getString("seen.$deck", "")!!.split(',').mapNotNull { it.toIntOrNull() }.toSet() else memSeen[deck].orEmpty()

    fun setSeen(deck: String, ids: Set<Int>) {
        if (::prefs.isInitialized) prefs.edit().putString("seen.$deck", ids.joinToString(",")).apply() else memSeen[deck] = ids
    }

    private val memSeen = mutableMapOf<String, Set<Int>>()

    // ---- records (arcade, solo scores) ----
    fun best(key: String): Int = if (::prefs.isInitialized) prefs.getInt("best.$key", 0) else memBest[key] ?: 0

    /** Saves [score] if it beats the record; true when it did. */
    fun record(key: String, score: Int): Boolean {
        if (score <= best(key)) return false
        if (::prefs.isInitialized) prefs.edit().putInt("best.$key", score).apply() else memBest[key] = score
        return true
    }

    private val memBest = mutableMapOf<String, Int>()

    /** Arcade records: all time, today and this week. Returns which of them [score] beat. */
    fun arcadeRecord(game: String, score: Int, now: java.util.Calendar = java.util.Calendar.getInstance()): Set<String> {
        val day = "%04d%03d".format(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.DAY_OF_YEAR))
        val week = "%04dw%02d".format(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.WEEK_OF_YEAR))
        val out = mutableSetOf<String>()
        if (record("arc.$game", score)) out += "all"
        if (record("arc.$game.$day", score)) out += "day"
        if (record("arc.$game.$week", score)) out += "week"
        return out
    }

    fun arcadeBest(game: String, now: java.util.Calendar = java.util.Calendar.getInstance()): Triple<Int, Int, Int> {
        val day = "%04d%03d".format(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.DAY_OF_YEAR))
        val week = "%04dw%02d".format(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.WEEK_OF_YEAR))
        return Triple(best("arc.$game"), best("arc.$game.$day"), best("arc.$game.$week"))
    }
}
