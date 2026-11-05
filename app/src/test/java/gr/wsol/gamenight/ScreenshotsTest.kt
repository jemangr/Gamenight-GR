package gr.wsol.gamenight

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Palette
import gr.wsol.gamenight.core.Player
import gr.wsol.gamenight.core.Settings
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.games.GameHost
import gr.wsol.gamenight.games.Games
import gr.wsol.gamenight.ui.GamenightTheme
import gr.wsol.gamenight.ui.HomeScreen
import gr.wsol.gamenight.ui.PlayersScreen
import gr.wsol.gamenight.ui.ScoreScreen
import gr.wsol.gamenight.ui.SettingsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screens, rendered on the JVM: checked by eye, and the README screenshots (docs/screenshots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    private val names = listOf("Γιάννης", "Μαρία", "Νίκος", "Ελένη", "Κώστας")

    @Before fun night() {
        val players = names.mapIndexed { i, n -> Player(n, Palette.players[i]) }
        Store.inMemory(Night(players = players, points = mapOf("Γιάννης" to 7, "Μαρία" to 9, "Νίκος" to 4, "Ελένη" to 6, "Κώστας" to 2)), Settings(sounds = false, haptics = false, updateCheck = false))
    }

    private val host = object : GameHost {
        override fun award(who: Collection<String>, pts: Int) { Store.night = Store.night.award(who, pts) }
        override fun log(note: String) {}
        override fun exit() {}
    }

    private fun shot(name: String, vararg clicks: String, wait: Long = 1200, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { GamenightTheme { content() } }
        compose.mainClock.advanceTimeBy(600)
        clicks.forEach {
            // The paused clock doesn't apply state written by a click: let it run for the click, then pause again.
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(it, substring = true).performSemanticsAction(SemanticsActions.OnClick)
            compose.mainClock.autoAdvance = false
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeBy(700)
        }
        compose.mainClock.advanceTimeBy(wait)
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    private fun game(id: String) = Games.byId(id)!!.play!!

    @Test fun s01_home() = shot("01-home") { HomeScreen() }
    @Test fun s02_players() { Store.night = Store.night.copy(teams = true, teamOf = names.withIndex().associate { (i, n) -> n to i % 2 }); shot("02-players") { PlayersScreen() } }
    @Test fun s03_score() = shot("03-score") { ScoreScreen() }
    @Test fun s04_settings() = shot("04-settings") { SettingsScreen() }
    @Test fun s05_taboo_intro() = shot("05-taboo-intro") { game("taboo")(host) }
    @Test fun s06_taboo() = shot("06-taboo", "Ξεκινάμε", "Ξεκίνα τον χρόνο") { game("taboo")(host) }
    @Test fun s07_quads() = shot("07-quads", "Ξεκινάμε") { Store.night = Store.night.copy(players = Store.night.players.take(1)); game("quads")(host) }
    @Test fun s08_bomb() = shot("08-bomb", "Ξεκινάμε", "Άναψε") { game("bomb")(host) }
    @Test fun s09_find10() = shot("09-find10", "Ξεκινάμε", "Δείξε") { game("find10")(host) }
    @Test fun s10_related() = shot("10-related", "Ξεκινάμε") { game("related")(host) }
    @Test fun s11_song() = shot("11-song", "Ξεκινάμε", "Ξεκίνα τα 20") { game("song")(host) }
    @Test fun s12_movies() = shot("12-movies", "Ξεκινάμε") { game("movies")(host) }

    @Test fun s13_alien() = shot("13-alien", "Ξεκινάμε", "Είμαι εγώ") { game("alien")(host) }
    @Test fun s14_forehead() = shot("14-forehead", "Ξεκινάμε", "Έτοιμος", wait = 3600) { game("forehead")(host) }
    @Test fun s15_bluffer() = shot("15-bluffer", "Ξεκινάμε", "Γράψτε μπλόφες", "Είμαι εγώ") { game("bluffer")(host) }
    @Test fun s16_lie() = shot("16-lie", "Ξεκινάμε", "Είμαι εγώ") { game("lie")(host) }
    @Test fun s17_number() = shot("17-number", "Ξεκινάμε") { game("number")(host) }
    @Test fun s18_minefield() = shot("18-minefield", "Ξεκινάμε") { game("minefield")(host) }

    @Test fun s19_fake() = shot("19-fake", "Ξεκινάμε") { game("fake")(host) }
    @Test fun s20_telephone() = shot("20-telephone", "Ξεκινάμε", "Είμαι εγώ") { game("telephone")(host) }
    @Test fun s21_saboteur() = shot("21-saboteur", "Ξεκινάμε") { game("saboteur")(host) }
    @Test fun s22_witness() = shot("22-witness", "Ξεκινάμε", "Δείξε τη σκηνή", wait = 500) { game("witness")(host) }
    @Test fun s23_balance() = shot("23-balance", "Ξεκινάμε", "Ξεκίνα τα 5", wait = 500) { game("balance")(host) }
    @Test fun s24_witness_scene() = shot("24-witness-scene") { gr.wsol.gamenight.games.WitnessScene(gr.wsol.gamenight.games.WitnessScenes.make(kotlin.random.Random(7))) }

    @Test fun s25_house_setup() = shot("25-house-setup", "Ξεκινάμε") { game("house")(host) }
}
