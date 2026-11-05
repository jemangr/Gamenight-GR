package gr.wsol.gamenight.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Fact
import gr.wsol.gamenight.core.Greek
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Input
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown

/** Scoring of «Ο Μπλοφατζής»: pure logic. */
object BluffRules {
    /** The same answer, whatever the accents, case or spaces. */
    fun same(a: String, b: String) = Greek.fold(a).trim('.', ' ') == Greek.fold(b).trim('.', ' ')

    /** The choices shown: the truth and every distinct bluff, shuffled. Value: who wrote it ("" for the truth). */
    fun options(truth: String, bluffs: Map<String, String>, rnd: kotlin.random.Random = kotlin.random.Random): List<Pair<String, List<String>>> {
        val groups = mutableListOf<Pair<String, MutableList<String>>>(truth to mutableListOf())
        bluffs.forEach { (who, b) ->
            val g = groups.firstOrNull { same(it.first, b) }
            if (g != null) g.second += who else groups += b to mutableListOf(who)
        }
        return groups.shuffled(rnd).map { it.first to it.second.toList() }
    }

    /** +2 for finding the truth; +1 to a bluff's authors for each player it fooled. */
    fun points(truth: String, options: List<Pair<String, List<String>>>, votes: Map<String, String>): Map<String, Int> {
        val p = mutableMapOf<String, Int>()
        votes.forEach { (voter, pick) ->
            if (same(pick, truth)) p[voter] = (p[voter] ?: 0) + 2
            else options.firstOrNull { it.first == pick }?.second?.filter { it != voter }?.forEach { a -> p[a] = (p[a] ?: 0) + 1 }
        }
        return p
    }
}

private enum class B { Intro, Read, Write, Vote, Reveal, Over }

@Composable
fun BlufferGame(host: GameHost) {
    val game = Games.byId("bluffer")!!
    val ctx = LocalContext.current
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(B.Intro) }
    var count by remember { mutableIntStateOf(5) }
    var q by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("facts-bluff", Cards.get(ctx).facts, { it.a }) }
    var fact by remember { mutableStateOf<Fact?>(null) }
    val bluffs = remember { mutableStateMapOf<String, String>() }
    var options by remember { mutableStateOf(listOf<Pair<String, List<String>>>()) }
    var votes by remember { mutableStateOf(mapOf<String, String>()) }
    val tally = remember { Tally() }

    when (phase) {
        B.Intro -> Intro(game, host, listOf(
            t("Μια παράξενη αλλά αληθινή ερώτηση για όλους.", "A strange but true question for everyone."),
            t("Το κινητό περνάει από όλους: ο καθένας γράφει κρυφά μια ψεύτικη απάντηση που να ακούγεται αληθινή.", "The phone goes round: each one secretly writes a fake answer that sounds true."),
            t("Μετά ο καθένας ψηφίζει κρυφά ποια είναι η αληθινή. +2 αν τη βρεις, +1 για κάθε παίκτη που ψήφισε τη δική σου μπλόφα.", "Then each one secretly votes for the true one. +2 if you find it, +1 for each player who picked your bluff."),
        ), deck.size > 0, {
            Body(t("Ερωτήσεις", "Questions"), color = C.text)
            Seg(listOf(3 to "3", 5 to "5", 8 to "8"), count, { count = it }, color = C.ghost)
        }) { q = 0; fact = deck.next(); bluffs.clear(); phase = B.Read }

        B.Read -> { QuitGuard(host); Page(t("Ερώτηση ${q + 1} από $count", "Question ${q + 1} of $count"), onBack = null, bottom = {
            BigButton(t("Γράψτε μπλόφες", "Write your bluffs"), { phase = B.Write }, color = C.ghost)
        }) {
            Body(t("Διαβάστε τη δυνατά:", "Read it out loud:"))
            Text(fact!!.q, style = Type.title, color = C.text)
        } }

        B.Write -> {
            QuitGuard(host)
            var i by remember(q) { mutableIntStateOf(0) }
            var ready by remember(q, i) { mutableStateOf(false) }
            var text by remember(q, i) { mutableStateOf("") }
            var warn by remember(q, i) { mutableStateOf<String?>(null) }
            val who = players[i]
            if (!ready) PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Γράψε κρυφά τη μπλόφα σου.", "Write your bluff in secret.")) { ready = true }
            else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
                Text(fact!!.q, style = Type.title, color = C.text)
                Input(text, { text = it; warn = null }, t("Μια απάντηση που ακούγεται αληθινή", "An answer that sounds true"))
                warn?.let { Text(it, style = Type.body, color = C.amber) }
                BigButton(t("Έτοιμο", "Done"), {
                    when {
                        BluffRules.same(text, fact!!.ans) -> warn = t("Μπράβο, αυτή είναι η αληθινή! Γράψε μια ψεύτικη.", "Well done, that's the true one! Write a fake one.")
                        else -> {
                            bluffs[who] = text.trim()
                            if (i + 1 < players.size) i++ else { options = BluffRules.options(fact!!.ans, bluffs.toMap()); phase = B.Vote }
                        }
                    }
                }, enabled = text.isNotBlank(), color = C.ghost)
            }
        }

        B.Vote -> {
            QuitGuard(host)
            var i by remember(q) { mutableIntStateOf(0) }
            var ready by remember(q, i) { mutableStateOf(false) }
            val picked = remember(q) { mutableStateMapOf<String, String>() }
            val who = players[i]
            if (!ready) PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Ψήφισε κρυφά την αληθινή.", "Secretly vote for the true one.")) { ready = true }
            else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
                Text(fact!!.q, style = Type.body, color = C.text)
                options.filter { who !in it.second }.forEach { (o, _) ->
                    GhostButton(o, {
                        picked[who] = o
                        if (i + 1 < players.size) i++ else { votes = picked.toMap(); phase = B.Reveal }
                    }, Modifier.fillMaxWidth())
                }
            }
        }

        B.Reveal -> {
            QuitGuard(host)
            val f = fact!!
            val pts = remember(q) { BluffRules.points(f.ans, options, votes).also { m -> m.forEach { (p, v) -> tally.add(host, listOf(p), v) }; Sfx.win() } }
            Page(t("Η αλήθεια", "The truth"), onBack = null, bottom = {
                BigButton(if (q + 1 < count) t("Επόμενη ερώτηση", "Next question") else t("Αποτελέσματα", "Results"), {
                    host.log(t("Μπλοφατζής: «${f.ans}»", "Bluffer: «${f.ans}»"))
                    if (q + 1 < count) { q++; fact = deck.next(); bluffs.clear(); phase = B.Read } else phase = B.Over
                }, color = C.ghost)
            }) {
                Text(f.q, style = Type.body, color = C.muted)
                Panel { Text(f.ans, style = Type.big, color = C.green, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                options.filter { !BluffRules.same(it.first, f.ans) }.forEach { (o, authors) ->
                    val fooled = votes.filter { it.value == o }.keys
                    Panel {
                        Text("«$o»", style = Type.h, color = C.text)
                        Text(t("Μπλόφα του/της ", "Bluff by ") + authors.joinToString(", "), style = Type.small, color = C.ghost)
                        if (fooled.isNotEmpty()) Text(t("Την πίστεψαν: ", "Fooled: ") + fooled.joinToString(", "), style = Type.small, color = C.amber)
                    }
                }
                val right = votes.filter { BluffRules.same(it.value, f.ans) }.keys
                Text(if (right.isEmpty()) t("Κανείς δεν βρήκε την αλήθεια!", "Nobody found the truth!") else t("Τη βρήκαν: ", "Found it: ") + right.joinToString(", "), style = Type.body, color = C.text)
                pts.entries.sortedByDescending { it.value }.forEach { (p, v) -> Text("+$v $p", style = Type.body, color = C.green) }
            }
        }

        B.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = B.Intro }
    }
}

/** Scoring of «Ψέμα υπό πίεση». */
object LieRules {
    /** Judge right: +1 to the judge. Judge wrong: +1 to the speaker, +2 if they fooled them with a lie. */
    fun points(speaker: String, judge: String, lied: Boolean, judgedLie: Boolean): Pair<String, Int> =
        if (lied == judgedLie) judge to 1 else speaker to if (lied) 2 else 1
}

private enum class L { Intro, PassSpeaker, Choose, Think, PassJudge, Judge, Result, Over }

@Composable
fun LieGame(host: GameHost) {
    val game = Games.byId("lie")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(L.Intro) }
    var each by remember { mutableIntStateOf(2) }
    var turn by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("facts-lie", Cards.get(ctx).facts, { it.a }) }
    var fact by remember { mutableStateOf<Fact?>(null) }
    var lied by remember { mutableStateOf(false) }
    var judgedLie by remember { mutableStateOf(false) }
    val tally = remember { Tally() }
    val total = Turns.count(n, each)
    val speaker = Turns.player(n, turn)
    val judge = Turns.player(n, turn + 1).let { if (it == speaker) Turns.player(n, turn + 2) else it }

    when (phase) {
        L.Intro -> Intro(game, host, listOf(
            t("Βλέπεις κρυφά μια περίεργη ερώτηση με την αληθινή απάντηση. Διαλέγεις: αλήθεια ή ψέμα.", "You secretly see a strange question with its true answer. You choose: truth or lie."),
            t("Αν πεις ψέμα, σκαρώνεις μια πειστική απάντηση (έχεις 10″ να σκεφτείς).", "If you lie, make up a convincing answer (10″ to think)."),
            t("Λες δυνατά ερώτηση και απάντηση. Ο επόμενος κρίνει: Αλήθεια ή Ψέμα;", "Say the question and answer out loud. The next player judges: truth or lie?"),
            t("Σωστός κριτής +1. Αλλιώς +1 σε αυτόν που μίλησε, +2 αν ξεγέλασε με ψέμα.", "Right judge +1. Otherwise +1 to the speaker, +2 if they fooled with a lie."),
        ), deck.size > 0 && n.players.size >= 2, {
            Body(t("Γύροι για τον καθένα", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = C.pink)
        }) { turn = 0; phase = L.PassSpeaker }

        L.PassSpeaker -> { QuitGuard(host); PassTo(speaker, Color(n.colorOf(n.sideOf(speaker))), note = t("Μόνο εσύ βλέπεις την απάντηση.", "Only you see the answer.")) { fact = deck.next(); phase = L.Choose } }

        L.Choose -> { QuitGuard(host); Page(speaker, onBack = null, bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(t("Πες την αλήθεια", "Tell the truth"), { lied = false; phase = L.PassJudge }, Modifier.weight(1f), color = C.green)
                BigButton(t("Πες ψέμα", "Tell a lie"), { lied = true; phase = L.Think }, Modifier.weight(1f), color = C.pink)
            }
        }) {
            Text(fact!!.q, style = Type.title, color = C.text)
            Panel { Label2(t("ΑΛΗΘΙΝΗ ΑΠΑΝΤΗΣΗ", "TRUE ANSWER")); Text(fact!!.ans, style = Type.big, color = C.green) }
        } }

        L.Think -> {
            QuitGuard(host)
            val left = rememberCountdown(10, running = true, key = turn) { phase = L.PassJudge }
            Page(t("Σκαρώσε το ψέμα σου", "Make up your lie"), onBack = null, bottom = { BigButton(t("Έτοιμος", "Ready"), { phase = L.PassJudge }, color = C.pink) }) {
                Text(fact!!.q, style = Type.title, color = C.text)
                TimerText(left, color = C.pink)
            }
        }

        L.PassJudge -> { QuitGuard(host); Page(t("Πες το δυνατά", "Say it out loud"), onBack = null, bottom = {
            BigButton(t("Το είπα · στον/στην $judge", "Said it · to $judge"), { phase = L.Judge }, color = C.pink)
        }) {
            Body(t("Διάβασε την ερώτηση και πες την απάντησή σου, αληθινή ή ψεύτικη. Μετά δώσε το κινητό στον/στην $judge.", "Read the question and say your answer, true or not. Then give the phone to $judge."), color = C.text)
            Text(fact!!.q, style = Type.title, color = C.text)
        } }

        L.Judge -> { QuitGuard(host); Page(judge, onBack = null, bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(t("Αλήθεια", "Truth"), { judgedLie = false; phase = L.Result }, Modifier.weight(1f), color = C.green)
                BigButton(t("Ψέμα", "Lie"), { judgedLie = true; phase = L.Result }, Modifier.weight(1f), color = C.pink)
            }
        }) {
            Body(t("Ο/Η $speaker είπε την αλήθεια ή ψέματα;", "Did $speaker tell the truth or lie?"), color = C.text)
            Text(fact!!.q, style = Type.title, color = C.text)
        } }

        L.Result -> {
            QuitGuard(host)
            val (who, pts) = remember(turn) { LieRules.points(speaker, judge, lied, judgedLie).also { tally.add(host, listOf(it.first), it.second); if (lied == judgedLie) Sfx.correct() else Sfx.wrong() } }
            Announce(if (lied == judgedLie) "🎯" else "🤥", if (lied) t("Ήταν ψέμα!", "It was a lie!") else t("Ήταν αλήθεια!", "It was true!"),
                t("Η αληθινή απάντηση: ${fact!!.ans}\n\n+$pts $who", "The true answer: ${fact!!.ans}\n\n+$pts $who"),
                if (turn + 1 < total) t("Επόμενος", "Next") else t("Αποτελέσματα", "Results"), C.pink) {
                host.log(t("Ψέμα υπό πίεση: $speaker ${if (lied) "ψέμα" else "αλήθεια"}, +$pts $who", "Lie: $speaker ${if (lied) "lied" else "truth"}, +$pts $who"))
                if (turn + 1 < total) { turn++; phase = L.PassSpeaker } else phase = L.Over
            }
        }

        L.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = L.Intro }
    }
}

@Composable private fun Label2(s: String) = gr.wsol.gamenight.ui.Label(s)
