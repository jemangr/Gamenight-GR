package gr.wsol.gamenight.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.AlienPair
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Toggle
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown
import kotlin.random.Random

/** One game of «Ο Εξωγήινος»: who is what, who is still in, and who has won. Pure logic. */
class AlienGame(val players: List<String>, val pair: AlienPair, withClone: Boolean, rnd: Random = Random) {
    val alien: String
    val clone: String?
    init {
        val s = players.shuffled(rnd)
        alien = s[0]
        clone = if (withClone && players.size >= 5) s[1] else null
    }
    val alive = players.toMutableList()
    var round = 0; private set

    enum class Winner { Humans, Alien, Clone }
    var winner: Winner? = null; private set

    fun wordOf(p: String): String? = when (p) { alien -> pair.x; clone -> null; else -> pair.h }
    fun roleOf(p: String) = when (p) { alien -> t("εξωγήινος", "the alien"); clone -> t("κλώνος", "the clone"); else -> t("άνθρωπος", "human") }

    /** The order of clues this round: the clone never speaks first. */
    fun order(rnd: Random = Random): List<String> {
        val o = alive.shuffled(rnd).toMutableList()
        if (o.firstOrNull() == clone && o.size > 1) { o.removeAt(0); o.add(1, clone!!) }
        return o
    }

    /** [p] was voted out (null: a tie, nobody). The clone's guess is decided apart, with [cloneGuessed]. */
    fun out(p: String?) {
        round++
        if (p != null) alive.remove(p)
        decide()
    }

    fun cloneGuessed(right: Boolean) { if (right) winner = Winner.Clone else decide() }

    private fun decide() {
        if (winner != null) return
        val alienIn = alien in alive
        val cloneIn = clone != null && clone in alive
        winner = when {
            players.size == 3 -> if (alienIn) Winner.Alien else Winner.Humans
            !alienIn && (!cloneIn || alive.size <= 2) -> Winner.Humans
            alienIn && alive.size <= 2 -> Winner.Alien
            else -> null
        }
    }

    /** +1 to every human if the humans win, +3 to the alien or the clone if they win. */
    fun points(): Map<String, Int> = when (winner) {
        Winner.Humans -> players.filter { it != alien && it != clone }.associateWith { 1 }
        Winner.Alien -> mapOf(alien to 3)
        Winner.Clone -> mapOf(clone!! to 3)
        null -> emptyMap()
    }
}

private enum class A { Intro, Reveal, Clues, Talk, Vote, Out, CloneGuess, Over }

@Composable
fun AlienGameScreen(host: GameHost) {
    val game = Games.byId("alien")!!
    val ctx = LocalContext.current
    val n = Store.night
    var phase by remember { mutableStateOf(A.Intro) }
    var withClone by remember { mutableStateOf(true) }
    var talk by remember { mutableIntStateOf(60) }
    val deck = remember { Deck("alien", Cards.get(ctx).alien, { it.a }) }
    var g by remember { mutableStateOf<AlienGame?>(null) }
    var gameNo by remember { mutableIntStateOf(0) }
    var lastOut by remember { mutableStateOf<String?>(null) }
    val tally = remember { Tally() }

    fun afterOut() {
        val gg = g!!
        phase = when {
            gg.winner != null -> A.Over
            else -> A.Clues
        }
    }

    when (phase) {
        A.Intro -> Intro(game, host, listOf(
            t("Κάποιος από την παρέα αντικαταστάθηκε από εξωγήινο. Έμαθε τη γλώσσα μας, αλλά όχι εντελώς…", "Someone in the group was replaced by an alien. It learned our language, but not quite…"),
            t("Όλοι βλέπουν κρυφά την ίδια λέξη. Ο εξωγήινος βλέπει μια παρόμοια και δεν ξέρει ότι είναι ο εξωγήινος.", "Everyone secretly sees the same word. The alien sees a similar one and doesn't know they're the alien."),
            t("Με τη σειρά, ο καθένας λέει μία λέξη-στοιχείο για τη λέξη του. Μετά συζήτηση και κρυφή ψηφοφορία.", "In turn, each says one clue word. Then a discussion and a secret vote."),
            t("Ο κλώνος (από 5 άτομα) δεν έχει λέξη και το ξέρει. Αν τον πιάσουν, μαντεύει τη λέξη για να κερδίσει.", "The clone (5+ players) has no word and knows it. If caught, they guess the word to win."),
            t("Νίκη: οι άνθρωποι +1 ο καθένας, ο εξωγήινος +3 αν μείνει στους δύο τελευταίους, ο κλώνος +3 αν βρει τη λέξη.", "Win: humans +1 each, the alien +3 if they reach the last two, the clone +3 if they find the word."),
        ), deck.size > 0, {
            if (n.players.size >= 5) Toggle(t("Με κλώνο", "With a clone"), null, withClone) { withClone = it }
            Body(t("Χρόνος συζήτησης", "Discussion time"), color = C.text)
            Seg(listOf(45 to "45″", 60 to "60″", 90 to "90″"), talk, { talk = it }, color = C.green)
        }) {
            g = AlienGame(n.names, deck.next()!!, withClone); gameNo++; phase = A.Reveal
        }

        A.Reveal -> { QuitGuard(host); RevealRound(n.names, key = gameNo, title = t("Η λέξη σου", "Your word"), secret = { p ->
            val w = g!!.wordOf(p)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (w == null) {
                    Text("🧬", fontSize = 48.sp)
                    Text(t("Είσαι ο κλώνος", "You are the clone"), style = Type.big, color = C.green, textAlign = TextAlign.Center)
                    Body(t("Δεν έχεις λέξη. Άκου τους άλλους και κάνε πως ξέρεις.", "You have no word. Listen and pretend you know."), align = TextAlign.Center)
                } else {
                    Text(w, style = Type.big, color = C.green, textAlign = TextAlign.Center)
                    Text(g!!.pair.c, style = Type.small, color = C.dim)
                }
            }
        }) { phase = A.Clues } }

        A.Clues -> {
            QuitGuard(host)
            val gg = g!!
            val order = remember(gg.round, gameNo) { gg.order() }
            Page(t("👽 Γύρος ${gg.round + 1}", "👽 Round ${gg.round + 1}"), onBack = null, bottom = {
                BigButton(t("Συζήτηση", "Discussion"), { phase = A.Talk }, color = C.green)
            }) {
                Body(t("Με αυτή τη σειρά, ο καθένας λέει δυνατά μία λέξη-στοιχείο για τη λέξη του. Όχι την ίδια τη λέξη!", "In this order, each says one clue word out loud. Not the word itself!"), color = C.text)
                order.forEachIndexed { i, p -> Text("${i + 1}. $p", style = Type.title, color = Color(n.colorOf(n.sideOf(p)))) }
            }
        }

        A.Talk -> {
            QuitGuard(host)
            val left = rememberCountdown(talk, running = true, key = g!!.round + gameNo * 100) { phase = A.Vote }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Text("👽", fontSize = 64.sp)
                Text(t("Ποιος μιλάει περίεργα;", "Who sounds strange?"), style = Type.title, color = C.text)
                TimerText(left, color = C.green)
                BigButton(t("Ψηφοφορία τώρα", "Vote now"), { phase = A.Vote }, color = C.green)
            }
        }

        A.Vote -> { QuitGuard(host); SecretVote(g!!.alive, g!!.alive, key = g!!.round + gameNo * 100, question = t("Ποιος είναι ο εξωγήινος;", "Who is the alien?")) { votes ->
            lastOut = Votes.top(votes)
            val gg = g!!
            if (lastOut != null && lastOut == gg.clone) { gg.out(lastOut); phase = A.CloneGuess }
            else { gg.out(lastOut); phase = A.Out }
            Sfx.end()
        } }

        A.CloneGuess -> {
            QuitGuard(host)
            val gg = g!!
            Page(t("Πιάσατε τον κλώνο!", "You caught the clone!"), onBack = null) {
                Text("🧬 ${gg.clone}", style = Type.big, color = C.green)
                Body(t("Έχει μία ευκαιρία: να πει δυνατά τη λέξη των ανθρώπων. Μετά πατήστε τι έγινε.", "One chance: say the humans' word out loud. Then press what happened."), color = C.text)
                var shown by remember { mutableStateOf(false) }
                if (!shown) GhostButton(t("Το είπε · δείξε τη λέξη", "Said it · show the word"), { shown = true }, Modifier.fillMaxWidth())
                else {
                    Panel { Text(gg.pair.h, style = Type.big, color = C.amber, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(t("✓ Τη βρήκε", "✓ Got it"), { gg.cloneGuessed(true); phase = A.Over }, Modifier.weight(1f), color = C.green)
                        GhostButton(t("✗ Όχι", "✗ No"), { gg.cloneGuessed(false); afterOut() }, Modifier.weight(1f), color = C.red)
                    }
                }
            }
        }

        A.Out -> {
            QuitGuard(host)
            val gg = g!!
            val p = lastOut
            Announce(
                if (p == null) "🤝" else if (p == gg.alien) "👽" else "🙂",
                if (p == null) t("Ισοψηφία", "A tie") else p,
                if (p == null) t("Δεν βγαίνει κανείς.", "Nobody leaves.") else t("Ήταν ${gg.roleOf(p)}.", "Was ${gg.roleOf(p)}."),
                if (gg.winner != null) t("Αποτελέσματα", "Results") else t("Επόμενος γύρος", "Next round"), C.green,
            ) { afterOut() }
        }

        A.Over -> {
            val gg = g!!
            remember(gameNo) {
                gg.points().forEach { (p, pts) -> tally.add(host, listOf(p), pts) }
                host.log(t("Εξωγήινος: ${gg.alien} («${gg.pair.x}» αντί για «${gg.pair.h}»)", "Alien: ${gg.alien} («${gg.pair.x}» instead of «${gg.pair.h}»)"))
                Sfx.win()
            }
            GameOver(game, host, tally, headline = when (gg.winner) {
                AlienGame.Winner.Alien -> t("Κέρδισε ο εξωγήινος!", "The alien wins!")
                AlienGame.Winner.Clone -> t("Κέρδισε ο κλώνος!", "The clone wins!")
                else -> t("Κέρδισαν οι άνθρωποι!", "The humans win!")
            }, extra = {
                Panel {
                    Text("👽 ${gg.alien}: «${gg.pair.x}»", style = Type.h, color = C.green)
                    gg.clone?.let { Text("🧬 $it: ${t("χωρίς λέξη", "no word")}", style = Type.body, color = C.text) }
                    Text(t("Οι άνθρωποι είχαν: «${gg.pair.h}»", "The humans had: «${gg.pair.h}»"), style = Type.body, color = C.muted)
                }
            }) { tally.bySide.clear(); phase = A.Intro }
        }
    }
}
