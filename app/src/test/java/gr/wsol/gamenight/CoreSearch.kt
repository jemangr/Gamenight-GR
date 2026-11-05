package gr.wsol.gamenight

import gr.wsol.gamenight.games.CoreLevel
import gr.wsol.gamenight.games.CoreLevels
import gr.wsol.gamenight.games.CoreRules
import gr.wsol.gamenight.games.CoreSim
import gr.wsol.gamenight.games.Node
import org.junit.Test

/** Dev tool (run by hand with -Dcore.search=true): finds the best known solution of each level. */
class CoreSearch {
    @Test fun search() {
        if (System.getProperty("core.search") != "true") return
        val only = System.getProperty("core.only")?.split(',')?.map { it.toInt() }
        CoreLevels.all.filter { only == null || it.id in only }.forEach { lv ->
            val best = find(lv)
            val s = best?.let { b -> b.entries.joinToString(";") { "${it.key / lv.w},${it.key % lv.w},${it.value}" } } ?: "NONE"
            val sc = best?.let { CoreRules.score(lv, it) }
            println("LEVEL ${lv.id} => \"$s\" nodes=${sc?.nodes} cycles=${sc?.cycles} robust=${sc?.robust}")
        }
    }

    private fun find(lv: CoreLevel): Map<Int, Node>? {
        val sets = lv.inputSets()
        val pool = lv.palette.flatMap { (n, c) -> List(c) { n } }
        val free = (0 until lv.w * lv.h).filter { it !in lv.blocked }
        for (k in 1..pool.size.coerceAtMost(5)) {
            var best: Pair<Map<Int, Node>, Int>? = null
            val kinds = multisets(pool, k)
            combos(free, k) { cellsSel ->
                kinds.forEach { ks ->
                    perms(ks).forEach { order ->
                        val m = cellsSel.zip(order).toMap()
                        val sim = CoreSim(lv, m, sets[0]).run(80)
                        if (sim.correct() && sets.drop(1).all { CoreSim(lv, m, it).run(120).correct() }) {
                            if (best == null || sim.lastTargetTick < best!!.second) best = m to sim.lastTargetTick
                        }
                    }
                }
            }
            if (best != null) return best!!.first
        }
        return null
    }

    private fun multisets(pool: List<Node>, k: Int): Set<List<Node>> {
        val out = mutableSetOf<List<Node>>()
        fun go(start: Int, cur: List<Node>) { if (cur.size == k) { out += cur.sorted(); return }; for (i in start until pool.size) go(i + 1, cur + pool[i]) }
        go(0, emptyList()); return out
    }

    private fun perms(l: List<Node>): Set<List<Node>> = if (l.size <= 1) setOf(l) else l.indices.flatMap { i -> perms(l.take(i) + l.drop(i + 1)).map { listOf(l[i]) + it } }.toSet()

    private fun combos(items: List<Int>, k: Int, f: (List<Int>) -> Unit) {
        fun go(start: Int, cur: List<Int>) { if (cur.size == k) { f(cur); return }; for (i in start until items.size) go(i + 1, cur + items[i]) }
        go(0, emptyList())
    }
}
