package gr.wsol.gamenight.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Input
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Type

/** 1.1: small building blocks shared by the new games. */

/** The colour of a player (or their team). */
fun colorOf(name: String) = Store.night.let { Color(it.colorOf(it.sideOf(name))) }

/**
 * The phone goes round [players]; each one, alone, types an answer to [prompt]. [onDone] gets
 * player → text. [hint] is the grey text in the field.
 */
@Composable
fun SecretInputRound(players: List<String>, key: Any, prompt: (String) -> String, hint: String = t("Γράψε εδώ", "Type here"), onDone: (Map<String, String>) -> Unit) {
    var i by remember(key) { mutableIntStateOf(0) }
    var ready by remember(key) { mutableStateOf(false) }
    var text by remember(key, i) { mutableStateOf("") }
    val got = remember(key) { mutableStateMapOf<String, String>() }
    val who = players.getOrNull(i) ?: return
    if (!ready) { PassTo(who, colorOf(who), note = t("Γράφεις κρυφά. Μην κοιτάνε οι άλλοι.", "You write in secret. No peeking.")) { ready = true }; return }
    fun next() {
        got[who] = text.trim()
        if (i + 1 < players.size) { i++; ready = false } else onDone(got.toMap())
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(who, style = Type.h, color = colorOf(who))
        Text(prompt(who), style = Type.title, color = C.text)
        Input(text, { text = it }, hint) { if (text.isNotBlank()) next() }
        BigButton(t("Έτοιμο", "Done"), { next() }, enabled = text.isNotBlank())
    }
}

/** A secret vote where you may also pick yourself (e.g. «Ποιος από εμάς;»). */
@Composable
fun SecretPick(voters: List<String>, options: List<String>, key: Any, question: String, self: Boolean = true, onDone: (Map<String, String>) -> Unit) {
    var i by remember(key) { mutableIntStateOf(0) }
    var ready by remember(key) { mutableStateOf(false) }
    val votes = remember(key) { mutableStateMapOf<String, String>() }
    val who = voters.getOrNull(i) ?: return
    if (!ready) { PassTo(who, colorOf(who), note = t("Κρυφή ψηφοφορία.", "Secret vote.")) { ready = true }; return }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(who, style = Type.h, color = colorOf(who))
        Text(question, style = Type.title, color = C.text)
        options.filter { self || it != who }.forEach { c ->
            GhostButton(c, {
                votes[who] = c; Sfx.tap()
                if (i + 1 < voters.size) { i++; ready = false } else onDone(votes.toMap())
            }, Modifier.fillMaxWidth(), color = if (Store.night.names.contains(c)) colorOf(c) else C.text)
        }
    }
}

/** A big card in the middle of the screen with what to do, and the buttons under it. */
@Composable
fun CardScreen(top: String, topColor: Color, emoji: String, text: String, sub: String? = null, color: Color = C.amber, buttons: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(top, style = Type.h, color = topColor)
        Spacer(Modifier.height(8.dp))
        Text(emoji, fontSize = 56.sp)
        Text(text, style = Type.title, color = C.text, textAlign = TextAlign.Center)
        if (sub != null) Body(sub, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
        Spacer(Modifier.height(4.dp))
    }
}

/** «Το έκανε;» ✓ / ✗, for the group to judge a dare or a mime. */
@Composable
fun JudgeButtons(color: Color, yes: String = t("✓ Το έκανε", "✓ Done it"), no: String = t("✗ Δεν το έκανε", "✗ Didn't"), onJudge: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton(no, { Sfx.wrong(); onJudge(false) }, Modifier.weight(1f))
        BigButton(yes, { Sfx.correct(); onJudge(true) }, Modifier.weight(1f), color = color)
    }
}

/** Counts each choice in [votes] and gives the top ones (all of them on a tie). */
object Tally11 {
    fun counts(votes: Map<String, String>): List<Pair<String, Int>> = votes.values.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    fun tops(votes: Map<String, String>): Set<String> {
        val c = counts(votes); val max = c.firstOrNull()?.second ?: return emptySet()
        return c.filter { it.second == max }.map { it.first }.toSet()
    }
}
