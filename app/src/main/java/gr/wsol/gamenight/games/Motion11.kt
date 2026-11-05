package gr.wsol.gamenight.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Prompt
import gr.wsol.gamenight.core.Quiz
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.MotionNow
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.extras.rememberMotion
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/* ======================= 🛸 Το ανθρώπινο drone ======================= */
/**
 * A maze of cells (true = wall), the ball in cell units. Tilt moves the ball; walls stop it, holes
 * send it back to the start. The goal is the bottom-right corner.
 */
class DroneMaze(val cols: Int = 9, val rows: Int = 13, private val rnd: Random = Random) {
    data class Hole(var x: Float, val y: Float, val from: Float, val to: Float, var v: Float)
    val wall = Array(rows) { BooleanArray(cols) { true } }
    var x = 1.5f; private set
    var y = 1.5f; private set
    private var vx = 0f
    private var vy = 0f
    val holes = mutableListOf<Hole>()
    var falls = 0; private set
    var done = false; private set
    val goal = (cols - 2) to (rows - 2)
    val r = 0.28f

    init {
        // a maze by random depth-first search on the odd cells
        val stack = ArrayDeque<Pair<Int, Int>>()
        wall[1][1] = false; stack.addLast(1 to 1)
        while (stack.isNotEmpty()) {
            val (cx, cy) = stack.last()
            val next = listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2).shuffled(rnd).map { (dx, dy) -> cx + dx to cy + dy }
                .firstOrNull { (nx, ny) -> nx in 1 until cols - 1 && ny in 1 until rows - 1 && wall[ny][nx] }
            if (next == null) stack.removeLast() else {
                wall[(cy + next.second) / 2][(cx + next.first) / 2] = false; wall[next.second][next.first] = false; stack.addLast(next)
            }
        }
        // a few extra openings so there's more than one way
        repeat(cols * rows / 18) { val wx = 1 + rnd.nextInt(cols - 2); val wy = 1 + rnd.nextInt(rows - 2); if ((wx + wy) % 2 == 1) wall[wy][wx] = false }
        // moving holes in long horizontal corridors
        for (yy in 3 until rows - 2 step 2) {
            var run = 0
            for (xx in 1 until cols) {
                if (xx < cols - 1 && !wall[yy][xx]) run++ else {
                    if (run >= 3 && holes.size < 3 && rnd.nextFloat() < 0.6f) { val a = (xx - run).toFloat(); holes += Hole(a + 0.5f, yy + 0.5f, a + 0.5f, xx - 0.5f, 0.9f) }
                    run = 0
                }
            }
        }
    }

    fun isWall(cx: Float, cy: Float) = cy.toInt() !in 0 until rows || cx.toInt() !in 0 until cols || wall[cy.toInt()][cx.toInt()]

    private fun blocked(px: Float, py: Float) = isWall(px - r, py - r) || isWall(px + r, py - r) || isWall(px - r, py + r) || isWall(px + r, py + r)

    /** [ax], [ay] in m/s² (tilt). True when it reached the goal this frame. */
    fun step(ax: Float, ay: Float, dt: Float): Boolean {
        if (done) return false
        holes.forEach { h -> h.x += h.v * dt; if (h.x > h.to) { h.x = h.to; h.v = -abs(h.v) }; if (h.x < h.from) { h.x = h.from; h.v = abs(h.v) } }
        vx = (vx + ax * 0.9f * dt) * (1f - 1.6f * dt); vy = (vy + ay * 0.9f * dt) * (1f - 1.6f * dt)
        val nx = x + vx * dt; if (!blocked(nx, y)) x = nx else vx = 0f
        val ny = y + vy * dt; if (!blocked(x, ny)) y = ny else vy = 0f
        if (holes.any { hypot(it.x - x, it.y - y) < 0.4f }) { falls++; x = 1.5f; y = 1.5f; vx = 0f; vy = 0f }
        if (x.toInt() == goal.first && y.toInt() == goal.second) { done = true; return true }
        return false
    }

    fun push(dx: Float, dy: Float) { vx += dx; vy += dy }
}

private enum class Drone11 { Intro, Pass, Fly, Result, Over }

@Composable
fun DroneGame(host: GameHost) {
    val game = Games.byId("drone")!!
    val n = Store.night
    var phase by remember { mutableStateOf(Drone11.Intro) }
    var turn by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(0) }
    var ok by remember { mutableStateOf(false) }
    val tally = remember { Tally() }
    val total = Turns.count(n, 1)
    val pilot = Turns.player(n, turn)

    when (phase) {
        Drone11.Intro -> Intro(game, host, listOf(
            t("Ο «drone» κρατάει το κινητό όρθιο στο στήθος, με την οθόνη προς την παρέα. Δεν βλέπει τίποτα!", "The «drone» holds the phone upright on their chest, screen towards the group. They see nothing!"),
            t("Στην οθόνη μια μπίλια σε λαβύρινθο κινείται με την κλίση: γείρε δεξιά-αριστερά, και μπρος-πίσω (την κορυφή του κινητού).", "On screen a ball in a maze moves with the tilt: lean left-right, and forward-back (the top of the phone)."),
            t("Οι άλλοι φωνάζουν οδηγίες («λίγο μπροστά, στοπ, πίσω!») για να φτάσει στο 🏁 σε 45″. Οι μαύρες τρύπες κινούνται και σε γυρνάνε στην αρχή.", "The others shout directions («a bit forward, stop, back!») to reach 🏁 in 45″. The black holes move and send you back to the start."),
            t("Αν φτάσει: +2 στον drone και σε όσους φώναζαν (στην ομάδα του), +1 ακόμα με πάνω από 15″.", "If it gets there: +2 to the drone and the callers (their team), +1 more with over 15″ left."),
        )) { turn = 0; phase = Drone11.Pass }

        Drone11.Pass -> { QuitGuard(host); PassTo(pilot, colorOf(pilot), note = t("Κράτα το όρθιο στο στήθος, οθόνη προς τους άλλους.", "Hold it upright on your chest, screen to the others."), button = t("Απογείωση!", "Take off!")) { phase = Drone11.Fly } }

        Drone11.Fly -> {
            QuitGuard(host)
            val m = remember(turn) { DroneMaze() }
            val motion by rememberMotion()
            var frame by remember(turn) { mutableIntStateOf(0) }
            val ms = rememberCountdown(45, running = !m.done, key = turn) { ok = false; left = 0; Sfx.boom(); phase = Drone11.Result }
            LaunchedEffect(turn) {
                val clock = FrameClock()
                while (!m.done) {
                    val dt = clock.tick(withFrameNanos { it })
                    // upright phone: left-right from x, forward-back from z
                    if (m.step(-motion.gx * 0.6f, -motion.gz * 0.6f, dt)) { ok = true; left = (ms / 1000).toInt(); Sfx.win(); phase = Drone11.Result }
                    frame++
                }
            }
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0A12)).statusBarsPadding().navigationBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛸 $pilot", style = Type.h, color = colorOf(pilot))
                TimerText(ms, color = game.color)
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    frame.let { }
                    val cell = minOf(size.width / m.cols, size.height / m.rows)
                    val o = Offset((size.width - cell * m.cols) / 2, (size.height - cell * m.rows) / 2)
                    for (yy in 0 until m.rows) for (xx in 0 until m.cols) if (m.wall[yy][xx]) drawRect(C.ghost.copy(alpha = 0.55f), o + Offset(xx * cell, yy * cell), Size(cell, cell))
                    drawRect(C.green.copy(alpha = 0.5f), o + Offset(m.goal.first * cell, m.goal.second * cell), Size(cell, cell))
                    m.holes.forEach { h -> drawCircle(Color.Black, cell * 0.4f, o + Offset(h.x * cell, h.y * cell)); drawCircle(C.red, cell * 0.4f, o + Offset(h.x * cell, h.y * cell), style = Stroke(3f)) }
                    drawCircle(game.color, cell * m.r, o + Offset(m.x * cell, m.y * cell))
                }
                Text(t("πεσίματα: ${m.falls}", "falls: ${m.falls}"), style = Type.small, color = C.muted)
                if (!motion.has) Body(t("Αυτό το κινητό δεν έχει αισθητήρα κλίσης.", "This phone has no tilt sensor."), color = C.red)
            }
        }

        Drone11.Result -> {
            QuitGuard(host)
            remember(turn) {
                if (ok) { val pts = 2 + if (left > 15) 1 else 0; tally.add(host, listOf(pilot) + Turns.others(n, pilot).let { if (n.teams) it else emptyList() }, pts); host.log(t("Drone: $pilot έφτασε με ${left}″", "Drone: $pilot made it with ${left}″")) }
                0
            }
            Announce(if (ok) "🏁" else "💥", pilot, if (ok) t("Έφτασε! Περίσσεψαν ${left}″.", "Made it! ${left}″ to spare.") else t("Τελείωσε ο χρόνος.", "Out of time."),
                if (turn + 1 < total) t("Επόμενος drone", "Next drone") else t("Αποτελέσματα", "Results"), game.color) {
                if (turn + 1 < total) { turn++; phase = Drone11.Pass } else phase = Drone11.Over
            }
        }

        Drone11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Drone11.Intro }
    }
}

/* ======================= 💣 Αφόπλισε τη βόμβα: ομαλή πάσα ======================= */
object DefuseRules {
    const val MAX_TILT = 15.0
    const val MAX_JERK = 7.5f

    /** Degrees between the gravity now and at the start of the turn. */
    fun tilt(ref: MotionNow, now: MotionNow): Double {
        val a = sqrt(ref.gx * ref.gx + ref.gy * ref.gy + ref.gz * ref.gz); val b = sqrt(now.gx * now.gx + now.gy * now.gy + now.gz * now.gz)
        if (a == 0f || b == 0f) return 0.0
        val c = ((ref.gx * now.gx + ref.gy * now.gy + ref.gz * now.gz) / (a * b)).toDouble().coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(c))
    }

    fun boom(ref: MotionNow, now: MotionNow) = tilt(ref, now) > MAX_TILT || now.jerk > MAX_JERK
}

private enum class Defuse11 { Intro, Pass, Hold, Boom, Over }

@Composable
fun DefuseGame(host: GameHost) {
    val game = Games.byId("defuse")!!
    val ctx = LocalContext.current
    val n = Store.night
    val deck = remember { Deck("defuse", Cards.get(ctx).find10, { it.a }) }
    var phase by remember { mutableStateOf(Defuse11.Intro) }
    var turn by remember { mutableIntStateOf(0) }
    var rounds by remember { mutableIntStateOf(2) }
    var task by remember { mutableStateOf<Prompt?>(null) }
    var why by remember { mutableStateOf("") }
    val tally = remember { Tally() }
    val total = Turns.count(n, rounds)
    val who = Turns.player(n, turn)

    when (phase) {
        Defuse11.Intro -> Intro(game, host, listOf(
            t("Κρατάς τη «βόμβα» (το κινητό) σταθερή και κάνεις μια αποστολή: «πες 3 νησιά των Κυκλάδων».", "You hold the «bomb» (the phone) steady and do a task: «name 3 Cyclades islands»."),
            t("Αν γείρει πάνω από 15° ή κουνηθεί απότομα, σκάει. Μετά το δίνεις απαλά στον επόμενο: η πάσα είναι το πιο δύσκολο!", "If it tilts over 15° or jolts, it blows. Then hand it gently to the next one: the pass is the hard part!"),
            t("Αποστολή και πάσα χωρίς έκρηξη: +1. Σκάει στα χέρια σου: −1.", "Task and pass without a blast: +1. It blows in your hands: −1."),
        ), deck.size > 0, {
            Body(t("Γύροι ο καθένας", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), rounds, { rounds = it }, color = game.color)
        }) { turn = 0; task = deck.next(); phase = Defuse11.Pass }

        Defuse11.Pass -> { QuitGuard(host); PassTo(who, colorOf(who), note = t("Πάρ' το απαλά και κράτα το όπως θα το κρατάς.", "Take it gently and hold it as you'll hold it."), button = t("Το κρατάω σταθερά", "I'm holding it steady")) { phase = Defuse11.Hold } }

        Defuse11.Hold -> {
            QuitGuard(host)
            val motion by rememberMotion()
            var ref by remember(turn) { mutableStateOf<MotionNow?>(null) }
            var passing by remember(turn) { mutableStateOf(false) }
            var held by remember(turn) { mutableFloatStateOf(0f) }
            LaunchedEffect(turn) {
                kotlinx.coroutines.delay(400); ref = motion
                val clock = FrameClock()
                while (true) {
                    val dt = clock.tick(withFrameNanos { it })
                    val r0 = ref ?: continue
                    if (DefuseRules.boom(r0, motion)) { why = if (motion.jerk > DefuseRules.MAX_JERK) t("απότομη κίνηση", "a jolt") else t("έγειρε πολύ", "tilted too far"); Sfx.boom(); Sfx.buzz(400, true); tally.add(host, listOf(who), -1); host.stat(who, "boom"); phase = Defuse11.Boom; break }
                    if (passing) { held += dt; if (held >= 4f) break }
                }
                if (phase == Defuse11.Hold) { tally.add(host, listOf(who), 1); Sfx.correct(); if (turn + 1 < total) { turn++; task = deck.next(); phase = Defuse11.Pass } else phase = Defuse11.Over }
            }
            val tl = ref?.let { DefuseRules.tilt(it, motion) } ?: 0.0
            val frac = (tl / DefuseRules.MAX_TILT).toFloat().coerceIn(0f, 1f)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(who, style = Type.h, color = colorOf(who))
                Text(if (passing) t("Δώσ' το απαλά στον επόμενο!", "Hand it gently to the next!") else task?.t ?: "", style = Type.title, color = C.text, textAlign = TextAlign.Center)
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val c = Offset(size.width / 2, size.height / 2); val R = minOf(size.width, size.height) * 0.42f
                    drawCircle(C.green.copy(alpha = 0.18f), R, c); drawCircle(C.green, R, c, style = Stroke(6f))
                    val col = if (frac > 0.75f) C.red else if (frac > 0.45f) C.amber else C.green
                    drawCircle(col, R * 0.16f, c + Offset((motion.gx - (ref?.gx ?: motion.gx)) * R / 2.6f, (motion.gy - (ref?.gy ?: motion.gy)) * -R / 2.6f))
                    drawArc(col, -90f, 360f * frac, false, Offset(c.x - R * 1.08f, c.y - R * 1.08f), Size(R * 2.16f, R * 2.16f), style = Stroke(10f))
                }
                if (!passing) BigButton(t("Έγινε · πάσα", "Done · pass"), { passing = true; Sfx.tap() }, color = game.color)
                else Text(t("Πάσα… ${(4 - held).toInt().coerceAtLeast(0)}″", "Passing… ${(4 - held).toInt().coerceAtLeast(0)}″"), style = Type.title, color = game.color)
            }
        }

        Defuse11.Boom -> Announce("💥", who, t("Μπουμ! ($why) −1", "Boom! ($why) −1"), if (turn + 1 < total) t("Επόμενος", "Next") else t("Αποτελέσματα", "Results"), C.red) {
            if (turn + 1 < total) { turn++; task = deck.next(); phase = Defuse11.Pass } else phase = Defuse11.Over
        }

        Defuse11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Defuse11.Intro }
    }
}

/* ======================= 🧊 Trivia σε ισορροπία ======================= */
/** A cursor that slides like on ice; holding it 1.5″ in a corner picks that answer. Spikes cost time. */
class IceCursor(private val rnd: Random = Random) {
    data class Spike(var x: Float, var y: Float, var vx: Float, var vy: Float)
    var x = 0f; private set
    var y = 0f; private set
    private var vx = 0f
    private var vy = 0f
    var corner = -1; private set
    var dwell = 0f; private set
    val spikes = mutableListOf(Spike(0.5f, 0f, 0.35f, 0.25f), Spike(-0.5f, 0.2f, -0.3f, 0.32f))
    var cooldown = 0f; private set

    /** Corner 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right, or -1. Coordinates −1..1. */
    fun cornerAt(px: Float, py: Float): Int = if (abs(px) < 0.3f || abs(py) < 0.55f) -1 else (if (py < 0) 0 else 2) + (if (px < 0) 0 else 1)

    /** Returns the picked corner (once dwelled 1.5″), -2 when a spike was touched, else -1. */
    fun step(ax: Float, ay: Float, dt: Float): Int {
        cooldown = (cooldown - dt).coerceAtLeast(0f)
        vx = (vx + ax * 0.22f * dt) * (1f - 0.35f * dt); vy = (vy + ay * 0.22f * dt) * (1f - 0.35f * dt)
        x = (x + vx * dt); y = (y + vy * dt)
        if (abs(x) > 1f) { x = x.coerceIn(-1f, 1f); vx = -vx * 0.4f }
        if (abs(y) > 1f) { y = y.coerceIn(-1f, 1f); vy = -vy * 0.4f }
        spikes.forEach { s -> s.x += s.vx * dt; s.y += s.vy * dt; if (abs(s.x) > 0.9f) s.vx = -s.vx; if (abs(s.y) > 0.9f) s.vy = -s.vy }
        if (cooldown == 0f && spikes.any { hypot(it.x - x, it.y - y) < 0.12f }) { cooldown = 1.2f; return -2 }
        val c = cornerAt(x, y)
        if (c != corner) { corner = c; dwell = 0f }
        if (c >= 0) { dwell += dt; if (dwell >= 1.5f) { dwell = 0f; x = 0f; y = 0f; vx = 0f; vy = 0f; corner = -1; return c } }
        return -1
    }
}

private enum class IceQ11 { Intro, Pass, Play, Result, Over }

@Composable
fun IceTriviaGame(host: GameHost) {
    val game = Games.byId("icequiz")!!
    val ctx = LocalContext.current
    val n = Store.night
    val deck = remember { Deck("icequiz", Cards.get(ctx).quiz, { it.a }) }
    var phase by remember { mutableStateOf(IceQ11.Intro) }
    var each by remember { mutableIntStateOf(2) }
    var turn by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf<Quiz?>(null) }
    var opts by remember { mutableStateOf(listOf<String>()) }
    var pick by remember { mutableIntStateOf(-1) }
    val tally = remember { Tally() }
    val total = Turns.count(n, each)
    val who = Turns.player(n, turn)

    fun deal() { q = deck.next(); opts = q?.let { QuizRules.options(it, Random.nextInt()) }.orEmpty(); pick = -1 }

    when (phase) {
        IceQ11.Intro -> Intro(game, host, listOf(
            t("Ερώτηση με 4 απαντήσεις, μία σε κάθε γωνία. Δεν πατάς τίποτα: γέρνεις το κινητό για να πάει ο κέρσορας στη γωνία, και τον κρατάς εκεί 1,5″.", "A question with 4 answers, one in each corner. No tapping: tilt the phone to slide the cursor to a corner and hold it there 1.5″."),
            t("Το ταμπλό γλιστράει σαν πάγος. Οι κόκκινες ακίδες κόβουν 3″ από τον χρόνο σου.", "The board is slippery as ice. The red spikes take 3″ off your time."),
            t("Σωστό +1 (για την ομάδα). 20″ για κάθε ερώτηση, μετά πασάρεις.", "Right +1 (for the team). 20″ per question, then pass."),
        ), deck.size > 0, {
            Body(t("Ερωτήσεις ο καθένας", "Questions each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = game.color)
        }) { turn = 0; deal(); phase = IceQ11.Pass }

        IceQ11.Pass -> { QuitGuard(host); PassTo(who, colorOf(who), note = t("Κράτα το κινητό ίσια, σαν δίσκο.", "Hold the phone flat, like a tray."), button = t("Πάμε", "Go")) { phase = IceQ11.Play } }

        IceQ11.Play -> {
            QuitGuard(host)
            val cur = remember(turn) { IceCursor() }
            val motion by rememberMotion()
            var frame by remember(turn) { mutableIntStateOf(0) }
            var penalty by remember(turn) { mutableIntStateOf(0) }
            val ms = rememberCountdown(20, running = pick < 0, key = turn) { pick = -1; Sfx.wrong(); phase = IceQ11.Result }
            val left = (ms - penalty * 3000L).coerceAtLeast(0L)
            val leftNow = androidx.compose.runtime.rememberUpdatedState(left)
            LaunchedEffect(turn) {
                val clock = FrameClock()
                while (pick < 0 && phase == IceQ11.Play) {
                    val dt = clock.tick(withFrameNanos { it })
                    when (val r = cur.step(-motion.gx, motion.gy, dt)) {
                        -2 -> { penalty++; Sfx.wrong(); Sfx.buzz(120, true) }
                        -1 -> {}
                        else -> { pick = r; val ok = opts.getOrNull(r) == q?.ok; if (ok) { Sfx.correct(); tally.add(host, listOf(who), 1) } else Sfx.wrong(); phase = IceQ11.Result }
                    }
                    if (leftNow.value <= 0L && pick < 0) { Sfx.wrong(); phase = IceQ11.Result }
                    frame++
                }
            }
            Box(Modifier.fillMaxSize().background(Color(0xFF0E1622)).statusBarsPadding().navigationBarsPadding()) {
                Canvas(Modifier.fillMaxSize()) {
                    frame.let { }
                    val w = size.width; val h = size.height
                    fun sp(px: Float, py: Float) = Offset(w / 2 + px * w * 0.46f, h / 2 + py * h * 0.42f)
                    for (k in 0..3) {
                        val tl = Offset(if (k % 2 == 0) 0f else w * 0.64f, if (k < 2) 0f else h * 0.73f)
                        drawRoundRect(if (cur.corner == k) game.color.copy(alpha = 0.35f + 0.4f * cur.dwell / 1.5f) else Color.White.copy(alpha = 0.07f), tl + Offset(8f, 8f), Size(w * 0.36f - 16f, h * 0.27f - 16f), CornerRadius(24f))
                    }
                    cur.spikes.forEach { s -> drawCircle(C.red, w * 0.035f, sp(s.x, s.y)) }
                    drawCircle(Color.White, w * 0.045f, sp(cur.x, cur.y))
                }
                listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEachIndexed { k, a ->
                    Text(opts.getOrNull(k) ?: "", style = Type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = C.text, textAlign = TextAlign.Center, modifier = Modifier.align(a).fillMaxWidth(0.36f).padding(14.dp))
                }
                Column(Modifier.align(Alignment.Center).fillMaxWidth(0.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(q?.q ?: "", style = Type.h, color = C.text, textAlign = TextAlign.Center)
                    TimerText(left, color = game.color)
                }
            }
        }

        IceQ11.Result -> {
            val right = pick >= 0 && opts.getOrNull(pick) == q?.ok
            Announce(if (right) "✅" else "❌", if (right) t("Σωστό!", "Right!") else t("Όχι…", "No…"), t("Η απάντηση: ${q?.ok}", "The answer: ${q?.ok}"), if (turn + 1 < total) t("Πάσα", "Pass") else t("Αποτελέσματα", "Results"), game.color) {
                if (turn + 1 < total) { turn++; deal(); phase = IceQ11.Pass } else phase = IceQ11.Over
            }
        }

        IceQ11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = IceQ11.Intro }
    }
}

/* ======================= 🔐 Χρηματοκιβώτιο ======================= */
/**
 * The dial has 40 numbers. Step 0 turns clockwise to the first number, step 1 anticlockwise to the
 * second, step 2 clockwise to the third. Turning the wrong way or too fast resets it to step 0.
 */
class SafeDial(val code: List<Int>) {
    val numbers = 40
    var step = 0; private set
    var angle = 0f; private set // degrees, clockwise positive
    private var last: Float? = null
    var resets = 0; private set
    val number get() = (floor(((angle % 360 + 360) % 360) / 360f * numbers).toInt()) % numbers
    val clockwise get() = step % 2 == 0
    val open get() = step >= 3
    private var against = 0f

    /** Feed the wheel's angle in degrees (any range) and the frame time. Returns "reset" or null. */
    fun feed(wheel: Float, dt: Float): String? {
        val l = last; last = wheel
        if (l == null || open) return null
        var d = wheel - l
        if (d > 180) d -= 360; if (d < -180) d += 360
        angle += d
        if (dt > 0 && abs(d) / dt > 420f) { reset(); return "reset" }
        val wrong = if (clockwise) d < 0 else d > 0
        against = if (wrong) against + abs(d) else 0f
        if (against > 360f / numbers * 1.5f) { reset(); return "reset" }
        return null
    }

    /** How near the dial is to the number it needs (0 = on it). */
    fun distance(): Int { if (open) return 0; val dd = abs(number - code[step]); return minOf(dd, numbers - dd) }

    /** «Κλείδωσε!»: true if on the right number (next step), false resets. */
    fun lock(): Boolean { if (open) return true; return if (distance() == 0) { step++; against = 0f; true } else { reset(); false } }

    private fun reset() { step = 0; resets++; against = 0f }

    companion object { fun random(rnd: Random = Random) = SafeDial(List(3) { rnd.nextInt(40) }) }
}

private enum class Safe11 { Intro, Pass, Turn, Result, Over }

@Composable
fun SafeGame(host: GameHost) {
    val game = Games.byId("safe")!!
    val n = Store.night
    var phase by remember { mutableStateOf(Safe11.Intro) }
    var round by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }
    val sides = n.sides()
    val side = sides.getOrElse(round % maxOf(1, sides.size)) { "" }
    val crew = n.membersOf(side).ifEmpty { n.names }
    val rounds = sides.size

    when (phase) {
        Safe11.Intro -> Intro(game, host, listOf(
            t("60″ για να ανοίξετε ένα χρηματοκιβώτιο με 3 νούμερα (0–39). Κρατάς το κινητό οριζόντιο, όρθιο μπροστά σου, και το γυρίζεις σαν τιμόνι.", "60″ to open a safe with 3 numbers (0–39). Hold the phone sideways, upright in front of you, and turn it like a steering wheel."),
            t("Όσο πλησιάζεις στο σωστό, το κινητό δονείται πιο δυνατά. Πάνω του: δόνηση και «κλικ».", "The closer you get, the stronger it buzzes. On it: a buzz and a «click»."),
            t("1ο νούμερο δεξιόστροφα, 2ο αριστερόστροφα, 3ο δεξιόστροφα, κι αλλάζει ο παίκτης σε κάθε νούμερο. Όταν είσαι πάνω του φωνάζεις «Κλείδωσε!» και κάποιος πατάει το κουμπί.", "1st number clockwise, 2nd anticlockwise, 3rd clockwise, and the player changes each number. On it, shout «Lock!» and someone presses the button."),
            t("Λάθος φορά, πολύ γρήγορο γύρισμα ή λάθος «Κλείδωσε»: ο μηχανισμός μηδενίζει. Άνοιξε: +3 στην ομάδα (+1 ακόμα με πάνω από 20″).", "Wrong way, too fast or a wrong «Lock»: the mechanism resets. Opened: +3 to the team (+1 more with over 20″ left)."),
        )) { round = 0; phase = Safe11.Pass }

        Safe11.Pass -> { QuitGuard(host); PassTo(n.label(side), androidx.compose.ui.graphics.Color(n.colorOf(side)), note = t("Ξεκινάει ο/η ${crew.first()}. Κινητό οριζόντιο, όρθιο.", "${crew.first()} starts. Phone sideways, upright."), button = t("Ανοίγουμε", "Crack it")) { phase = Safe11.Turn } }

        Safe11.Turn -> {
            QuitGuard(host)
            val dial = remember(round) { SafeDial.random() }
            val motion by rememberMotion()
            var frame by remember(round) { mutableIntStateOf(0) }
            var msg by remember(round) { mutableStateOf<String?>(null) }
            val ms = rememberCountdown(60, running = !dial.open, key = round) { opened = false; left = 0; Sfx.boom(); phase = Safe11.Result }
            LaunchedEffect(round) {
                val clock = FrameClock()
                var lastNum = -1
                while (!dial.open) {
                    val dt = clock.tick(withFrameNanos { it })
                    val wheel = Math.toDegrees(atan2(motion.gx.toDouble(), motion.gy.toDouble())).toFloat()
                    if (dial.feed(wheel, dt) == "reset") { msg = t("Μηδένισε!", "Reset!"); Sfx.wrong(); Sfx.buzz(200, true) }
                    if (dial.number != lastNum) {
                        lastNum = dial.number
                        when (dial.distance()) { 0 -> { Sfx.tap(); Sfx.buzz(70, true) }; 1, 2 -> Sfx.buzz(25); in 3..5 -> Sfx.buzz(8); else -> {} }
                    }
                    frame++
                }
            }
            val who = crew[dial.step.coerceAtMost(2) % crew.size]
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔐 $who", style = Type.h, color = colorOf(who))
                TimerText(ms, color = game.color)
                Text(t("Νούμερο ${dial.step + 1} / 3 · ", "Number ${dial.step + 1} / 3 · ") + if (dial.clockwise) t("δεξιόστροφα ↻", "clockwise ↻") else t("αριστερόστροφα ↺", "anticlockwise ↺"), style = Type.title, color = C.text)
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    frame.let { }
                    val c = Offset(size.width / 2, size.height / 2); val R = minOf(size.width, size.height) * 0.42f
                    drawCircle(Color(0xFF2A2F3A), R, c); drawCircle(C.line, R, c, style = Stroke(6f))
                    for (k in 0 until dial.numbers) rotate(dial.angle + k * 360f / dial.numbers, c) { val big = k % 5 == 0; drawLine(if (big) C.text else C.dim, Offset(c.x, c.y - R), Offset(c.x, c.y - R + (if (big) 28f else 14f)), if (big) 5f else 3f) }
                    drawLine(game.color, Offset(c.x, c.y - R - 24f), Offset(c.x, c.y - R + 8f), 8f)
                    drawCircle(game.color.copy(alpha = 0.25f), R * 0.3f, c)
                }
                Text("${dial.number}", style = Type.huge, color = C.text)
                msg?.let { Text(it, style = Type.h, color = C.red) }
                BigButton(t("🔒 Κλείδωσε!", "🔒 Lock!"), {
                    if (dial.lock()) { msg = null; Sfx.correct(); Sfx.buzz(120, true); if (dial.open) { opened = true; left = (ms / 1000).toInt(); Sfx.win(); phase = Safe11.Result } }
                    else { msg = t("Λάθος! Μηδένισε.", "Wrong! Reset."); Sfx.wrong() }
                }, color = game.color)
            }
        }

        Safe11.Result -> {
            QuitGuard(host)
            remember(round) { if (opened) { tally.add(host, crew, 3 + if (left > 20) 1 else 0); host.log(t("Χρηματοκιβώτιο: ${n.label(side)} το άνοιξε", "Safe: ${n.label(side)} opened it")) }; 0 }
            Announce(if (opened) "💰" else "🔒", n.label(side), if (opened) t("Το ανοίξατε! (${left}″)", "Opened! (${left}″)") else t("Έμεινε κλειστό.", "Stayed shut."),
                if (round + 1 < rounds) t("Επόμενη ομάδα", "Next team") else t("Αποτελέσματα", "Results"), game.color) {
                if (round + 1 < rounds) { round++; phase = Safe11.Pass } else phase = Safe11.Over
            }
        }

        Safe11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Safe11.Intro }
    }
}
