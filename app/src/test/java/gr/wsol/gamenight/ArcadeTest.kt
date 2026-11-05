package gr.wsol.gamenight

import androidx.compose.ui.geometry.Offset
import gr.wsol.gamenight.games.DropGame
import gr.wsol.gamenight.games.GateGame
import gr.wsol.gamenight.games.GridGame
import gr.wsol.gamenight.games.OrbitGame
import gr.wsol.gamenight.games.PathGame
import gr.wsol.gamenight.games.Showdown
import gr.wsol.gamenight.games.SlingGame
import gr.wsol.gamenight.games.SliceGame
import gr.wsol.gamenight.games.TowerGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArcadeTest {
    private val dt = 1 / 60f

    @Test fun showdownGivesThreeTwoOne() {
        assertEquals(mapOf("Γ" to 3, "Α" to 2, "Δ" to 1), Showdown.points(listOf("Α" to 50, "Β" to 10, "Γ" to 90, "Δ" to 30)))
    }

    @Test fun gateBotThatMatchesColoursKeepsGoingAndCombos() {
        val g = GateGame(Random(3))
        var handled: GateGame.Bar? = null
        repeat(60 * 60) {
            val next = g.bars.firstOrNull { !it.passed }
            // Set the colour just before the bar arrives (a perfect tap).
            if (next != null && next !== handled && next.y > g.ballY - 0.06f) { handled = next; do g.tap() while (g.ball != next.color % g.colors) }
            g.step(dt)
        }
        assertFalse(g.over); assertTrue(g.passes > 20); assertTrue(g.score > g.passes) // multipliers kicked in
    }

    @Test fun gateEndsOnWrongColour() {
        val g = GateGame(Random(1))
        var over = false
        repeat(60 * 30) { if (g.step(dt) < 0) over = true }
        assertTrue(over || g.over)
    }

    @Test fun towerPerfectDropsGrowAgain() {
        val g = TowerGame()
        repeat(3) { while (kotlin.math.abs(g.x - g.floors.last().x) > 0.005f) g.step(0.002f); assertEquals("perfect", g.drop()) }
        assertTrue(g.floors.last().w > 0.4f)
        g.step(0.3f); val r = g.drop(); assertTrue(r == "ok" || r == "over")
    }

    @Test fun gridRightTapsScoreAndWrongOnesCostTime() {
        val g = GridGame(Random(2))
        repeat(30) { val i = (0 until 16).first { g.ok(it) }; assertTrue(g.tap(i)) }
        assertTrue(g.score >= 30)
        val wrong = (0 until 16).firstOrNull { !g.ok(it) }
        if (wrong != null) { val t = g.time; assertFalse(g.tap(wrong)); assertTrue(g.time < t) }
    }

    @Test fun sliceRedWithBlueAgainst() {
        val g = SliceGame(Random(5))
        repeat(200) { g.step(dt) }
        val s = g.target()!!
        val right = if (s.red) s.dir else (s.dir + 2) % 4
        assertTrue(g.swipe(right))
        repeat(3) { g.step(1f); g.target()?.let { t -> g.swipe(if (t.red) (t.dir + 2) % 4 else t.dir) } }
        assertTrue(g.over)
    }

    @Test fun dropStandingStillGetsCaughtByTheCeiling() {
        val g = DropGame(Random(4))
        repeat(60 * 120) { if (!g.over) g.step(dt, 0f) }
        assertTrue(g.over)
    }

    @Test fun orbitWithoutHoldingFliesOff() {
        val g = OrbitGame(Random(6))
        repeat(60 * 30) { if (!g.over) g.step(dt) }
        assertTrue(g.over)
    }

    @Test fun pathTracingTheShownPathScores() {
        val g = PathGame(Random(7))
        assertEquals(3, g.path.size)
        assertTrue(g.trace(g.path)); assertEquals(4, g.length)
        // Paths are orthogonally connected and never revisit a cell.
        repeat(50) { val p = g.path; assertEquals(p.size, p.toSet().size); p.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.abs(a - b) == 5 || (kotlin.math.abs(a - b) == 1 && a / 5 == b / 5)) }; g.trace(p) }
        assertFalse(g.trace(listOf(0)))
        assertEquals(2, g.lives)
    }

    @Test fun slingShotsFlyAndTimeRunsOut() {
        val g = SlingGame(Random(8))
        g.shoot(Offset(0f, 0.3f))
        assertTrue(g.flying)
        repeat(60 * 70) { g.step(dt); if (!g.flying) g.shoot(Offset(0.05f, 0.4f)) }
        assertTrue(g.over)
    }
}
