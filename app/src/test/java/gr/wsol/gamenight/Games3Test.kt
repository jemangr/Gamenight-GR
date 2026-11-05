package gr.wsol.gamenight

import gr.wsol.gamenight.games.BalanceBall
import gr.wsol.gamenight.games.FakeRules
import gr.wsol.gamenight.games.SaboteurRules
import gr.wsol.gamenight.games.TelephoneRules
import gr.wsol.gamenight.games.WitnessScenes
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Games3Test {
    private val p = listOf("Α", "Β", "Γ", "Δ")

    @Test fun fakerNeverDrawsFirstAndScoring() {
        repeat(40) { s -> val f = FakeRules.faker(p, Random(s)); assertTrue(FakeRules.order(p, f, Random(s + 1)).first() != f) }
        assertEquals(mapOf("Β" to 1, "Γ" to 1, "Δ" to 1), FakeRules.points(p, "Α", caught = true, guessed = false))
        assertEquals(mapOf("Α" to 3), FakeRules.points(p, "Α", caught = true, guessed = true))
        assertEquals(mapOf("Α" to 3), FakeRules.points(p, "Α", caught = false, guessed = false))
    }

    @Test fun saboteurScoring() {
        assertEquals(3, SaboteurRules.points(p, "Γ", caught = true, wordSaid = true).size)
        assertEquals(mapOf("Γ" to 4), SaboteurRules.points(p, "Γ", caught = false, wordSaid = true))
        assertEquals(mapOf("Γ" to 3), SaboteurRules.points(p, "Γ", caught = false, wordSaid = false))
    }

    @Test fun telephoneAlternates() {
        assertEquals(listOf(false, true, false, true), (0..3).map { TelephoneRules.drawsAt(it) })
    }

    @Test fun witnessQuestionsAreAlwaysAnswerable() {
        repeat(300) { s ->
            val sc = WitnessScenes.make(Random(s))
            assertEquals(1, sc.people.count { it.hat != null })
            val qs = WitnessScenes.questions(sc, Random(s))
            assertEquals(3, qs.size)
            qs.forEach { q ->
                assertTrue(q.q, q.answer in q.options)
                assertEquals(q.q, q.options.size, q.options.toSet().size)
                assertTrue(q.options.size >= 2)
            }
        }
    }

    @Test fun ballStaysStillOnAFlatPhoneAndFallsWhenTilted() {
        val flat = BalanceBall()
        repeat(300) { assertFalse(flat.step(Offset.Zero, 1 / 60f)) }
        val tilted = BalanceBall(BalanceBall.sensitivityAt(5))
        var hit = false
        repeat(600) { if (tilted.step(Offset(4f, 0f), 1 / 60f)) hit = true }
        assertTrue(hit)
        assertTrue(BalanceBall.sensitivityAt(3) > BalanceBall.sensitivityAt(1))
    }
}
