package gr.wsol.gamenight.games

import gr.wsol.gamenight.core.t
import kotlin.random.Random

/** The nodes of «Ο Πυρήνας». Each has a symbol and a shape, not only a colour. */
enum class Node(val sym: String, private val el: String, private val en: String, private val whatEl: String, private val whatEn: String) {
    INV("¬", "Αντιστροφέας", "Inverter", "Αντιστρέφει: v → 9 − v", "Inverts: v → 9 − v"),
    SHF("«", "Ολισθητής", "Shifter", "Μετακινεί τα bits: v → 2·v (mod 10)", "Shifts the bits: v → 2·v (mod 10)"),
    DLY("⧗", "Καθυστέρηση", "Delay", "Κρατά το σήμα έναν κύκλο", "Holds the signal one cycle"),
    ADD("+", "Αθροιστής", "Adder", "Προσθέτει το σήμα της από πάνω γραμμής, την ίδια στιγμή (mod 10)", "Adds the signal in the row above, at the same moment (mod 10)"),
    SPL("⑂", "Διακλάδωση", "Splitter", "Στέλνει αντίγραφο του σήματος στη γραμμή από κάτω", "Sends a copy of the signal to the row below");

    val title get() = t(el, en)
    val what get() = t(whatEl, whatEn)
}

/** What a target output must be, as a function of the inputs (A, B, C = rows given by the level). */
sealed interface Expr {
    data class In(val row: Int) : Expr
    data class Inv(val e: Expr) : Expr
    data class Dbl(val e: Expr) : Expr
    data class Add(val a: Expr, val b: Expr) : Expr

    fun eval(inputs: Map<Int, List<Int>>, i: Int): Int = when (this) {
        is In -> inputs[row]!![i]
        is Inv -> 9 - e.eval(inputs, i)
        is Dbl -> (2 * e.eval(inputs, i)) % 10
        is Add -> (a.eval(inputs, i) + b.eval(inputs, i)) % 10
    }

    fun rows(): Set<Int> = when (this) { is In -> setOf(row); is Inv -> e.rows(); is Dbl -> e.rows(); is Add -> a.rows() + b.rows() }

    fun text(names: Map<Int, String>): String = when (this) {
        is In -> names[row] ?: "?"
        is Inv -> "¬(${e.text(names)})"
        is Dbl -> "2·(${e.text(names)})"
        is Add -> "${a.text(names)} + ${b.text(names)}"
    }
}

data class CoreLevel(
    val id: Int,
    val chapter: Int,
    val nameEl: String,
    val nameEn: String,
    val w: Int,
    val h: Int,
    /** Input row → the tick its first value enters (usually 0). */
    val inputs: Map<Int, Int>,
    val targets: Map<Int, Expr>,
    val palette: Map<Node, Int>,
    val blocked: Set<Int> = emptySet(),
    /** A known solution (cell index → node): proves the level can be solved and sets its par. */
    val reference: Map<Int, Node>,
    val hintEl: String = "",
    val hintEn: String = "",
) {
    val name get() = t(nameEl, nameEn)
    val hint get() = t(hintEl, hintEn)
    fun cell(r: Int, c: Int) = r * w + c
    val names: Map<Int, String> get() = inputs.keys.sorted().mapIndexed { i, r -> r to "ABC"[i].toString() }.toMap()

    /** The main inputs and 3 stress tests with spikes: fixed per level, the same for everyone. */
    fun inputSets(): List<Map<Int, List<Int>>> {
        val rnd = Random(id * 7919)
        fun seq(len: Int, spikes: Boolean) = List(len) { if (spikes && it % 3 == 1) listOf(0, 9)[rnd.nextInt(2)] else rnd.nextInt(10) }
        val main = inputs.keys.associateWith { seq(6, false) }
        val s1 = inputs.keys.associateWith { k -> List(6) { i -> if ((i + k) % 2 == 0) 9 else 0 } }
        val s2 = inputs.keys.associateWith { seq(10, true) }
        val s3 = inputs.keys.associateWith { seq(8, true) }
        return listOf(main, s1, s2, s3)
    }
}

/**
 * The simulator: packets move one cell right per clock tick along their row. A node acts on a packet
 * entering its cell. Interference: an empty cell right above or below a node holds a passing packet
 * one extra tick. Deterministic, no Android.
 */
class CoreSim(val level: CoreLevel, val nodes: Map<Int, Node>, val inputs: Map<Int, List<Int>>) {
    data class Packet(val id: Int, var r: Int, var c: Int, var v: Int, var wait: Int = 0)
    data class State(val tick: Int, val packets: List<Packet>, val out: Map<Int, List<Int>>, val nextId: Int)

    var tick = 0; private set
    val packets = mutableListOf<Packet>()
    val out = mutableMapOf<Int, MutableList<Int>>()
    var lastTargetTick = 0; private set
    private var nextId = 0
    private val history = ArrayDeque<State>()

    private fun snapshot() = State(tick, packets.map { it.copy() }, out.mapValues { it.value.toList() }, nextId)

    fun interfered(r: Int, c: Int): Boolean = nodes[level.cell(r, c)] == null &&
        ((r > 0 && nodes[level.cell(r - 1, c)] != null) || (r < level.h - 1 && nodes[level.cell(r + 1, c)] != null))

    fun step() {
        history.addLast(snapshot()); if (history.size > 400) history.removeFirst()
        // Inputs enter at column −1 and move into column 0 in this tick.
        inputs.forEach { (row, seq) -> val i = tick - level.inputs.getValue(row); if (i in seq.indices) packets += Packet(nextId++, row, -1, seq[i]) }
        val entering = mutableListOf<Packet>()
        val exits = mutableListOf<Packet>()
        packets.forEach { p -> if (p.wait > 0) p.wait-- else { p.c++; if (p.c >= level.w) exits += p else entering += p } }
        packets.removeAll(exits)
        exits.forEach { p -> out.getOrPut(p.r) { mutableListOf() }.add(p.v); if (p.r in level.targets) lastTargetTick = tick + 1 }
        val consumed = mutableSetOf<Int>()
        val born = mutableListOf<Packet>()
        entering.sortedBy { it.r }.forEach { p ->
            if (p.id in consumed) return@forEach
            when (nodes[level.cell(p.r, p.c)]) {
                Node.INV -> p.v = 9 - p.v
                Node.SHF -> p.v = (2 * p.v) % 10
                Node.DLY -> p.wait = 1
                Node.SPL -> if (p.r + 1 < level.h) born += Packet(nextId++, p.r + 1, p.c, p.v)
                Node.ADD -> packets.firstOrNull { q -> q.r == p.r - 1 && q.c == p.c && q.id !in consumed && q.id != p.id }?.let { q -> p.v = (p.v + q.v) % 10; consumed += q.id }
                null -> if (interfered(p.r, p.c)) p.wait = 1
            }
        }
        packets.removeAll { it.id in consumed }
        packets += born
        tick++
    }

    fun back(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        tick = s.tick; nextId = s.nextId
        packets.clear(); packets += s.packets.map { it.copy() }
        out.clear(); s.out.forEach { (k, v) -> out[k] = v.toMutableList() }
        return true
    }

    val done get() = packets.isEmpty() && inputs.all { (row, seq) -> tick - level.inputs.getValue(row) >= seq.size }

    fun run(max: Int = 200): CoreSim { while (!done && tick < max) step(); return this }

    /** Each target row got exactly the expected values, in order. */
    fun correct(): Boolean = level.targets.all { (row, e) ->
        val n = inputs.values.first().size
        out[row].orEmpty() == List(n) { e.eval(inputs, it) }
    }

    fun expected(row: Int): List<Int> { val n = inputs.values.first().size; return List(n) { level.targets.getValue(row).eval(inputs, it) } }
}

/** The three scores of a solution. */
data class CoreScore(val solved: Boolean, val nodes: Int, val cycles: Int, val robust: Int)

object CoreRules {
    fun score(level: CoreLevel, nodes: Map<Int, Node>): CoreScore {
        val sets = level.inputSets()
        val main = CoreSim(level, nodes, sets[0]).run()
        val ok = main.correct()
        val robust = sets.drop(1).count { CoreSim(level, nodes, it).run().correct() }
        return CoreScore(ok, nodes.size, main.lastTargetTick, if (ok) robust else 0)
    }

    fun par(level: CoreLevel) = score(level, level.reference)

    /** The nodes still allowed. */
    fun left(level: CoreLevel, nodes: Map<Int, Node>, n: Node) = (level.palette[n] ?: 0) - nodes.values.count { it == n }

    fun encode(nodes: Map<Int, Node>) = nodes.entries.joinToString(";") { "${it.key}:${it.value.name}" }
    fun decode(s: String?): Map<Int, Node> = s.orEmpty().split(';').mapNotNull { p -> p.split(':').takeIf { it.size == 2 }?.let { (k, v) -> k.toIntOrNull()?.let { it to Node.valueOf(v) } } }.toMap()
}
