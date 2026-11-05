package gr.wsol.gamenight

import gr.wsol.gamenight.games.HouseEngine
import gr.wsol.gamenight.games.HouseSetup
import gr.wsol.gamenight.games.NightActions
import gr.wsol.gamenight.games.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HouseTest {
    private val seven = listOf("Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η")

    /** An engine with roles placed by hand: [roles] in the players' order. */
    private fun engine(roles: List<Role>, lovers: Boolean = false): HouseEngine {
        val names = seven.take(roles.size)
        val g = HouseEngine(names, HouseSetup(0, false, emptySet(), lovers), Random(1))
        names.zip(roles).forEach { (p, r) -> g.role[p] = r }
        return HouseEngine::class.java.getDeclaredField("original").let { f -> f.isAccessible = true; f.set(g, g.role.toMap()); g }
    }

    @Test fun dealMatchesTheSetup() {
        repeat(20) { s ->
            val g = HouseEngine(seven, HouseSetup.suggested(7), Random(s))
            val r = g.role.values.groupingBy { it }.eachCount()
            assertEquals(1, r[Role.Zombie]); assertEquals(1, r[Role.Leader]); assertEquals(1, r[Role.Possessed]); assertEquals(7, g.role.size)
            assertTrue(g.lovers != null && g.lovers!!.first != g.lovers!!.second)
        }
    }

    @Test fun biteHealAndIncense() {
        val g = engine(listOf(Role.Zombie, Role.Healer, Role.Granny, Role.Human, Role.Human))
        assertEquals("Δ", g.resolveNight(NightActions(bite = "Δ")).eaten)
        assertTrue(g.resolveNight(NightActions(bite = "Ε", protect = "Ε")).saved)
        assertTrue("Ε" in g.alive)
        assertTrue("Ε" !in g.healable()) // can't guard Ε twice in a row
        assertNull(g.resolveNight(NightActions(incense = true, bite = "Ε")).eaten)
        assertTrue(g.incenseUsed)
        assertEquals("Ε", g.resolveNight(NightActions(incense = true, bite = "Ε")).eaten) // only once
    }

    @Test fun leaderInfectsOnceAndExorcistCures() {
        val g = engine(listOf(Role.Leader, Role.Exorcist, Role.Human, Role.Human, Role.Human, Role.Human))
        val r = g.resolveNight(NightActions(bite = "Γ", infect = true))
        assertEquals("Γ", r.infected); assertNull(r.eaten); assertEquals(Role.Zombie, g.role["Γ"])
        val r2 = g.resolveNight(NightActions(bite = "Δ", infect = true, exorcise = "Γ"))
        assertEquals("Δ", r2.eaten) // the infection was used up: a normal bite
        assertEquals("Γ", r2.cured); assertEquals(Role.Human, g.role["Γ"])
        g.resolveNight(NightActions(exorcise = "Α")) // already used, and the leader can't be cured anyway
        assertEquals(Role.Leader, g.role["Α"])
    }

    @Test fun mageSeesInfectedAsZombie() {
        val g = engine(listOf(Role.Leader, Role.Mage, Role.Human, Role.Human, Role.Human, Role.Human))
        g.resolveNight(NightActions(bite = "Γ", infect = true))
        assertEquals("Γ" to true, g.resolveNight(NightActions(look = "Γ")).mageSaw)
    }

    @Test fun hunterTakesOneAndLoversLeaveTogether() {
        val g = engine(listOf(Role.Zombie, Role.Hunter, Role.Human, Role.Human, Role.Human, Role.Human))
        g.resolveNight(NightActions(bite = "Β"))
        assertEquals("Β", g.hunterPending)
        g.hunterTakes("Α")
        assertEquals(HouseEngine.Winner.Humans, g.winner)
    }

    @Test fun zombiesWinAtParity() {
        val g = engine(listOf(Role.Zombie, Role.Human, Role.Human, Role.Human))
        g.resolveNight(NightActions(bite = "Β")); assertNull(g.winner)
        g.vote(mapOf("Α" to "Γ", "Γ" to "Δ", "Δ" to "Γ")) // Γ out: 1 zombie vs 1 human
        assertEquals(HouseEngine.Winner.Zombies, g.winner)
        assertEquals(mapOf("Α" to 2), g.points())
    }

    @Test fun possessedWinsWhenVotedOut() {
        val g = engine(listOf(Role.Zombie, Role.Possessed, Role.Human, Role.Human, Role.Human))
        g.vote(mapOf("Α" to "Β", "Γ" to "Β", "Δ" to "Β"))
        assertEquals(HouseEngine.Winner.Possessed, g.winner)
        assertEquals(mapOf("Β" to 3), g.points())
    }

    @Test fun tieSendsNobodyOutAndGhostsVoteOnce() {
        val g = engine(listOf(Role.Zombie, Role.Human, Role.Human, Role.Human, Role.Human))
        g.resolveNight(NightActions(bite = "Β"))
        assertEquals(listOf("Β"), g.ghostsWhoMayVote())
        assertNull(g.vote(mapOf("Α" to "Γ", "Γ" to "Α")))
        assertEquals(listOf("Β"), g.ghostsWhoMayVote())
        g.vote(mapOf("Β" to "Α", "Γ" to "Α", "Α" to "Γ"))
        assertTrue("Β" !in g.ghostsWhoMayVote())
        assertEquals(HouseEngine.Winner.Humans, g.winner)
    }
}
