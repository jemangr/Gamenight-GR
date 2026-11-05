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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Tagged
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.HoldToReveal
import gr.wsol.gamenight.ui.Input
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown

/* ======================= 🔊 Αλυσίδα ήχων ======================= */
/**
 * The phone remembers the chain: after each sound, the one who made it writes two words about it
 * («γάτα που νιαουρίζει»). The others then see the whole chain while the next one repeats it.
 */
class SoundChain(players: List<String>) {
    val alive = players.toMutableList()
    val sounds = mutableListOf<Pair<String, String>>() // who → what
    var turn = 0
    var best = 0
        private set

    val current get() = alive[turn % alive.size]
    val over get() = alive.size <= 1

    /** [who] repeated the chain and added [what]. */
    fun ok(what: String) { sounds += current to what.trim(); best = maxOf(best, sounds.size); turn = (turn + 1) % alive.size }

    /** [current] got it wrong: out, the chain stays as it was for the next one. */
    fun out(): String { val p = current; val i = alive.indexOf(p); alive.removeAt(i); if (alive.isNotEmpty()) turn = i % alive.size; return p }
}

private enum class Chain11 { Intro, Pass, Judge, Add, Out, Over }

@Composable
fun ChainGame(host: GameHost) {
    val game = Games.byId("chain")!!
    val n = Store.night
    var phase by remember { mutableStateOf(Chain11.Intro) }
    var g by remember { mutableStateOf(SoundChain(n.names)) }
    var gameNo by remember { mutableIntStateOf(0) }
    var lastOut by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val tally = remember { Tally() }

    when (phase) {
        Chain11.Intro -> Intro(game, host, listOf(
            t("Ο πρώτος κάνει έναν ήχο (γάβγισμα, παλαμάκι, «μπιπ»…) και γράφει δυο λέξεις για να τον θυμάται το κινητό.", "The first makes a sound (a bark, a clap, a «beep»…) and writes two words so the phone remembers it."),
            t("Ο επόμενος επαναλαμβάνει όλη την αλυσίδα με τη σειρά και προσθέτει έναν δικό του.", "The next repeats the whole chain in order and adds one of their own."),
            t("Οι άλλοι βλέπουν την αλυσίδα στο κινητό και κρίνουν. Όποιος κάνει λάθος βγαίνει.", "The others see the chain on the phone and judge. Whoever slips is out."),
            t("+1 για κάθε σωστή επανάληψη, +3 σε όποιον μείνει τελευταίος.", "+1 for each right repeat, +3 to the last one standing."),
        )) { g = SoundChain(n.names.shuffled()); gameNo++; text = ""; phase = Chain11.Add }

        Chain11.Pass -> { QuitGuard(host); CardScreen(g.current, colorOf(g.current), "🔊", t("Επανάλαβε τους ${g.sounds.size} ήχους με τη σειρά και πρόσθεσε έναν.", "Repeat the ${g.sounds.size} sounds in order and add one."), t("Δώσε το κινητό σε κάποιον άλλο για να κρίνει.", "Hand the phone to someone else to judge."), game.color) {
            BigButton(t("Ξεκίνα · κρίνουν οι άλλοι", "Go · the others judge"), { phase = Chain11.Judge }, color = game.color)
        } }

        Chain11.Judge -> {
            QuitGuard(host)
            Page(t("Κρίνετε τον/την ${g.current}", "Judge ${g.current}"), onBack = null, bottom = {
                JudgeButtons(game.color, yes = t("✓ Σωστά", "✓ Right"), no = t("✗ Λάθος", "✗ Wrong")) { ok ->
                    if (ok) { text = ""; phase = Chain11.Add } else { lastOut = g.out(); host.stat(lastOut, "out"); phase = Chain11.Out }
                }
            }) {
                Label(t("Η ΑΛΥΣΙΔΑ (ΜΗΝ ΤΗΝ ΔΕΙΧΝΕΤΕ)", "THE CHAIN (DON'T SHOW IT)"))
                Panel { g.sounds.forEachIndexed { i, (who, what) -> Row { Text("${i + 1}. ", style = Type.h, color = C.dim); Text(what, style = Type.h, color = C.text, modifier = Modifier.weight(1f)); Text(who, style = Type.small, color = colorOf(who)) } } }
            }
        }

        Chain11.Add -> {
            QuitGuard(host)
            val who = g.current
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(who, style = Type.h, color = colorOf(who))
                Text(if (g.sounds.isEmpty()) t("Κάνε τον πρώτο ήχο και γράψε τι ήταν.", "Make the first sound and write what it was.") else t("Τι ήχο πρόσθεσες;", "What sound did you add?"), style = Type.title, color = C.text)
                Input(text, { text = it.take(30) }, t("π.χ. γάτα, παλαμάκι, τρένο", "e.g. cat, clap, train"))
                BigButton(t("Έτοιμο", "Done"), {
                    if (g.sounds.isNotEmpty()) tally.add(host, listOf(who), 1)
                    g.ok(text); Sfx.correct()
                    phase = Chain11.Pass
                }, enabled = text.isNotBlank(), color = game.color)
            }
        }

        Chain11.Out -> {
            QuitGuard(host)
            Announce("💥", lastOut, t("Βγαίνει. Η αλυσίδα έχει ${g.sounds.size} ήχους.", "Is out. The chain has ${g.sounds.size} sounds."), if (g.over) t("Αποτελέσματα", "Results") else t("Συνεχίζουμε", "Carry on"), game.color) {
                if (g.over) {
                    g.alive.firstOrNull()?.let { tally.add(host, listOf(it), 3); host.log(t("Αλυσίδα ήχων: ${it} (${g.best} ήχοι)", "Sound chain: $it (${g.best} sounds)")) }
                    Sfx.win(); phase = Chain11.Over
                } else phase = Chain11.Pass
            }
        }

        Chain11.Over -> GameOver(game, host, tally, extra = {
            Panel { Text(t("Ρεκόρ αλυσίδας: ${g.best} ήχοι", "Longest chain: ${g.best} sounds"), style = Type.h, color = game.color) }
        }) { tally.bySide.clear(); phase = Chain11.Intro }
    }
}

/* ======================= 🙊 Μίμηση με κανόνα ======================= */
object MimeRules {
    /** One action and one rule; a 18+ card only when allowed (the deck already filters them). */
    fun pair(actions: Deck<Tagged>, rules: Deck<Tagged>): Pair<Tagged, Tagged>? {
        val a = actions.next() ?: return null
        val r = rules.next() ?: return null
        return a to r
    }
}

private enum class Mime11 { Intro, Pass, Look, Play, Over }

@Composable
fun MimeGame(host: GameHost) {
    val game = Games.byId("mime")!!
    val ctx = LocalContext.current
    val n = Store.night
    val all = remember { Cards.get(ctx).mime }
    val actions = remember { Deck("mime-a", all.filter { it.c == "a" }, { it.a }) }
    val rules = remember { Deck("mime-r", all.filter { it.c == "r" }, { it.a }) }
    var phase by remember { mutableStateOf(Mime11.Intro) }
    var each by remember { mutableIntStateOf(2) }
    var secs by remember { mutableIntStateOf(60) }
    var turn by remember { mutableIntStateOf(0) }
    var card by remember { mutableStateOf<Pair<Tagged, Tagged>?>(null) }
    val tally = remember { Tally() }
    val total = Turns.count(n, each)
    val actor = Turns.player(n, turn)

    fun next() { if (turn + 1 < total) { turn++; card = MimeRules.pair(actions, rules); phase = Mime11.Pass } else phase = Mime11.Over }

    when (phase) {
        Mime11.Intro -> Intro(game, host, listOf(
            t("Παντομίμα, αλλά κάθε φορά με έναν κανόνα: «μόνο με το ένα χέρι», «σαν να είσαι 90 χρονών»…", "Mime, but each time with a rule: «one hand only», «as if you were 90»…"),
            t("Βλέπεις κρυφά τι παίζεις και τον κανόνα. Χωρίς λόγια και ήχους.", "You see in secret what to act and the rule. No words, no sounds."),
            t("Όποιος το βρει πρώτος παίρνει +1, και +1 ο ηθοποιός. Αν σπάσεις τον κανόνα, δεν μετράει.", "Whoever gets it first scores +1, and the actor +1. Break the rule and it doesn't count."),
        ), actions.size > 0 && rules.size > 0, {
            Body(t("Γύροι ο καθένας", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = game.color)
            Body(t("Χρόνος", "Time"), color = C.text)
            Seg(listOf(45 to "45″", 60 to "60″", 90 to "90″"), secs, { secs = it }, color = game.color)
        }) { turn = 0; card = MimeRules.pair(actions, rules); phase = Mime11.Pass }

        Mime11.Pass -> { QuitGuard(host); PassTo(actor, colorOf(actor), note = t("Μόνο εσύ βλέπεις τι θα παίξεις.", "Only you see what to act.")) { phase = Mime11.Look } }

        Mime11.Look -> {
            QuitGuard(host)
            var seen by remember(turn) { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(actor, style = Type.h, color = colorOf(actor))
                HoldToReveal(Modifier.weight(1f), onSeen = { seen = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(card?.first?.t ?: "", style = Type.big, color = game.color, textAlign = TextAlign.Center)
                        Label(t("ΚΑΝΟΝΑΣ", "RULE"))
                        Text(card?.second?.t ?: "", style = Type.title, color = C.text, textAlign = TextAlign.Center)
                    }
                }
                BigButton(t("Πάμε! Ξεκινάει ο χρόνος", "Go! The clock starts"), { phase = Mime11.Play }, enabled = seen, color = game.color)
            }
        }

        Mime11.Play -> {
            QuitGuard(host)
            val left = rememberCountdown(secs, running = true, key = turn) { Sfx.wrong(); next() }
            val guessers = if (n.teams) Turns.others(n, actor) else n.names.filter { it != actor }
            Page(t("🙊 $actor παίζει", "🙊 $actor is acting"), onBack = null, bottom = {
                GhostButton(t("Δεν το βρήκε κανείς", "Nobody got it"), { Sfx.wrong(); host.log(t("Μίμηση: κανείς δεν βρήκε «${card?.first?.t}»", "Mime: nobody got «${card?.first?.t}»")); next() }, Modifier.fillMaxWidth())
            }) {
                TimerText(left, color = game.color, modifier = Modifier.fillMaxWidth())
                Label(t("ΚΑΝΟΝΑΣ (ΤΟΝ ΒΛΕΠΟΥΝ ΟΛΟΙ)", "RULE (EVERYONE SEES IT)"))
                Text(card?.second?.t ?: "", style = Type.title, color = C.text)
                Label(t("ΠΟΙΟΣ ΤΟ ΒΡΗΚΕ;", "WHO GOT IT?"))
                guessers.forEach { p ->
                    GhostButton(p, {
                        Sfx.correct(); tally.add(host, listOf(p), 1)
                        if (!n.teams || n.sideOf(p) != n.sideOf(actor)) tally.add(host, listOf(actor), 1)
                        host.log(t("Μίμηση: $p βρήκε «${card?.first?.t}»", "Mime: $p got «${card?.first?.t}»")); next()
                    }, Modifier.fillMaxWidth(), color = colorOf(p))
                }
            }
        }

        Mime11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Mime11.Intro }
    }
}
