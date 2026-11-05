package gr.wsol.gamenight.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.QuadGroup
import gr.wsol.gamenight.core.QuadPuzzle
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown

/** One puzzle of «Τετράδες» being played: pure logic, tested without Android. */
class QuadBoard(val puzzle: QuadPuzzle, rnd: kotlin.random.Random = kotlin.random.Random) {
    var left: List<String> = puzzle.groups.flatMap { it.words }.shuffled(rnd); private set
    val solved = mutableListOf<QuadGroup>()
    var mistakes = 0; private set
    val over get() = solved.size == 4 || mistakes >= MAX_MISTAKES
    val won get() = solved.size == 4

    enum class Result { Solved, OneAway, Wrong, Repeat }
    private val tried = mutableSetOf<Set<String>>()

    fun submit(sel: Set<String>): Result {
        require(sel.size == 4)
        if (sel in tried) return Result.Repeat
        val g = puzzle.groups.firstOrNull { it.words.toSet() == sel && it !in solved }
        if (g != null) { solved += g; left = left.filter { it !in sel }; return Result.Solved }
        tried += sel
        mistakes++
        val oneAway = puzzle.groups.any { it !in solved && it.words.count { w -> w in sel } == 3 }
        return if (oneAway) Result.OneAway else Result.Wrong
    }

    fun shuffle(rnd: kotlin.random.Random = kotlin.random.Random) { left = left.shuffled(rnd) }

    /** Versus mode: +2 per group found, −1 per mistake, +1 if all four were found in time. */
    fun versusPoints(inTime: Boolean): Int = solved.size * 2 - mistakes + if (won && inTime) 1 else 0

    companion object { const val MAX_MISTAKES = 4 }
}

private enum class QPhase { Intro, Pass, Play, Done, Over }

@Composable
fun QuadsGame(host: GameHost) {
    val game = Games.byId("quads")!!
    val ctx = LocalContext.current
    val n = Store.night
    val sides = n.sides()
    var versus by remember { mutableStateOf(sides.size >= 2) }
    var phase by remember { mutableStateOf(QPhase.Intro) }
    val deck = remember { Deck("quads", Cards.get(ctx).quads, { it.a }) }
    var board by remember { mutableStateOf<QuadBoard?>(null) }
    var turn by remember { mutableIntStateOf(0) }
    var inTime by remember { mutableStateOf(true) }
    var version by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }
    val results = remember { mutableListOf<Pair<String, Int>>() }

    fun newBoard() { board = deck.next()?.let { QuadBoard(it) }; inTime = true; version++ }

    when (phase) {
        QPhase.Intro -> Intro(
            game, host,
            rules = listOf(
                t("16 λέξεις κρύβουν 4 ομάδες των 4 που έχουν κάτι κοινό. Διάλεξε 4 και πάτα «Έλεγχος».", "16 words hide 4 groups of 4 with something in common. Pick 4 and press «Check»."),
                t("Κίτρινη η εύκολη ομάδα, μετά πράσινη, μπλε, και μωβ η πονηρή.", "Yellow is the easy group, then green, blue, and purple is the tricky one."),
                t("4 λάθη και τελειώνει. Αν είσαι «ένα μακριά», σου το λέμε.", "4 mistakes and it's over. If you're «one away», we tell you."),
                if (versus) t("Κόντρα: κάθε ${if (n.teams) "ομάδα" else "παίκτης"} παίζει δικό του γρίφο σε 3′. +2 ανά ομάδα λέξεων, −1 ανά λάθος, +1 αν τελειώσει στον χρόνο.", "Versus: each ${if (n.teams) "team" else "player"} solves their own puzzle in 3′. +2 per group, −1 per mistake, +1 if done in time.")
                else t("Μαζί: ένας γρίφος για όλους. Αν τον λύσετε, +2 σε όλους.", "Together: one puzzle for everyone. Solve it and everyone gets +2."),
            ),
            startEnabled = deck.size > 0,
            options = if (sides.size >= 2) ({
                Seg(listOf(false to t("Μαζί", "Together"), true to t("Κόντρα", "Versus")), versus, { versus = it })
                Text(t("${deck.size} γρίφοι", "${deck.size} puzzles"), style = Type.small, color = C.dim)
            }) else null,
        ) { turn = 0; results.clear(); newBoard(); phase = if (versus) QPhase.Pass else QPhase.Play }

        QPhase.Pass -> {
            QuitGuard(host)
            val side = sides[turn]
            PassTo(n.label(side), Color(n.colorOf(side)), note = t("Ο δικός σας γρίφος, 3 λεπτά.", "Your own puzzle, 3 minutes."), button = t("Ξεκίνα", "Start")) { newBoard(); phase = QPhase.Play }
        }

        QPhase.Play -> {
            QuitGuard(host)
            val b = board ?: run { phase = QPhase.Over; return }
            QuadPlay(b, version, timed = versus, onTimeUp = { inTime = false; phase = QPhase.Done }, onOver = { phase = QPhase.Done })
        }

        QPhase.Done -> {
            QuitGuard(host)
            val b = board!!
            val side = if (versus) sides[turn] else null
            val pts = remember(version) {
                if (versus) b.versusPoints(inTime).also { p -> tally.add(host, n.membersOf(side!!), p); results += side to p }
                else if (b.won) 2.also { tally.add(host, n.names, it) } else 0
            }
            remember(version) { host.log(t("Τετράδες: ${b.solved.size}/4, ${b.mistakes} λάθη", "Fours: ${b.solved.size}/4, ${b.mistakes} mistakes")) }
            gr.wsol.gamenight.ui.Page(if (b.won) t("Λύθηκε! 🎉", "Solved! 🎉") else t("Τέλος", "Over"), onBack = null, bottom = {
                val more = versus && turn + 1 < sides.size
                BigButton(if (more) t("Επόμενος", "Next") else t("Συνέχεια", "Continue"), {
                    if (more) { turn++; phase = QPhase.Pass } else phase = QPhase.Over
                }, color = game.color)
            }) {
                if (side != null) Text("${n.label(side)}: ${if (pts > 0) "+" else ""}$pts", style = Type.title, color = Color(n.colorOf(side)))
                Body(t("Όλες οι ομάδες:", "All the groups:"))
                b.puzzle.groups.sortedBy { it.level }.forEach { GroupBar(it, found = it in b.solved) }
            }
        }

        QPhase.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = QPhase.Intro }
    }
}

@Composable
private fun GroupBar(g: QuadGroup, found: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.quad[g.level.coerceIn(0, 3)].copy(alpha = if (found) 1f else 0.55f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(g.name, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center)
        Text(g.words.joinToString(", "), color = Color.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuadPlay(b: QuadBoard, key: Int, timed: Boolean, onTimeUp: () -> Unit, onOver: () -> Unit) {
    var sel by remember(key) { mutableStateOf(setOf<String>()) }
    var msg by remember(key) { mutableStateOf<String?>(null) }
    var tick by remember(key) { mutableIntStateOf(0) } // recomposes after the board changes
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (timed) TimerText(rememberCountdown(180, running = !b.over, key = key, tickLast = 10, onEnd = onTimeUp), color = C.quad[0])
        else Text(t("Βρείτε τις 4 τετράδες", "Find the 4 groups"), style = Type.h, color = C.text)
        tick.let { }
        b.solved.forEach { GroupBar(it) }
        val rows = b.left.chunked(4)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { w ->
                    val on = w in sel
                    val bg by animateColorAsState(if (on) C.text else C.panel2, label = "tile")
                    Box(
                        Modifier.weight(1f).aspectRatio(1.15f).clip(RoundedCornerShape(12.dp)).background(bg).border(1.dp, C.line, RoundedCornerShape(12.dp))
                            .semantics { selected = on }
                            .clickable(role = Role.Checkbox) {
                                sel = if (on) sel - w else if (sel.size < 4) sel + w else sel
                                Sfx.tap(); msg = null
                            }.padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(w, color = if (on) Color.Black else C.text, fontWeight = FontWeight.ExtraBold, fontSize = if (w.length > 9) 11.5.sp else 13.5.sp, textAlign = TextAlign.Center, lineHeight = 15.sp) }
                }
            }
        }
        Text(t("Λάθη: ", "Mistakes: ") + "●".repeat(QuadBoard.MAX_MISTAKES - b.mistakes) + "○".repeat(b.mistakes), color = C.muted, fontSize = 16.sp)
        msg?.let { Text(it, style = Type.h, color = C.amber) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(t("Ανακάτεμα", "Shuffle"), { b.shuffle(); tick++ }, Modifier.weight(1f))
            GhostButton(t("Καθάρισμα", "Clear"), { sel = emptySet() }, Modifier.weight(1f), enabled = sel.isNotEmpty())
        }
        BigButton(t("Έλεγχος", "Check"), {
            when (b.submit(sel)) {
                QuadBoard.Result.Solved -> { Sfx.correct(); Sfx.buzz(40); sel = emptySet(); msg = null }
                QuadBoard.Result.OneAway -> { Sfx.wrong(); Sfx.buzz(150); msg = t("Ένα μακριά!", "One away!") }
                QuadBoard.Result.Wrong -> { Sfx.wrong(); Sfx.buzz(150); msg = t("Όχι αυτό", "Not that") }
                QuadBoard.Result.Repeat -> msg = t("Το δοκιμάσατε ήδη", "Already tried")
            }
            tick++
            if (b.over) onOver()
        }, enabled = sel.size == 4, color = C.quad[0])
    }
}
