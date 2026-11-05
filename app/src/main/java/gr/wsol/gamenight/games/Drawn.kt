package gr.wsol.gamenight.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Word
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.DrawPad
import gr.wsol.gamenight.ui.Drawing
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Input
import gr.wsol.gamenight.ui.Line
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.inkOf
import gr.wsol.gamenight.ui.rememberCountdown
import kotlin.random.Random

/** «Ένας λείπει»: the faker and the drawing order. Pure logic. */
object FakeRules {
    fun faker(players: List<String>, rnd: Random = Random) = players.random(rnd)

    /** Drawing order: the faker never draws first. */
    fun order(players: List<String>, faker: String, rnd: Random = Random): List<String> {
        val o = players.shuffled(rnd).toMutableList()
        if (o.first() == faker && o.size > 1) { o.removeAt(0); o.add(1, faker) }
        return o
    }

    /** Caught and wrong guess: +1 to everyone else. Not caught, or caught but guessed: +3 to the faker. */
    fun points(players: List<String>, faker: String, caught: Boolean, guessed: Boolean): Map<String, Int> =
        if (caught && !guessed) players.filter { it != faker }.associateWith { 1 } else mapOf(faker to 3)
}

private enum class FK { Intro, Reveal, Draw, Vote, Guess, Over }

@Composable
fun FakeArtistGame(host: GameHost) {
    val game = Games.byId("fake")!!
    val ctx = LocalContext.current
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(FK.Intro) }
    val deck = remember { Deck("draw-fake", Cards.get(ctx).draw, { it.a }) }
    var word by remember { mutableStateOf<Word?>(null) }
    var faker by remember { mutableStateOf("") }
    var order by remember { mutableStateOf(listOf<String>()) }
    var gameNo by remember { mutableIntStateOf(0) }
    val lines = remember { mutableStateListOf<Line>() }
    var caught by remember { mutableStateOf(false) }
    var fakerWon by remember { mutableStateOf(false) }
    val tally = remember { Tally() }

    when (phase) {
        FK.Intro -> Intro(game, host, listOf(
            t("Όλοι βλέπουν κρυφά ένα θέμα και μια λέξη, π.χ. «Ζώα: χελώνα». Ένας βλέπει μόνο την κατηγορία: είναι ο απατεώνας.", "Everyone secretly sees a topic and a word, e.g. «Animals: turtle». One sees only the topic: the faker."),
            t("Με τη σειρά, ο καθένας κάνει μία γραμμή στον κοινό πίνακα, σε 3″, με το χρώμα του. Δύο γύροι.", "In turn, each draws one line on the shared pad, in 3″, in their colour. Two rounds."),
            t("Μετά δείχνετε όλοι μαζί ποιος είναι ο απατεώνας. Αν τον πιάσετε, έχει μία ευκαιρία να μαντέψει τη λέξη.", "Then everyone points at the faker together. If caught, they get one guess at the word."),
            t("Πιάστηκε και δεν τη βρήκε: +1 σε όλους τους άλλους. Αλλιώς +3 στον απατεώνα.", "Caught and wrong: +1 to everyone else. Otherwise +3 to the faker."),
        ), deck.size > 0 && players.size >= 3) {
            word = deck.next(); faker = FakeRules.faker(players); order = FakeRules.order(players, faker); lines.clear(); gameNo++; phase = FK.Reveal
        }

        FK.Reveal -> { QuitGuard(host); RevealRound(players, key = gameNo, title = t("Τι ζωγραφίζουμε", "What we draw"), secret = { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(word!!.c, style = Type.h, color = C.muted)
                if (p == faker) {
                    Text("???", style = Type.huge, color = C.pink)
                    Text(t("Είσαι ο απατεώνας", "You are the faker"), style = Type.title, color = C.pink, textAlign = TextAlign.Center)
                    Body(t("Ζωγράφισε σαν να ξέρεις.", "Draw as if you knew."), align = TextAlign.Center)
                } else Text(word!!.w, style = Type.big, color = C.pink, textAlign = TextAlign.Center)
            }
        }) { phase = FK.Draw } }

        FK.Draw -> {
            QuitGuard(host)
            var turn by remember(gameNo) { mutableIntStateOf(0) }
            var drawing by remember(gameNo, turn) { mutableStateOf(false) }
            var done by remember(gameNo, turn) { mutableStateOf(false) }
            val who = order[turn % order.size]
            val round = turn / order.size + 1
            val color = inkOf(n.players.first { it.name == who }.color)
            val left = rememberCountdown(3, running = drawing && !done, key = "$gameNo-$turn", tickLast = 3) { done = true }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Γύρος $round από 2 · θέμα: ${word!!.c}", "Round $round of 2 · topic: ${word!!.c}"), style = Type.label, color = C.dim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(who, style = Type.title, color = Color(n.colorOf(n.sideOf(who))), modifier = Modifier.weight(1f))
                    Text(if (drawing && !done) "${(left + 999) / 1000}″" else "", style = Type.title, color = C.pink)
                }
                DrawPad(lines, color, enabled = !done, onStart = { drawing = true }) { l -> lines.add(l); done = true; Sfx.tap() }
                Body(if (done) t("Έγινε. Δώσε το κινητό στον επόμενο.", "Done. Pass the phone on.") else t("Μία γραμμή, χωρίς να σηκώσεις το δάχτυλο. Ο χρόνος ξεκινά με το άγγιγμα.", "One line, without lifting your finger. The clock starts at your touch."))
                if (done) BigButton(if (turn + 1 < order.size * 2) t("Επόμενος: ${order[(turn + 1) % order.size]}", "Next: ${order[(turn + 1) % order.size]}") else t("Ψηφοφορία", "Vote"), {
                    if (turn + 1 < order.size * 2) turn++ else phase = FK.Vote
                }, color = C.pink)
            }
        }

        FK.Vote -> { QuitGuard(host); Page(t("Ποιος είναι ο απατεώνας;", "Who is the faker?"), onBack = null) {
            Body(t("Μετρήστε ως το τρία και δείξτε όλοι μαζί. Πατήστε αυτόν με τις περισσότερες ψήφους.", "Count to three and all point together. Tap the one with most votes."), color = C.text)
            DrawPad(lines, 0, enabled = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                players.forEach { p -> Chip(p, color = Color(n.colorOf(p)), onClick = { caught = p == faker; if (caught) phase = FK.Guess else { fakerWon = true; finishFake(host, tally, players, faker, false, false, word!!); phase = FK.Over } }) }
            }
        } }

        FK.Guess -> { QuitGuard(host); Page(t("Πιάσατε τον απατεώνα!", "You caught the faker!"), onBack = null) {
            Text("🎨 $faker", style = Type.big, color = C.pink)
            Body(t("Έχει μία ευκαιρία: να πει δυνατά τη λέξη.", "One chance: say the word out loud."), color = C.text)
            var shown by remember { mutableStateOf(false) }
            if (!shown) GhostButton(t("Το είπε · δείξε τη λέξη", "Said it · show the word"), { shown = true }, Modifier.fillMaxWidth())
            else {
                Panel { Text(word!!.w, style = Type.big, color = C.amber, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(t("✓ Τη βρήκε", "✓ Got it"), { fakerWon = true; finishFake(host, tally, players, faker, true, true, word!!); phase = FK.Over }, Modifier.weight(1f), color = C.green)
                    GhostButton(t("✗ Όχι", "✗ No"), { fakerWon = false; finishFake(host, tally, players, faker, true, false, word!!); phase = FK.Over }, Modifier.weight(1f), color = C.red)
                }
            }
        } }

        FK.Over -> {
            remember(gameNo) { gr.wsol.gamenight.chaos.Moments.last = "«${word!!.w}»" to lines.toList(); 0 }
            GameOver(game, host, tally, headline = if (fakerWon) t("Κέρδισε ο απατεώνας!", "The faker wins!") else t("Τον πιάσατε!", "You caught them!"), extra = {
            Text(t("Ήταν: «${word!!.w}» · απατεώνας: $faker", "It was: «${word!!.w}» · faker: $faker"), style = Type.body, color = C.text)
            DrawPad(lines, 0, enabled = false)
            GhostButton(t("Κοινοποίηση του σχεδίου", "Share the drawing"), { Drawing.share(ctx, lines.toList(), "Gamenight GR · ${word!!.w}") }, Modifier.fillMaxWidth())
        }) { tally.bySide.clear(); phase = FK.Intro }
        }
    }
}

private fun finishFake(host: GameHost, tally: Tally, players: List<String>, faker: String, caught: Boolean, guessed: Boolean, word: Word) {
    FakeRules.points(players, faker, caught, guessed).forEach { (p, v) -> tally.add(host, listOf(p), v) }
    if (!caught || guessed) host.stat(faker, "sneaky")
    host.log(t("Ένας λείπει: «${word.w}», απατεώνας $faker", "One is missing: «${word.w}», faker $faker"))
    Sfx.win()
}

/** «Χαλασμένο τηλέφωνο»: who does what at each step. Pure logic. */
object TelephoneRules {
    /** Step 0 writes, then draw, write, draw… one step per player. */
    fun drawsAt(step: Int) = step % 2 == 1
}

private sealed interface Page2 { data class Text(val who: String, val s: String) : Page2; data class Pic(val who: String, val lines: List<Line>) : Page2 }
private enum class TP { Intro, Step, Show, Over }

@Composable
fun TelephoneGame(host: GameHost) {
    val game = Games.byId("telephone")!!
    val ctx = LocalContext.current
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(TP.Intro) }
    var drawSec by remember { mutableIntStateOf(15) }
    val deck = remember { Deck("phrases", Cards.get(ctx).phrases, { it.a }) }
    val pages = remember { mutableStateListOf<Page2>() }
    var step by remember { mutableIntStateOf(0) }
    var order by remember { mutableStateOf(listOf<String>()) }
    val tally = remember { Tally() }

    when (phase) {
        TP.Intro -> Intro(game, host, listOf(
            t("Ο πρώτος γράφει μια μυστική φράση, ή παίρνει μία έτοιμη από την εφαρμογή.", "The first writes a secret phrase, or takes a ready one from the app."),
            t("Ο επόμενος βλέπει μόνο τη φράση και τη ζωγραφίζει. Ο επόμενος βλέπει μόνο το σχέδιο και γράφει τι λέει.", "The next sees only the phrase and draws it. The next sees only the drawing and writes what it says."),
            t("Κείμενο, σχέδιο, κείμενο… μέχρι να περάσει από όλους. Στο τέλος βλέπετε όλη την παρανόηση!", "Text, drawing, text… until everyone has had a turn. At the end you see the whole mix-up!"),
            t("Χωρίς πόντους, μόνο γέλιο. Αν θέλετε, ψηφίζετε το καλύτερο σχέδιο για +1.", "No points, just laughs. If you like, vote the best drawing for +1."),
        ), players.size >= 3, {
            Body(t("Χρόνος για κάθε σχέδιο", "Time for each drawing"), color = C.text)
            Seg(listOf(15 to "15″", 30 to "30″", 45 to "45″"), drawSec, { drawSec = it }, color = C.lime)
        }) { pages.clear(); step = 0; order = players.shuffled(); phase = TP.Step }

        TP.Step -> {
            QuitGuard(host)
            val who = order[step]
            var ready by remember(step) { mutableStateOf(false) }
            if (!ready) { PassTo(who, Color(n.colorOf(n.sideOf(who))), note = if (step == 0) t("Εσύ ξεκινάς τη φράση.", "You start the phrase.") else t("Μην δείξεις σε κανέναν.", "Don't show anyone.")) { ready = true }; return }
            val prev = pages.lastOrNull()
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
                if (TelephoneRules.drawsAt(step)) {
                    val color = inkOf(n.players.first { it.name == who }.color)
                    val lines = remember(step) { mutableStateListOf<Line>() }
                    var started by remember(step) { mutableStateOf(false) }
                    var timeUp by remember(step) { mutableStateOf(false) }
                    val left = rememberCountdown(drawSec, running = started && !timeUp, key = step, tickLast = 3) { timeUp = true }
                    Text(t("Ζωγράφισε:", "Draw:"), style = Type.small, color = C.muted)
                    Text((prev as? Page2.Text)?.s ?: "", style = Type.title, color = C.text)
                    Text(if (started) "${(left + 999) / 1000}″" else t("Ο χρόνος ξεκινά με την πρώτη γραμμή", "The clock starts with the first line"), style = Type.small, color = C.lime)
                    DrawPad(lines, color, enabled = !timeUp, onStart = { started = true }) { lines.add(it) }
                    BigButton(t("Έτοιμο", "Done"), { pages.add(Page2.Pic(who, lines.toList())); if (step + 1 < order.size) step++ else phase = TP.Show }, color = C.lime, enabled = lines.isNotEmpty())
                } else {
                    var text by remember(step) { mutableStateOf("") }
                    if (step == 0) {
                        Text(t("Γράψε μια φράση για να τη ζωγραφίσει ο επόμενος", "Write a phrase for the next one to draw"), style = Type.body, color = C.text)
                        GhostButton(t("🎲 Δώσε μου μία έτοιμη", "🎲 Give me a ready one"), { text = deck.next()?.t ?: "" }, Modifier.fillMaxWidth())
                    } else {
                        Text(t("Τι λέει αυτό το σχέδιο;", "What does this drawing say?"), style = Type.body, color = C.text)
                        DrawPad((prev as? Page2.Pic)?.lines ?: emptyList(), 0, enabled = false)
                    }
                    Input(text, { text = it }, t("Μια φράση", "A phrase"))
                    BigButton(t("Έτοιμο", "Done"), { pages.add(Page2.Text(who, text.trim())); if (step + 1 < order.size) step++ else phase = TP.Show }, color = C.lime, enabled = text.isNotBlank())
                }
            }
        }

        TP.Show -> {
            QuitGuard(host)
            var i by remember { mutableIntStateOf(0) }
            var best by remember { mutableStateOf<String?>(null) }
            Page(t("Η παρανόηση, βήμα βήμα", "The mix-up, step by step"), onBack = null, bottom = {
                if (i + 1 < pages.size) BigButton(t("Επόμενο (${i + 2}/${pages.size})", "Next (${i + 2}/${pages.size})"), { i++; Sfx.tap() }, color = C.lime)
                else BigButton(t("Τέλος", "Finish"), {
                    best?.let { tally.add(host, listOf(it), 1) }
                    pages.filterIsInstance<Page2.Pic>().firstOrNull()?.let { gr.wsol.gamenight.chaos.Moments.last = "«${(pages.first() as Page2.Text).s}»" to it.lines }
                    host.log(t("Χαλασμένο τηλέφωνο: «${(pages.first() as Page2.Text).s}» → «${(pages.lastOrNull { it is Page2.Text } as? Page2.Text)?.s}»", "Broken telephone"))
                    phase = TP.Over
                }, color = C.lime)
            }) {
                pages.take(i + 1).forEach { p ->
                    when (p) {
                        is Page2.Text -> Panel { Text(p.who, style = Type.small, color = C.muted); Text("«${p.s}»", style = Type.title, color = C.text) }
                        is Page2.Pic -> Column { Text(p.who, style = Type.small, color = C.muted); DrawPad(p.lines, 0, enabled = false) }
                    }
                }
                if (i + 1 >= pages.size) {
                    Body(t("Το καλύτερο σχέδιο (+1, αν θέλετε):", "Best drawing (+1, if you like):"), color = C.text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pages.filterIsInstance<Page2.Pic>().map { it.who }.distinct().forEach { p -> Chip(p, selected = best == p, color = C.lime, onClick = { best = if (best == p) null else p }) }
                    }
                    pages.filterIsInstance<Page2.Pic>().forEach { pic ->
                        GhostButton(t("Κοινοποίηση σχεδίου: ${pic.who}", "Share drawing: ${pic.who}"), { Drawing.share(ctx, pic.lines, "Gamenight GR · Χαλασμένο τηλέφωνο") }, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        TP.Over -> GameOver(game, host, tally, headline = t("Τι γέλιο!", "What a laugh!")) { tally.bySide.clear(); phase = TP.Intro }
    }
}
