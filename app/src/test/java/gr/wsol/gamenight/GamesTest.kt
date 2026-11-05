package gr.wsol.gamenight

import gr.wsol.gamenight.core.QuadGroup
import gr.wsol.gamenight.core.QuadPuzzle
import gr.wsol.gamenight.games.BombRules
import gr.wsol.gamenight.games.Find10Rules
import gr.wsol.gamenight.games.Games
import gr.wsol.gamenight.games.QuadBoard
import gr.wsol.gamenight.games.TabooRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GamesTest {
    @Test fun all28GamesWithUniqueIds() {
        assertEquals(28, Games.all.size)
        assertEquals(28, Games.all.map { it.id }.toSet().size)
        Games.all.forEach { assertTrue("${it.id} players", it.min in 1..7 && it.max in it.min..7) }
    }

    @Test fun tabooScoring() {
        assertEquals(listOf("Α" to 1, "Β" to 1), TabooRules.correct(false, 4, "Α", "Β"))
        assertEquals(listOf("Α" to 1), TabooRules.correct(true, 4, "Α", null))
        assertEquals(emptyList<Pair<String, Int>>(), TabooRules.correct(false, 2, "Α", "Β"))
        assertEquals(listOf("Α" to -1), TabooRules.banned(4, false, "Α"))
    }

    private val puzzle = QuadPuzzle(listOf(
        QuadGroup("a", listOf("1", "2", "3", "4"), 0), QuadGroup("b", listOf("5", "6", "7", "8"), 1),
        QuadGroup("c", listOf("9", "10", "11", "12"), 2), QuadGroup("d", listOf("13", "14", "15", "16"), 3),
    ))

    @Test fun quadsSolveAndOneAway() {
        val b = QuadBoard(puzzle, Random(1))
        assertEquals(QuadBoard.Result.OneAway, b.submit(setOf("1", "2", "3", "5")))
        assertEquals(QuadBoard.Result.Repeat, b.submit(setOf("1", "2", "3", "5")))
        assertEquals(1, b.mistakes)
        assertEquals(QuadBoard.Result.Solved, b.submit(setOf("1", "2", "3", "4")))
        assertEquals(12, b.left.size)
        assertEquals(QuadBoard.Result.Wrong, b.submit(setOf("5", "6", "9", "10")))
        assertFalse(b.over)
    }

    @Test fun quadsEndAfterFourMistakes() {
        val b = QuadBoard(puzzle)
        listOf(setOf("1", "5", "9", "13"), setOf("2", "6", "10", "14"), setOf("3", "7", "11", "15"), setOf("4", "8", "12", "16")).forEach { b.submit(it) }
        assertTrue(b.over); assertFalse(b.won)
    }

    @Test fun quadsVersusPoints() {
        val b = QuadBoard(puzzle)
        b.submit(setOf("1", "2", "3", "5")) // mistake
        listOf(puzzle.groups[0], puzzle.groups[1], puzzle.groups[2], puzzle.groups[3]).forEach { b.submit(it.words.toSet()) }
        assertEquals(4 * 2 - 1 + 1, b.versusPoints(inTime = true))
        assertEquals(4 * 2 - 1, b.versusPoints(inTime = false))
    }

    @Test fun bombFuseAndHeartbeat() {
        repeat(200) { val f = BombRules.fuseMs(Random(it)); assertTrue(f in 10_000..45_000) }
        assertEquals(1000, BombRules.beatMs(0, 20_000))
        assertEquals(250, BombRules.beatMs(20_000, 20_000))
        assertEquals("Γ", BombRules.nextHolder(listOf("Α", "Β", "Γ"), setOf("Β"), "Α"))
        assertEquals("Α", BombRules.nextHolder(listOf("Α", "Β", "Γ"), setOf("Β"), "Γ"))
        assertTrue(BombRules.letter(Random(2), not = 'Α') != 'Α')
    }

    @Test fun find10Points() {
        assertEquals(2, Find10Rules.points(10)); assertEquals(1, Find10Rules.points(7)); assertEquals(0, Find10Rules.points(6))
    }
}
