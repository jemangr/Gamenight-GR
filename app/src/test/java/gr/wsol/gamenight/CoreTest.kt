package gr.wsol.gamenight

import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Greek
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Player
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.update.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoreTest {
    private fun night(teams: Boolean = false, vararg names: String) =
        Night(players = names.mapIndexed { i, n -> Player(n, i) }, teams = teams, teamOf = if (teams) names.withIndex().associate { (i, n) -> n to i % 2 } else emptyMap())

    @Test fun soloAwardGoesToEachPlayer() {
        val n = night(false, "Α", "Β", "Γ").award(listOf("Α", "Β"), 2)
        assertEquals(2, n.points["Α"]); assertEquals(2, n.points["Β"]); assertEquals(null, n.points["Γ"])
    }

    @Test fun teamGetsPointsOnceEvenWithManyMembers() {
        // Α and Γ are in team 0, Β and Δ in team 1.
        val n = night(true, "Α", "Β", "Γ", "Δ").award(listOf("Α", "Γ", "Β"), 1)
        assertEquals(1, n.points["#0"]); assertEquals(1, n.points["#1"])
    }

    @Test fun rankingIsBestFirst() {
        val n = night(false, "Α", "Β", "Γ").award(listOf("Β"), 3).award(listOf("Γ"), 1)
        assertEquals(listOf("Β", "Γ", "Α"), n.ranking().map { it.first })
    }

    @Test fun turnsAlternateTeamsAndRotateMembers() {
        val n = night(true, "Α", "Β", "Γ", "Δ")
        assertEquals(listOf("Α", "Β", "Γ", "Δ", "Α"), (0..4).map { Turns.player(n, it) })
        val solo = night(false, "Α", "Β", "Γ")
        assertEquals(listOf("Α", "Β", "Γ", "Α"), (0..3).map { Turns.player(solo, it) })
    }

    @Test fun unevenTeamsStillGiveEveryoneATurn() {
        val n = Night(players = listOf("Α", "Β", "Γ").mapIndexed { i, s -> Player(s, i) }, teams = true, teamOf = mapOf("Α" to 0, "Β" to 0, "Γ" to 1))
        val turns = Turns.count(n, 1)
        val who = (0 until turns).map { Turns.player(n, it) }.toSet()
        assertEquals(setOf("Α", "Β", "Γ"), who)
    }

    @Test fun randomTeamsAreBalanced() {
        val t = Turns.randomTeams(listOf("1", "2", "3", "4", "5", "6", "7"), 3, Random(1))
        val sizes = t.values.groupingBy { it }.eachCount().values.sorted()
        assertEquals(listOf(2, 2, 3), sizes)
    }

    @Test fun greekFoldIgnoresAccentsAndCase() {
        assertEquals(Greek.fold("Ψάρι"), Greek.fold("ψαρι"))
        assertEquals("σασ", Greek.fold("Σας"))
        assertEquals(Greek.stem("ψάρια"), Greek.stem("ψάρι"))
    }

    @Test fun deckDoesNotRepeatUntilEmpty() {
        Store.inMemory()
        val d = Deck("test-deck", (1..20).toList(), { false }, rnd = Random(3))
        val drawn = (1..20).map { d.next()!! }
        assertEquals(20, drawn.toSet().size)
        val again = d.next()!!
        assertTrue(again in 1..20)
    }

    @Test fun deckSkipsAdultCardsUnlessAllowed() {
        Store.inMemory()
        val d = Deck("test-adult", listOf(1, 2, 3, 4), { it % 2 == 0 }, allowAdult = false)
        val seen = (1..6).map { d.next()!! }.toSet()
        assertEquals(setOf(1, 3), seen)
    }

    @Test fun versions() {
        assertTrue(Updates.newer("0.2.0", "0.1.9"))
        assertTrue(Updates.newer("v1.10.0", "1.9.2"))
        assertFalse(Updates.newer("0.1.0", "0.1.0"))
        assertFalse(Updates.newer("0.1.0-beta", "0.1.0"))
    }
}
