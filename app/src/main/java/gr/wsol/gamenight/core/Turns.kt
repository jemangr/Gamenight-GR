package gr.wsol.gamenight.core

/**
 * Whose turn: in team mode the teams take turns and inside a team the players take turns;
 * otherwise the players go round. [n] counts from 0.
 */
object Turns {
    fun player(night: Night, n: Int): String {
        val names = night.names
        if (names.isEmpty()) return ""
        if (!night.teams) return names[n % names.size]
        val teams = (0 until night.teamCount).map { t -> names.filter { night.teamOf[it] == t } }.filter { it.isNotEmpty() }
        if (teams.isEmpty()) return names[n % names.size]
        val team = teams[n % teams.size]
        return team[(n / teams.size) % team.size]
    }

    /** How many turns make a fair game: everyone (or every team member) gets [each] turns. */
    fun count(night: Night, each: Int): Int {
        if (!night.teams) return night.names.size * each
        val sizes = (0 until night.teamCount).map { t -> night.names.count { night.teamOf[it] == t } }.filter { it > 0 }
        if (sizes.isEmpty()) return night.names.size * each
        return sizes.size * sizes.max() * each
    }

    /** Teammates of [name] (who guess for them), or everyone else in solo mode. */
    fun others(night: Night, name: String): List<String> =
        if (night.teams) night.names.filter { it != name && night.teamOf[it] == night.teamOf[name] } else night.names.filter { it != name }

    /** Random teams of about the same size. */
    fun randomTeams(names: List<String>, count: Int, rnd: kotlin.random.Random = kotlin.random.Random): Map<String, Int> =
        names.shuffled(rnd).withIndex().associate { (i, n) -> n to i % count }
}
