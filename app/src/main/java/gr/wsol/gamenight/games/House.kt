package gr.wsol.gamenight.games

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Narrator
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
import gr.wsol.gamenight.ui.Toggle
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown
import kotlinx.coroutines.delay

private enum class H { Intro, Setup, Deal, Night, Morning, Day, Hunter, Talk, Vote, Out, Over }

/** Each step of the night lasts at least this long, so nobody can tell from the time which roles are still in. */
private const val STEP_MS = 9000L
/**
 * After a role has chosen: its «close your eyes» and then silence, before the next role is woken.
 * The one who chose has time to put the phone back in the middle and close their eyes, so they
 * don't see (or hear right next to them) who takes it next.
 */
private const val SLEEP_MS = 7000L

@Composable
fun HouseGame(host: GameHost) {
    val game = Games.byId("house")!!
    val ctx = LocalContext.current
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(H.Intro) }
    var setup by remember { mutableStateOf(HouseSetup.suggested(players.size)) }
    var talk by remember { mutableIntStateOf(120) }
    var e by remember { mutableStateOf<HouseEngine?>(null) }
    var gameNo by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<NightResult?>(null) }
    var votedOut by remember { mutableStateOf<String?>(null) }
    var afterHunter by remember { mutableStateOf(H.Talk) }
    val tally = remember { Tally() }

    DisposableEffect(Unit) { Narrator.init(ctx); onDispose { Narrator.stop() } }

    fun next(after: H) { val g = e!!; phase = when { g.winner != null -> H.Over; g.hunterPending != null -> { afterHunter = after; H.Hunter }; else -> after } }

    when (phase) {
        H.Intro -> Intro(game, host, listOf(
            HouseTexts.story(),
            t("Ο αφηγητής μιλάει με τη φωνή του κινητού. Το κινητό μένει στη μέση· κάθε ρόλος, όταν τον φωνάξει, το παίρνει, διαλέγει και το αφήνει.", "The narrator speaks with the phone's voice. The phone stays in the middle; each role, when called, takes it, chooses and puts it back."),
            t("Το πρωί το κινητό περνάει από όλους. Οι περισσότεροι βλέπουν «Τίποτα νέο», για να μην ξέρει κανείς ποιος έμαθε κάτι.", "In the morning the phone goes round. Most see «Nothing new», so nobody knows who learned something."),
            t("Τη μέρα συζητάτε και ψηφίζετε κρυφά ποιος θα πεταχτεί έξω στα φαντάσματα. Όποιος φεύγει γίνεται φάντασμα: δεν μιλάει, αλλά μία φορά στοιχειώνει με μια κρυφή ψήφο.", "By day you talk and secretly vote who's thrown out to the ghosts. Whoever leaves becomes a ghost: silent, but once they haunt with a secret vote."),
            t("Νίκη: οι άνθρωποι όταν δεν μείνει ζόμπι, τα ζόμπι όταν γίνουν όσοι οι υπόλοιποι. +2 σε κάθε παίκτη της ομάδας που κέρδισε.", "Win: humans when no zombie is left, zombies when they equal the rest. +2 to each player of the winning side."),
        ), players.size >= 4) { phase = H.Setup }

        H.Setup -> Page(t("🏚️ Οι ρόλοι", "🏚️ The roles"), onBack = { phase = H.Intro }, bottom = {
            val z = setup.zombies + (if (setup.leader) 1 else 0)
            // the zombies must start fewer than the rest, or the game ends after the first night
            val ok = setup.size() <= players.size && z >= 1 && z < players.size - z
            BigButton(t("Μοίρασε τους ρόλους", "Deal the roles"), {
                e = HouseEngine(players, setup); gameNo++; last = null; tally.bySide.clear(); phase = H.Deal
            }, color = C.ghost, enabled = ok)
        }) {
            Body(t("Προτεινόμενο για ${players.size} άτομα. Αλλάξτε ό,τι θέλετε.", "Suggested for ${players.size} players. Change anything."))
            Panel {
                Label(t("ΖΟΜΠΙ", "ZOMBIES"))
                Seg(listOf(1 to "1", 2 to "2"), setup.zombies, { setup = setup.copy(zombies = it) }, color = C.ghost)
                Toggle("${Role.Leader.emoji} ${Role.Leader.title}", Role.Leader.what, setup.leader) { setup = setup.copy(leader = it) }
            }
            Panel {
                Label(t("ΕΙΔΙΚΟΙ ΡΟΛΟΙ", "SPECIAL ROLES"))
                HouseSetup.optional.forEach { r -> Toggle("${r.emoji} ${r.title}", r.what, r in setup.roles) { on -> setup = setup.copy(roles = if (on) setup.roles + r else setup.roles - r) } }
                Toggle(t("💞 Ερωτευμένοι", "💞 Lovers"), t("Δύο παίκτες δένονται: αν φύγει ο ένας, φεύγει και ο άλλος. Αν είναι άνθρωπος και ζόμπι, κερδίζουν μόνο αν μείνουν οι δύο τελευταίοι.", "Two players are bound: if one leaves, so does the other. Human and zombie win only if they're the last two."), setup.lovers) { setup = setup.copy(lovers = it) }
            }
            val humans = players.size - setup.size()
            Text(if (humans < 0) t("Πάρα πολλοί ρόλοι για ${players.size} άτομα.", "Too many roles for ${players.size} players.") else t("Άνθρωποι χωρίς ρόλο: $humans", "Plain humans: $humans"), style = Type.body, color = if (humans < 0) C.red else C.muted)
            Body(t("Χρόνος συζήτησης", "Discussion time"), color = C.text)
            Seg(listOf(90 to "1:30", 120 to "2:00", 180 to "3:00"), talk, { talk = it }, color = C.ghost)
        }

        H.Deal -> {
            QuitGuard(host)
            HouseBackground(isNight = true) {
                HouseDealScreen(e!!, gameNo) { phase = H.Night }
            }
        }

        H.Night -> { QuitGuard(host); NightScreen(e!!, gameNo) { r -> last = r; phase = H.Morning } }

        H.Morning -> {
            QuitGuard(host)
            val g = e!!; val r = last!!
            LaunchedEffect(g.night) { Sfx.bell(); Narrator.say(HouseTexts.morning()) }
            RevealRound(g.alive.toList(), key = "m${g.night}-$gameNo", title = t("Πρωινός έλεγχος", "Morning check"), secret = { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (p) {
                        r.infected -> { Text("🧟", fontSize = 56.sp); Text(t("Δαγκώθηκες: είσαι πια ζόμπι!", "You were bitten: you're a zombie now!"), style = Type.title, color = C.green, textAlign = TextAlign.Center); Body(t("Παίζεις με τα ζόμπι. Τα άλλα: ", "You play with the zombies. The others: ") + g.zombiesAlive().filter { it != p }.joinToString(", "), align = TextAlign.Center) }
                        r.cured -> { Text("✝️", fontSize = 56.sp); Text(t("Ξαναγύρισες άνθρωπος!", "You're human again!"), style = Type.title, color = C.ghost, textAlign = TextAlign.Center) }
                        else -> { Text("🌅", fontSize = 56.sp); Text(t("Τίποτα νέο", "Nothing new"), style = Type.title, color = C.text) }
                    }
                }
            }) { phase = H.Day }
        }

        H.Day -> {
            QuitGuard(host)
            val g = e!!; val r = last!!
            val line = remember(g.night) {
                (r.eaten?.let { HouseTexts.eaten(it) } ?: HouseTexts.nobody(g.ghosts.isNotEmpty())) +
                    r.alsoLeft.joinToString("") { t(" Μαζί του έφυγε από αγάπη και ο/η $it.", " $it left with them, out of love.") }
            }
            LaunchedEffect(g.night) { Narrator.say(HouseTexts.lightsOn() + " " + line) }
            Announce(if (r.eaten != null) "👻" else "🕯️", r.eaten ?: t("Κανείς", "Nobody"), line, t("Συνέχεια", "Continue"), C.ghost) { next(H.Talk) }
        }

        H.Hunter -> {
            QuitGuard(host)
            val g = e!!; val h = g.hunterPending!!
            Page(t("🔦 Ο κυνηγός φεύγει", "🔦 The hunter leaves"), onBack = null) {
                Body(t("$h ήταν κυνηγός φαντασμάτων και παίρνει μαζί του έναν. Ποιον;", "$h was a ghost hunter and takes one along. Whom?"), color = C.text)
                g.alive.forEach { p -> GhostButton(p, { g.hunterTakes(p); Sfx.wind(); next(afterHunter) }, Modifier.fillMaxWidth()) }
            }
        }

        H.Talk -> {
            QuitGuard(host)
            val g = e!!
            LaunchedEffect(g.night) { Narrator.say(HouseTexts.talk()) }
            val left = rememberCountdown(talk, running = true, key = "t${g.night}-$gameNo", tickLast = 10) { phase = H.Vote }
            HouseBackground(isNight = false) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                ) {
                    Panel {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Text(t("Ποιος είναι ζόμπι;", "Who's a zombie?"), style = Type.title, color = C.text)
                            TimerText(left, color = C.ghost)
                            Body(t("Στο σπίτι: ", "In the house: ") + g.alive.joinToString(", "), align = TextAlign.Center, color = C.text)
                            if (g.ghosts.isNotEmpty()) Body(t("👻 Φαντάσματα (σιωπή!): ", "👻 Ghosts (silence!): ") + g.ghosts.joinToString(", "), align = TextAlign.Center)
                            BigButton(t("Ψηφοφορία τώρα", "Vote now"), { phase = H.Vote }, color = C.ghost)
                        }
                    }
                }
            }
        }

        H.Vote -> { QuitGuard(host); HouseVote(e!!, "v${e!!.night}-$gameNo") { votes ->
            votedOut = e!!.vote(votes); Sfx.creak(); phase = H.Out
        } }

        H.Out -> {
            QuitGuard(host)
            val p = votedOut
            val line = remember(e!!.night, p) { (p?.let { HouseTexts.out(it) } ?: HouseTexts.tie()) + e!!.alsoLeftByVote.joinToString("") { t(" Μαζί έφυγε από αγάπη και ο/η $it.", " $it left too, out of love.") } }
            LaunchedEffect(line) { Narrator.say(line) }
            Announce(if (p == null) "🤝" else "🚪", p ?: t("Ισοψηφία", "A tie"), line, t("Συνέχεια", "Continue"), C.ghost) { next(H.Night) }
        }

        H.Over -> {
            val g = e!!
            remember(gameNo) {
                tally.addAll(host, g.points())
                host.log(t("Στοιχειωμένο σπίτι: ", "Haunted house: ") + winnerText(g))
                Sfx.win(); Narrator.say(winnerText(g))
            }
            GameOver(game, host, tally, headline = winnerText(g), extra = {
                Panel {
                    Label(t("ΠΟΙΟΣ ΗΤΑΝ ΤΙ", "WHO WAS WHAT"))
                    players.forEach { p ->
                        val o = g.original[p]!!; val now = g.role[p]!!
                        Text("${o.emoji} $p: ${o.title}" + (if (now != o) " → ${now.title}" else "") + (if (g.loverOf(p) != null) " 💞" else "") + (if (p in g.ghosts) " 👻" else ""), style = Type.body, color = C.text)
                    }
                }
                Panel {
                    Label(t("ΤΙ ΕΓΙΝΕ ΚΑΘΕ ΣΚΟΤΑΔΙ", "WHAT HAPPENED EACH NIGHT"))
                    g.story.forEach { Text("• $it", style = Type.small, color = C.muted) }
                }
            }) { phase = H.Setup }
        }
    }
}

private fun winnerText(g: HouseEngine) = when (g.winner) {
    HouseEngine.Winner.Humans -> t("Κέρδισαν οι άνθρωποι! Τα φαντάσματα έφυγαν.", "The humans win! The ghosts are gone.")
    HouseEngine.Winner.Zombies -> t("Κέρδισαν τα ζόμπι! Το σπίτι είναι δικό τους.", "The zombies win! The house is theirs.")
    HouseEngine.Winner.Possessed -> t("Κέρδισε ο δαιμονισμένος! Ήθελε να τον πετάξετε έξω.", "The possessed wins! They wanted to be thrown out.")
    HouseEngine.Winner.Lovers -> t("Κέρδισαν οι ερωτευμένοι! Η αγάπη νίκησε τα φαντάσματα.", "The lovers win! Love beat the ghosts.")
    null -> ""
}

/** The steps of a night, in order; roles not in this game are skipped, roles that left still «play» (for the same timing). */
private fun nightSteps(g: HouseEngine): List<Role> = listOf(Role.Granny, Role.Zombie, Role.Healer, Role.Mage, Role.Exorcist, Role.Medium)
    .filter { r -> if (r == Role.Zombie) true else g.original.values.contains(r) }

@Composable
private fun NightScreen(g: HouseEngine, gameNo: Int, onDone: (NightResult) -> Unit) {
    val steps = remember(g.night, gameNo) { nightSteps(g) }
    var i by remember(g.night, gameNo) { mutableIntStateOf(-1) } // -1: lights out
    var acts by remember(g.night, gameNo) { mutableStateOf(NightActions()) }
    var chosen by remember(g.night, gameNo, i) { mutableStateOf(false) }
    var minDone by remember(g.night, gameNo, i) { mutableStateOf(false) }
    val step = steps.getOrNull(i)
    val active = step != null && (if (step == Role.Zombie) g.zombiesAlive().isNotEmpty() else g.has(step))

    LaunchedEffect(g.night, gameNo, i) {
        if (i == -1) { Sfx.wind(); Narrator.say(HouseTexts.lightsOut()); delay(6000); i = 0; return@LaunchedEffect }
        if (step == null) return@LaunchedEffect
        Sfx.creak(); Narrator.say(HouseTexts.wake(step))
        delay(STEP_MS)
        minDone = true
    }
    LaunchedEffect(minDone, chosen, active) {
        if (minDone && (chosen || !active)) {
            step?.let { Narrator.say(HouseTexts.sleep(it)) }
            delay(SLEEP_MS)
            if (i + 1 < steps.size) i++ else onDone(g.resolveNight(acts))
        }
    }

    val flame by rememberInfiniteTransition(label = "candle").animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "f")
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07060C), Color(0xFF120E1E)))).statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(28.dp, 44.dp).scale(flame).clip(RoundedCornerShape(50)).background(Brush.radialGradient(listOf(Color(0xFFFFF6D6), C.candle, Color.Transparent))))
        Text(t("Σκοτάδι ${g.night + 1}", "Night ${g.night + 1}"), style = Type.label, color = C.dim)
        if (step == null) { Text(t("Όλοι μάτια κλειστά…", "Everyone, eyes closed…"), style = Type.title, color = C.candle, textAlign = TextAlign.Center); return@Column }
        Text("${step.emoji} ${if (step == Role.Zombie) t("Ζόμπι", "Zombies") else step.title}", style = Type.title, color = C.candle, textAlign = TextAlign.Center)
        if (!active || chosen) {
            Text(t("Άφησε το κινητό στη μέση και κλείσε τα μάτια.", "Put the phone in the middle and close your eyes."), style = Type.body, color = C.muted, textAlign = TextAlign.Center, modifier = Modifier.alpha(0.8f))
            return@Column
        }
        val others = { self: Role -> g.alive.filter { g.role[it] != self } }
        when (step) {
            Role.Granny -> {
                if (g.incenseUsed) { Body(t("Το θυμιατό το έχεις ήδη χρησιμοποιήσει.", "You've already used the censer."), align = TextAlign.Center); BigButton("OK", { chosen = true }, color = C.candle) }
                else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(t("Θυμιάζω απόψε", "Cense tonight"), { acts = acts.copy(incense = true); chosen = true }, Modifier.weight(1f), color = C.candle)
                    GhostButton(t("Όχι απόψε", "Not tonight"), { chosen = true }, Modifier.weight(1f))
                }
            }
            Role.Zombie -> {
                var infect by remember(g.night) { mutableStateOf(false) }
                if (g.has(Role.Leader) && !g.leaderInfected) Toggle(t("🧟‍♂️ Ο αρχηγός μολύνει απόψε", "🧟‍♂️ The leader infects tonight"), t("Μία φορά: ο δαγκωμένος γίνεται ζόμπι αντί να φύγει.", "Once: the bitten turns into a zombie instead of leaving."), infect) { infect = it }
                Body(t("Ποιον δαγκώνετε;", "Whom do you bite?"), color = C.text)
                g.alive.filter { !g.isZombie(it) }.forEach { p -> GhostButton(p, { acts = acts.copy(bite = p, infect = infect); chosen = true }, Modifier.fillMaxWidth(), color = C.green) }
            }
            Role.Healer -> {
                Body(t("Ποιον προστατεύεις; (Όχι τον ίδιο δύο φορές στη σειρά.)", "Whom do you protect? (Not the same twice in a row.)"), color = C.text)
                // everyone is listed (last night's one greyed), so a peek shows nothing about who was guarded
                g.alive.forEach { p -> GhostButton(p, { acts = acts.copy(protect = p); chosen = true }, Modifier.fillMaxWidth(), color = C.ghost, enabled = p != g.lastGuarded) }
            }
            Role.Mage -> {
                var seen by remember(g.night) { mutableStateOf<String?>(null) }
                if (seen == null) { Body(t("Ποιον κοιτάς στη σφαίρα;", "Whom do you look at?"), color = C.text); others(Role.Mage).forEach { p -> GhostButton(p, { seen = p; acts = acts.copy(look = p) }, Modifier.fillMaxWidth(), color = C.ghost) } }
                else {
                    val z = g.isZombie(seen!!)
                    Text(if (z) "🧟" else "🙂", fontSize = 56.sp)
                    Text(if (z) t("$seen είναι ζόμπι!", "$seen is a zombie!") else t("$seen δεν είναι ζόμπι.", "$seen is not a zombie."), style = Type.title, color = if (z) C.green else C.text, textAlign = TextAlign.Center)
                    BigButton(t("Το είδα", "Seen it"), { chosen = true }, color = C.ghost)
                }
            }
            Role.Exorcist -> {
                if (g.exorcistUsed) { Body(t("Τον εξορκισμό τον έχεις ήδη κάνει.", "You've already done your exorcism."), align = TextAlign.Center); BigButton("OK", { chosen = true }, color = C.candle) }
                else {
                    Body(t("Θα ξορκίσεις κάποιον; Μόνο μία φορά στο παιχνίδι.", "Exorcise someone? Only once per game."), color = C.text)
                    others(Role.Exorcist).forEach { p -> GhostButton(p, { acts = acts.copy(exorcise = p); chosen = true }, Modifier.fillMaxWidth(), color = C.ghost) }
                    GhostButton(t("Όχι απόψε", "Not tonight"), { chosen = true }, Modifier.fillMaxWidth())
                }
            }
            Role.Medium -> {
                var heard by remember(g.night) { mutableStateOf<String?>(null) }
                when {
                    g.ghosts.isEmpty() -> { Body(t("Δεν υπάρχουν ακόμα φαντάσματα.", "There are no ghosts yet."), align = TextAlign.Center); BigButton("OK", { chosen = true }, color = C.candle) }
                    heard == null -> { Body(t("Ποιο φάντασμα ρωτάς;", "Which ghost do you ask?"), color = C.text); g.ghosts.forEach { p -> GhostButton(p, { heard = p; acts = acts.copy(medium = p) }, Modifier.fillMaxWidth(), color = C.ghost) } }
                    else -> {
                        val r = g.original[heard!!]!!
                        Text(r.emoji, fontSize = 56.sp)
                        Text(t("$heard ήταν: ${r.title}", "$heard was: ${r.title}"), style = Type.title, color = C.ghost, textAlign = TextAlign.Center)
                        BigButton(t("Το άκουσα", "Heard it"), { chosen = true }, color = C.ghost)
                    }
                }
            }
            else -> {}
        }
    }
}

/** The day's vote: everyone in the house, plus the ghosts who haven't haunted yet (they may skip). */
@Composable
private fun HouseVote(g: HouseEngine, key: String, onDone: (Map<String, String>) -> Unit) {
    val voters = remember(key) { g.alive.toList() + g.ghostsWhoMayVote() }
    var i by remember(key) { mutableIntStateOf(0) }
    var ready by remember(key) { mutableStateOf(false) }
    val votes = remember(key) { mutableStateMapOf<String, String>() }
    val n = Store.night
    val who = voters.getOrNull(i) ?: return
    val ghost = who in g.ghosts
    fun next() { if (i + 1 < voters.size) { i++; ready = false } else onDone(votes.toMap()) }
    if (!ready) { PassTo(who, Color(n.colorOf(n.sideOf(who))), note = if (ghost) t("👻 Είσαι φάντασμα.", "👻 You're a ghost.") else t("Κρυφή ψηφοφορία.", "Secret vote.")) { ready = true }; return }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
        Text(if (ghost) t("Θα στοιχειώσεις; Μία κρυφή ψήφος, μία φορά στο παιχνίδι.", "Will you haunt? One secret vote, once per game.") else t("Ποιον πετάμε έξω στα φαντάσματα;", "Whom do we throw out to the ghosts?"), style = Type.title, color = C.text)
        g.alive.filter { it != who }.forEach { c -> GhostButton(c, { votes[who] = c; next() }, Modifier.fillMaxWidth(), color = Color(n.colorOf(n.sideOf(c)))) }
        if (ghost) Chip(t("Όχι τώρα", "Not now"), onClick = { next() })
    }
}

/** Secret deal round with 3:4 gothic card and FlipCard hold-to-reveal. */
@Composable
private fun HouseDealScreen(g: HouseEngine, gameNo: Int, onDone: () -> Unit) {
    val players = g.players
    var i by remember(gameNo) { mutableIntStateOf(0) }
    var ready by remember(gameNo, i) { mutableStateOf(false) }
    var held by remember(gameNo, i) { mutableStateOf(false) }
    var seen by remember(gameNo, i) { mutableStateOf(false) }
    val n = Store.night
    val who = players.getOrNull(i) ?: return

    if (!ready) {
        PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Μόνο εσύ πρέπει να βλέπεις την οθόνη.", "Only you should see the screen.")) {
            ready = true
            held = false
            seen = false
        }
        return
    }

    val role = g.role[who]!!
    val lover = g.loverOf(who)
    val otherZombies = if (role.zombie) g.zombiesAlive().filter { it != who } else emptyList()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(who, style = Type.h, color = Color(n.colorOf(n.sideOf(who))))
        Text(t("Ο ρόλος σου", "Your role"), style = Type.body, color = C.muted)

        // 3:4 Gothic role card that flips while held
        Box(
            modifier = Modifier
                .semantics { contentDescription = t("Κράτα πατημένο για να δεις", "Hold to see") }
                .pointerInput(gameNo, i) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            seen = true
                            Sfx.buzz(15)
                            tryAwaitRelease()
                            held = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            gr.wsol.gamenight.ui.FlipCard(
                front = {
                    GothicRoleCard(
                        role = role,
                        lover = lover,
                        otherZombies = otherZombies
                    )
                },
                back = {
                    GothicRoleCardBack()
                },
                flipped = held
            )
        }

        Text(
            t("Κράτα πατημένο για να δεις", "Hold to see"),
            style = Type.body,
            color = if (held) Color.Transparent else C.muted,
            textAlign = TextAlign.Center
        )

        BigButton(
            if (i + 1 < players.size) t("Το είδα · επόμενος", "Seen it · next") else t("Το είδα · τέλος", "Seen it · done"),
            {
                if (i + 1 < players.size) {
                    i++
                    ready = false
                    held = false
                    seen = false
                } else {
                    onDone()
                }
            },
            enabled = seen,
            color = C.ghost
        )
    }
}
