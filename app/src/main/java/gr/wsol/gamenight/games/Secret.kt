package gr.wsol.gamenight.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.HoldToReveal
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Type

/**
 * The phone goes round [players]: each one, alone, holds a finger to see [secret] (their role,
 * word or number), then passes it on. [onDone] after the last one.
 */
@Composable
fun RevealRound(players: List<String>, key: Any = Unit, title: String = t("Δες κρυφά", "Look in secret"), secret: @Composable (String) -> Unit, onDone: () -> Unit) {
    var i by remember(key) { mutableIntStateOf(0) }
    var ready by remember(key) { mutableStateOf(false) }
    var seen by remember(key) { mutableStateOf(false) }
    val n = Store.night
    val who = players.getOrNull(i) ?: return
    if (!ready) {
        PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Μόνο εσύ πρέπει να βλέπεις την οθόνη.", "Only you should see the screen.")) { ready = true; seen = false }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
        Text(title, style = Type.body, color = C.muted)
        HoldToReveal(Modifier.weight(1f), onSeen = { seen = true }) { secret(who) }
        BigButton(if (i + 1 < players.size) t("Το είδα · επόμενος", "Seen it · next") else t("Το είδα · τέλος", "Seen it · done"), {
            if (i + 1 < players.size) { i++; ready = false } else onDone()
        }, enabled = seen)
    }
}

/**
 * A secret vote: the phone goes round [voters] and each one taps a name (never their own).
 * [onDone] gets voter → choice.
 */
@Composable
fun SecretVote(voters: List<String>, candidates: List<String>, key: Any = Unit, question: String = t("Ποιον ψηφίζεις;", "Who do you vote for?"), onDone: (Map<String, String>) -> Unit) {
    var i by remember(key) { mutableIntStateOf(0) }
    var ready by remember(key) { mutableStateOf(false) }
    val votes = remember(key) { mutableStateMapOf<String, String>() }
    val n = Store.night
    val who = voters.getOrNull(i) ?: return
    if (!ready) {
        PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Κρυφή ψηφοφορία.", "Secret vote.")) { ready = true }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
        Text(question, style = Type.title, color = C.text)
        candidates.filter { it != who }.forEach { c ->
            GhostButton(c, {
                votes[who] = c
                if (i + 1 < voters.size) { i++; ready = false } else onDone(votes.toMap())
            }, Modifier.fillMaxWidth(), color = Color(n.colorOf(n.sideOf(c))))
        }
    }
}

object Votes {
    /** The one with most votes, or null on a tie (nobody leaves). */
    fun top(votes: Map<String, String>): String? {
        val c = votes.values.groupingBy { it }.eachCount()
        val max = c.maxOfOrNull { it.value } ?: return null
        val tops = c.filter { it.value == max }.keys
        return tops.singleOrNull()
    }
}

/** A big centered message with a button. */
@Composable
fun Announce(emoji: String, title: String, body: String?, button: String, color: Color = C.amber, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(emoji, style = Type.huge)
        Spacer(Modifier.height(10.dp))
        Text(title, style = Type.big, color = color, textAlign = TextAlign.Center)
        if (body != null) { Spacer(Modifier.height(10.dp)); Body(body, align = TextAlign.Center, color = C.text) }
        Spacer(Modifier.height(26.dp))
        BigButton(button, onNext, color = color)
    }
}
