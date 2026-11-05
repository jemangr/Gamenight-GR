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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Place
import gr.wsol.gamenight.core.Prompt
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Tagged
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Toggle
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown
import kotlin.random.Random

/* ======================= 🤫 Ψίθυρος στο σκοτάδι ======================= */
object WhisperRules {
    /** Who has a secret word: one, or two from 6 players when asked. */
    fun suspects(players: List<String>, two: Boolean, rnd: Random = Random): List<String> =
        players.shuffled(rnd).take(if (two && players.size >= 6) 2 else 1)

    /**
     * Caught suspects (on top of the vote): +1 to each who voted for them. A suspect who wasn't
     * caught and said the word: +3. [said] = suspects the group agrees said their word.
     */
    fun points(votes: Map<String, String>, suspects: List<String>, said: Set<String>): Map<String, Int> {
        val tops = Tally11.tops(votes)
        val out = mutableMapOf<String, Int>()
        votes.forEach { (v, c) -> if (c in suspects && c in tops) out[v] = (out[v] ?: 0) + 1 }
        suspects.forEach { s -> if (s !in tops && s in said) out[s] = (out[s] ?: 0) + 3 }
        return out
    }
}

private enum class Whisper11 { Intro, Reveal, Talk, Vote, Judge, Result, Over }

@Composable
fun WhisperGame(host: GameHost) {
    val game = Games.byId("whisper")!!
    val ctx = LocalContext.current
    val n = Store.night
    val all = remember { Cards.get(ctx).whisper }
    val topics = remember { Deck("whisper-t", all.filter { it.c == "t" }, { it.a }) }
    val words = remember { Deck("whisper-w", all.filter { it.c == "w" }, { it.a }) }
    var phase by remember { mutableStateOf(Whisper11.Intro) }
    var two by remember { mutableStateOf(false) }
    var secs by remember { mutableIntStateOf(120) }
    var rounds by remember { mutableIntStateOf(3) }
    var round by remember { mutableIntStateOf(0) }
    var topic by remember { mutableStateOf<Tagged?>(null) }
    var sus by remember { mutableStateOf(listOf<String>()) }
    var word by remember { mutableStateOf(mapOf<String, String>()) }
    var votes by remember { mutableStateOf(mapOf<String, String>()) }
    var said by remember { mutableStateOf(setOf<String>()) }
    val tally = remember { Tally() }

    fun deal() {
        topic = topics.next(); sus = WhisperRules.suspects(n.names, two)
        word = sus.associateWith { words.next()?.t ?: "" }; said = emptySet(); phase = Whisper11.Reveal
    }

    when (phase) {
        Whisper11.Intro -> Intro(game, host, listOf(
            t("Όλοι βλέπουν κρυφά ένα θέμα κουβέντας. Ένας «ύποπτος» βλέπει και μια μυστική λέξη.", "Everyone secretly sees a topic. One «suspect» also sees a secret word."),
            t("Συζητάτε για το θέμα. Ο ύποπτος πρέπει να πει τη λέξη του φυσικά, χωρίς να τον καταλάβουν.", "You chat about the topic. The suspect must say their word naturally, without being spotted."),
            t("Στο τέλος κρυφή ψηφοφορία. Όσοι βρουν τον ύποπτο +1. Αν δεν τον βρουν και είπε τη λέξη, +3 στον ύποπτο.", "Then a secret vote. Whoever spots the suspect +1. If not, and they said the word, +3 to the suspect."),
        ), topics.size > 0 && words.size > 0, {
            if (n.players.size >= 6) Toggle(t("Δύο ύποπτοι", "Two suspects"), t("Ο καθένας με τη δική του λέξη.", "Each with their own word."), two) { two = it }
            Body(t("Χρόνος κουβέντας", "Chat time"), color = C.text)
            Seg(listOf(90 to "90″", 120 to "2′", 180 to "3′"), secs, { secs = it }, color = game.color)
            Body(t("Γύροι", "Rounds"), color = C.text)
            Seg(listOf(1 to "1", 3 to "3", 5 to "5"), rounds, { rounds = it }, color = game.color)
        }) { round = 0; deal() }

        Whisper11.Reveal -> { QuitGuard(host); RevealRound(n.names, key = round, title = t("Θέμα και ρόλος", "Topic and role"), secret = { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(t("ΘΕΜΑ", "TOPIC"))
                Text(topic?.t ?: "", style = Type.title, color = C.text, textAlign = TextAlign.Center)
                if (p in sus) {
                    Text("🤫", fontSize = 40.sp)
                    Text(t("Είσαι ύποπτος", "You are a suspect"), style = Type.h, color = game.color)
                    Body(t("Πες φυσικά μέσα στην κουβέντα τη λέξη:", "Say this word naturally in the chat:"), align = TextAlign.Center)
                    Text(word[p] ?: "", style = Type.big, color = game.color, textAlign = TextAlign.Center)
                } else Body(t("Δεν έχεις μυστική λέξη. Πρόσεχε ποιος λέει κάτι περίεργο.", "No secret word. Watch for anyone saying something odd."), align = TextAlign.Center)
            }
        }) { phase = Whisper11.Talk } }

        Whisper11.Talk -> {
            QuitGuard(host)
            val left = rememberCountdown(secs, running = true, key = round) { phase = Whisper11.Vote }
            CardScreen(t("Γύρος ${round + 1} / $rounds", "Round ${round + 1} / $rounds"), C.dim, "🤫", topic?.t ?: "", t("Συζητήστε για το θέμα.", "Chat about the topic."), game.color) {
                TimerText(left, color = game.color, modifier = Modifier.fillMaxWidth())
                BigButton(t("Ψηφοφορία τώρα", "Vote now"), { phase = Whisper11.Vote }, color = game.color)
            }
        }

        Whisper11.Vote -> { QuitGuard(host); SecretVote(n.names, n.names, key = round, question = t("Ποιος έχει μυστική λέξη;", "Who has a secret word?")) { votes = it; Sfx.end(); phase = Whisper11.Judge } }

        Whisper11.Judge -> {
            QuitGuard(host)
            val todo = sus.filter { it !in said }
            Page(t("Ποιος ήταν;", "Who was it?"), onBack = null, bottom = {
                BigButton(t("Αποτελέσματα γύρου", "Round results"), {
                    WhisperRules.points(votes, sus, said).forEach { (p, v) -> tally.add(host, listOf(p), v) }
                    host.log(t("Ψίθυρος: ${sus.joinToString { "$it («${word[it]}»)" }}", "Whisper: ${sus.joinToString { "$it («${word[it]}»)" }}"))
                    phase = Whisper11.Result
                }, color = game.color)
            }) {
                sus.forEach { s ->
                    Panel {
                        Text("🤫 $s", style = Type.title, color = colorOf(s))
                        Text("«${word[s]}»", style = Type.big, color = game.color)
                        if (s in said) Text(t("✓ Την είπε", "✓ Said it"), style = Type.h, color = C.green)
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Body(t("Την είπε μέσα στην κουβέντα;", "Did they say it in the chat?"), color = C.text, modifier = Modifier.weight(1f).padding(top = 10.dp))
                            GhostButton(t("✓ Ναι", "✓ Yes"), { said = said + s; Sfx.correct() }, color = C.green)
                        }
                    }
                }
                if (todo.isNotEmpty()) Body(t("Αν δεν την είπε, πατήστε απλώς «Αποτελέσματα».", "If not, just press «Results»."))
            }
        }

        Whisper11.Result -> {
            QuitGuard(host)
            val tops = Tally11.tops(votes)
            Page(t("Γύρος ${round + 1}", "Round ${round + 1}"), onBack = null, bottom = {
                BigButton(if (round + 1 < rounds) t("Επόμενος γύρος", "Next round") else t("Αποτελέσματα", "Results"), { if (round + 1 < rounds) { round++; deal() } else phase = Whisper11.Over }, color = game.color)
            }) {
                sus.forEach { s ->
                    val caught = s in tops
                    Panel { Text(if (caught) t("🔦 Πιάσατε τον/την $s", "🔦 You caught $s") else t("😎 Ο/Η $s ξέφυγε", "😎 $s got away"), style = Type.h, color = if (caught) C.green else game.color) }
                }
                Panel { Tally11.counts(votes).forEach { (p, c) -> Row { Text(p, style = Type.body, color = colorOf(p), modifier = Modifier.weight(1f)); Text("$c", style = Type.h, color = C.text) } } }
            }
        }

        Whisper11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Whisper11.Intro }
    }
}

/* ======================= 🕶️ Ο κατάσκοπος στο σπίτι ======================= */
object SpyRules {
    /** Everyone's role at [place]; the spy gets null. Roles repeat if there are more players. */
    fun deal(players: List<String>, place: Place, rnd: Random = Random): Pair<String, Map<String, String?>> {
        val spy = players.random(rnd)
        val roles = place.roles.shuffled(rnd)
        var k = 0
        return spy to players.associateWith { if (it == spy) null else roles.getOrElse(k++ % roles.size.coerceAtLeast(1)) { "" } }
    }

    /** The real place among [n] others, for the spy to pick from. */
    fun choices(real: Place, all: List<Place>, n: Int = 9, rnd: Random = Random): List<String> =
        (all.map { it.place }.filter { it != real.place }.distinct().shuffled(rnd).take(n) + real.place).shuffled(rnd)

    /** Spy wins: +3 to the spy. Group wins: +1 to everyone else. */
    fun points(players: List<String>, spy: String, spyWins: Boolean): Map<String, Int> =
        if (spyWins) mapOf(spy to 3) else players.filter { it != spy }.associateWith { 1 }
}

private enum class Spy11 { Intro, Reveal, Ask, Vote, Caught, Guess, Over }

@Composable
fun SpyGame(host: GameHost) {
    val game = Games.byId("spy")!!
    val ctx = LocalContext.current
    val n = Store.night
    val places = remember { Cards.get(ctx).spy }
    val deck = remember { Deck("spy", places, { it.a }) }
    var phase by remember { mutableStateOf(Spy11.Intro) }
    var mins by remember { mutableIntStateOf(5) }
    var gameNo by remember { mutableIntStateOf(0) }
    var place by remember { mutableStateOf<Place?>(null) }
    var spy by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf(mapOf<String, String?>()) }
    var accused by remember { mutableStateOf<String?>(null) }
    var spyWins by remember { mutableStateOf(false) }
    var guessed by remember { mutableStateOf<String?>(null) }
    val tally = remember { Tally() }

    fun finish(win: Boolean) {
        spyWins = win
        SpyRules.points(n.names, spy, win).forEach { (p, v) -> tally.add(host, listOf(p), v) }
        if (win) host.stat(spy, "sneaky")
        host.log(t("Κατάσκοπος: $spy · ${place?.place}", "Spy: $spy · ${place?.place}"))
        Sfx.win(); phase = Spy11.Over
    }

    when (phase) {
        Spy11.Intro -> Intro(game, host, listOf(
            t("Όλοι μαθαίνουν κρυφά το μέρος (π.χ. «Νοσοκομείο») και τον ρόλο τους εκεί. Ένας είναι ο κατάσκοπος και δεν ξέρει το μέρος.", "Everyone secretly learns the place (e.g. «Hospital») and their role there. One is the spy and doesn't know the place."),
            t("Με τη σειρά, ο καθένας κάνει μια ερώτηση σε όποιον θέλει: «Πόσο συχνά έρχεσαι εδώ;». Οι απαντήσεις να μην το μαρτυράνε!", "In turn, each asks anyone a question: «How often do you come here?». Answers shouldn't give it away!"),
            t("Ο κατάσκοπος μπορεί όποτε θέλει να πει «Ξέρω το μέρος!» και να το διαλέξει.", "The spy may at any time say «I know the place!» and pick it."),
            t("Αν τον πιάσετε, έχει μία ευκαιρία να βρει το μέρος. Νίκη κατασκόπου +3, αλλιώς +1 σε όλους τους άλλους.", "If caught, the spy has one chance to name the place. Spy wins +3, otherwise +1 to everyone else."),
            t("Διαφορά από τον Εξωγήινο: εδώ ο κατάσκοπος ξέρει ότι είναι κατάσκοπος.", "Unlike the Alien: here the spy knows they are the spy."),
        ), deck.size > 0, {
            Body(t("Χρόνος ερωτήσεων", "Question time"), color = C.text)
            Seg(listOf(3 to "3′", 5 to "5′", 8 to "8′"), mins, { mins = it }, color = game.color)
        }) {
            val p = deck.next() ?: return@Intro
            place = p; SpyRules.deal(n.names, p).let { (s, r) -> spy = s; roles = r }
            accused = null; guessed = null; gameNo++; phase = Spy11.Reveal
        }

        Spy11.Reveal -> { QuitGuard(host); RevealRound(n.names, key = gameNo, title = t("Το μέρος και ο ρόλος σου", "The place and your role"), secret = { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p == spy) {
                    Text("🕶️", fontSize = 48.sp)
                    Text(t("Είσαι ο κατάσκοπος", "You are the spy"), style = Type.big, color = game.color, textAlign = TextAlign.Center)
                    Body(t("Δεν ξέρεις το μέρος. Άκου τις ερωτήσεις και μάντεψε.", "You don't know the place. Listen and work it out."), align = TextAlign.Center)
                } else {
                    Text(place?.place ?: "", style = Type.big, color = game.color, textAlign = TextAlign.Center)
                    Text(t("Είσαι: ${roles[p] ?: ""}", "You are: ${roles[p] ?: ""}"), style = Type.h, color = C.text)
                }
            }
        }) { phase = Spy11.Ask } }

        Spy11.Ask -> {
            QuitGuard(host)
            val first = remember(gameNo) { n.names.random() }
            val left = rememberCountdown(mins * 60, running = true, key = gameNo) { phase = Spy11.Vote }
            Page(t("🕶️ Ερωτήσεις", "🕶️ Questions"), onBack = null, bottom = {
                BigButton(t("Ψηφοφορία", "Vote"), { phase = Spy11.Vote }, color = game.color)
                GhostButton(t("🕶️ Ο κατάσκοπος: «Ξέρω το μέρος!»", "🕶️ The spy: «I know the place!»"), { accused = spy; phase = Spy11.Guess }, Modifier.fillMaxWidth())
            }) {
                TimerText(left, color = game.color, modifier = Modifier.fillMaxWidth())
                Body(t("Ξεκινάει ο/η $first. Ρωτάει όποιον θέλει, κι εκείνος ρωτάει μετά κάποιον άλλο (όχι αμέσως πίσω).", "$first starts. They ask anyone, who then asks someone else (not straight back)."), color = C.text)
                Label(t("ΔΥΝΑΤΑ ΜΕΡΗ", "POSSIBLE PLACES"))
                Body(remember(gameNo) { places.map { it.place }.distinct().sorted().joinToString(" · ") })
            }
        }

        Spy11.Vote -> { QuitGuard(host); SecretVote(n.names, n.names, key = gameNo, question = t("Ποιος είναι ο κατάσκοπος;", "Who is the spy?")) { v ->
            accused = Votes.top(v); Sfx.end(); phase = Spy11.Caught
        } }

        Spy11.Caught -> {
            QuitGuard(host)
            val a = accused
            when {
                a == null -> Announce("🤝", t("Ισοψηφία", "A tie"), t("Ο κατάσκοπος ήταν ο/η $spy και ξέφυγε.", "The spy was $spy and got away."), t("Αποτελέσματα", "Results"), game.color) { finish(true) }
                a != spy -> Announce("🙂", a, t("Δεν ήταν ο κατάσκοπος! Ήταν ο/η $spy.", "Not the spy! It was $spy."), t("Αποτελέσματα", "Results"), game.color) { finish(true) }
                else -> Announce("🕶️", spy, t("Πιάστηκε! Έχει μία ευκαιρία να βρει το μέρος.", "Caught! One chance to name the place."), t("Διάλεξε μέρος", "Pick the place"), game.color) { phase = Spy11.Guess }
            }
        }

        Spy11.Guess -> {
            QuitGuard(host)
            val p = place ?: return
            val opts = remember(gameNo) { SpyRules.choices(p, places) }
            Page(t("🕶️ $spy, πού είμαστε;", "🕶️ $spy, where are we?"), onBack = null) {
                opts.forEach { o ->
                    GhostButton(o, { guessed = o; if (o == p.place) Sfx.correct() else Sfx.wrong(); finish(o == p.place) }, Modifier.fillMaxWidth())
                }
            }
        }

        Spy11.Over -> GameOver(game, host, tally, headline = if (spyWins) t("Κέρδισε ο κατάσκοπος!", "The spy wins!") else t("Πιάσατε τον κατάσκοπο!", "You caught the spy!"), extra = {
            Panel {
                Text("🕶️ $spy", style = Type.h, color = colorOf(spy))
                Text(t("Μέρος: ${place?.place}", "Place: ${place?.place}"), style = Type.body, color = C.text)
                guessed?.let { Text(t("Ο κατάσκοπος είπε: $it", "The spy said: $it"), style = Type.body, color = C.muted) }
            }
        }) { tally.bySide.clear(); phase = Spy11.Intro }
    }
}

/* ======================= 🗼 Πύργος από ψέματα ======================= */
object TowerRules {
    /** One liar up to 4 players, two from 5. */
    fun liars(players: List<String>, rnd: Random = Random): Set<String> = players.shuffled(rnd).take(if (players.size >= 5) 2 else 1).toSet()

    /** +1 for each vote on a liar; a liar who isn't on top of the vote +2. */
    fun points(votes: Map<String, String>, liars: Set<String>): Map<String, Int> {
        val tops = Tally11.tops(votes)
        val out = mutableMapOf<String, Int>()
        votes.forEach { (v, c) -> if (c in liars) out[v] = (out[v] ?: 0) + 1 }
        liars.forEach { l -> if (l !in tops) out[l] = (out[l] ?: 0) + 2 }
        return out
    }
}

private enum class Tower11 { Intro, Reveal, Story, Vote, Result, Over }

@Composable
fun LieTowerGame(host: GameHost) {
    val game = Games.byId("lietower")!!
    val ctx = LocalContext.current
    val n = Store.night
    val deck = remember { Deck("tower", Cards.get(ctx).tower, { it.a }) }
    var phase by remember { mutableStateOf(Tower11.Intro) }
    var laps by remember { mutableIntStateOf(2) }
    var rounds by remember { mutableIntStateOf(2) }
    var round by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf<Prompt?>(null) }
    var liars by remember { mutableStateOf(setOf<String>()) }
    var order by remember { mutableStateOf(listOf<String>()) }
    var step by remember { mutableIntStateOf(0) }
    var votes by remember { mutableStateOf(mapOf<String, String>()) }
    val tally = remember { Tally() }

    fun deal() { start = deck.next(); liars = TowerRules.liars(n.names); order = n.names.shuffled(); step = 0; phase = Tower11.Reveal }

    when (phase) {
        Tower11.Intro -> Intro(game, host, listOf(
            t("Λέτε μαζί την ιστορία «τι έγινε χθες βράδυ». Ο καθένας με τη σειρά προσθέτει μια φράση.", "Together you tell «what happened last night». In turn, each adds a sentence."),
            t("Το κινητό έχει διαλέξει κρυφά ψεύτες (1, ή 2 από 5 άτομα). Ο ψεύτης πρέπει να βάλει ένα ψέμα: κάτι που χαλάει όσα ειπώθηκαν, αλλά τόσο φυσικά που να μην το πιάσουν.", "The phone secretly picked liars (1, or 2 from 5 players). A liar must slip in a lie: something that clashes with what was said, but so naturally nobody notices."),
            t("Οι υπόλοιποι λένε την αλήθεια: κάτι που ταιριάζει με όλα όσα ειπώθηκαν.", "Everyone else tells the truth: something that fits all that was said."),
            t("Στο τέλος κρυφή ψηφοφορία: +1 για κάθε ψήφο σε ψεύτη, +2 στον ψεύτη που δεν πιάστηκε.", "Then a secret vote: +1 for each vote on a liar, +2 to a liar who wasn't caught."),
        ), deck.size > 0, {
            Body(t("Γύροι φράσεων", "Laps of sentences"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), laps, { laps = it }, color = game.color)
            Body(t("Ιστορίες", "Stories"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), rounds, { rounds = it }, color = game.color)
        }) { round = 0; deal() }

        Tower11.Reveal -> { QuitGuard(host); RevealRound(n.names, key = round, title = t("Ο ρόλος σου", "Your role"), secret = { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p in liars) {
                    Text("🤥", fontSize = 48.sp)
                    Text(t("Είσαι ψεύτης", "You are a liar"), style = Type.big, color = game.color, textAlign = TextAlign.Center)
                    Body(t("Κάποια στιγμή βάλε ένα ψέμα που χαλάει την ιστορία, αλλά να πείθει.", "At some point slip in a lie that clashes with the story, but convincingly."), align = TextAlign.Center)
                    if (liars.size > 1) Body(t("Υπάρχει κι άλλος ψεύτης. Δεν ξέρεις ποιος.", "There's another liar. You don't know who."), align = TextAlign.Center)
                } else {
                    Text("🙂", fontSize = 48.sp)
                    Text(t("Λες την αλήθεια", "You tell the truth"), style = Type.big, color = C.green, textAlign = TextAlign.Center)
                    Body(t("Πρόσθεσε κάτι που ταιριάζει με όλα όσα ειπώθηκαν. Πρόσεχε τις αντιφάσεις!", "Add something that fits all that was said. Watch for clashes!"), align = TextAlign.Center)
                }
            }
        }) { phase = Tower11.Story } }

        Tower11.Story -> {
            QuitGuard(host)
            val total = order.size * laps
            val who = order[step % order.size]
            CardScreen(t("Φράση ${step + 1} / $total", "Sentence ${step + 1} / $total"), C.dim, "🗼", if (step == 0) start?.t ?: "" else t("Συνεχίζει ο/η $who", "$who carries on"),
                if (step == 0) t("Ο/Η $who διαβάζει την αρχή δυνατά και συνεχίζει με μια φράση.", "$who reads the start out loud and adds a sentence.") else start?.t, game.color) {
                Text(who, style = Type.title, color = colorOf(who), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                BigButton(if (step + 1 < total) t("Επόμενος", "Next") else t("Ψηφοφορία", "Vote"), { Sfx.tap(); if (step + 1 < total) step++ else phase = Tower11.Vote }, color = game.color)
            }
        }

        Tower11.Vote -> { QuitGuard(host); SecretVote(n.names, n.names, key = round, question = t("Ποιος είπε ψέμα;", "Who lied?")) { v ->
            votes = v; Sfx.end()
            TowerRules.points(v, liars).forEach { (p, pts) -> tally.add(host, listOf(p), pts) }
            liars.forEach { host.stat(it, "sneaky") }
            host.log(t("Πύργος από ψέματα: ψεύτες ${liars.joinToString()}", "Tower of lies: liars ${liars.joinToString()}"))
            phase = Tower11.Result
        } }

        Tower11.Result -> {
            QuitGuard(host)
            val tops = Tally11.tops(votes)
            Page(t("Οι ψεύτες ήταν…", "The liars were…"), onBack = null, bottom = {
                BigButton(if (round + 1 < rounds) t("Νέα ιστορία", "New story") else t("Αποτελέσματα", "Results"), { if (round + 1 < rounds) { round++; deal() } else phase = Tower11.Over }, color = game.color)
            }) {
                liars.forEach { l -> Panel { Text("🤥 $l", style = Type.big, color = colorOf(l)); Text(if (l in tops) t("Πιάστηκε", "Caught") else t("Ξέφυγε · +2", "Got away · +2"), style = Type.h, color = if (l in tops) C.green else game.color) } }
                Panel { Tally11.counts(votes).forEach { (p, c) -> Row { Text(p, style = Type.body, color = colorOf(p), modifier = Modifier.weight(1f)); Text("$c", style = Type.h, color = C.text) } } }
            }
        }

        Tower11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Tower11.Intro }
    }
}
