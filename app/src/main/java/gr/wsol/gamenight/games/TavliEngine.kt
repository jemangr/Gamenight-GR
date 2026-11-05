package gr.wsol.gamenight.games

/**
 * Τάβλι: Πόρτες, Πλακωτό και Φεύγα, without UI. Positions are «pips» from each player's own side:
 * 24 is where they start, 1 is the last point before bearing off, 25 is the bar (Portes only) and 0
 * means off the board. [Pt] is one of the 24 board points in absolute terms.
 */
enum class TavliKind { Portes, Plakoto, Fevga }

/** [owner] -1 = empty. [pinned] = the player whose single checker is pinned underneath (Plakoto). */
data class Pt(val owner: Int = -1, val count: Int = 0, val pinned: Int = -1)

data class TMove(val from: Int, val to: Int, val die: Int)

data class TBoard(val kind: TavliKind, val pts: List<Pt>, val bar: List<Int> = listOf(0, 0), val off: List<Int> = listOf(0, 0), val motherPinned: Int = -1) {

    /** Absolute index (0–23) of pip [pip] (1–24) for player [p]. */
    fun abs(p: Int, pip: Int): Int = when {
        p == 0 -> pip - 1
        kind == TavliKind.Fevga -> (pip - 1 + 12) % 24
        else -> 24 - pip
    }

    /** The pip of absolute point [a] seen by [p]. */
    fun pipOf(p: Int, a: Int): Int = when {
        p == 0 -> a + 1
        kind == TavliKind.Fevga -> (a - 12 + 24) % 24 + 1
        else -> 24 - a
    }

    fun at(p: Int, pip: Int): Pt = pts[abs(p, pip)]

    /** How many of [p]'s checkers are on the board at [pip] (on top, or pinned underneath). */
    fun mine(p: Int, pip: Int): Int = at(p, pip).let { (if (it.owner == p) it.count else 0) + (if (it.pinned == p) 1 else 0) }

    fun onBoard(p: Int) = (1..24).sumOf { mine(p, it) } + bar[p]

    /** All of [p]'s checkers are in their home (pips 1–6). */
    fun allHome(p: Int) = bar[p] == 0 && (7..24).all { mine(p, it) == 0 }

    val winner: Int? get() = when {
        motherPinned >= 0 -> 1 - motherPinned
        off[0] == 15 -> 0
        off[1] == 15 -> 1
        else -> null
    }

    /** Points for the winner: 2 if the loser bore off nothing (or lost their «μάνα»), else 1. */
    fun points(): Int { val w = winner ?: return 0; return if (motherPinned >= 0 || off[1 - w] == 0) 2 else 1 }

    /** Can [p] move one checker from [from] with [die]? Gives the target pip, or null. */
    fun target(p: Int, from: Int, die: Int): Int? {
        val o = 1 - p
        if (kind == TavliKind.Portes && bar[p] > 0 && from != 25) return null
        if (from == 25) { if (kind != TavliKind.Portes || bar[p] == 0) return null }
        else { val pt = at(p, from); if (pt.owner != p || pt.count == 0) return null }
        val to = from - die
        if (to >= 1) {
            val pt = at(p, to)
            val ok = when {
                pt.count == 0 -> true
                pt.owner == p -> true
                kind == TavliKind.Portes -> pt.count == 1
                kind == TavliKind.Plakoto -> pt.count == 1 && pt.pinned == -1
                else -> false
            }
            if (!ok) return null
            if (kind == TavliKind.Fevga && !fevgaOk(p, from, to)) return null
            return to
        }
        if (!allHome(p)) return null
        if (to == 0) return 0
        // a bigger die bears off only the farthest checker
        return if ((from + 1..6).all { mine(p, it) == 0 }) 0 else null
    }

    /** Fevga: the first checker must reach the other's starting quarter before a second one leaves; no trapping 6-wall. */
    private fun fevgaOk(p: Int, from: Int, to: Int): Boolean {
        if (from == 24 && at(p, 24).let { it.owner == p && it.count < 15 } && (1..12).none { mine(p, it) > 0 }) return false
        val after = move(p, TMove(from, to, from - to))
        val o = 1 - p
        val mine = BooleanArray(24) { after.pts[it].owner == p }
        val oppPips = (1..24).filter { after.mine(o, it) > 0 }
        if (oppPips.isEmpty()) return true
        for (start in 0 until 24) {
            if ((0 until 6).all { mine[(start + it) % 24] }) {
                val pips = (0 until 6).map { after.pipOf(o, (start + it) % 24) }.sorted()
                if (pips.last() - pips.first() != 5) continue // across the seam: no-one is trapped
                if (oppPips.all { it > pips.last() }) return false
            }
        }
        return true
    }

    fun move(p: Int, m: TMove): TBoard {
        val o = 1 - p
        val np = pts.toMutableList(); val nb = bar.toMutableList(); val no = off.toMutableList()
        var mother = motherPinned
        if (m.from == 25) nb[p]-- else {
            val a = abs(p, m.from); val pt = np[a]
            np[a] = when {
                pt.count > 1 -> pt.copy(count = pt.count - 1)
                pt.pinned >= 0 -> Pt(pt.pinned, 1, -1)
                else -> Pt()
            }
        }
        if (m.to == 0) no[p]++ else {
            val a = abs(p, m.to); val pt = np[a]
            np[a] = when {
                pt.count == 0 -> Pt(p, 1, pt.pinned)
                pt.owner == p -> pt.copy(count = pt.count + 1)
                kind == TavliKind.Portes -> { nb[o]++; Pt(p, 1, -1) }
                else -> { if (pipOf(o, a) == 24 && mother < 0) mother = o; Pt(p, 1, o) }
            }
        }
        return TBoard(kind, np, nb, no, mother)
    }

    /** Every single move [p] can make now with one of [dice]. */
    fun steps(p: Int, dice: List<Int>): List<TMove> {
        val out = mutableListOf<TMove>()
        val froms = if (kind == TavliKind.Portes && bar[p] > 0) listOf(25) else (1..24).filter { at(p, it).let { pt -> pt.owner == p && pt.count > 0 } }
        for (d in dice.distinct()) for (f in froms) target(p, f, d)?.let { out += TMove(f, it, d) }
        return out
    }

    /**
     * The moves [p] may start with: only those that lead to using as many of [dice] as possible
     * (and the bigger die when just one of two can be used), as in the real rules.
     */
    fun legal(p: Int, dice: List<Int>): List<TMove> {
        if (dice.isEmpty() || winner != null) return emptyList()
        val first = steps(p, dice)
        if (first.isEmpty()) return emptyList()
        val memo = HashMap<Pair<TBoard, List<Int>>, Int>()
        fun depth(b: TBoard, left: List<Int>): Int {
            if (left.isEmpty()) return 0
            // a move that wins the game counts as using every die left (nothing is left to play)
            if (b.winner != null) return left.size
            val key = b to left.sorted()
            memo[key]?.let { return it }
            var best = 0
            for (s in b.steps(p, left)) {
                best = maxOf(best, 1 + depth(b.move(p, s), left - s.die))
                if (best == left.size) break
            }
            memo[key] = best
            return best
        }
        val scored = first.map { it to 1 + depth(move(p, it), dice - it.die) }
        val max = scored.maxOf { it.second }
        var ok = scored.filter { it.second == max }.map { it.first }
        if (max == 1 && dice.size == 2 && dice[0] != dice[1]) {
            val big = maxOf(dice[0], dice[1])
            if (ok.any { it.die == big }) ok = ok.filter { it.die == big }
        }
        return ok
    }

    companion object {
        fun start(kind: TavliKind): TBoard {
            val b = TBoard(kind, List(24) { Pt() })
            val setup = if (kind == TavliKind.Portes) mapOf(24 to 2, 13 to 5, 8 to 3, 6 to 5) else mapOf(24 to 15)
            val pts = MutableList(24) { Pt() }
            for (p in 0..1) for ((pip, c) in setup) pts[b.abs(p, pip)] = Pt(p, c)
            return TBoard(kind, pts)
        }

        /** The dice to play: a double plays four times. */
        fun dice(a: Int, b: Int) = if (a == b) listOf(a, a, a, a) else listOf(a, b)
    }
}

/** A match: Πόρτες → Πλακωτό → Φεύγα in turn (or just one kind), first to [target] points. */
class TavliMatch(val target: Int, val kinds: List<TavliKind>) {
    val score = intArrayOf(0, 0)
    var gameNo = 0
        private set
    val kind get() = kinds[gameNo % kinds.size]
    val winner: Int? get() = (0..1).firstOrNull { score[it] >= target }

    fun won(p: Int, pts: Int) { score[p] += pts; gameNo++ }
}
