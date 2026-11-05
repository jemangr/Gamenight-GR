package gr.wsol.gamenight

import gr.wsol.gamenight.core.AlienPair
import gr.wsol.gamenight.core.Minefield
import gr.wsol.gamenight.games.AlienGame
import gr.wsol.gamenight.games.BluffRules
import gr.wsol.gamenight.games.LieRules
import gr.wsol.gamenight.games.MineChain
import gr.wsol.gamenight.games.NumberRules
import gr.wsol.gamenight.games.Votes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Games2Test {
    private val pair = AlienPair("καφές", "τσάι")
    private val five = listOf("Α", "Β", "Γ", "Δ", "Ε")

    @Test fun alienCatchingAlienAndCloneWinsForHumans() {
        val g = AlienGame(five, pair, withClone = true, rnd = Random(4))
        val clone = g.clone!!
        g.out(g.alien); assertNull(g.winner) // the clone is still in
        g.out(clone); g.cloneGuessed(false)
        assertEquals(AlienGame.Winner.Humans, g.winner)
        assertEquals(3, g.points().size); assertTrue(g.points().values.all { it == 1 })
    }

    @Test fun alienWinsInLastTwo() {
        val g = AlienGame(listOf("Α", "Β", "Γ", "Δ"), pair, withClone = false, rnd = Random(1))
        val humans = g.players - g.alien
        g.out(humans[0]); assertNull(g.winner)
        g.out(humans[1]); assertEquals(AlienGame.Winner.Alien, g.winner)
        assertEquals(mapOf(g.alien to 3), g.points())
    }

    @Test fun cloneGuessingWins() {
        val g = AlienGame(five, pair, withClone = true, rnd = Random(9))
        g.out(g.clone); g.cloneGuessed(true)
        assertEquals(AlienGame.Winner.Clone, g.winner)
        assertEquals(mapOf(g.clone!! to 3), g.points())
    }

    @Test fun alienWithThreeIsOneVote() {
        val g = AlienGame(listOf("Α", "Β", "Γ"), pair, withClone = true, rnd = Random(2))
        assertNull(g.clone)
        g.out(null)
        assertEquals(AlienGame.Winner.Alien, g.winner)
    }

    @Test fun cloneNeverSpeaksFirst() {
        repeat(30) { s ->
            val g = AlienGame(five, pair, withClone = true, rnd = Random(s))
            assertTrue(g.order(Random(s + 100)).first() != g.clone)
            assertNull(g.wordOf(g.clone!!)); assertEquals("τσάι", g.wordOf(g.alien))
        }
    }

    @Test fun votesTieMeansNobody() {
        assertNull(Votes.top(mapOf("Α" to "Β", "Β" to "Α")))
        assertEquals("Γ", Votes.top(mapOf("Α" to "Γ", "Β" to "Γ", "Γ" to "Α")))
    }

    @Test fun bluffScoring() {
        val opts = BluffRules.options("Τρεις", mapOf("Α" to "Δύο", "Β" to "δύο ", "Γ" to "Πέντε"), Random(1))
        assertEquals(3, opts.size) // the two «δύο» are one choice
        val pts = BluffRules.points("Τρεις", opts, mapOf("Α" to "Τρεις", "Β" to "Πέντε", "Γ" to "Δύο"))
        assertEquals(3, pts["Α"]) // found the truth (+2), and Γ fell for Α's «Δύο» (+1)
        assertEquals(1, pts["Γ"]) // Β fell for «Πέντε»
        assertEquals(1, pts["Β"]) // Γ fell for «δύο», which Β wrote too
        assertTrue(BluffRules.same("Τρείς.", "τρεις"))
    }

    @Test fun lieScoring() {
        assertEquals("Κ" to 1, LieRules.points("Μ", "Κ", lied = true, judgedLie = true))
        assertEquals("Μ" to 2, LieRules.points("Μ", "Κ", lied = true, judgedLie = false))
        assertEquals("Μ" to 1, LieRules.points("Μ", "Κ", lied = false, judgedLie = true))
    }

    @Test fun numbersAreDistinctAndScored() {
        val d = NumberRules.deal(listOf("Α", "Β", "Γ", "Δ", "Ε", "Ζ"), Random(5))
        assertEquals(6, d.values.toSet().size); assertTrue(d.values.all { it in 1..10 })
        val real = mapOf("Α" to 2, "Β" to 5, "Γ" to 9)
        val p = NumberRules.points("Σ", real, mapOf("Α" to 2, "Β" to 6, "Γ" to 7))
        assertEquals(3 + 1, p["Σ"]) // all three in order, one exact
        assertEquals(1, p["Α"]); assertEquals(1, p["Β"]); assertNull(p["Γ"])
    }

    @Test fun minefield() {
        val c = MineChain(Minefield("Θάλασσα", listOf(listOf("ψάρι", "ψάρια"), listOf("νερό"), listOf("κολύμπι"))))
        assertEquals(MineChain.Result.Mine, c.say("Ψάρια"))
        assertEquals(MineChain.Result.Safe, c.say("βάρκα"))
        assertEquals(MineChain.Result.Repeat, c.say("Βάρκα"))
        assertEquals(MineChain.Result.Repeat, c.say("θαλασσα"))
        assertEquals(MineChain.Result.Mine, c.say("νερά"))
        assertEquals("βάρκα", c.last)
    }
}
