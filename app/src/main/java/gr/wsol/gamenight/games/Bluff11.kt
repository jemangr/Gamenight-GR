package gr.wsol.gamenight.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import gr.wsol.gamenight.core.NumberQ
import gr.wsol.gamenight.core.Quiz
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Input
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import kotlin.math.abs

/* ======================= 📏 Το κοντινότερο ======================= */
object ClosestRules {
    /** «2.918», «2918», «2,5» → a number (Greek or English decimal marks). */
    fun parse(s: String): Double? {
        val c = s.trim().replace(" ", "").replace("’", "").replace("'", "")
        if (c.isEmpty()) return null
        val norm = when {
            c.count { it == ',' } == 1 && c.count { it == '.' } >= 1 -> c.replace(".", "").replace(',', '.')
            c.count { it == ',' } == 1 && c.substringAfter(',').length != 3 -> c.replace(',', '.')
            else -> c.replace(",", "").let { if (it.count { ch -> ch == '.' } > 1 || (it.contains('.') && it.substringAfter('.').length == 3 && it.substringBefore('.').length in 1..3)) it.replace(".", "") else it }
        }
        return norm.toDoubleOrNull()
    }

    /**
     * Who is closest to [answer] gets +1 and their bet (1–3); exactly right gets +2 more. Everyone
     * else who bet more than 1 loses 1.
     */
    fun points(answer: Double, guesses: Map<String, Double>, bets: Map<String, Int>): Map<String, Int> {
        if (guesses.isEmpty()) return emptyMap()
        val best = guesses.values.minOf { abs(it - answer) }
        return guesses.mapValues { (p, g) ->
            val bet = bets[p] ?: 1
            when {
                abs(g - answer) == best -> 1 + bet + (if (g == answer) 2 else 0)
                bet > 1 -> -1
                else -> 0
            }
        }
    }

    fun show(d: Double): String = if (d == Math.floor(d) && abs(d) < 1e15) "%,d".format(d.toLong()).replace(',', '.') else "%.2f".format(d).replace('.', ',')
}

private enum class Close11 { Intro, Question, Guess, Result, Over }

@Composable
fun ClosestGame(host: GameHost) {
    val game = Games.byId("closest")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(Close11.Intro) }
    var rounds by remember { mutableIntStateOf(8) }
    var round by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("closest", Cards.get(ctx).closest, { it.a }) }
    var card by remember { mutableStateOf<NumberQ?>(null) }
    var i by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var bet by remember { mutableIntStateOf(1) }
    val guesses = remember { mutableStateMapOf<String, Double>() }
    val bets = remember { mutableStateMapOf<String, Int>() }
    val tally = remember { Tally() }

    when (phase) {
        Close11.Intro -> Intro(game, host, listOf(
            t("Ερώτηση με αριθμό, π.χ. «Πόσο ψηλός είναι ο Όλυμπος;». Ο καθένας γράφει κρυφά τη μαντεψιά του.", "A question with a number, e.g. «How tall is Olympus?». Everyone writes a guess in secret."),
            t("Ποντάρεις και 1, 2 ή 3 πόντους στο πόσο σίγουρος είσαι.", "You also bet 1, 2 or 3 on how sure you are."),
            t("Ο πιο κοντινός παίρνει +1 και το ποντάρισμά του (+2 ακόμα αν το βρει ακριβώς). Όσοι πόνταραν 2 ή 3 και δεν βγήκαν πρώτοι χάνουν 1.", "The closest gets +1 plus their bet (+2 more if exact). Anyone else who bet 2 or 3 loses 1."),
        ), deck.size > 0, {
            Body(t("Ερωτήσεις", "Questions"), color = C.text)
            Seg(listOf(5 to "5", 8 to "8", 12 to "12"), rounds, { rounds = it }, color = game.color)
        }) { round = 0; card = deck.next(); phase = Close11.Question }

        Close11.Question -> { QuitGuard(host); CardScreen(t("Ερώτηση ${round + 1} / $rounds", "Question ${round + 1} / $rounds"), C.dim, "📏", card?.q ?: "", card?.u?.takeIf { it.isNotBlank() }?.let { t("Μονάδα: $it", "Unit: $it") }, game.color) {
            BigButton(t("Γράφουμε κρυφά", "Write in secret"), { guesses.clear(); bets.clear(); i = 0; ready = false; phase = Close11.Guess }, color = game.color)
        } }

        Close11.Guess -> {
            QuitGuard(host)
            val who = n.names.getOrNull(i) ?: return
            if (!ready) { PassTo(who, colorOf(who), note = t("Γράφεις κρυφά.", "You write in secret.")) { ready = true; text = ""; bet = 1 }; return }
            val value = ClosestRules.parse(text)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(who, style = Type.h, color = colorOf(who))
                Text(card?.q ?: "", style = Type.title, color = C.text)
                Input(text, { text = it.filter { ch -> ch.isDigit() || ch in ".,- " } }, card?.u?.ifBlank { null } ?: t("Αριθμός", "Number"))
                Label(t("ΠΟΝΤΑΡΙΣΜΑ", "BET"))
                Seg(listOf(1 to "1", 2 to "2", 3 to "3"), bet, { bet = it }, color = game.color)
                BigButton(t("Έτοιμο", "Done"), {
                    guesses[who] = value ?: return@BigButton; bets[who] = bet
                    if (i + 1 < n.names.size) { i++; ready = false } else phase = Close11.Result
                }, enabled = value != null, color = game.color)
            }
        }

        Close11.Result -> {
            QuitGuard(host)
            val q = card ?: return
            val pts = remember(round) { ClosestRules.points(q.n, guesses.toMap(), bets.toMap()).also { m ->
                tally.addAll(host, m)
                host.log(t("Το κοντινότερο: ${m.maxByOrNull { it.value }?.key}", "Closest wins: ${m.maxByOrNull { it.value }?.key}"))
            } }
            Page(q.q, onBack = null, bottom = {
                BigButton(if (round + 1 < rounds) t("Επόμενη", "Next") else t("Αποτελέσματα", "Results"), {
                    if (round + 1 < rounds) { round++; card = deck.next(); phase = Close11.Question } else phase = Close11.Over
                }, color = game.color)
            }) {
                Label(t("ΣΩΣΤΗ ΑΠΑΝΤΗΣΗ", "RIGHT ANSWER"))
                Text(ClosestRules.show(q.n) + (if (q.u.isNotBlank()) " ${q.u}" else ""), style = Type.big, color = game.color)
                Panel {
                    guesses.entries.sortedBy { abs(it.value - q.n) }.forEach { (p, g) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p, style = Type.body, color = colorOf(p), modifier = Modifier.weight(1f))
                            Text(ClosestRules.show(g) + "  ×${bets[p] ?: 1}", style = Type.body, color = C.muted)
                            Text("  " + (pts[p] ?: 0).let { if (it > 0) "+$it" else "$it" }, style = Type.h, color = if ((pts[p] ?: 0) >= 0) C.green else C.red)
                        }
                    }
                }
            }
        }

        Close11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Close11.Intro }
    }
}

/* ======================= 🛎️ Κουίζ γειτονιάς ======================= */
object QuizRules {
    const val MAX_SIDES = 4
    /** The four answers in a fixed shuffled order for this question. */
    fun options(q: Quiz, seed: Int): List<String> = (listOf(q.ok) + q.no).shuffled(kotlin.random.Random(seed))
}

private enum class Quiz11 { Intro, Play, Over }

@Composable
fun QuizGame(host: GameHost) {
    val game = Games.byId("quiz")!!
    val ctx = LocalContext.current
    val n = Store.night
    val sides = remember { n.playingSides() }
    var phase by remember { mutableStateOf(Quiz11.Intro) }
    var rounds by remember { mutableIntStateOf(10) }
    var round by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("quiz", Cards.get(ctx).quiz, { it.a }) }
    var card by remember { mutableStateOf<Quiz?>(null) }
    var seed by remember { mutableIntStateOf(0) }
    var buzzed by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(setOf<String>()) }
    var shown by remember { mutableStateOf<String?>(null) }
    val tally = remember { Tally() }

    fun next() {
        if (round + 1 < rounds) { round++; card = deck.next(); seed = kotlin.random.Random.nextInt(); buzzed = null; locked = emptySet(); shown = null } else phase = Quiz11.Over
    }

    when (phase) {
        Quiz11.Intro -> Intro(game, host, listOf(
            t("Βάλτε το κινητό στη μέση. Κάθε παίκτης (ή ομάδα) έχει τη γωνία του με ένα μεγάλο κουμπί.", "Put the phone in the middle. Each player (or team) gets a corner with a big button."),
            t("Βγαίνει ερώτηση με 4 απαντήσεις. Όποιος πατήσει πρώτος απαντάει.", "A question with 4 answers appears. Whoever buzzes first answers."),
            t("Σωστό +1. Λάθος −1, και οι άλλοι μπορούν να πατήσουν ξανά.", "Right +1. Wrong −1, and the others may buzz again."),
            t("Έως 4 γωνίες. Με περισσότερους παίκτες, παίξτε σε ομάδες.", "Up to 4 corners. With more players, play in teams."),
        ), deck.size > 0 && sides.size in 2..QuizRules.MAX_SIDES, {
            Body(t("Ερωτήσεις", "Questions"), color = C.text)
            Seg(listOf(8 to "8", 12 to "12", 20 to "20"), rounds, { rounds = it }, color = game.color)
            if (sides.size > QuizRules.MAX_SIDES) Body(t("Είστε ${sides.size}: φτιάξτε ομάδες από την αρχική για να παίξετε.", "You are ${sides.size}: make teams on the home screen to play."), color = C.red)
        }) { round = 0; card = deck.next(); seed = kotlin.random.Random.nextInt(); buzzed = null; locked = emptySet(); shown = null; phase = Quiz11.Play }

        Quiz11.Play -> {
            QuitGuard(host)
            val q = card ?: return
            val opts = remember(round, seed) { QuizRules.options(q, seed) }
            LaunchedEffect(shown) { if (shown != null) { kotlinx.coroutines.delay(1800); next() } }
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                val corners = listOf(Alignment.BottomStart, Alignment.TopEnd, Alignment.BottomEnd, Alignment.TopStart)
                sides.forEachIndexed { k, s ->
                    val top = corners[k] == Alignment.TopEnd || corners[k] == Alignment.TopStart
                    val c = Color(n.colorOf(s))
                    val can = buzzed == null && shown == null && s !in locked
                    Box(Modifier.align(corners[k]).padding(10.dp).fillMaxWidth(0.46f).heightIn(min = 96.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (buzzed == s) c else c.copy(alpha = if (can) 0.35f else 0.12f)).border(2.dp, c, RoundedCornerShape(22.dp))
                        // the buzzer fires when the finger touches it (not when it lifts): the fastest finger wins
                        .pointerInput(can) {
                            if (!can) return@pointerInput
                            awaitEachGesture { awaitFirstDown(requireUnconsumed = false); if (buzzed == null && shown == null) { buzzed = s; Sfx.buzz(80, true); Sfx.tap() } }
                        }
                        .clickable(enabled = can, role = Role.Button) { if (buzzed == null) { buzzed = s; Sfx.buzz(80, true); Sfx.tap() } }.rotate(if (top) 180f else 0f),
                        contentAlignment = Alignment.Center) {
                        Text(n.label(s) + if (s in locked) " ✗" else "", color = if (buzzed == s) Color.Black else C.text, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                    }
                }
                Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(q.q, style = Type.h, color = C.muted, textAlign = TextAlign.Center, modifier = Modifier.rotate(180f))
                    Text("${round + 1} / $rounds", style = Type.label, color = C.dim)
                    Text(q.q, style = Type.title, color = C.text, textAlign = TextAlign.Center)
                    opts.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { o ->
                                val isOk = o == q.ok
                                val bg = when { shown != null && isOk -> C.green; shown == o && !isOk -> C.red; else -> C.panel2 }
                                Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                                    .clickable(enabled = buzzed != null && shown == null) {
                                        val s = buzzed ?: return@clickable
                                        if (isOk) { Sfx.correct(); tally.add(host, n.membersOf(s), 1); shown = o; host.log(t("Κουίζ: ${n.label(s)} σωστά", "Quiz: ${n.label(s)} right")) }
                                        else {
                                            Sfx.wrong(); tally.add(host, n.membersOf(s), -1); locked = locked + s; buzzed = null
                                            if (locked.size >= sides.size) shown = o
                                        }
                                    }.padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text(o, color = if (bg == C.panel2) C.text else Color.Black, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (buzzed != null) Text(t("Απαντάει: ${n.label(buzzed!!)}", "Answering: ${n.label(buzzed!!)}"), style = Type.h, color = Color(n.colorOf(buzzed!!)))
                    else if (shown == null) androidx.compose.material3.TextButton(onClick = { shown = q.ok }) { Text(t("Κανείς; Δείξε την απάντηση", "Nobody? Show the answer"), color = C.dim) }
                }
            }
        }

        Quiz11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Quiz11.Intro }
    }
}
