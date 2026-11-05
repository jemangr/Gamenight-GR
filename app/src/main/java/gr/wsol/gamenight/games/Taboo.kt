package gr.wsol.gamenight.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.TabooCard
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown

/** Scoring of «Πες το αλλιώς», kept apart from the screens so it can be tested. */
object TabooRules {
    const val MAX_PASSES = 3

    /**
     * Points of one correct guess. Teams: the describer's team +1. Solo: describer +1 and guesser +1.
     * Two players play together (no points, a shared record).
     */
    fun correct(teams: Boolean, players: Int, describer: String, guesser: String?): List<Pair<String, Int>> = when {
        players == 2 && !teams -> emptyList()
        teams -> listOf(describer to 1)
        guesser != null -> listOf(describer to 1, guesser to 1)
        else -> listOf(describer to 1)
    }

    /** Saying a banned word: −1 to the describer (or their team). */
    fun banned(players: Int, teams: Boolean, describer: String): List<Pair<String, Int>> =
        if (players == 2 && !teams) emptyList() else listOf(describer to -1)
}

private enum class TabooPhase { Intro, Pass, Play, Turn, Over }

@Composable
fun TabooGame(host: GameHost) {
    val game = Games.byId("taboo")!!
    val ctx = LocalContext.current
    val n = Store.night
    val coop = n.players.size == 2 && !n.teams
    var phase by remember { mutableStateOf(TabooPhase.Intro) }
    var seconds by remember { mutableIntStateOf(60) }
    var each by remember { mutableIntStateOf(2) }
    var turn by remember { mutableIntStateOf(0) }
    val deck = remember { Deck("taboo", Cards.get(ctx).taboo, { it.a }) }
    var card by remember { mutableStateOf<TabooCard?>(null) }
    val done = remember { mutableStateListOf<Pair<TabooCard, String>>() } // card and «ok» / «pass» / «no»
    var passes by remember { mutableIntStateOf(0) }
    var coopTotal by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }
    val total = Turns.count(n, each)
    val describer = Turns.player(n, turn)

    fun next() { card = deck.next() }

    when (phase) {
        TabooPhase.Intro -> Intro(
            game, host,
            rules = listOf(
                t("Ο παίκτης περιγράφει τη λέξη χωρίς να πει καμία από τις 5 απαγορευμένες, ούτε κομμάτι τους.", "The player describes the word without saying any of the 5 banned words, or part of them."),
                if (n.teams) t("Μαντεύουν οι συμπαίκτες του. +1 στην ομάδα για κάθε σωστή, −1 για απαγορευμένη.", "Their teammates guess. +1 to the team per correct word, −1 for a banned word.")
                else if (coop) t("Με 2 άτομα παίζετε μαζί: περιγράφετε εναλλάξ και κυνηγάτε το ρεκόρ σας.", "With 2 players you play together: take turns describing and chase your record.")
                else t("Όποιος το βρει πρώτος παίρνει +1, και +1 παίρνει και αυτός που περιγράφει. Απαγορευμένη λέξη: −1.", "Whoever gets it first gets +1, and the describer gets +1 too. Banned word: −1."),
                t("Έως 3 πάσο σε κάθε γύρο.", "Up to 3 passes per turn."),
            ),
            startEnabled = deck.size > 0,
            options = {
                Body(t("Χρόνος ανά γύρο", "Time per turn"), color = C.text)
                Seg(listOf(30 to "30″", 60 to "60″", 90 to "90″"), seconds, { seconds = it })
                Body(t("Γύροι για τον καθένα", "Turns each"), color = C.text)
                Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it })
                Text(t("${deck.size} κάρτες", "${deck.size} cards"), style = Type.small, color = C.dim)
            },
        ) { turn = 0; phase = TabooPhase.Pass }

        TabooPhase.Pass -> {
            QuitGuard(host)
            PassTo(describer, Color(n.colorOf(n.sideOf(describer))),
                note = t("Γύρος ${turn + 1} από $total. Μόνο εσύ βλέπεις την κάρτα.", "Turn ${turn + 1} of $total. Only you see the card."),
                button = t("Ξεκίνα τον χρόνο", "Start the clock")) {
                done.clear(); passes = 0; next(); phase = TabooPhase.Play
            }
        }

        TabooPhase.Play -> {
            QuitGuard(host)
            val left = rememberCountdown(seconds, running = true, key = turn) { phase = TabooPhase.Turn }
            val c = card
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("ΠΕΡΙΓΡΑΦΕΙ", "DESCRIBING"), style = Type.label, color = C.dim)
                        Text(describer, style = Type.h, color = Color(n.colorOf(n.sideOf(describer))))
                    }
                    Text("✓ ${done.count { it.second == "ok" }}", style = Type.h, color = C.green)
                }
                TimerText(left)
                if (c == null) Body(t("Τελείωσαν οι κάρτες.", "Out of cards."))
                else Column(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(C.panel).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(c.w, style = Type.big, color = C.amber, textAlign = TextAlign.Center)
                    HorizontalDivider(color = C.line)
                    Text(t("ΜΗ ΠΕΙΣ", "DON'T SAY"), style = Type.label, color = C.red)
                    c.no.forEach { Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.text) }
                }
                if (c != null) {
                    if (!n.teams && !coop) {
                        Text(t("Ποιος το βρήκε;", "Who got it?"), style = Type.small, color = C.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Turns.others(n, describer).forEach { g ->
                                Chip(g, color = Color(n.colorOf(g)), onClick = {
                                    TabooRules.correct(false, n.players.size, describer, g).forEach { (who, p) -> tally.add(host, listOf(who), p) }
                                    Sfx.correct(); Sfx.buzz(25); done.add(c to "ok"); next()
                                })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(t("Πάσο (${TabooRules.MAX_PASSES - passes})", "Pass (${TabooRules.MAX_PASSES - passes})"), {
                            passes++; Sfx.pass(); done.add(c to "pass"); next()
                        }, Modifier.weight(1f), enabled = passes < TabooRules.MAX_PASSES)
                        GhostButton(t("✗ Απαγορευμένη", "✗ Banned"), {
                            host.stat(describer, "banned"); TabooRules.banned(n.players.size, n.teams, describer).forEach { (who, p) -> tally.add(host, listOf(who), p) }
                            Sfx.wrong(); Sfx.buzz(200, true); done.add(c to "no"); next()
                        }, Modifier.weight(1f), color = C.red)
                    }
                    if (n.teams || coop) BigButton(t("✓ Σωστό", "✓ Correct"), {
                        TabooRules.correct(n.teams, n.players.size, describer, null).forEach { (who, p) -> tally.add(host, listOf(who), p) }
                        if (coop) coopTotal++
                        Sfx.correct(); Sfx.buzz(25); done.add(c to "ok"); next()
                    }, color = C.green, height = 72.dp)
                }
            }
        }

        TabooPhase.Turn -> {
            QuitGuard(host)
            val ok = done.count { it.second == "ok" }
            Page(t("Τέλος χρόνου", "Time's up"), onBack = null, bottom = {
                BigButton(if (turn + 1 < total) t("Επόμενος", "Next") else t("Αποτελέσματα", "Results"), {
                    host.log(t("Πες το αλλιώς: $describer βρήκε $ok", "Say it otherwise: $describer got $ok"))
                    if (turn + 1 < total) { turn++; phase = TabooPhase.Pass } else phase = TabooPhase.Over
                }, color = game.color)
            }) {
                Text("$describer: $ok ✓", style = Type.big, color = C.amber)
                done.forEach { (c, r) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(when (r) { "ok" -> "✓"; "no" -> "✗"; else -> "→" }, color = when (r) { "ok" -> C.green; "no" -> C.red; else -> C.dim }, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.padding(start = 10.dp))
                        Text(c.w, style = Type.body, color = C.text)
                    }
                }
                card?.let { Text(t("Έμεινε: ${it.w}", "Left on screen: ${it.w}"), style = Type.small, color = C.dim) }
            }
        }

        TabooPhase.Over -> GameOver(game, host, tally, extra = if (coop) ({
            val key = "taboo-coop-$seconds-$each" // a record per length of game
            val record = remember { Store.record(key, coopTotal) }
            Panel {
                Text(t("Μαζί βρήκατε $coopTotal", "Together you got $coopTotal"), style = Type.title, color = C.text)
                Text(if (record) t("Νέο ρεκόρ! 🎉", "New record! 🎉") else t("Ρεκόρ: ${Store.best(key)}", "Record: ${Store.best(key)}"), style = Type.body, color = C.amber)
            }
        }) else null) {
            turn = 0; coopTotal = 0; tally.bySide.clear(); phase = TabooPhase.Intro
        }
    }
}
