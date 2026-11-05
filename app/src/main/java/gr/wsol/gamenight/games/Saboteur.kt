package gr.wsol.gamenight.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Mission
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.Type

object SaboteurRules {
    /** Caught: +1 to everyone else. Not caught: +3 to the saboteur, +1 more if the story reached their word. */
    fun points(players: List<String>, saboteur: String, caught: Boolean, wordSaid: Boolean): Map<String, Int> =
        if (caught) players.filter { it != saboteur }.associateWith { 1 } else mapOf(saboteur to 3 + if (wordSaid) 1 else 0)
}

private enum class S { Intro, Reveal, Rounds, Vote, Result, Over }

@Composable
fun SaboteurGame(host: GameHost) {
    val game = Games.byId("saboteur")!!
    val ctx = LocalContext.current
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(S.Intro) }
    val deck = remember { Deck("missions", Cards.get(ctx).missions, { it.a }) }
    var mission by remember { mutableStateOf<Mission?>(null) }
    var saboteur by remember { mutableStateOf("") }
    var order by remember { mutableStateOf(listOf<String>()) }
    var gameNo by remember { mutableIntStateOf(0) }
    var accused by remember { mutableStateOf("") }
    val tally = remember { Tally() }

    when (phase) {
        S.Intro -> Intro(game, host, listOf(
            t("Κάθε γύρος έχει μια κοινή αποστολή: μια λίστα («πείτε πρωτεύουσες») ή μια ιστορία, μία πρόταση ο καθένας.", "Each round has a shared mission: a list («name capitals») or a story, one sentence each."),
            t("Κρυφά, ένας είναι ο σαμποτέρ, με μυστική αποστολή: να πει μια απάντηση που μοιάζει σωστή αλλά δεν είναι, ή να φέρει την ιστορία σε μια απαγορευμένη λέξη.", "Secretly, one is the saboteur with a secret task: give an answer that sounds right but isn't, or steer the story to a forbidden word."),
            t("Δύο γύροι με τη σειρά, μετά δείχνετε όλοι μαζί τον ύποπτο.", "Two rounds in turn, then everyone points at the suspect."),
            t("Τον πιάσατε: +1 σε όλους τους άλλους. Δεν τον πιάσατε: +3 στον σαμποτέρ, +1 αν είπε την απαγορευμένη λέξη.", "Caught: +1 to everyone else. Not caught: +3 to the saboteur, +1 if the forbidden word came up."),
        ), deck.size > 0 && players.size >= 3) {
            mission = deck.next(); saboteur = players.random(); order = players.shuffled(); gameNo++; phase = S.Reveal
        }

        S.Reveal -> { QuitGuard(host); RevealRound(players, key = gameNo, title = t("Ο ρόλος σου", "Your role"), secret = { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (p == saboteur) {
                    Text("🧨", style = Type.huge)
                    Text(t("Είσαι ο σαμποτέρ", "You are the saboteur"), style = Type.title, color = C.red, textAlign = TextAlign.Center)
                    val m = mission!!
                    Body(if (m.story) t("Οδήγησε την ιστορία να ειπωθεί η λέξη: «${m.word}»", "Steer the story until someone says: «${m.word}»") else t("Κάποια στιγμή πες ως απάντηση: «${m.trap}». Μοιάζει σωστό, αλλά δεν είναι!", "At some point answer: «${m.trap}». It sounds right, but it isn't!"), color = C.text, align = TextAlign.Center)
                } else {
                    Text("🙂", style = Type.huge)
                    Text(t("Είσαι πολίτης", "You are a citizen"), style = Type.title, color = C.green, textAlign = TextAlign.Center)
                    Body(t("Βοήθα την αποστολή και πρόσεχε ποιος τη χαλάει.", "Help the mission and watch who wrecks it."), align = TextAlign.Center)
                }
            }
        }) { phase = S.Rounds } }

        S.Rounds -> {
            QuitGuard(host)
            var turn by remember(gameNo) { mutableIntStateOf(0) }
            val who = order[turn % order.size]
            Page(t("🧨 Γύρος ${turn / order.size + 1} από 2", "🧨 Round ${turn / order.size + 1} of 2"), onBack = null, bottom = {
                BigButton(if (turn + 1 < order.size * 2) t("Επόμενος", "Next") else t("Ψηφοφορία", "Vote"), { if (turn + 1 < order.size * 2) { turn++; Sfx.tap() } else phase = S.Vote }, color = C.red)
            }) {
                Panel { Text(mission!!.t, style = Type.title, color = C.text) }
                Text(t("Σειρά του/της", "Turn of"), style = Type.small, color = C.muted)
                Text(who, style = Type.big, color = Color(n.colorOf(n.sideOf(who))))
                Body(if (mission!!.story) t("Πες δυνατά την επόμενη πρόταση της ιστορίας.", "Say the next sentence of the story out loud.") else t("Πες δυνατά μία απάντηση. Χωρίς επαναλήψεις!", "Say one answer out loud. No repeats!"), color = C.text)
            }
        }

        S.Vote -> { QuitGuard(host); Page(t("Ποιος είναι ο σαμποτέρ;", "Who is the saboteur?"), onBack = null) {
            Body(t("Μετρήστε ως το τρία και δείξτε όλοι μαζί. Πατήστε αυτόν με τις περισσότερες ψήφους.", "Count to three and all point together. Tap the one with most votes."), color = C.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                players.forEach { p -> Chip(p, color = Color(n.colorOf(p)), onClick = { accused = p; phase = S.Result }) }
            }
        } }

        S.Result -> {
            QuitGuard(host)
            val caught = accused == saboteur
            val m = mission!!
            var said by remember(gameNo) { mutableStateOf<Boolean?>(if (m.story && !caught) null else false) }
            Page(if (caught) t("Τον πιάσατε! 🎉", "Caught! 🎉") else t("Σας ξέφυγε! 🧨", "Got away! 🧨"), onBack = null, bottom = {
                BigButton(t("Αποτελέσματα", "Results"), {
                    tally.addAll(host, SaboteurRules.points(players, saboteur, caught, said == true))
                    if (!caught) host.stat(saboteur, "sneaky")
                    host.log(t("Σαμποτέρ: $saboteur ${if (caught) "πιάστηκε" else "ξέφυγε"}", "Saboteur: $saboteur ${if (caught) "caught" else "got away"}"))
                    Sfx.win(); phase = S.Over
                }, color = C.red, enabled = said != null)
            }) {
                Text(t("Ο σαμποτέρ ήταν:", "The saboteur was:"), style = Type.body, color = C.muted)
                Text(saboteur, style = Type.big, color = C.red)
                Panel { Body(if (m.story) t("Η μυστική λέξη: «${m.word}»", "The secret word: «${m.word}»") else t("Η παγίδα: «${m.trap}»", "The trap: «${m.trap}»"), color = C.text) }
                if (m.story && !caught) {
                    Body(t("Ειπώθηκε η λέξη «${m.word}» στην ιστορία;", "Did the word «${m.word}» come up in the story?"), color = C.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip(t("Ναι", "Yes"), selected = said == true, color = C.red, onClick = { said = true }, modifier = Modifier.weight(1f))
                        Chip(t("Όχι", "No"), selected = said == false, color = C.red, onClick = { said = false }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        S.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = S.Intro }
    }
}
