package gr.wsol.gamenight.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PointsLine
import gr.wsol.gamenight.ui.Type

/** The first screen of every game: what it is, how it's played, its options, and «Ξεκινάμε». */
@Composable
fun Intro(game: Game, host: GameHost, rules: List<String>, startEnabled: Boolean = true, options: (@Composable ColumnScope.() -> Unit)? = null, onStart: () -> Unit) {
    gr.wsol.gamenight.chaos.CalmScreen()
    Page("${game.emoji} ${game.title}", onBack = { host.exit() }, bottom = {
        BigButton(t("Ξεκινάμε", "Let's go"), onStart, color = game.color, enabled = startEnabled)
    }) {
        Text(game.short, style = Type.title, color = C.text)
        Panel {
            Label(t("ΠΩΣ ΠΑΙΖΕΤΑΙ", "HOW TO PLAY"))
            rules.forEach { Body("• $it", color = C.text) }
        }
        if (options != null) Panel {
            Label(t("ΕΠΙΛΟΓΕΣ", "OPTIONS"))
            options()
        }
    }
}

/**
 * Inside a game the back button asks first, so a bumped phone doesn't end the round.
 * Returns the composable to show when asked (null otherwise).
 */
@Composable
fun QuitGuard(host: GameHost, enabled: Boolean = true) {
    var ask by remember { mutableStateOf(false) }
    BackHandler(enabled) { ask = true }
    if (ask) androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(t("Έξοδος από το παιχνίδι;", "Leave the game?"), color = C.text) },
        text = { Text(t("Οι πόντοι που δόθηκαν μένουν στον πίνακα.", "The points already given stay on the scoreboard."), color = C.muted) },
        confirmButton = { TextButton(onClick = { ask = false; host.exit() }) { Text(t("Έξοδος", "Leave"), color = C.amber) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(t("Συνέχεια", "Keep playing"), color = C.muted) } },
        containerColor = C.panel,
    )
}

/** Tracks the points a game gave, to show them at the end. */
class Tally {
    val bySide = mutableMapOf<String, Int>()
    fun add(host: GameHost, who: Collection<String>, pts: Int) {
        if (pts == 0 || who.isEmpty()) return
        host.award(who, pts)
        who.map { Store.night.sideOf(it) }.toSet().forEach { bySide[it] = (bySide[it] ?: 0) + pts }
    }
}

/** The end of a game: who got what, then «Ξανά» or back to the menu. */
@Composable
fun GameOver(game: Game, host: GameHost, tally: Tally, headline: String? = null, extra: (@Composable ColumnScope.() -> Unit)? = null, onAgain: () -> Unit) {
    gr.wsol.gamenight.chaos.CalmScreen()
    val n = Store.night
    val lines = tally.bySide.entries.sortedByDescending { it.value }
    Page("${game.emoji} ${game.title}", onBack = { host.exit() }, bottom = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(t("Ξανά", "Again"), onAgain, Modifier.weight(1f), color = game.color)
            GhostButton(t("Στο μενού", "Menu"), { host.exit() }, Modifier.weight(1f))
        }
    }) {
        Text("🏁", fontSize = 48.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(headline ?: t("Τέλος!", "The end!"), style = Type.big, color = game.color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        extra?.invoke(this)
        if (lines.isNotEmpty()) Panel {
            Label(t("ΠΟΝΤΟΙ ΑΠΟ ΑΥΤΟ ΤΟ ΠΑΙΧΝΙΔΙ", "POINTS FROM THIS GAME"))
            lines.forEach { (s, p) -> PointsLine(n, s, p) }
        }
        Panel {
            Label(t("ΣΥΝΟΛΟ ΒΡΑΔΙΑΣ", "TONIGHT'S TOTAL"))
            n.ranking().forEach { (s, p) ->
                Row { Text(n.label(s), style = Type.body, color = C.text, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Text("$p", style = Type.h, color = C.text) }
            }
        }
    }
}

/** Header of a turn: whose turn and how far into the game. */
@Composable
fun TurnHeader(who: String, color: androidx.compose.ui.graphics.Color, progress: String, role: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(progress, style = Type.label, color = C.dim)
        Text("$role: $who", style = Type.h, color = color)
    }
}
