package gr.wsol.gamenight.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.Word
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.OnTilt
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown
import gr.wsol.gamenight.ui.sideways
import kotlinx.coroutines.delay

private enum class F { Intro, Pass, Ready, Play, Turn, Over }

@Composable
fun ForeheadGame(host: GameHost) {
    val game = Games.byId("forehead")!!
    val ctx = LocalContext.current
    val n = Store.night
    val all = remember { Cards.get(ctx).forehead }
    val cats = remember { all.filter { Store.settings.adult || !it.a }.map { it.c }.distinct() }
    var cat by remember { mutableStateOf<String?>(null) } // null: all categories
    var phase by remember { mutableStateOf(F.Intro) }
    var turn by remember { mutableIntStateOf(0) }
    var each by remember { mutableIntStateOf(1) }
    val tally = remember { Tally() }
    val results = remember { mutableStateListOf<Pair<String, Boolean>>() }
    var word by remember { mutableStateOf<Word?>(null) }
    var flash by remember { mutableStateOf<Boolean?>(null) }
    val deck = remember(cat) { Deck("forehead-${cat ?: "all"}", all.filter { cat == null || it.c == cat }, { it.a }) }
    val total = Turns.count(n, each)
    val who = Turns.player(n, turn)

    when (phase) {
        F.Intro -> Intro(game, host, listOf(
            t("Βάλε το κινητό στο μέτωπο, με την οθόνη προς την παρέα. Αυτοί βλέπουν τη λέξη και σου δίνουν στοιχεία ή κάνουν παντομίμα.", "Put the phone on your forehead, screen to the group. They see the word and give you clues or mime."),
            t("Γείρε το κινητό κάτω για «σωστό», πάνω για «πάσο». Ή πάτα δεξιά/αριστερά στην οθόνη.", "Tilt it down for «correct», up for «pass». Or tap right/left on the screen."),
            t("60″ ο γύρος. +1 για κάθε λέξη που βρίσκεις.", "60″ a turn. +1 for each word you get."),
        ), deck.size > 0, {
            Body(t("Κατηγορία", "Category"), color = C.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(t("Όλες", "All"), selected = cat == null, color = C.cyan, onClick = { cat = null })
                cats.forEach { c -> Chip(c, selected = cat == c, color = C.cyan, onClick = { cat = c }) }
            }
            Body(t("Γύροι για τον καθένα", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = C.cyan)
        }) { turn = 0; phase = F.Pass }

        F.Pass -> { QuitGuard(host); PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Μη δεις την οθόνη! Πάτα και βάλε αμέσως το κινητό στο μέτωπο.", "Don't look! Press and put the phone on your forehead at once."), button = t("Έτοιμος", "Ready")) { results.clear(); phase = F.Ready } }

        F.Ready -> {
            QuitGuard(host)
            var c by remember(turn) { mutableIntStateOf(3) }
            LaunchedEffect(turn) { while (c > 0) { Sfx.tick(); Sfx.buzz(40); delay(1000); c-- }; word = deck.next(); phase = F.Play }
            Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
                Text("$c", style = Type.huge, color = C.cyan, modifier = Modifier.rotate(90f))
            }
        }

        F.Play -> {
            QuitGuard(host)
            fun answer(ok: Boolean) {
                val w = word ?: return
                results.add(w.w to ok)
                if (ok) { tally.add(host, listOf(who), 1); Sfx.correct(); Sfx.buzz(60) } else { Sfx.pass(); Sfx.buzz(25) }
                flash = ok
                word = deck.next()
            }
            LaunchedEffect(flash) { if (flash != null) { delay(450); flash = null } }
            val left = rememberCountdown(60, running = true, key = turn, tickLast = 5) { phase = F.Turn }
            OnTilt(onDown = { answer(true) }, onUp = { answer(false) })
            val bg by animateColorAsState(when (flash) { true -> C.green; false -> C.amber; null -> C.bg }, label = "flash")
            Row(Modifier.fillMaxSize().background(bg)) {
                // Rotated for a phone held sideways on the forehead.
                Box(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { answer(false) }, contentAlignment = Alignment.Center) {
                    Text(t("ΠΑΣΟ", "PASS"), color = C.dim, fontWeight = FontWeight.Black, modifier = Modifier.rotate(90f))
                }
                Box(Modifier.weight(4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Column(Modifier.sideways(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(left + 999) / 1000}″", fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (flash != null) Color.Black else C.cyan)
                        Text(word?.w ?: "", fontSize = 60.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, color = if (flash != null) Color.Black else C.text, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(word?.c ?: "", fontSize = 16.sp, color = if (flash != null) Color.Black else C.muted)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { answer(true) }, contentAlignment = Alignment.Center) {
                    Text(t("ΣΩΣΤΟ", "CORRECT"), color = C.green, fontWeight = FontWeight.Black, modifier = Modifier.rotate(90f))
                }
            }
        }

        F.Turn -> {
            QuitGuard(host)
            val ok = results.count { it.second }
            remember(turn) { host.log(t("Στο μέτωπο: $who βρήκε $ok", "Forehead: $who got $ok")) }
            Page(t("Τέλος χρόνου", "Time's up"), onBack = null, bottom = {
                BigButton(if (turn + 1 < total) t("Επόμενος: ${Turns.player(n, turn + 1)}", "Next: ${Turns.player(n, turn + 1)}") else t("Αποτελέσματα", "Results"), {
                    if (turn + 1 < total) { turn++; phase = F.Pass } else phase = F.Over
                }, color = game.color)
            }) {
                Text("$who: $ok ✓", style = Type.big, color = C.cyan)
                results.forEach { (w, r) -> Text((if (r) "✓  " else "→  ") + w, style = Type.body, color = if (r) C.green else C.dim) }
            }
        }

        F.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = F.Intro }
    }
}
