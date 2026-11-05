package gr.wsol.gamenight.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Prompt
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Tagged
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class Who11 { Intro, Card, Vote, Result, Over }

/* ======================= 👉 Ποιος από εμάς; ======================= */
object WhoRules {
    /** +1 to everyone who voted for the one the group picked most (on a tie, for any of the top ones). */
    fun points(votes: Map<String, String>): Map<String, Int> {
        val tops = Tally11.tops(votes)
        return votes.filter { it.value in tops }.mapValues { 1 }
    }
}

@Composable
fun WhoGame(host: GameHost) {
    val game = Games.byId("whois")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(Who11.Intro) }
    var rounds by remember { mutableIntStateOf(8) }
    var round by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("whois", Cards.get(ctx).whois, { it.a }) }
    var card by remember { mutableStateOf<Prompt?>(null) }
    var votes by remember { mutableStateOf(mapOf<String, String>()) }
    val tally = remember { Tally() }

    when (phase) {
        Who11.Intro -> Intro(game, host, listOf(
            t("Η εφαρμογή ρωτάει, π.χ. «Ποιος από εμάς θα ξεχνούσε το μωρό στο σούπερ μάρκετ;».", "The app asks, e.g. «Who of us would forget the baby at the supermarket?»."),
            t("Όλοι ψηφίζουν κρυφά έναν παίκτη (και τον εαυτό τους, αν θέλουν).", "Everyone votes in secret for a player (themselves too, if they like)."),
            t("Βγαίνει η κατάταξη. +1 σε όσους ψήφισαν αυτόν που βγήκε πρώτος.", "The ranking comes out. +1 to everyone who voted for the one on top."),
        ), deck.size > 0, {
            Body(t("Ερωτήσεις", "Questions"), color = C.text)
            Seg(listOf(5 to "5", 8 to "8", 12 to "12"), rounds, { rounds = it }, color = game.color)
        }) { round = 0; card = deck.next(); phase = Who11.Card }

        Who11.Card -> { QuitGuard(host); CardScreen(t("Ερώτηση ${round + 1} / $rounds", "Question ${round + 1} / $rounds"), C.dim, "👉", card?.t ?: "", t("Διαβάστε τη δυνατά. Μετά ψηφίζει ο καθένας κρυφά.", "Read it out loud. Then everyone votes in secret."), game.color) {
            BigButton(t("Ψηφοφορία", "Vote"), { phase = Who11.Vote }, color = game.color)
        } }

        Who11.Vote -> { QuitGuard(host); SecretPick(n.names, n.names, key = round, question = card?.t ?: "") { votes = it; phase = Who11.Result } }

        Who11.Result -> {
            QuitGuard(host)
            val counts = Tally11.counts(votes)
            val tops = Tally11.tops(votes)
            remember(round) {
                tally.addAll(host, WhoRules.points(votes))
                tops.forEach { host.stat(it, "whois") }
                host.log(t("Ποιος από εμάς: ${tops.joinToString()}", "Who of us: ${tops.joinToString()}"))
            }
            Page(card?.t ?: "", onBack = null, bottom = {
                BigButton(if (round + 1 < rounds) t("Επόμενη ερώτηση", "Next question") else t("Αποτελέσματα", "Results"), {
                    if (round + 1 < rounds) { round++; card = deck.next(); phase = Who11.Card } else phase = Who11.Over
                }, color = game.color)
            }) {
                Text(tops.joinToString(" · "), style = Type.big, color = game.color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Panel { counts.forEach { (p, c) -> Row { Text(p, style = Type.body, color = colorOf(p), modifier = Modifier.weight(1f)); Text("$c", style = Type.h, color = C.text) } } }
                Body(t("+1 σε όσους το μάντεψαν.", "+1 to everyone who guessed it."), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        Who11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Who11.Intro }
    }
}

/* ======================= 🪞 Πόσο με ξέρεις; ======================= */
private enum class Know11 { Intro, Pass, Answer, Guess, Judge, Over }

@Composable
fun KnowMeGame(host: GameHost) {
    val game = Games.byId("knowme")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(Know11.Intro) }
    var each by remember { mutableIntStateOf(1) }
    var turn by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("knowme", Cards.get(ctx).knowme, { it.a }) }
    var card by remember { mutableStateOf<Prompt?>(null) }
    var answer by remember { mutableStateOf("") }
    var guesses by remember { mutableStateOf(mapOf<String, String>()) }
    var right by remember { mutableStateOf(setOf<String>()) }
    val tally = remember { Tally() }
    val total = n.names.size * each
    val who = n.names.getOrNull(turn % n.names.size.coerceAtLeast(1)) ?: ""
    val others = n.names.filter { it != who }

    when (phase) {
        Know11.Intro -> Intro(game, host, listOf(
            t("Ένας παίκτης απαντά κρυφά σε μια ερώτηση για τον εαυτό του, π.χ. «το πιο άχρηστο ταλέντο σου».", "One player secretly answers a question about themselves, e.g. «your most useless talent»."),
            t("Οι άλλοι γράφουν κρυφά τι πιστεύουν ότι απάντησε.", "The others secretly write what they think the answer was."),
            t("Εκείνος διαλέγει ποιοι το βρήκαν: +1 σε καθέναν, και +1 σε αυτόν αν το βρήκε έστω ένας.", "They pick who got it: +1 each, and +1 to them if at least one did."),
        ), deck.size > 0, {
            Body(t("Γύροι για τον καθένα", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = game.color)
        }) { turn = 0; phase = Know11.Pass }

        Know11.Pass -> { QuitGuard(host); PassTo(who, colorOf(who), note = t("Η ερώτηση είναι για σένα.", "The question is about you.")) { card = deck.next(); answer = ""; phase = Know11.Answer } }

        Know11.Answer -> { QuitGuard(host); SecretInputRound(listOf(who), key = "a$turn", prompt = { card?.t ?: "" }, hint = t("Η αλήθεια σου", "Your truth")) { answer = it[who].orEmpty(); phase = Know11.Guess } }

        Know11.Guess -> { QuitGuard(host); SecretInputRound(others, key = "g$turn", prompt = { t("Τι απάντησε ο/η $who; «${card?.t}»", "What did $who answer? «${card?.t}»") }) { guesses = it; right = emptySet(); phase = Know11.Judge } }

        Know11.Judge -> {
            QuitGuard(host)
            Page(card?.t ?: "", onBack = null, bottom = {
                BigButton(t("Αυτοί το βρήκαν", "These got it"), {
                    tally.add(host, right, 1)
                    if (right.isNotEmpty()) tally.add(host, listOf(who), 1)
                    host.log(t("Πόσο με ξέρεις: $who, το βρήκαν ${right.size}", "How well: $who, ${right.size} got it"))
                    if (turn + 1 < total) { turn++; phase = Know11.Pass } else phase = Know11.Over
                }, color = game.color)
            }) {
                Label(t("Η ΑΠΑΝΤΗΣΗ ΤΟΥ/ΤΗΣ $who", "$who'S ANSWER"))
                Text(answer, style = Type.title, color = game.color)
                Body(t("$who, πάτα όσους το βρήκαν (ή σχεδόν).", "$who, tap everyone who got it (or nearly)."), color = C.text)
                guesses.forEach { (p, g) ->
                    val on = p in right
                    Panel(color = if (on) game.color.copy(alpha = 0.25f) else C.panel, onClick = { right = if (on) right - p else right + p; Sfx.tap() }) {
                        Text(p, style = Type.h, color = colorOf(p)); Text(g.ifBlank { "—" }, style = Type.body, color = C.text)
                    }
                }
            }
        }

        Know11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Know11.Intro }
    }
}

/* ======================= 🎲 Ζάρι της ντροπής ======================= */
object DiceRules {
    /** What each face of the die means. 5 sings a word, 6 lets the player choose. */
    fun face(f: Int) = when (f) { 1 -> "q"; 2 -> "d"; 3 -> "m"; 4 -> "n"; 5 -> "s"; else -> "*" }
    fun title(kind: String) = when (kind) {
        "q" -> t("Ερώτηση", "Question"); "d" -> t("Δοκιμασία", "Dare"); "m" -> t("Μίμηση", "Mime")
        "n" -> t("Καλή κουβέντα", "Nice words"); "s" -> t("Τραγούδι", "Song"); else -> t("Διάλεξε εσύ", "Your choice")
    }
}

private enum class Dice11 { Intro, Pass, Roll, Choose, Task, Over }

@Composable
fun DiceGame(host: GameHost) {
    val game = Games.byId("dice")!!
    val ctx = LocalContext.current
    val n = Store.night
    val cards = remember { Cards.get(ctx) }
    val decks = remember { listOf("q", "d", "m", "n").associateWith { k -> Deck("dice.$k", cards.dice.filter { it.c == k }, { it.a }) } }
    val songs = remember { Deck("song", cards.song, { it.a }) }
    var phase by remember { mutableStateOf(Dice11.Intro) }
    var each by remember { mutableIntStateOf(2) }
    var turn by remember { mutableIntStateOf(0) }
    var face by remember { mutableIntStateOf(1) }
    var kind by remember { mutableStateOf("q") }
    var task by remember { mutableStateOf("") }
    var rolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tally = remember { Tally() }
    val total = Turns.count(n, each)
    val who = Turns.player(n, turn)

    fun pick(k: String) {
        kind = k
        task = if (k == "s") t("Τραγούδησε ένα κομμάτι με τη λέξη «${songs.next()?.t}».", "Sing a song with the word «${songs.next()?.t}».") else decks[k]?.next()?.t.orEmpty()
        phase = Dice11.Task
    }

    when (phase) {
        Dice11.Intro -> Intro(game, host, listOf(
            t("Ρίχνεις το ζάρι: 1 ερώτηση, 2 ήπια δοκιμασία, 3 μίμηση, 4 καλή κουβέντα, 5 τραγούδι, 6 διαλέγεις εσύ.", "Roll the die: 1 question, 2 mild dare, 3 mime, 4 nice words, 5 song, 6 your choice."),
            t("Η παρέα κρίνει: αν το έκανες, +1.", "The group judges: if you did it, +1."),
            t("Τίποτα επικίνδυνο, τίποτα σεξουαλικό. Μπορείς πάντα να πεις «πάσο».", "Nothing dangerous, nothing sexual. You can always pass."),
        ), decks.values.all { it.size > 0 }, {
            Body(t("Ρίψεις για τον καθένα", "Rolls each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = game.color)
        }) { turn = 0; phase = Dice11.Pass }

        Dice11.Pass -> { QuitGuard(host); PassTo(who, colorOf(who), button = t("Ρίξε το ζάρι", "Roll the die")) { phase = Dice11.Roll } }

        Dice11.Roll -> {
            QuitGuard(host)
            LaunchedEffect(turn) {
                rolling = true
                repeat(12) { face = Random.nextInt(1, 7); Sfx.tick(); kotlinx.coroutines.delay(60L + it * 12) }
                rolling = false; Sfx.buzz(60)
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                Text(who, style = Type.h, color = colorOf(who))
                DieFace(face, game.color, Modifier.fillMaxWidth(0.5f))
                Text(DiceRules.title(DiceRules.face(face)), style = Type.big, color = game.color)
                BigButton(t("Πάμε", "Go"), { val k = DiceRules.face(face); if (k == "*") phase = Dice11.Choose else pick(k) }, color = game.color, enabled = !rolling)
            }
        }

        Dice11.Choose -> { QuitGuard(host); Page(t("Διάλεξε εσύ", "Your choice"), onBack = null) {
            listOf("q", "d", "m", "n", "s").forEach { k -> GhostButton(DiceRules.title(k), { pick(k) }, Modifier.fillMaxWidth(), color = game.color) }
        } }

        Dice11.Task -> { QuitGuard(host); CardScreen(who, colorOf(who), "🎲 $face", task, DiceRules.title(kind), game.color) {
            JudgeButtons(game.color) { ok ->
                if (ok) { tally.add(host, listOf(who), 1); host.stat(who, "dice") }
                if (turn + 1 < total) { turn++; phase = Dice11.Pass } else phase = Dice11.Over
            }
            GhostButton(t("Πάσο, άλλο", "Pass, another"), { pick(kind) }, Modifier.fillMaxWidth())
        } }

        Dice11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Dice11.Intro }
    }
}

/** A die with [n] dots. */
@Composable
fun DieFace(n: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension
        drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.18f))
        val d = r * 0.09f
        fun dot(x: Float, y: Float) = drawCircle(Color(0xFF101018), d, Offset(r * x, r * y))
        val a = 0.27f; val m = 0.5f; val b = 0.73f
        when (n) {
            1 -> dot(m, m)
            2 -> { dot(a, a); dot(b, b) }
            3 -> { dot(a, a); dot(m, m); dot(b, b) }
            4 -> { dot(a, a); dot(b, a); dot(a, b); dot(b, b) }
            5 -> { dot(a, a); dot(b, a); dot(m, m); dot(a, b); dot(b, b) }
            else -> { dot(a, a); dot(b, a); dot(a, m); dot(b, m); dot(a, b); dot(b, b) }
        }
    }
}

/* ======================= 🍾 Ρουλέτα μπουκαλιού ======================= */
object BottleRules {
    /** Where the bottle stops: the player at [angle] (degrees, 0 = up, clockwise) among [n] seats. */
    fun seat(angle: Float, n: Int): Int {
        val a = ((angle % 360f) + 360f) % 360f
        return ((a + 180f / n) / (360f / n)).toInt() % n
    }
}

private enum class Bottle11 { Intro, Spin, Choose, Card, Over }

@Composable
fun BottleGame(host: GameHost) {
    val game = Games.byId("bottle")!!
    val ctx = LocalContext.current
    val n = Store.night
    val names = remember { n.names }
    val cards = remember { Cards.get(ctx) }
    val truths = remember { Deck("bottle.t", cards.bottle.filter { it.c == "t" }, { it.a }) }
    val dares = remember { Deck("bottle.d", cards.bottle.filter { it.c == "d" }, { it.a }) }
    var phase by remember { mutableStateOf(Bottle11.Intro) }
    var spins by remember { mutableIntStateOf(10) }
    var spin by remember { mutableIntStateOf(0) }
    var spinner by remember { mutableStateOf(names.firstOrNull().orEmpty()) }
    var target by remember { mutableStateOf("") }
    var card by remember { mutableStateOf<Tagged?>(null) }
    val angle = remember { Animatable(0f) }
    var turning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tally = remember { Tally() }

    when (phase) {
        Bottle11.Intro -> Intro(game, host, listOf(
            t("Βάλτε το κινητό στη μέση του τραπεζιού. Τα ονόματα μπαίνουν σε κύκλο, με τη σειρά που κάθεστε.", "Put the phone in the middle of the table. The names go round in a circle, in your seating order."),
            t("Γυρίζεις το μπουκάλι. Όποιος βγει διαλέγει «Αλήθεια» ή «Θάρρος».", "Spin the bottle. Whoever it points at picks «Truth» or «Dare»."),
            t("Μόνο ήπια πράγματα, ποτέ σεξουαλικά. Αν το κάνει, +1, και γυρίζει αυτός.", "Only mild things, never sexual. If they do it, +1, and they spin next."),
        ), names.size >= 3 && truths.size > 0 && dares.size > 0, {
            Body(t("Γύρισμα μπουκαλιού", "Spins"), color = C.text)
            Seg(listOf(6 to "6", 10 to "10", 15 to "15"), spins, { spins = it }, color = game.color)
        }) { spin = 0; spinner = names.first(); phase = Bottle11.Spin }

        Bottle11.Spin -> {
            QuitGuard(host)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Γυρίζει: $spinner", "Spinning: $spinner"), style = Type.h, color = colorOf(spinner))
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    names.forEachIndexed { i, p ->
                        val a = 2 * PI * i / names.size - PI / 2
                        Text(p, style = Type.h, color = colorOf(p), modifier = Modifier.align(Alignment.Center).padding(0.dp)
                            .then(Modifier.offsetFraction(cos(a).toFloat() * 0.40f, sin(a).toFloat() * 0.40f)))
                    }
                    Canvas(Modifier.fillMaxSize(0.62f)) {
                        rotate(angle.value) {
                            val w = size.width; val h = size.height
                            val body = Path().apply {
                                moveTo(w * 0.5f, h * 0.06f); lineTo(w * 0.56f, h * 0.30f); lineTo(w * 0.60f, h * 0.88f)
                                lineTo(w * 0.40f, h * 0.88f); lineTo(w * 0.44f, h * 0.30f); close()
                            }
                            drawPath(body, Color(0xFF39D98A))
                            drawCircle(Color(0xFFFFD166), w * 0.035f, Offset(w * 0.5f, h * 0.06f))
                        }
                    }
                }
                BigButton(t("Γύρνα το!", "Spin it!"), {
                    if (turning) return@BigButton
                    turning = true
                    scope.launch {
                        val others = names.indices.filter { names[it] != spinner }
                        val seat = others.random()
                        val end = angle.value - (angle.value % 360f) + 360f * (4 + Random.nextInt(3)) + seat * 360f / names.size + Random.nextFloat() * (200f / names.size) - 100f / names.size
                        angle.animateTo(end, tween(3200, easing = FastOutSlowInEasing))
                        target = names[BottleRules.seat(angle.value, names.size)].let { if (it == spinner) names[seat] else it }
                        Sfx.bell(); turning = false; phase = Bottle11.Choose
                    }
                }, color = game.color, enabled = !turning)
            }
        }

        Bottle11.Choose -> { QuitGuard(host); CardScreen(t("Το μπουκάλι δείχνει", "The bottle points at"), C.dim, "🍾", target, null, colorOf(target)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(t("Αλήθεια", "Truth"), { card = truths.next(); phase = Bottle11.Card }, Modifier.weight(1f), color = C.cyan)
                BigButton(t("Θάρρος", "Dare"), { card = dares.next(); phase = Bottle11.Card }, Modifier.weight(1f), color = C.pink)
            }
        } }

        Bottle11.Card -> { QuitGuard(host); CardScreen(target, colorOf(target), if (card?.c == "t") "💬" else "🔥", card?.t ?: "", if (card?.c == "t") t("Αλήθεια", "Truth") else t("Θάρρος", "Dare"), game.color) {
            JudgeButtons(game.color) { ok ->
                if (ok) tally.add(host, listOf(target), 1)
                host.log(t("Μπουκάλι: $target", "Bottle: $target"))
                spinner = target
                if (spin + 1 < spins) { spin++; phase = Bottle11.Spin } else phase = Bottle11.Over
            }
        } }

        Bottle11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Bottle11.Intro }
    }
}

/** Moves content by a fraction of its parent's size (for the names around the bottle). */
private fun Modifier.offsetFraction(fx: Float, fy: Float): Modifier = this.then(
    Modifier.layout { m, c ->
        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) { p.place(((c.maxWidth * fx)).toInt(), ((c.maxHeight * fy)).toInt()) }
    },
)
