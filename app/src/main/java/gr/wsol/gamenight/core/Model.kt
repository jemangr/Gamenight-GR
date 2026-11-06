package gr.wsol.gamenight.core

import kotlinx.serialization.Serializable

/** A player of tonight. [color] is an index into [Palette.players], [avatar] is the monster avatar variant. */
@Serializable
data class Player(val name: String, val color: Int, val avatar: Int = 0)

/** Points per side: in team mode the sides are the teams, otherwise the players. */
@Serializable
data class Night(
    val players: List<Player> = emptyList(),
    val teams: Boolean = false,
    /** Team of each player (by name), 0-based; only in team mode. */
    val teamOf: Map<String, Int> = emptyMap(),
    val teamCount: Int = 2,
    /** Points per side key: the player's name, or "#0", "#1", "#2" for the teams. */
    val points: Map<String, Int> = emptyMap(),
    /** Which games were played tonight and how many points they gave, newest last. */
    val log: List<Entry> = emptyList(),
    val started: Long = System.currentTimeMillis(),
    /** Small counters for the end-of-night awards: player → (what → how many). */
    val stats: Map<String, Map<String, Int>> = emptyMap(),
) {
    fun stat(name: String, key: String): Night {
        val m = stats[name].orEmpty()
        return copy(stats = stats + (name to (m + (key to (m[key] ?: 0) + 1))))
    }

    @Serializable
    data class Entry(val game: String, val note: String, val time: Long = System.currentTimeMillis())

    val names: List<String> get() = players.map { it.name }

    fun sideOf(name: String): String = if (teams) "#" + (teamOf[name] ?: 0) else name

    fun sides(): List<String> = if (teams) (0 until teamCount).map { "#$it" } else names

    /** The sides that have someone in them (a team can be left empty on the Players screen). */
    fun playingSides(): List<String> = sides().filter { membersOf(it).isNotEmpty() }

    /** «#2» → 2 in team mode; anything else is a player's name. */
    private fun teamNo(side: String): Int? = if (teams && side.startsWith("#")) side.drop(1).toIntOrNull() else null

    fun membersOf(side: String): List<String> =
        teamNo(side)?.let { k -> names.filter { teamOf[it] == k } } ?: listOf(side)

    fun label(side: String): String =
        teamNo(side)?.let { teamName(it) } ?: side

    fun colorOf(side: String): Int =
        teamNo(side)?.let { Palette.teamColor(it) } ?: players.firstOrNull { it.name == side }?.color ?: 0

    /**
     * [pts] to each of [who]. In team mode a team gets the points once, however many of its
     * players are in [who] (so a team of three doesn't count triple).
     */
    fun award(who: Collection<String>, pts: Int): Night {
        if (pts == 0 || who.isEmpty()) return this
        val keys = who.map { sideOf(it) }.filter { it in sides() }.toSet()
        val p = points.toMutableMap()
        keys.forEach { p[it] = (p[it] ?: 0) + pts }
        return copy(points = p)
    }

    fun awardSide(side: String, pts: Int): Night = if (pts == 0) this else copy(points = points + (side to (points[side] ?: 0) + pts))

    fun note(game: String, note: String) = copy(log = (log + Entry(game, note)).takeLast(200))

    /** Sides by points, best first. */
    fun ranking(): List<Pair<String, Int>> = sides().map { it to (points[it] ?: 0) }.sortedByDescending { it.second }

    companion object {
        fun teamName(i: Int) = t(listOf("Ομάδα κόκκινη", "Ομάδα μπλε", "Ομάδα πράσινη"), listOf("Red team", "Blue team", "Green team"))[i.coerceIn(0, 2)]
        private fun <T> t(el: List<T>, en: List<T>) = if (Lang.english) en else el
    }
}

object Palette {
    /** Player colours (ARGB): amber, pink, violet, green, cyan, red, lime. */
    val players = listOf(0xFFFFB020, 0xFFFF5FA2, 0xFFB48CFF, 0xFF39FF88, 0xFF22D3EE, 0xFFFF6B5E, 0xFFC6F432).map { it.toInt() }
    val teams = listOf(0xFFFF5E6C, 0xFF4D8DFF, 0xFF39D98A).map { it.toInt() }
    fun teamColor(i: Int) = teams[i.coerceIn(0, 2)]
}
