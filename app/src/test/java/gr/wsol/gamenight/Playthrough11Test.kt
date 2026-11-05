package gr.wsol.gamenight

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Palette
import gr.wsol.gamenight.core.Player
import gr.wsol.gamenight.core.Settings
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.games.GameHost
import gr.wsol.gamenight.games.Games
import gr.wsol.gamenight.ui.GamenightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * 1.1: each new game played on the real screens, from «Ξεκινάμε» to the results, by a «player»
 * that taps whatever the screen offers. Checks that no game gets stuck or crashes, and the points
 * of the games where the rules give exact numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Playthrough11Test {
    @get:Rule val compose = createComposeRule()

    private val names = listOf("Άννα", "Βάσω", "Γιώργος", "Δήμος")
    private val awards = mutableListOf<Pair<Collection<String>, Int>>()
    private var exited = false

    private val host = object : GameHost {
        override fun award(who: Collection<String>, pts: Int) { awards += who to pts; Store.night = Store.night.award(who, pts) }
        override fun log(note: String) {}
        override fun exit() { exited = true }
    }

    @Before fun night() {
        Store.inMemory(Night(players = names.mapIndexed { i, n -> Player(n, Palette.players[i]) }), Settings(sounds = false, haptics = false, updateCheck = false, chaos = false))
        awards.clear(); exited = false
    }

    private fun texts(m: SemanticsMatcher) = compose.onAllNodes(m, useUnmergedTree = false).fetchSemanticsNodes().map { n ->
        (n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") ?: "") + (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("") ?: "")
    }

    private fun onScreen(s: String) = compose.onAllNodesWithText(s, substring = true).fetchSemanticsNodes().isNotEmpty()

    private val never = listOf("Στο μενού", "Πίσω", "Έξοδος", "Ξανά", "Σβήσε", "Δες τα πακέτα")

    /**
     * Plays [id] by tapping: answers inputs with [answer], holds the secret cards, and otherwise taps
     * a button chosen by [pick] (default: a random one). Stops at the results («Ξανά»).
     */
    private fun play(id: String, steps: Int = 400, seed: Int = 1, tick: Long = 0, answer: (String) -> String = { "12" }, pick: ((List<String>) -> String?)? = null) {
        val rnd = Random(seed)
        compose.setContent { GamenightTheme { Games.byId(id)!!.play!!(host) } }
        compose.waitForIdle()
        var n = 0
        while (!onScreen("Ξανά")) {
            assertTrue("$id stuck after $n steps on: " + texts(hasClickAction()).joinToString(" | "), n++ < steps)
            // a secret to see: hold it
            val hold = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Κράτα πατημένο για να δεις")))
            if (hold.fetchSemanticsNodes().isNotEmpty()) {
                hold[0].performTouchInput { down(center); advanceEventTime(300); up() }
                compose.waitForIdle()
            }
            // a text field: type an answer
            val fields = compose.onAllNodes(hasSetTextAction())
            if (fields.fetchSemanticsNodes().isNotEmpty()) {
                val label = texts(hasSetTextAction()).firstOrNull().orEmpty()
                fields[0].performTextReplacement(answer(label))
                compose.waitForIdle()
            }
            val buttons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            val labels = buttons.map { b -> (b.config.getOrNull(SemanticsProperties.Text)?.joinToString("") ?: "") + (b.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("") ?: "") }
            val ok = labels.indices.filter { i ->
                val l = labels[i]
                l.isNotBlank() && never.none { l.contains(it) } && buttons[i].config.getOrNull(SemanticsProperties.Disabled) == null
            }
            if (ok.isEmpty()) {
                // nothing to tap: maybe a pad to draw on, or a timer to wait for
                runCatching { compose.onRoot().performTouchInput { swipe(Offset(width * 0.3f, height * 0.45f), Offset(width * 0.7f, height * 0.55f), 300) } }
                compose.mainClock.advanceTimeBy(1500); continue
            }
            val chosen = pick?.invoke(ok.map { labels[it] })?.let { want -> ok.firstOrNull { labels[it] == want } } ?: ok[rnd.nextInt(ok.size)]
            compose.onAllNodes(hasClickAction())[chosen].performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(tick)
        }
        assertTrue("$id exited instead of finishing", !exited)
    }

    /** Taps the «main» button first (Ξεκινάμε, Επόμενη…, Αποτελέσματα), so a game goes forward. */
    private fun forward(prefer: List<String>) = { labels: List<String> -> prefer.firstNotNullOfOrNull { p -> labels.firstOrNull { it.contains(p) } } }

    private val main = listOf("Ξεκινάμε", "Είμαι εγώ", "Το είδα", "Αποτελέσματα", "Επόμενη", "Επόμενος", "Ψηφοφορία", "Γράφουμε", "Έτοιμο", "Νέα ιστορία", "Συνέχεια")

    @Test fun whoOfUs() {
        // everyone votes for Γιώργος: everyone who voted for the top one gets +1, 8 questions
        play("whois", pick = { l -> l.firstOrNull { it == "Γιώργος" } ?: forward(main)(l) })
        // one award per value: the voters of a question together (so in teams a team gets it once)
        assertEquals(8 * 4, awards.sumOf { it.first.size * it.second })
        assertTrue(awards.all { it.second == 1 })
    }

    @Test fun closest() {
        // the answer typed is always 12; with bets of 1 the closest gets +2, no one loses points
        play("closest", pick = forward(main))
        assertTrue(awards.isNotEmpty())
        assertTrue(awards.all { it.second >= 0 })
    }

    @Test fun chainEndsWithOneLeft() = play("chain", pick = { l -> l.firstOrNull { it.contains("Λάθος") } ?: forward(main)(l) })

    @Test fun knowMe() = play("knowme", pick = forward(main + listOf("Το βρήκε", "Σωστό")))
    @Test fun dice() = play("dice", seed = 2)
    @Test fun bottle() = play("bottle", seed = 3)
    @Test fun quiz() { Store.night = Store.night.copy(teams = true, teamCount = 2, teamOf = names.withIndex().associate { (i, n) -> n to i % 2 }); play("quiz", steps = 600, seed = 4) }
    @Test fun mime() = play("mime", seed = 5)
    @Test fun whisper() = play("whisper", seed = 6, pick = forward(main + listOf("Ναι")))
    @Test fun spy() = play("spy", seed = 7)
    @Test fun lieTower() = play("lietower", seed = 8, pick = forward(main))
    @Test fun defuseStartsAndPasses() {
        compose.setContent { GamenightTheme { Games.byId("defuse")!!.play!!(host) } }
        compose.onAllNodesWithText("Ξεκινάμε")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("σταθερά", substring = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(onScreen("πάσα"))
    }

    @Test fun tavliTurnsGoRound() {
        compose.setContent { GamenightTheme { Games.byId("tavli")!!.play!!(host) } }
        compose.onAllNodesWithText("Ξεκινάμε")[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        // roll; a player who has no move can end the turn, otherwise the turn waits for moves
        repeat(6) {
            val roll = compose.onAllNodesWithText("Ρίξε", substring = true).fetchSemanticsNodes()
            if (roll.isNotEmpty()) compose.onAllNodesWithText("Ρίξε", substring = true)[0].performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            assertTrue(onScreen("Τέλος σειράς"))
        }
    }

    // ---- the 1.0 games ----
    @Test fun alien() = play("alien", steps = 800, seed = 11)
    @Test fun saboteur() = play("saboteur", steps = 800, seed = 12)
    @Test fun fake() = play("fake", steps = 800, seed = 13, tick = 300)
    @Test fun taboo() = play("taboo", steps = 800, seed = 14, tick = 2500)
    @Test fun forehead() = play("forehead", steps = 800, seed = 15, tick = 2500)
    @Test fun quads() { Store.night = Store.night.copy(players = Store.night.players.take(1)); play("quads", steps = 1500, seed = 16) }
    @Test fun minefield() = play("minefield", steps = 800, seed = 17)
    @Test fun find10() = play("find10", steps = 800, seed = 18, tick = 2000)
    @Test fun related() = play("related", steps = 800, seed = 19)
    @Test fun bluffer() = play("bluffer", steps = 800, seed = 20)
    @Test fun lie() = play("lie", steps = 800, seed = 21)
    @Test fun witness() = play("witness", steps = 800, seed = 22, tick = 1500)
    @Test fun movies() = play("movies", steps = 800, seed = 23)
    @Test fun number() = play("number", steps = 800, seed = 24)
    @Test fun telephone() = play("telephone", steps = 800, seed = 25, tick = 300)
    @Test fun song() = play("song", steps = 800, seed = 26)
    @Test fun bomb() = play("bomb", steps = 800, seed = 27, tick = 1500)
    @Test fun house() = play("house", steps = 1500, seed = 28)
}
