package gr.wsol.gamenight.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import gr.wsol.gamenight.core.Greek
import gr.wsol.gamenight.core.Minefield
import gr.wsol.gamenight.core.Scene
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Input
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown
import kotlin.random.Random

/** Scoring of «Το νούμερό σου»: pure logic. */
object NumberRules {
    /** Different numbers 1–10, one per actor. */
    fun deal(actors: List<String>, rnd: Random = Random): Map<String, Int> = actors.zip((1..10).shuffled(rnd)).toMap()

    /**
     * The director's guesses against the real numbers. The director gets +1 for each actor in the right
     * place of the order and +1 for each exact number; each actor gets +1 if the guess was within ±1.
     */
    fun points(director: String, real: Map<String, Int>, guess: Map<String, Int>): Map<String, Int> {
        val p = mutableMapOf<String, Int>()
        val realOrder = real.keys.sortedBy { real[it] }
        val guessOrder = real.keys.sortedBy { guess[it] ?: 0 } // ties keep the actors' order
        var d = 0
        realOrder.forEachIndexed { i, a -> if (guessOrder[i] == a) d++ }
        real.forEach { (a, r) ->
            val g = guess[a] ?: return@forEach
            if (g == r) d++
            if (kotlin.math.abs(g - r) <= 1) p[a] = 1
        }
        if (d > 0) p[director] = d
        return p
    }
}

private enum class N { Intro, Deal, Read, Act, Guess, Reveal, Over }

@Composable
fun NumberGame(host: GameHost) {
    val game = Games.byId("number")!!
    val ctx = LocalContext.current
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(N.Intro) }
    var round by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("scenes", Cards.get(ctx).scenes, { it.a }) }
    var scene by remember { mutableStateOf<Scene?>(null) }
    var numbers by remember { mutableStateOf(mapOf<String, Int>()) }
    val guess = remember { mutableStateMapOf<String, Int>() }
    val tally = remember { Tally() }
    val director = players[round % players.size]
    val actors = players.filter { it != director }

    fun start() { numbers = NumberRules.deal(actors); scene = deck.next(); guess.clear(); actors.forEach { guess[it] = 5 }; phase = N.Deal }

    when (phase) {
        N.Intro -> Intro(game, host, listOf(
            t("Ένας είναι ο σκηνοθέτης. Οι άλλοι παίρνουν κρυφά ένα νούμερο από το 1 έως το 10 (όχι δύο ίδια).", "One is the director. The others secretly get a number from 1 to 10 (no two the same)."),
            t("Ο σκηνοθέτης διαβάζει ένα σενάριο με κλίμακα, π.χ. «πόσο χαίρεσαι που έρχεται η πεθερά;».", "The director reads a scene with a scale, e.g. «how happy are you that your mother-in-law is coming?»."),
            t("Ο καθένας παίζει το σενάριο όπως το νούμερό του, 20″ ο καθένας. Ο σκηνοθέτης μαντεύει τα νούμερα.", "Each one acts it like their number, 20″ each. The director guesses the numbers."),
            t("Σκηνοθέτης: +1 για κάθε σωστή θέση στη σειρά, +1 για κάθε ακριβές νούμερο. Ηθοποιός: +1 αν ο σκηνοθέτης έπεσε μέσα κατά ±1.", "Director: +1 for each right place in the order, +1 for each exact number. Actor: +1 if the director was within ±1."),
        ), deck.size > 0 && players.size >= 3) { round = 0; start() }

        N.Deal -> { QuitGuard(host); RevealRound(actors, key = round, title = t("Το νούμερό σου", "Your number"), secret = { a ->
            Text("${numbers[a]}", style = Type.huge, color = C.amber, fontSize = 110.sp)
        }) { phase = N.Read } }

        N.Read -> { QuitGuard(host); Page(t("🎬 Σκηνοθέτης: $director", "🎬 Director: $director"), onBack = null, bottom = {
            BigButton(t("Πάμε, παίζουν οι ηθοποιοί", "Go, the actors play"), { phase = N.Act }, color = game.color)
        }) {
            Body(t("Δώσε το κινητό στον σκηνοθέτη. Διάβασε δυνατά:", "Give the phone to the director. Read out loud:"))
            Panel { Text(scene!!.t, style = Type.title, color = C.text) }
            Text("1 = ${scene!!.lo}", style = Type.body, color = C.muted)
            Text("10 = ${scene!!.hi}", style = Type.body, color = C.amber)
        } }

        N.Act -> {
            QuitGuard(host)
            var i by remember(round) { mutableIntStateOf(0) }
            var running by remember(round, i) { mutableStateOf(false) }
            val a = actors[i]
            val left = rememberCountdown(20, running = running, key = "$round-$i") { running = false }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Text(scene!!.t, style = Type.body, color = C.muted, textAlign = TextAlign.Center)
                Text(t("Παίζει:", "Acting:"), style = Type.small, color = C.dim)
                Text(a, style = Type.big, color = Color(n.colorOf(n.sideOf(a))))
                TimerText(left)
                if (!running) BigButton(t("Ξεκίνα τα 20″", "Start the 20″"), { running = true })
                Body(t("Ο σκηνοθέτης μπορεί να κάνει μία ερώτηση.", "The director may ask one question."), align = TextAlign.Center)
                GhostButton(if (i + 1 < actors.size) t("Επόμενος ηθοποιός", "Next actor") else t("Ο σκηνοθέτης μαντεύει", "The director guesses"), {
                    if (i + 1 < actors.size) i++ else phase = N.Guess
                }, Modifier.fillMaxWidth())
            }
        }

        N.Guess -> { QuitGuard(host); Page(t("Τι νούμερο είχε ο καθένας;", "What number did each one have?"), onBack = null, bottom = {
            BigButton(t("Αποκάλυψη", "Reveal"), { phase = N.Reveal }, color = game.color)
        }) {
            Body(t("$director, βάλε ένα νούμερο σε κάθε ηθοποιό.", "$director, give each actor a number."))
            actors.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a, style = Type.h, color = Color(n.colorOf(n.sideOf(a))), modifier = Modifier.weight(1f))
                    GhostButton("−", { guess[a] = ((guess[a] ?: 5) - 1).coerceAtLeast(1) }, Modifier.width(56.dp))
                    Text("${guess[a] ?: 5}", style = Type.title, color = C.amber, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                    GhostButton("+", { guess[a] = ((guess[a] ?: 5) + 1).coerceAtMost(10) }, Modifier.width(56.dp))
                }
            }
        } }

        N.Reveal -> {
            QuitGuard(host)
            val pts = remember(round) { NumberRules.points(director, numbers, guess.toMap()).also { m -> m.forEach { (p, v) -> tally.add(host, listOf(p), v) }; Sfx.win() } }
            Page(t("Αποκάλυψη", "Reveal"), onBack = null, bottom = {
                BigButton(if (round + 1 < players.size) t("Επόμενος σκηνοθέτης", "Next director") else t("Αποτελέσματα", "Results"), {
                    host.log(t("Το νούμερό σου: σκηνοθέτης $director, +${pts[director] ?: 0}", "Your number: director $director, +${pts[director] ?: 0}"))
                    if (round + 1 < players.size) { round++; start() } else phase = N.Over
                }, color = game.color)
            }) {
                actors.sortedBy { numbers[it] }.forEach { a ->
                    val r = numbers[a]!!; val g = guess[a] ?: 0
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a, style = Type.h, color = Color(n.colorOf(n.sideOf(a))), modifier = Modifier.weight(1f))
                            Text("$r", style = Type.title, color = C.amber)
                            Text(t("  (μάντεψε $g)", "  (guessed $g)"), style = Type.small, color = if (g == r) C.green else C.muted)
                        }
                    }
                }
                pts.entries.sortedByDescending { it.value }.forEach { (p, v) -> Text("+$v $p", style = Type.body, color = C.green) }
            }
        }

        N.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = N.Intro }
    }
}

/** «Ναρκοπέδιο»: one topic being played. Pure logic. */
class MineChain(val field: Minefield) {
    private val mineStems = field.mines.map { forms -> forms.map { Greek.stem(it) }.toSet() }
    val used = mutableListOf<String>()
    val last get() = used.lastOrNull() ?: field.w

    enum class Result { Safe, Mine, Repeat, Empty }

    /** Which mine (0–2) the word hits, or -1. */
    fun mineOf(word: String): Int {
        val s = Greek.stem(word)
        return mineStems.indexOfFirst { s in it }
    }

    fun say(word: String): Result {
        val w = word.trim()
        if (w.isEmpty()) return Result.Empty
        if (mineOf(w) >= 0) return Result.Mine
        val s = Greek.stem(w)
        if (s == Greek.stem(field.w) || used.any { Greek.stem(it) == s }) return Result.Repeat
        used += w
        return Result.Safe
    }

    companion object { const val SAFE_TO_WIN = 10 }
}

private enum class M { Intro, Play, Boom, Clear, Over }

@Composable
fun MinefieldGame(host: GameHost) {
    val game = Games.byId("minefield")!!
    val ctx = LocalContext.current
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(M.Intro) }
    var topics by remember { mutableIntStateOf(3) }
    var topic by remember { mutableIntStateOf(0) }
    var turn by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("mines", Cards.get(ctx).mines, { it.a }) }
    var chain by remember { mutableStateOf<MineChain?>(null) }
    var loser by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf<String?>(null) }
    val tally = remember { Tally() }
    val who = players[turn % players.size]

    fun newTopic() { chain = deck.next()?.let { MineChain(it) }; text = ""; warn = null; phase = M.Play }
    fun boom(reason: String) { loser = who; why = reason; tally.add(host, listOf(who), -1); Sfx.boom(); Sfx.pattern(300, 80, 400); phase = M.Boom }

    when (phase) {
        M.Intro -> Intro(game, host, listOf(
            t("Μια λέξη-αφετηρία, π.χ. «Θάλασσα». Πίσω της κρύβονται 3 νάρκες: οι πιο προφανείς σχετικές λέξεις.", "A starting word, e.g. «Sea». Behind it hide 3 mines: the most obvious related words."),
            t("Ο καθένας γράφει σε 8″ μια λέξη σχετική με την τελευταία και δίνει το κινητό στον επόμενο.", "In 8″ each one types a word related to the last one and passes the phone."),
            t("Νάρκη, επανάληψη ή χρόνος που τελείωσε: −1 και αλλάζει το θέμα. Τόνοι και καταλήξεις δεν μετράνε.", "A mine, a repeat or time running out: −1 and the topic changes. Accents and endings don't matter."),
            t("Αν η παρέα κρίνει ότι η λέξη ήταν άσχετη, πατάει «✗ Άσχετη».", "If the group thinks a word was unrelated, press «✗ Unrelated»."),
        ), deck.size > 0, {
            Body(t("Θέματα", "Topics"), color = C.text)
            Seg(listOf(3 to "3", 5 to "5", 8 to "8"), topics, { topics = it }, color = C.red)
        }) { topic = 0; turn = 0; newTopic() }

        M.Play -> {
            QuitGuard(host)
            val c = chain ?: run { phase = M.Over; return }
            val left = rememberCountdown(8, running = true, key = "$topic-$turn", tickLast = 3) { boom(t("Τελείωσε ο χρόνος", "Time ran out")) }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Θέμα ${topic + 1} από $topics · αφετηρία: ${c.field.w}", "Topic ${topic + 1} of $topics · start: ${c.field.w}"), style = Type.label, color = C.dim)
                Text(t("Τελευταία λέξη", "Last word"), style = Type.small, color = C.muted)
                Text(c.last, style = Type.big, color = C.red)
                Text(t("${c.used.size} από ${MineChain.SAFE_TO_WIN} λέξεις χωρίς νάρκη", "${c.used.size} of ${MineChain.SAFE_TO_WIN} words without a mine"), style = Type.small, color = C.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(who, style = Type.title, color = Color(n.colorOf(n.sideOf(who))), modifier = Modifier.weight(1f))
                    Text("${(left + 999) / 1000}″", style = Type.title, color = if (left < 3000) C.red else C.text)
                }
                Input(text, { text = it; warn = null }, t("Μια σχετική λέξη", "A related word"), onDone = {})
                warn?.let { Text(it, style = Type.body, color = C.amber) }
                BigButton(t("Πάσα", "Pass it on"), {
                    when (c.say(text)) {
                        MineChain.Result.Mine -> boom(t("Πάτησε νάρκη: «${text.trim()}»", "Stepped on a mine: «${text.trim()}»"))
                        MineChain.Result.Repeat -> boom(t("Ειπώθηκε ήδη: «${text.trim()}»", "Already said: «${text.trim()}»"))
                        MineChain.Result.Empty -> warn = t("Γράψε μια λέξη", "Type a word")
                        MineChain.Result.Safe -> {
                            Sfx.pass(); Sfx.buzz(20); text = ""; turn++
                            if (c.used.size >= MineChain.SAFE_TO_WIN) phase = M.Clear
                        }
                    }
                }, color = C.red, enabled = text.isNotBlank())
                if (c.used.isNotEmpty()) GhostButton(t("✗ Άσχετη η «${c.last}»", "✗ «${c.last}» is unrelated"), {
                    val prev = players[(turn - 1 + players.size) % players.size]
                    loser = prev; why = t("Άσχετη λέξη: «${c.last}»", "Unrelated word: «${c.last}»"); tally.add(host, listOf(prev), -1); Sfx.wrong(); phase = M.Boom
                }, Modifier.fillMaxWidth(), color = C.muted)
            }
        }

        M.Boom, M.Clear -> {
            QuitGuard(host)
            val c = chain!!
            val boom = phase == M.Boom
            Announce(if (boom) "💥" else "🎉", if (boom) loser else t("Γλιτώσατε!", "You made it!"),
                (if (boom) "$why · −1\n\n" else t("10 λέξεις χωρίς νάρκη.\n\n", "10 words without a mine.\n\n")) +
                    t("Οι νάρκες της «${c.field.w}» ήταν: ", "The mines of «${c.field.w}» were: ") + c.field.mines.joinToString(", ") { it.first() },
                if (topic + 1 < topics) t("Επόμενο θέμα", "Next topic") else t("Αποτελέσματα", "Results"), C.red) {
                host.log(t("Ναρκοπέδιο: «${c.field.w}»", "Minefield: «${c.field.w}»") + if (boom) " 💥 $loser" else " ✓")
                if (topic + 1 < topics) { topic++; if (boom) turn = players.indexOf(loser).coerceAtLeast(0); newTopic() } else phase = M.Over
            }
        }

        M.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = M.Intro }
    }
}
