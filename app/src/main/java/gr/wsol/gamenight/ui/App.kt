package gr.wsol.gamenight.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import gr.wsol.gamenight.BuildConfig
import gr.wsol.gamenight.chaos.Chaos
import gr.wsol.gamenight.chaos.ChaosOverlay
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.extras.Crash
import gr.wsol.gamenight.extras.Report
import gr.wsol.gamenight.games.GameHost
import gr.wsol.gamenight.games.Games
import gr.wsol.gamenight.update.Updates

sealed interface Screen {
    data object Home : Screen
    data object Players : Screen
    data object Score : Screen
    data object Settings : Screen
    data object Report : Screen
    data object History : Screen
    data class Play(val id: String) : Screen
}

/** A simple stack of screens (no navigation library). */
object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

/** The update found on GitHub, if any (shown as a popup on the home screen). */
object UpdateState {
    var release by mutableStateOf<Updates.Release?>(null)
    /** Goes up every time the app comes to the front: the check runs then too, not only at a cold start. */
    var resumes by mutableStateOf(0)
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(Crash.last(ctx)) }

    LaunchedEffect(UpdateState.resumes) {
        val s = Store.settings
        if (UpdateState.release == null && BuildConfig.UPDATE_CHECK && s.updateCheck && System.currentTimeMillis() - Store.lastUpdateCheck > Updates.EVERY_MS) {
            Store.lastUpdateCheck = System.currentTimeMillis()
            UpdateState.release = Updates.check(BuildConfig.VERSION_NAME)?.takeIf { it.version != s.skipVersion }
        }
    }

    BackHandler(Nav.stack.size > 1) { Nav.back() }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (val s = Nav.top) {
            Screen.Home -> HomeScreen()
            Screen.Players -> PlayersScreen()
            Screen.Score -> ScoreScreen()
            Screen.Settings -> SettingsScreen()
            Screen.Report -> ReportScreen()
            Screen.History -> HistoryScreen()
            is Screen.Play -> {
                val g = Games.byId(s.id)
                if (g?.play == null) Nav.back() else {
                    LaunchedEffect(s.id) { Report.game = s.id; Store.lastGame = s.id }
                    val host = remember(s.id) {
                        object : GameHost {
                            override fun award(who: Collection<String>, pts: Int) { Store.night = Store.night.award(who, pts) }
                            override fun log(note: String) { Store.night = Store.night.note(s.id, note); Chaos.maybe(ctx); gr.wsol.gamenight.ai.Havales.maybe(ctx) }
                            override fun exit() { Report.game = null; Nav.back() }
                        }
                    }
                    g.play.invoke(host)
                }
            }
        }
        Chaos.pending?.let { e ->
            val home = Nav.top !is Screen.Play
            if (home || Chaos.canShow) ChaosOverlay(e, fromHome = home) { Chaos.pending = null }
        }
        crash?.let { CrashDialog(it) { Crash.clear(ctx); crash = null } }
    }
}
