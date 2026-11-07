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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import gr.wsol.gamenight.core.Settings
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.games.GameHost
import gr.wsol.gamenight.games.Games
import gr.wsol.gamenight.ui.GamenightTheme
import gr.wsol.gamenight.ui.HomeScreen
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.PlayersScreen
import gr.wsol.gamenight.ui.ScoreScreen
import gr.wsol.gamenight.ui.SettingsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
        val players = names.mapIndexed { i, n -> Player(n, Palette.players[i], avatar = i) }
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
    @Test fun s02_players() {
        val sixNames = listOf("Γιάννης", "Μαρία", "Νίκος", "Ελένη", "Κώστας", "Δήμητρα")
        val sixPlayers = sixNames.mapIndexed { i, n -> Player(n, Palette.players[i], avatar = i) }
        Store.night = Store.night.copy(players = sixPlayers, teams = true, teamOf = sixNames.withIndex().associate { (i, n) -> n to i % 2 })
        shot("02-players") { PlayersScreen() }
    }
    @Test fun s03_score() = shot("03-score") { ScoreScreen() }
    @Test fun s03b_score_zero() {
        val players = names.mapIndexed { i, n -> Player(n, Palette.players[i], avatar = i) }
        Store.night = Store.night.copy(players = players, teams = false, points = emptyMap())
        shot("03b-score-zero") { ScoreScreen() }
    }
    @Test fun s03c_score_tie() {
        val players = names.mapIndexed { i, n -> Player(n, Palette.players[i], avatar = i) }
        Store.night = Store.night.copy(players = players, teams = false, points = mapOf("Γιάννης" to 8, "Μαρία" to 8, "Νίκος" to 8, "Ελένη" to 4, "Κώστας" to 2))
        shot("03c-score-tie") { ScoreScreen() }
    }
    @Test fun s03d_score_teams() {
        val sixNames = listOf("Γιάννης", "Μαρία", "Νίκος", "Ελένη", "Κώστας", "Δήμητρα")
        val sixPlayers = sixNames.mapIndexed { i, n -> Player(n, Palette.players[i], avatar = i) }
        Store.night = Store.night.copy(players = sixPlayers, teams = true, teamOf = sixNames.withIndex().associate { (i, n) -> n to i % 2 }, points = mapOf("#0" to 12, "#1" to 9))
        shot("03d-score-teams") { ScoreScreen() }
    }
    @Test fun s04_settings() = shot("04-settings") { SettingsScreen() }
    @Test fun s04b_havales() { Store.settings = Store.settings.copy(havales = true); shot("04b-havales") { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { gr.wsol.gamenight.ui.AiPanel() } } }
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

    @Test fun s25b_card_back() {
        compose.setContent {
            GamenightTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(gr.wsol.gamenight.ui.C.bg),
                    contentAlignment = Alignment.Center
                ) {
                    gr.wsol.gamenight.games.GothicRoleCardBack()
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/25b-card-back.png")
    }

    @Test fun s25c_role_zombie() {
        compose.setContent {
            GamenightTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(gr.wsol.gamenight.ui.C.bg),
                    contentAlignment = Alignment.Center
                ) {
                    gr.wsol.gamenight.games.GothicRoleCard(
                        role = gr.wsol.gamenight.games.Role.Zombie,
                        otherZombies = listOf("Μαρία")
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/25c-role-zombie.png")
    }

    @Test fun s25d_role_healer() {
        compose.setContent {
            GamenightTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(gr.wsol.gamenight.ui.C.bg),
                    contentAlignment = Alignment.Center
                ) {
                    gr.wsol.gamenight.games.GothicRoleCard(
                        role = gr.wsol.gamenight.games.Role.Healer
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/25d-role-healer.png")
    }

    @Test fun s25e_role_mage() {
        compose.setContent {
            GamenightTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(gr.wsol.gamenight.ui.C.bg),
                    contentAlignment = Alignment.Center
                ) {
                    gr.wsol.gamenight.games.GothicRoleCard(
                        role = gr.wsol.gamenight.games.Role.Mage,
                        lover = "Νίκος"
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/25e-role-mage.png")
    }

    @Test fun s25f_house_day() {
        compose.setContent {
            GamenightTheme {
                gr.wsol.gamenight.games.HouseBackground(isNight = false) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        gr.wsol.gamenight.ui.Panel {
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = androidx.compose.ui.Modifier.padding(12.dp)
                            ) {
                                androidx.compose.material3.Text("☀️ Πρωινό φως", style = gr.wsol.gamenight.ui.Type.title, color = gr.wsol.gamenight.ui.C.text)
                                gr.wsol.gamenight.ui.Body("Τα φαντάσματα ησύχασαν. Κανείς δεν χάθηκε απόψε.", align = androidx.compose.ui.text.style.TextAlign.Center, color = gr.wsol.gamenight.ui.C.text)
                                gr.wsol.gamenight.ui.BigButton("Συζήτηση", {}, color = gr.wsol.gamenight.ui.C.ghost)
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/25f-house-day.png")
    }

    @Test fun s25g_house_night() {
        compose.setContent {
            GamenightTheme {
                gr.wsol.gamenight.games.HouseBackground(isNight = true) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        gr.wsol.gamenight.ui.Panel {
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = androidx.compose.ui.Modifier.padding(12.dp)
                            ) {
                                androidx.compose.material3.Text("🕯️ Σκοτάδι 1", style = gr.wsol.gamenight.ui.Type.title, color = gr.wsol.gamenight.ui.C.candle)
                                gr.wsol.gamenight.ui.Body("Όλοι μάτια κλειστά. Το σπίτι κοιμάται...", align = androidx.compose.ui.text.style.TextAlign.Center, color = gr.wsol.gamenight.ui.C.text)
                                gr.wsol.gamenight.ui.BigButton("Εντάξει", {}, color = gr.wsol.gamenight.ui.C.ghost)
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/25g-house-night.png")
    }

    @Test fun s26_gate() = shot("26-gate", "Ξεκινάμε", "Πάμε", wait = 1500) { game("gate")(host) }
    @Test fun s27_tower() = shot("27-tower", "Ξεκινάμε", "Πάμε", wait = 1500) { game("tower")(host) }
    @Test fun s28_grid() = shot("28-grid", "Ξεκινάμε", "Πάμε", wait = 1500) { game("grid")(host) }
    @Test fun s29_slice() = shot("29-slice", "Ξεκινάμε", "Πάμε", wait = 1500) { game("slice")(host) }
    @Test fun s30_drop() = shot("30-drop", "Ξεκινάμε", "Πάμε", wait = 1500) { game("drop")(host) }
    @Test fun s31_orbit() = shot("31-orbit", "Ξεκινάμε", "Πάμε", wait = 1500) { game("orbit")(host) }
    @Test fun s32_path() = shot("32-path", "Ξεκινάμε", "Πάμε", wait = 1500) { game("path")(host) }
    @Test fun s33_sling() = shot("33-sling", "Ξεκινάμε", "Πάμε", wait = 1500) { game("sling")(host) }
    @Test fun s26b_new_record() {
        Store.forceBest("gate.record.all", 10)
        shot("26b-new-record") {
            val g = gr.wsol.gamenight.games.Games.byId("gate")!!
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                ) {
                    androidx.compose.material3.Text(g.emoji, fontSize = 52.sp)
                    androidx.compose.material3.Text("42", style = gr.wsol.gamenight.ui.Type.huge, color = g.color)
                    androidx.compose.material3.Text("Νέο ρεκόρ! 🏆", style = gr.wsol.gamenight.ui.Type.huge, color = gr.wsol.gamenight.ui.C.amber)
                    gr.wsol.gamenight.ui.Panel {
                        androidx.compose.foundation.layout.Row {
                            androidx.compose.material3.Text("Ρεκόρ", style = gr.wsol.gamenight.ui.Type.body, color = gr.wsol.gamenight.ui.C.muted, modifier = androidx.compose.ui.Modifier.weight(1f))
                            androidx.compose.material3.Text("🏆 42", style = gr.wsol.gamenight.ui.Type.h, color = gr.wsol.gamenight.ui.C.text)
                        }
                        androidx.compose.foundation.layout.Row {
                            androidx.compose.material3.Text("Σήμερα", style = gr.wsol.gamenight.ui.Type.body, color = gr.wsol.gamenight.ui.C.muted, modifier = androidx.compose.ui.Modifier.weight(1f))
                            androidx.compose.material3.Text("42", style = gr.wsol.gamenight.ui.Type.h, color = gr.wsol.gamenight.ui.C.text)
                        }
                    }
                    gr.wsol.gamenight.ui.BigButton("Ξανά", {}, color = g.color, height = 72.dp)
                }
                gr.wsol.gamenight.ui.Confetti(trigger = Unit)
            }
        }
    }

    @Test fun s34_core_levels() = shot("34-core-levels") { game("core")(host) }
    @Test fun s35_core_board() { (1..10).forEach { Store.forceBest("core.$it.solved", 1) }; shot("35-core-board", "11") { game("core")(host) } }
    @Test fun s36_chaos() = shot("36-chaos") { gr.wsol.gamenight.chaos.ChaosOverlay(gr.wsol.gamenight.chaos.Chaos.Event(gr.wsol.gamenight.chaos.Chaos.Kind.Penalty, "🚨", "Ποινή!", "Νίκος: −1, γιατί δεν πότισε το καναρίνι.", "Νίκος", -1)) {} }


    // ---- 1.1 ----
    private fun teams() { Store.night = Store.night.copy(teams = true, teamCount = 2, teamOf = names.withIndex().associate { (i, n) -> n to i % 2 }) }
    private fun aiKey() { gr.wsol.gamenight.ai.Keys.set(gr.wsol.gamenight.ai.Provider.GEMINI, "test-key-for-screenshots") }
    @Test fun n01_whois() = shot("40-whois", "Ξεκινάμε") { game("whois")(host) }
    @Test fun n02_knowme() = shot("41-knowme", "Ξεκινάμε") { game("knowme")(host) }
    @Test fun n03_dice() = shot("42-dice", "Ξεκινάμε") { game("dice")(host) }
    @Test fun n04_bottle() = shot("43-bottle", "Ξεκινάμε") { game("bottle")(host) }
    @Test fun n05_closest() = shot("44-closest", "Ξεκινάμε") { game("closest")(host) }
    @Test fun n06_quiz() { teams(); shot("45-quiz", "Ξεκινάμε") { game("quiz")(host) } }
    @Test fun n07_chain() = shot("46-chain-intro") { game("chain")(host) }
    @Test fun n08_mime() = shot("47-mime-intro") { game("mime")(host) }
    @Test fun n09_whisper() = shot("48-whisper-intro") { game("whisper")(host) }
    @Test fun n10_spy() = shot("49-spy-intro") { game("spy")(host) }
    @Test fun n11_lietower() = shot("50-lietower-intro") { game("lietower")(host) }
    @Test fun n12_tavli_intro() = shot("51-tavli-intro") { game("tavli")(host) }
    @Test fun n13_tavli() = shot("52-tavli", "Ξεκινάμε", "Ρίξε") { game("tavli")(host) }
    @Test fun n14_interro() { aiKey(); shot("53-interro-intro") { game("interro")(host) } }
    @Test fun n15_drawai() { aiKey(); shot("54-drawai-intro") { game("drawai")(host) } }
    @Test fun n16_endless() { aiKey(); shot("55-endless-intro") { game("endless")(host) } }
    @Test fun n17_court() { aiKey(); shot("56-court-intro") { game("court")(host) } }
    @Test fun n18_aiq() { aiKey(); shot("57-aiq-intro") { game("aiq")(host) } }
    @Test fun n19_oracle() { aiKey(); shot("58-oracle-intro") { game("oracle")(host) } }
    @Test fun n20_noai() = shot("59-ai-needs-key") { game("court")(host) }
    @Test fun n21_dual() = shot("60-dual", "Ξεκινάμε", "Πάμε", wait = 1500) { game("dual")(host) }
    @Test fun n22_ring() = shot("61-ring", "Ξεκινάμε", "Πάμε", wait = 1500) { game("ring")(host) }
    @Test fun n23_laser() = shot("62-laser", "Ξεκινάμε", "Πάμε", wait = 1500) { game("laser")(host) }
    @Test fun n24_spiral() = shot("63-spiral", "Ξεκινάμε", "Πάμε", wait = 1500) { game("spiral")(host) }
    @Test fun n25_drone() = shot("64-drone", "Ξεκινάμε", "Απογείωση", wait = 800) { game("drone")(host) }
    @Test fun n26_defuse() = shot("65-defuse-intro") { game("defuse")(host) }
    @Test fun n27_icequiz() = shot("66-icequiz", "Ξεκινάμε", "Πάμε", wait = 800) { game("icequiz")(host) }
    @Test fun n28_safe() = shot("67-safe", "Ξεκινάμε", "Ανοίγουμε", wait = 800) { game("safe")(host) }
    @Test fun n29_settings_packs() = shot("68-settings-packs") { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { gr.wsol.gamenight.ui.PacksPanel() } }
    @Test fun n30_history() {
        gr.wsol.gamenight.core.History.clear()
        listOf(mapOf("Μαρία" to 9, "Γιάννης" to 7), mapOf("Μαρία" to 5, "Νίκος" to 8), mapOf("Μαρία" to 11, "Ελένη" to 6)).forEach { gr.wsol.gamenight.core.History.add(Store.night.copy(points = it)) }
        shot("69-history") { gr.wsol.gamenight.ui.HistoryScreen() }
    }
    @Test fun n31_summer() { Store.settings = Store.settings.copy(look = 1); shot("70-summer") { HomeScreen() } }
    @Test fun n32_christmas() { Store.settings = Store.settings.copy(look = 2); shot("71-christmas", "Ξεκινάμε") { game("whois")(host) } }
    @Test fun n32b_christmas_home() { Store.settings = Store.settings.copy(look = 2); shot("71-christmas-home") { HomeScreen() } }

    @org.robolectric.annotation.Config(qualifiers = "w540dp-h1450dp-xhdpi")
    @Test fun s90_icons() {
        val allGames = gr.wsol.gamenight.games.Games.all
        val allCats = gr.wsol.gamenight.games.Category.entries
        compose.setContent {
            GamenightTheme {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(gr.wsol.gamenight.ui.C.bg).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allGames.size) { i ->
                        val g = allGames[i]
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = androidx.compose.ui.Modifier.padding(2.dp)
                        ) {
                            if (g.icon != 0) {
                                androidx.compose.material3.Icon(
                                    painter = androidx.compose.ui.res.painterResource(g.icon),
                                    contentDescription = null,
                                    tint = g.color,
                                    modifier = androidx.compose.ui.Modifier.size(44.dp)
                                )
                                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(2.dp))
                                androidx.compose.material3.Icon(
                                    painter = androidx.compose.ui.res.painterResource(g.icon),
                                    contentDescription = null,
                                    tint = g.color,
                                    modifier = androidx.compose.ui.Modifier.size(22.dp)
                                )
                            }
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(2.dp))
                            androidx.compose.material3.Text(
                                text = g.title,
                                style = gr.wsol.gamenight.ui.Type.small.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                color = gr.wsol.gamenight.ui.C.text,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                minLines = 2
                            )
                        }
                    }
                    items(allCats.size) { i ->
                        val c = allCats[i]
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = androidx.compose.ui.Modifier.padding(2.dp)
                        ) {
                            if (c.icon != 0) {
                                androidx.compose.material3.Icon(
                                    painter = androidx.compose.ui.res.painterResource(c.icon),
                                    contentDescription = null,
                                    tint = gr.wsol.gamenight.ui.C.amber,
                                    modifier = androidx.compose.ui.Modifier.size(44.dp)
                                )
                                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(2.dp))
                                androidx.compose.material3.Icon(
                                    painter = androidx.compose.ui.res.painterResource(c.icon),
                                    contentDescription = null,
                                    tint = gr.wsol.gamenight.ui.C.amber,
                                    modifier = androidx.compose.ui.Modifier.size(22.dp)
                                )
                            }
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(2.dp))
                            androidx.compose.material3.Text(
                                text = "[${c.title}]",
                                style = gr.wsol.gamenight.ui.Type.small.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                color = gr.wsol.gamenight.ui.C.amber,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                minLines = 2
                            )
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/90-icons.png")
    }

    @Test fun s91_font() {
        compose.setContent {
            GamenightTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.Text("Display Font: Manrope", style = gr.wsol.gamenight.ui.Type.title, color = gr.wsol.gamenight.ui.C.amber)
                    val glyphs = "ΆάΈέΉήΊίΌόΎύΏώΪϊΫϋΐΰ ς 0123456789 «» ;"
                    val sample = "Μάντεψε την ταινία ή σειρά"

                    androidx.compose.material3.Text("Bold (700):", color = gr.wsol.gamenight.ui.C.dim, style = gr.wsol.gamenight.ui.Type.small)
                    androidx.compose.material3.Text(glyphs, fontFamily = gr.wsol.gamenight.ui.DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp, color = gr.wsol.gamenight.ui.C.text)
                    androidx.compose.material3.Text(sample, fontFamily = gr.wsol.gamenight.ui.DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 20.sp, color = gr.wsol.gamenight.ui.C.text)

                    androidx.compose.material3.Text("ExtraBold (800):", color = gr.wsol.gamenight.ui.C.dim, style = gr.wsol.gamenight.ui.Type.small)
                    androidx.compose.material3.Text(glyphs, fontFamily = gr.wsol.gamenight.ui.DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 16.sp, color = gr.wsol.gamenight.ui.C.text)
                    androidx.compose.material3.Text(sample, fontFamily = gr.wsol.gamenight.ui.DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 22.sp, color = gr.wsol.gamenight.ui.C.amber)

                    androidx.compose.material3.Text("Black (900):", color = gr.wsol.gamenight.ui.C.dim, style = gr.wsol.gamenight.ui.Type.small)
                    androidx.compose.material3.Text(glyphs, fontFamily = gr.wsol.gamenight.ui.DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 16.sp, color = gr.wsol.gamenight.ui.C.text)
                    androidx.compose.material3.Text(sample, fontFamily = gr.wsol.gamenight.ui.DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 24.sp, color = gr.wsol.gamenight.ui.C.pink)
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/91-font.png")
    }

    @Test fun s92_witness_sheet() {
        val rnd = kotlin.random.Random(101)
        val scenes = (0 until 12).map { gr.wsol.gamenight.games.WitnessScenes.make(rnd) }
        compose.setContent {
            GamenightTheme {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = androidx.compose.ui.Modifier.fillMaxSize().background(gr.wsol.gamenight.ui.C.bg).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scenes.size) { idx ->
                        val s = scenes[idx]
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            gr.wsol.gamenight.games.WitnessScene(s, androidx.compose.ui.Modifier.fillMaxWidth().height(140.dp))
                            val placeName = when (s.place) { 1 -> "Beach"; 2 -> "Park"; else -> "Street" }
                            val dn = if (s.night) "Night" else "Day"
                            val info = "#${idx + 1}: $placeName · $dn\nCats:${s.cats.size} Birds:${s.birds} Bal:${s.balloons}(${s.balloonHue.el})\nLit:${s.litWindows}/4 Clk:${s.clock} Tr:${s.trees}"
                            androidx.compose.material3.Text(
                                text = info,
                                style = gr.wsol.gamenight.ui.Type.small,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                color = gr.wsol.gamenight.ui.C.muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = androidx.compose.ui.Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/92-witness-sheet.png")
    }
    @Test fun n33_en_spy() { english(); shot("en-05-spy-intro") { game("spy")(host) } }
    @Test fun n34_en_tavli() { english(); shot("en-06-tavli", "Let's go", "Roll") { game("tavli")(host) } }
    @Test fun n35_en_closest() { english(); shot("en-07-closest", "Let's go") { game("closest")(host) } }
    @Test fun n36_en_court() { english(); aiKey(); shot("en-08-court-intro") { game("court")(host) } }

    private fun english() { Store.settings = Store.settings.copy(english = true) }
    @Test fun e01_home() { english(); shot("en-01-home") { HomeScreen() } }
    @Test fun e02_taboo() { english(); shot("en-02-taboo", "Let's go", "Start the clock") { game("taboo")(host) } }
    @Test fun e03_house() { english(); shot("en-03-house", "Let's go") { game("house")(host) } }
    @Test fun e04_settings() { english(); shot("en-04-settings") { SettingsScreen() } }

    @Test fun s93_pass_to() {
        val p = Player("Γιάννης", Palette.players[0], avatar = 1)
        Store.night = Store.night.copy(players = listOf(p))
        shot("93-pass-to") {
            PassTo(
                name = "Γιάννης",
                color = androidx.compose.ui.graphics.Color(Palette.players[0]),
                note = "Μυστικός ρόλος. Μην κοιτάς!"
            ) {}
        }
    }

    @Test fun s94_icon_sheet() {
        compose.setContent {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xFF0B0A12))
                    .padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "Gamenight GR — Adaptive & Monochrome Icon",
                    fontFamily = gr.wsol.gamenight.ui.DisplayFont,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 15.sp,
                    color = gr.wsol.gamenight.ui.C.text
                )
                // 192dp adaptive
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(140.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF0B0A12))
                        ) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, androidx.compose.ui.Modifier.fillMaxSize())
                        }
                        androidx.compose.material3.Text("Adaptive (140dp)", fontSize = 11.sp, color = gr.wsol.gamenight.ui.C.muted)
                    }
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(140.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF232A3B))
                        ) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_monochrome), null, androidx.compose.ui.Modifier.fillMaxSize())
                        }
                        androidx.compose.material3.Text("Themed / Mono", fontSize = 11.sp, color = gr.wsol.gamenight.ui.C.muted)
                    }
                }
                // 96dp and 48dp sizes
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(80.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF0B0A12))
                        ) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, androidx.compose.ui.Modifier.fillMaxSize())
                        }
                        androidx.compose.material3.Text("96dp", fontSize = 11.sp, color = gr.wsol.gamenight.ui.C.muted)
                    }
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF0B0A12))
                        ) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, androidx.compose.ui.Modifier.fillMaxSize())
                        }
                        androidx.compose.material3.Text("48dp", fontSize = 11.sp, color = gr.wsol.gamenight.ui.C.muted)
                    }
                    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF232A3B))
                        ) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_monochrome), null, androidx.compose.ui.Modifier.fillMaxSize())
                        }
                        androidx.compose.material3.Text("48dp Mono", fontSize = 11.sp, color = gr.wsol.gamenight.ui.C.muted)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/94-icon-sheet.png")
    }

    @Test fun logo() {
        compose.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(androidx.compose.ui.unit.Dp(180f)).background(androidx.compose.ui.graphics.Color(0xFF0B0A12), androidx.compose.foundation.shape.RoundedCornerShape(androidx.compose.ui.unit.Dp(40f)))) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, androidx.compose.ui.Modifier.fillMaxSize())
            }
        }
        compose.onRoot().captureRoboImage("../docs/logo.png")
    }

    // -----------------------------------------------------------------------
    // Cinema look screenshots (96 - 104)
    // -----------------------------------------------------------------------
    private fun cinemaShot(name: String, vararg clicks: String, wait: Long = 1200, content: @Composable () -> Unit) {
        Store.settings = Store.settings.copy(look = 3)
        shot(name, *clicks, wait = wait, content = content)
    }

    @Test fun s96_cinema_home() = cinemaShot("96-cinema-home") { HomeScreen() }

    @Test fun s97_cinema_category() = cinemaShot("97-cinema-category") {
        HomeScreen()
    }

    @Test fun s98_cinema_intro_alien() = cinemaShot("98-cinema-intro-alien") {
        game("alien")(host)
    }

    @Test fun s99_cinema_pass() = cinemaShot("99-cinema-pass") {
        PassTo(names[0], androidx.compose.ui.graphics.Color(Palette.players[0])) {}
    }

    @Test fun s100_cinema_house_night() = cinemaShot("100-cinema-house-night") {
        gr.wsol.gamenight.games.HouseBackground(isNight = true) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                gr.wsol.gamenight.ui.Panel {
                    gr.wsol.gamenight.ui.Body("Όλοι μάτια κλειστά…")
                }
            }
        }
    }

    @Test fun s101_cinema_house_role() = cinemaShot("101-cinema-house-role") {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(gr.wsol.gamenight.ui.C.bg),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            gr.wsol.gamenight.games.GothicRoleCard(
                role = gr.wsol.gamenight.games.Role.Zombie,
                otherZombies = listOf("Μαρία")
            )
        }
    }

    @Test fun s102_cinema_house_back() = cinemaShot("102-cinema-house-back") {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(gr.wsol.gamenight.ui.C.bg),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            gr.wsol.gamenight.games.GothicRoleCardBack()
        }
    }

    @Test fun s103_cinema_chaos() = cinemaShot("103-cinema-chaos") {
        gr.wsol.gamenight.chaos.ChaosOverlay(
            event = gr.wsol.gamenight.chaos.Chaos.Event(
                gr.wsol.gamenight.chaos.Chaos.Kind.Gift,
                "🎁",
                "Δώρο από τον ουρανό!",
                "Μαρία: +1, γιατί είπε το καλύτερο αστείο.",
                "Μαρία",
                1
            )
        ) {}
    }

    @Test fun s104_cinema_score() = cinemaShot("104-cinema-score") {
        ScoreScreen()
    }
}
