package gr.wsol.gamenight.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Movie
import gr.wsol.gamenight.core.Prompt
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown

object Find10Rules {
    const val GOAL = 10
    /** 10 found: +2; 7 or more: +1. */
    fun points(found: Int) = when { found >= GOAL -> 2; found >= 7 -> 1; else -> 0 }
}

private enum class P { Intro, Pass, Play, Result, Over }

/* ---------------- 🔟 Βρες 10 ---------------- */
@Composable
fun Find10Game(host: GameHost) {
    val game = Games.byId("find10")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(P.Intro) }
    var each by remember { mutableIntStateOf(1) }
    var turn by remember { mutableIntStateOf(0) }
    var found by remember { mutableIntStateOf(0) }
    var timeUp by remember { mutableStateOf(false) }
    val deck = remember { Deck("find10", Cards.get(ctx).find10, { it.a }) }
    var card by remember { mutableStateOf<Prompt?>(null) }
    val tally = remember { Tally() }
    val total = Turns.count(n, each)
    val who = Turns.player(n, turn)

    when (phase) {
        P.Intro -> Intro(game, host, listOf(
            t("Ο παίκτης έχει 30″ να πει 10 πράγματα της κάρτας, π.χ. «10 νησιά».", "The player has 30″ to name 10 things on the card, e.g. «10 islands»."),
            t("Η παρέα μετράει πατώντας +1 για κάθε σωστό. Δεν μετράνε επαναλήψεις.", "The group counts with +1 for each right one. Repeats don't count."),
            t("10 σωστά: +2. Από 7 και πάνω: +1.", "10 right: +2. 7 or more: +1."),
        ), deck.size > 0, {
            Body(t("Γύροι για τον καθένα", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it })
        }) { turn = 0; phase = P.Pass }

        P.Pass -> { QuitGuard(host); PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Η παρέα μετράει, εσύ λες.", "The group counts, you talk."), button = t("Δείξε την κάρτα", "Show the card")) { card = deck.next(); found = 0; timeUp = false; phase = P.Play } }

        P.Play -> {
            QuitGuard(host)
            val left = rememberCountdown(30, running = !timeUp && found < Find10Rules.GOAL, key = turn) { timeUp = true; phase = P.Result }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
                Text(card?.t ?: "", style = Type.title, color = C.text, textAlign = TextAlign.Center)
                TimerText(left, color = C.lime)
                Text("$found / ${Find10Rules.GOAL}", style = Type.huge, color = C.text)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("−1", { if (found > 0) found-- }, Modifier.weight(1f))
                    BigButton("+1", {
                        found++; Sfx.correct(); Sfx.buzz(20)
                        if (found >= Find10Rules.GOAL) phase = P.Result
                    }, Modifier.weight(2f), color = C.lime, height = 96.dp)
                }
            }
        }

        P.Result -> {
            QuitGuard(host)
            val pts = Find10Rules.points(found)
            remember(turn) { tally.add(host, listOf(who), pts); host.log(t("Βρες 10: $who βρήκε $found", "Find 10: $who got $found")) }
            Page(if (found >= Find10Rules.GOAL) t("Τα βρήκε όλα! 🎉", "All ten! 🎉") else t("Τέλος χρόνου", "Time's up"), onBack = null, bottom = {
                BigButton(if (turn + 1 < total) t("Επόμενος", "Next") else t("Αποτελέσματα", "Results"), { if (turn + 1 < total) { turn++; phase = P.Pass } else phase = P.Over }, color = game.color)
            }) {
                Text("$who: $found", style = Type.big, color = C.lime)
                Body(if (pts > 0) "+$pts" else t("Χωρίς πόντο αυτή τη φορά.", "No point this time."), color = C.text)
            }
        }

        P.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = P.Intro }
    }
}

/* ---------------- 🔗 Σχετικές λέξεις ---------------- */
@Composable
fun RelatedGame(host: GameHost) {
    val game = Games.byId("related")!!
    val ctx = LocalContext.current
    val n = Store.night
    val order = remember { n.names }
    var phase by remember { mutableStateOf(P.Intro) }
    var rounds by remember { mutableIntStateOf(5) }
    var round by remember { mutableIntStateOf(0) }
    var holder by remember { mutableStateOf(order.random()) }
    var said by remember { mutableIntStateOf(0) }
    var loser by remember { mutableStateOf("") }
    val deck = remember { Deck("related", Cards.get(ctx).related, { it.a }) }
    var card by remember { mutableStateOf<Prompt?>(null) }
    val tally = remember { Tally() }

    fun lose() { loser = holder; Sfx.wrong(); Sfx.buzz(300, true); phase = P.Result }

    when (phase) {
        P.Intro -> Intro(game, host, listOf(
            t("Μια λέξη-αφετηρία για όλους. Με τη σειρά, ο καθένας λέει μια σχετική λέξη σε 5″.", "One starting word for everyone. In turn, each says a related word in 5″."),
            t("Πάτα «Το είπα» και δώσε το κινητό στον επόμενο.", "Press «Said it» and pass the phone on."),
            t("Όποιος κολλήσει ή πει κάτι που ειπώθηκε ήδη παίρνει −1.", "Whoever gets stuck or repeats a word gets −1."),
        ), deck.size > 0 && order.size >= 2, {
            Body(t("Γύροι", "Rounds"), color = C.text)
            Seg(listOf(3 to "3", 5 to "5", 8 to "8"), rounds, { rounds = it })
        }) { round = 0; card = deck.next(); said = 0; phase = P.Play }

        P.Play -> {
            QuitGuard(host)
            val left = rememberCountdown(5, running = true, key = said + round * 1000, tickLast = 3) { lose() }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(t("Γύρος ${round + 1} από $rounds", "Round ${round + 1} of $rounds"), style = Type.label, color = C.dim)
                Text(card?.t ?: "", style = Type.big, color = C.cyan, textAlign = TextAlign.Center)
                Text(t("$said λέξεις ως τώρα", "$said words so far"), style = Type.small, color = C.muted)
                TimerText(left, color = C.cyan)
                Text(holder, style = Type.big, color = Color(n.colorOf(n.sideOf(holder))))
                BigButton(t("✓ Το είπα · πάσα", "✓ Said it · pass"), {
                    said++; holder = BombRules.nextHolder(order, emptySet(), holder); Sfx.pass(); Sfx.buzz(15)
                }, color = C.cyan, height = 96.dp)
                GhostButton(t("✗ Κόλλησε ή επανάληψη", "✗ Stuck or a repeat"), { lose() }, Modifier.fillMaxWidth(), color = C.red)
            }
        }

        P.Result -> {
            QuitGuard(host)
            remember(round) { tally.add(host, listOf(loser), -1); host.log(t("Σχετικές λέξεις: κόλλησε ο/η $loser στο «${card?.t}»", "Related words: $loser got stuck on «${card?.t}»")) }
            Page(t("Κόλλησε!", "Stuck!"), onBack = null, bottom = {
                BigButton(if (round + 1 < rounds) t("Επόμενη λέξη", "Next word") else t("Αποτελέσματα", "Results"), {
                    if (round + 1 < rounds) { round++; card = deck.next(); said = 0; holder = loser; phase = P.Play } else phase = P.Over
                }, color = game.color)
            }) {
                Text(loser, style = Type.big, color = Color(n.colorOf(n.sideOf(loser))))
                Body(t("−1 πόντος. Η αλυσίδα είχε $said λέξεις.", "−1 point. The chain had $said words."), color = C.text)
            }
        }

        else -> GameOver(game, host, tally) { tally.bySide.clear(); phase = P.Intro }
    }
}

/* ---------------- 🎤 Λέξη σε τραγούδι ---------------- */
@Composable
fun SongGame(host: GameHost) {
    val game = Games.byId("song")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(P.Intro) }
    var each by remember { mutableIntStateOf(2) }
    var turn by remember { mutableIntStateOf(0) }
    var ok by remember { mutableStateOf<Boolean?>(null) }
    val deck = remember { Deck("song", Cards.get(ctx).song, { it.a }) }
    var card by remember { mutableStateOf<Prompt?>(null) }
    val tally = remember { Tally() }
    val total = Turns.count(n, each)
    val who = Turns.player(n, turn)

    when (phase) {
        P.Intro -> Intro(game, host, listOf(
            t("Η εφαρμογή δίνει μια λέξη. Έχεις 20″ να τραγουδήσεις ένα κομμάτι που την έχει μέσα.", "The app gives a word. You have 20″ to sing a song that has it."),
            t("Η παρέα κρίνει: ✓ +1, ✗ τίποτα.", "The group judges: ✓ +1, ✗ nothing."),
            t("Η εφαρμογή δεν έχει στίχους, μόνο λέξεις.", "The app has no lyrics, only words."),
        ), deck.size > 0, {
            Body(t("Γύροι για τον καθένα", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = C.pink)
        }) { turn = 0; card = deck.next(); ok = null; phase = P.Play }

        P.Play -> {
            QuitGuard(host)
            var running by remember(turn) { mutableStateOf(false) }
            val left = rememberCountdown(20, running = running && ok == null, key = turn) { if (ok == null) ok = false }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(t("Τραγουδάει", "Singing"), style = Type.label, color = C.dim)
                Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
                Text("🎤", fontSize = 56.sp)
                Text(card?.t ?: "", style = Type.big, color = C.pink, textAlign = TextAlign.Center)
                if (!running) BigButton(t("Ξεκίνα τα 20″", "Start the 20″"), { running = true }, color = C.pink)
                else TimerText(left, color = C.pink)
                if (running || ok != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(t("✗ Όχι", "✗ No"), {
                        ok = false; Sfx.wrong()
                    }, Modifier.weight(1f), color = C.red, enabled = ok == null)
                    BigButton(t("✓ Το βρήκε", "✓ Got it"), {
                        ok = true; Sfx.correct(); tally.add(host, listOf(who), 1)
                    }, Modifier.weight(1f), color = C.green, enabled = ok == null)
                }
                ok?.let {
                    Text(if (it) "+1 🎉" else t("Τίποτα αυτή τη φορά", "Nothing this time"), style = Type.h, color = if (it) C.green else C.muted)
                    BigButton(if (turn + 1 < total) t("Επόμενος: ${Turns.player(n, turn + 1)}", "Next: ${Turns.player(n, turn + 1)}") else t("Αποτελέσματα", "Results"), {
                        host.log(t("Λέξη σε τραγούδι: $who ${if (it) "✓" else "✗"} «${card?.t}»", "Word in a song: $who ${if (it) "✓" else "✗"} «${card?.t}»"))
                        if (turn + 1 < total) { turn++; card = deck.next(); ok = null } else phase = P.Over
                    }, color = game.color)
                }
            }
        }

        else -> GameOver(game, host, tally) { tally.bySide.clear(); phase = P.Intro }
    }
}

/* ---------------- 🎬 Μάντεψε την ταινία ή σειρά ---------------- */
@Composable
fun MoviesGame(host: GameHost) {
    val game = Games.byId("movies")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(P.Intro) }
    var count by remember { mutableIntStateOf(10) }
    var i by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("movies", Cards.get(ctx).movies, { it.a }) }
    var card by remember { mutableStateOf<Movie?>(null) }
    var reveal by remember { mutableStateOf<String?>(null) }
    val tally = remember { Tally() }

    when (phase) {
        P.Intro -> Intro(game, host, listOf(
            t("Ένας τίτλος ταινίας ή σειράς σε emoji. Όλοι κοιτάτε την οθόνη.", "A film or series title in emoji. Everyone looks at the screen."),
            t("Μετά από 15″ ανοίγει ένα στοιχείο-βοήθεια.", "After 15″ a hint opens."),
            t("Όποιος το πει πρώτος παίρνει +1: πατήστε το όνομά του.", "Whoever says it first gets +1: tap their name."),
        ), deck.size > 0, {
            Body(t("Πόσοι τίτλοι", "How many titles"), color = C.text)
            Seg(listOf(5 to "5", 10 to "10", 15 to "15"), count, { count = it })
        }) { i = 0; card = deck.next(); reveal = null; phase = P.Play }

        P.Play -> {
            QuitGuard(host)
            val c = card ?: run { phase = P.Over; return }
            var hint by remember(i) { mutableStateOf(false) }
            val left = rememberCountdown(15, running = !hint && reveal == null, key = i, tickLast = 0) { hint = true }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(t("${i + 1} από $count · ", "${i + 1} of $count · ") + (if (c.series) t("σειρά", "series") else t("ταινία", "film")) + (if (c.gr) t(" · ελληνική", " · Greek") else ""), style = Type.label, color = C.dim)
                Text(c.e, fontSize = 54.sp, textAlign = TextAlign.Center, lineHeight = 64.sp)
                if (reveal == null) {
                    if (hint) Panel { Text("💡 " + c.hint, style = Type.body, color = C.text) }
                    else Text(t("Βοήθεια σε ${(left + 999) / 1000}″", "Hint in ${(left + 999) / 1000}″"), style = Type.small, color = C.muted, modifier = Modifier.clickable(role = Role.Button) { hint = true }.padding(8.dp))
                    Text(t("Ποιος το βρήκε;", "Who got it?"), style = Type.small, color = C.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        n.names.forEach { p -> Chip(p, color = Color(n.colorOf(p)), onClick = { tally.add(host, listOf(p), 1); Sfx.correct(); reveal = p }) }
                    }
                    GhostButton(t("Κανείς · δείξε τον τίτλο", "Nobody · show the title"), { reveal = "" }, Modifier.fillMaxWidth())
                } else {
                    Text(c.title, style = Type.big, color = C.amber, textAlign = TextAlign.Center)
                    if (reveal!!.isNotEmpty()) Text("+1 ${reveal}", style = Type.h, color = C.green)
                    BigButton(if (i + 1 < count) t("Επόμενο", "Next") else t("Αποτελέσματα", "Results"), {
                        host.log(t("Ταινίες: «${c.title}»", "Films: «${c.title}»") + (reveal?.takeIf { it.isNotEmpty() }?.let { " ✓ $it" } ?: ""))
                        if (i + 1 < count) { i++; card = deck.next(); reveal = null } else phase = P.Over
                    }, color = game.color)
                }
            }
        }

        else -> GameOver(game, host, tally) { tally.bySide.clear(); phase = P.Intro }
    }
}
