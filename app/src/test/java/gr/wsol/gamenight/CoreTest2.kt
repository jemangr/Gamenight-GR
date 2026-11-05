package gr.wsol.gamenight

import androidx.test.core.app.ApplicationProvider
import gr.wsol.gamenight.chaos.Awards
import gr.wsol.gamenight.chaos.Chaos
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Player
import gr.wsol.gamenight.core.Settings
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.games.CoreLevels
import gr.wsol.gamenight.games.CoreRules
import gr.wsol.gamenight.games.CoreSim
import gr.wsol.gamenight.games.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoreTest2 {
    @Test fun fortyLevelsEachSolvableWithinItsPalette() {
        assertEquals(40, CoreLevels.all.size)
        assertEquals((1..40).toList(), CoreLevels.all.map { it.id })
        CoreLevels.all.forEach { lv ->
            assertTrue("${lv.id} has a solution", lv.reference.isNotEmpty())
            lv.reference.values.groupingBy { it }.eachCount().forEach { (n, c) -> assertTrue("${lv.id}: $n×$c within the palette", c <= (lv.palette[n] ?: 0)) }
            assertTrue("${lv.id}: no node on a blocked cell", lv.reference.keys.none { it in lv.blocked })
            val par = CoreRules.par(lv)
            assertTrue("${lv.id} solved", par.solved)
            assertEquals("${lv.id} robust", 3, par.robust)
        }
    }

    @Test fun emptyBoardDoesNotSolveAndStepBackWorks() {
        val lv = CoreLevels.byId(11)!!
        assertFalse(CoreRules.score(lv, emptyMap()).solved)
        val sim = CoreSim(lv, lv.reference, lv.inputSets()[0])
        repeat(4) { sim.step() }
        val t = sim.tick; val pk = sim.packets.map { it.copy() }
        sim.step(); assertTrue(sim.back())
        assertEquals(t, sim.tick); assertEquals(pk, sim.packets)
    }

    @Test fun nodesDoWhatTheySay() {
        val lv = CoreLevels.byId(1)!!
        val sim = CoreSim(lv, mapOf(lv.cell(2, 0) to Node.INV), mapOf(2 to listOf(3, 0, 9))).run()
        assertEquals(listOf(6, 9, 0), sim.out[2])
        val enc = CoreRules.encode(lv.reference); assertEquals(lv.reference, CoreRules.decode(enc))
    }

    @Test fun chaosEventsAreSmallAndApply() {
        Store.inMemory(Night(players = listOf("Α", "Β", "Γ").mapIndexed { i, n -> Player(n, i) }), Settings())
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        repeat(200) { s ->
            val e = Chaos.make(ctx, Store.night, Random(s))
            assertTrue(e.points in -1..1)
            assertTrue(e.text.isNotBlank())
        }
        Chaos.sheriff = null
        val before = Store.night.points.values.sum()
        Chaos.apply(Chaos.Event(Chaos.Kind.Penalty, "🚨", "", "x", "Α", -1))
        assertEquals(before - 1, Store.night.points.values.sum())
        assertTrue(Chaos.roast(ctx, "Β").text.contains("Β"))
        assertTrue(Chaos.roastWaitMs() > 0)
    }

    @Test fun chaosWaitsForItsTime() {
        Store.inMemory(Night(players = listOf("Α", "Β").mapIndexed { i, n -> Player(n, i) }), Settings())
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Chaos.pending = null; Chaos.nextAt = 0
        Chaos.maybe(ctx, now = 1_000_000); assertEquals(null, Chaos.pending) // first call only schedules
        Chaos.maybe(ctx, now = 1_000_000 + 60_000); assertEquals(null, Chaos.pending)
        Chaos.maybe(ctx, now = 1_000_000 + 30 * 60_000); assertTrue(Chaos.pending != null)
        assertFalse(Chaos.canShow) // no calm screen on show
    }

    @Test fun awards() {
        var n = Night(players = listOf("Α", "Β").mapIndexed { i, s -> Player(s, i) }).award(listOf("Α"), 5)
        n = n.stat("Β", "bomb").stat("Β", "bomb").stat("Α", "liar")
        val a = Awards.of(n).map { it.emoji to it.who }
        assertTrue(("👑" to "Α") in a); assertTrue(("💣" to "Β") in a); assertTrue(("🤥" to "Α") in a)
    }
}
