package gr.wsol.gamenight.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 1.1, «Ιστορικό βραδιών»: when a night is closed («Νέα βραδιά»), its ranking is kept on the phone
 * (only names and points). From it: who won how many times, and winning streaks.
 */
object History {
    @Serializable data class Row(val side: String, val members: List<String>, val pts: Int)
    @Serializable data class NightRecord(val at: Long, val rows: List<Row>) {
        val winners: List<Row> get() { val top = rows.maxOfOrNull { it.pts } ?: return emptyList(); return if (top <= 0) emptyList() else rows.filter { it.pts == top } }
    }
    data class Stat(val name: String, val wins: Int, val nights: Int, val streak: Int, val best: Int)

    private val json = Json { ignoreUnknownKeys = true }
    private const val KEY = "history.nights"
    const val MAX = 200

    fun all(): List<NightRecord> = Store.text(KEY)?.let { runCatching { json.decodeFromString(ListSerializer(NightRecord.serializer()), it) }.getOrNull() }.orEmpty()

    /** Keeps [n] if anyone scored. */
    fun add(n: Night, now: Long = System.currentTimeMillis()) {
        val rows = n.ranking().map { (s, p) -> Row(n.label(s), n.membersOf(s), p) }
        if (rows.none { it.pts != 0 }) return
        Store.putText(KEY, json.encodeToString(ListSerializer(NightRecord.serializer()), (all() + NightRecord(now, rows)).takeLast(MAX)))
    }

    fun clear() = Store.putText(KEY, "[]")

    /** Per player: nights won, nights played, the current streak of won nights and the best one. */
    fun stats(list: List<NightRecord> = all()): List<Stat> {
        val names = list.flatMap { r -> r.rows.flatMap { it.members } }.distinct()
        return names.map { name ->
            var cur = 0; var best = 0; var wins = 0; var played = 0
            list.sortedBy { it.at }.forEach { r ->
                if (r.rows.none { name in it.members }) return@forEach
                played++
                if (r.winners.any { name in it.members }) { wins++; cur++; best = maxOf(best, cur) } else cur = 0
            }
            Stat(name, wins, played, cur, best)
        }.sortedWith(compareByDescending<Stat> { it.wins }.thenByDescending { it.best })
    }
}
