package gr.wsol.gamenight.games

import gr.wsol.gamenight.core.t
import kotlin.random.Random

/** The roles of «Το στοιχειωμένο σπίτι». */
enum class Role(val emoji: String, private val el: String, private val en: String, private val whatEl: String, private val whatEn: String) {
    Zombie("🧟", "Ζόμπι", "Zombie", "Στο σκοτάδι δαγκώνετε μαζί έναν.", "In the dark you bite one, together."),
    Leader("🧟‍♂️", "Αρχηγός των ζόμπι", "Zombie leader", "Ζόμπι που μία φορά στο παιχνίδι μολύνει αντί να σκοτώσει: ο δαγκωμένος γίνεται κρυφά ζόμπι.", "A zombie that once per game infects instead of killing: the bitten one secretly turns."),
    Healer("🩺", "Θεραπευτής", "Healer", "Κάθε σκοτάδι προστατεύεις έναν, όχι τον ίδιο δύο φορές στη σειρά.", "Each night you protect one, not the same twice in a row."),
    Mage("🔮", "Μάγος", "Mage", "Κάθε σκοτάδι κοιτάζεις στη σφαίρα αν κάποιος είναι ζόμπι.", "Each night you look in the orb: is someone a zombie?"),
    Exorcist("✝️", "Εξορκιστής", "Exorcist", "Μία φορά στο παιχνίδι ξορκίζεις έναν. Αν είναι ζόμπι (όχι ο αρχηγός), ξαναγίνεται άνθρωπος.", "Once per game you exorcise one. A zombie (not the leader) turns human again."),
    Medium("🕯️", "Μέντιουμ", "Medium", "Κάθε σκοτάδι μαθαίνεις τι ήταν ένα φάντασμα.", "Each night you learn what a ghost was."),
    Hunter("🔦", "Κυνηγός φαντασμάτων", "Ghost hunter", "Αν φύγεις, παίρνεις αμέσως μαζί σου έναν που διαλέγεις.", "If you leave, you take one of your choice with you."),
    Granny("👵", "Γιαγιά με το θυμιατό", "Granny with the censer", "Μία φορά στο παιχνίδι θυμιάζεις το σπίτι και εκείνο το σκοτάδι δεν δαγκώνεται κανείς.", "Once per game you cense the house: nobody is bitten that night."),
    Possessed("😈", "Δαιμονισμένος", "Possessed", "Κερδίζεις μόνος σου αν η παρέα σε πετάξει έξω με ψηφοφορία.", "You win alone if the group votes you out."),
    Human("🙂", "Άνθρωπος", "Human", "Βρες τα ζόμπι και πέτα τα έξω.", "Find the zombies and throw them out.");

    val title get() = t(el, en)
    val what get() = t(whatEl, whatEn)
    val zombie get() = this == Zombie || this == Leader
}

/** The setup chosen on screen: how many zombies, which special roles, lovers. */
data class HouseSetup(val zombies: Int, val leader: Boolean, val roles: Set<Role>, val lovers: Boolean) {
    fun size() = zombies + (if (leader) 1 else 0) + roles.size

    companion object {
        /** The suggested deal per number of players (lovers from 6). */
        fun suggested(n: Int) = when {
            n <= 4 -> HouseSetup(1, false, setOf(Role.Healer, Role.Mage), false)
            n == 5 -> HouseSetup(1, false, setOf(Role.Healer, Role.Mage, Role.Granny), false)
            n == 6 -> HouseSetup(1, true, setOf(Role.Healer, Role.Mage, Role.Hunter, Role.Medium), true)
            else -> HouseSetup(1, true, setOf(Role.Healer, Role.Mage, Role.Exorcist, Role.Possessed, Role.Granny), true)
        }
        val optional = listOf(Role.Healer, Role.Mage, Role.Exorcist, Role.Medium, Role.Hunter, Role.Granny, Role.Possessed)
    }
}

/** What the roles chose in one night (null: nothing). */
data class NightActions(
    val incense: Boolean = false,
    val bite: String? = null,
    val infect: Boolean = false,
    val protect: String? = null,
    val look: String? = null,
    val exorcise: String? = null,
    val medium: String? = null,
)

/** What happened in a night, for the morning and the end-of-game story. */
data class NightResult(
    val eaten: String?,
    val infected: String?,
    val cured: String?,
    val saved: Boolean,
    val incense: Boolean,
    val mageSaw: Pair<String, Boolean>?,
    val mediumSaw: Pair<String, Role>?,
)

/**
 * The whole game, without screens: roles, who is in, the nights, the votes, the ghosts and who wins.
 * Tested on its own.
 */
class HouseEngine(val players: List<String>, setup: HouseSetup, rnd: Random = Random) {
    /** Current role of each player (an infected human becomes Zombie, a cured zombie Human). */
    val role = mutableMapOf<String, Role>()
    /** The role each one started with (shown at the end). */
    val original: Map<String, Role>
    val alive = players.toMutableList()
    val ghosts = mutableListOf<String>()
    val lovers: Pair<String, String>?
    var night = 0; private set
    var leaderInfected = false; private set
    var exorcistUsed = false; private set
    var incenseUsed = false; private set
    private var lastProtected: String? = null
    val ghostVoteUsed = mutableSetOf<String>()
    /** Lines of «τι έγινε κάθε σκοτάδι», in order. */
    val story = mutableListOf<String>()

    enum class Winner { Humans, Zombies, Possessed, Lovers }
    var winner: Winner? = null; private set
    /** A ghost hunter who just left and takes one with them (the screen asks whom). */
    var hunterPending: String? = null; private set

    init {
        val deck = mutableListOf<Role>()
        repeat(setup.zombies) { deck += Role.Zombie }
        if (setup.leader) deck += Role.Leader
        deck += setup.roles
        while (deck.size < players.size) deck += Role.Human
        val shuffled = deck.take(players.size).shuffled(rnd)
        players.forEachIndexed { i, p -> role[p] = shuffled[i] }
        original = role.toMap()
        lovers = if (setup.lovers && players.size >= 2) players.shuffled(rnd).let { it[0] to it[1] } else null
    }

    fun has(r: Role) = alive.any { role[it] == r }
    fun zombiesAlive() = alive.filter { role[it]!!.zombie }
    fun isZombie(p: String) = role[p]!!.zombie
    fun loverOf(p: String) = lovers?.let { if (it.first == p) it.second else if (it.second == p) it.first else null }

    /** Who the healer may protect tonight (not the same as last night). */
    fun healable() = alive.filter { it != lastProtected }

    fun resolveNight(a: NightActions): NightResult {
        night++
        val blocked = a.incense && !incenseUsed && has(Role.Granny)
        if (a.incense && has(Role.Granny)) incenseUsed = true
        val target = a.bite?.takeIf { it in alive && !blocked }
        if (a.protect != null && has(Role.Healer)) lastProtected = a.protect
        val saved = target != null && target == a.protect && has(Role.Healer)
        var eaten: String? = null
        var infected: String? = null
        if (target != null && !saved) {
            if (a.infect && !leaderInfected && has(Role.Leader) && !isZombie(target)) { leaderInfected = true; role[target] = Role.Zombie; infected = target }
            else if (!isZombie(target)) eaten = target
        }
        var cured: String? = null
        if (a.exorcise != null && !exorcistUsed && has(Role.Exorcist) && a.exorcise in alive) {
            exorcistUsed = true
            if (role[a.exorcise] == Role.Zombie) { role[a.exorcise] = Role.Human; cured = a.exorcise }
        }
        val mage = a.look?.takeIf { has(Role.Mage) && it in alive }?.let { it to isZombie(it) }
        val medium = a.medium?.takeIf { has(Role.Medium) && it in ghosts }?.let { it to original[it]!! }
        story += t("Σκοτάδι $night: ", "Night $night: ") + listOfNotNull(
            if (blocked) t("η γιαγιά θύμιασε το σπίτι", "granny censed the house") else null,
            a.bite?.let { t("τα ζόμπι διάλεξαν $it", "the zombies chose $it") + if (a.infect && infected != null) t(" (μόλυνση)", " (infection)") else "" },
            a.protect?.takeIf { has(Role.Healer) }?.let { t("ο θεραπευτής φύλαξε $it", "the healer guarded $it") },
            mage?.let { (who, z) -> t("ο μάγος κοίταξε $who (${if (z) "ζόμπι" else "όχι ζόμπι"})", "the mage looked at $who (${if (z) "zombie" else "not a zombie"})") },
            cured?.let { t("ο εξορκιστής ξανάκανε άνθρωπο $it", "the exorcist cured $it") },
            medium?.let { t("ο μέντιουμ έμαθε τι ήταν $it", "the medium learned what $it was") },
        ).joinToString(", ").ifEmpty { t("ησυχία", "quiet") }
        if (eaten != null) leave(eaten, byVote = false)
        decide()
        return NightResult(eaten, infected, cured, saved, blocked, mage, medium)
    }

    /** [p] leaves the house (eaten or thrown out): becomes a ghost, takes the lover along, and a hunter takes one. */
    fun leave(p: String, byVote: Boolean) {
        if (p !in alive) return
        alive.remove(p); ghosts += p
        if (byVote && original[p] == Role.Possessed && role[p] == Role.Possessed) { winner = Winner.Possessed; return }
        if (role[p] == Role.Hunter) hunterPending = p
        loverOf(p)?.let { l -> if (l in alive) { story += t("$l έφυγε μαζί με τον έρωτά του/της", "$l left with their love"); leave(l, byVote = false) } }
        decide()
    }

    fun hunterTakes(target: String) {
        val h = hunterPending ?: return
        hunterPending = null
        story += t("Ο κυνηγός $h πήρε μαζί του τον/την $target", "Hunter $h took $target along")
        leave(target, byVote = false)
        decide()
    }

    /** The vote of the day; [votes] voter → choice, ghosts' votes included. Returns who leaves (null on a tie). */
    fun vote(votes: Map<String, String>): String? {
        votes.keys.filter { it in ghosts }.forEach { ghostVoteUsed += it }
        val out = Votes.top(votes.filterValues { it in alive })
        if (out != null) { story += t("Η παρέα πέταξε έξω τον/την $out", "The group threw out $out"); leave(out, byVote = true) }
        else story += t("Ισοψηφία: δεν βγήκε κανείς", "A tie: nobody left")
        decide()
        return out
    }

    fun ghostsWhoMayVote() = ghosts.filter { it !in ghostVoteUsed }

    private fun decide() {
        if (winner != null) return
        val z = zombiesAlive().size
        val others = alive.size - z
        val l = lovers
        winner = when {
            l != null && alive.size == 2 && l.first in alive && l.second in alive && isZombie(l.first) != isZombie(l.second) -> Winner.Lovers
            z == 0 -> Winner.Humans
            z >= others -> Winner.Zombies
            else -> null
        }
    }

    /** +2 to each player of the winning side (ghosts too), +3 to the possessed or to mixed lovers. */
    fun points(): Map<String, Int> = when (winner) {
        Winner.Humans -> players.filter { !isZombie(it) && original[it] != Role.Possessed }.associateWith { 2 }
        Winner.Zombies -> players.filter { isZombie(it) }.associateWith { 2 }
        Winner.Possessed -> players.filter { original[it] == Role.Possessed }.associateWith { 3 }
        Winner.Lovers -> listOfNotNull(lovers?.first, lovers?.second).associateWith { 3 }
        null -> emptyMap()
    }
}
