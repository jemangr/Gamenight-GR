package gr.wsol.gamenight

import gr.wsol.gamenight.games.Pt
import gr.wsol.gamenight.games.TBoard
import gr.wsol.gamenight.games.TMove
import gr.wsol.gamenight.games.TavliKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TavliTest {
    private fun empty(kind: TavliKind) = TBoard(kind, List(24) { Pt() })
    private fun TBoard.put(p: Int, pip: Int, c: Int): TBoard = copy(pts = pts.toMutableList().also { it[abs(p, pip)] = Pt(p, c) })

    @Test fun everyKindStartsWithFifteenEach() {
        for (k in TavliKind.entries) {
            val b = TBoard.start(k)
            assertEquals(15, b.onBoard(0)); assertEquals(15, b.onBoard(1))
        }
    }

    @Test fun portesHitsABlotAndTheBarMustEnterFirst() {
        var b = empty(TavliKind.Portes).put(0, 13, 2).put(1, 15, 1) // white's 10 = black's 15
        b = b.put(1, 15, 1)
        val hit = b.legal(0, listOf(3, 5)).first { it.from == 13 && it.to == 10 }
        b = b.move(0, hit)
        assertEquals(1, b.bar[1])
        assertTrue(b.legal(1, listOf(2, 4)).all { it.from == 25 })
    }

    @Test fun portesCannotLandOnTwo() {
        val b = empty(TavliKind.Portes).put(0, 13, 1).put(1, 15, 2)
        assertNull(b.target(0, 13, 3))
    }

    @Test fun bearOffWithBiggerDieOnlyFromTheFarthest() {
        val b = empty(TavliKind.Portes).put(0, 3, 1).put(0, 2, 1).copy(off = listOf(13, 0)).put(1, 20, 15)
        assertEquals(0, b.target(0, 3, 6))
        assertNull(b.target(0, 2, 6))
    }

    @Test fun plakotoPinsInsteadOfHitting() {
        var b = empty(TavliKind.Plakoto).put(0, 13, 1).put(1, 15, 1)
        b = b.move(0, TMove(13, 10, 3))
        val pt = b.at(0, 10)
        assertEquals(0, pt.owner); assertEquals(1, pt.pinned)
        assertFalse(b.steps(1, listOf(1, 2)).any { it.from == 15 })
        b = b.move(0, TMove(10, 7, 3))
        assertEquals(1, b.at(0, 10).owner)
    }

    @Test fun plakotoMotherIsADouble() {
        var b = empty(TavliKind.Plakoto).put(0, 3, 1).put(1, 24, 1).put(1, 10, 14) // black's start = white's pip 1
        b = b.move(0, TMove(3, 1, 2))
        assertEquals(0, b.winner); assertEquals(2, b.points())
    }

    @Test fun fevgaNeverLandsOnTheOther() {
        val b = TBoard.start(TavliKind.Fevga)
        // white pip 12 = black's start
        assertNull(b.put(0, 13, 1).copy(pts = b.pts.toMutableList().also { it[b.abs(0, 13)] = Pt(0, 1); it[b.abs(0, 24)] = Pt(0, 14) }).target(0, 13, 1))
    }

    @Test fun fevgaFirstCheckerMustGoFarBeforeASecond() {
        var b = TBoard.start(TavliKind.Fevga)
        b = b.move(0, TMove(24, 20, 4))
        assertNull(b.target(0, 24, 3))
        assertNotNull(b.target(0, 20, 3))
    }

    @Test fun mustPlayTheBiggerDieWhenOnlyOneFits() {
        // white 20 with 2+6: 12 is blocked, so only one die can be played, and it must be the 6
        val b = empty(TavliKind.Portes).put(0, 20, 1).put(1, 13, 2)
        val legal = b.legal(0, listOf(2, 6))
        assertEquals(listOf(TMove(20, 14, 6)), legal)
    }

    @Test fun aWinningMoveIsAlwaysAllowed() {
        // the last checker on 6 with 6+1: bearing off with the 6 wins at once, though «1 then 6» uses both dice
        val b = empty(TavliKind.Portes).put(0, 6, 1).copy(off = listOf(14, 0)).put(1, 20, 15)
        assertTrue(b.legal(0, listOf(6, 1)).contains(TMove(6, 0, 6)))
    }

    @Test fun randomGamesAlwaysFinish() {
        for (k in TavliKind.entries) repeat(15) { g ->
            val rnd = Random(g * 7 + k.ordinal)
            var b = TBoard.start(k); var p = g % 2; var turns = 0
            while (b.winner == null && turns < 3000) {
                var dice = TBoard.dice(rnd.nextInt(1, 7), rnd.nextInt(1, 7))
                while (true) {
                    val l = b.legal(p, dice); if (l.isEmpty()) break
                    val m = l[rnd.nextInt(l.size)]; b = b.move(p, m); dice = dice - m.die
                    assertEquals(15, b.onBoard(0) + b.off[0]); assertEquals(15, b.onBoard(1) + b.off[1])
                }
                p = 1 - p; turns++
            }
            assertNotNull("$k game $g stuck", b.winner)
        }
    }
}
