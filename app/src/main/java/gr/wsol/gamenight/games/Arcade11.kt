package gr.wsol.gamenight.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.C
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()
private fun wrap(a: Float): Float { var x = a % TAU; if (x < 0) x += TAU; return x }
/** The smallest angle between [a] and [b], 0..π. */
fun angleGap(a: Float, b: Float): Float { val d = abs(wrap(a) - wrap(b)); return minOf(d, TAU - d) }

/* ======================= ↔️ Διπλή αποφυγή ======================= */
class DualGame(private val rnd: Random = Random) {
    data class Block(val side: Int, val lane: Int, var y: Float, var passed: Boolean = false)
    val lane = intArrayOf(0, 1)
    private val sinceSwitch = floatArrayOf(9f, 9f)
    private val leftLane = intArrayOf(-1, -1)
    private val next = floatArrayOf(0.4f, 0.9f)
    val blocks = mutableListOf<Block>()
    val dotY = 0.82f
    var score = 0; private set
    var close = 0; private set
    var over = false; private set
    var time = 0f; private set
    val mult get() = 1 + minOf(close / 3, 4)
    fun speed(side: Int) = (if (side == 0) 0.42f else 0.50f) + 0.02f * (score / 8)

    fun tap(side: Int) { if (over) return; leftLane[side] = lane[side]; lane[side] = 1 - lane[side]; sinceSwitch[side] = 0f }

    /** +points when a block passed, -1 on a hit. */
    fun step(dt: Float): Int {
        if (over) return 0
        time += dt
        var got = 0
        for (s in 0..1) {
            sinceSwitch[s] += dt
            next[s] -= dt
            if (next[s] <= 0f) {
                blocks += Block(s, rnd.nextInt(2), -0.05f)
                next[s] = (if (s == 0) 1.15f else 0.95f) * (0.75f + rnd.nextFloat() * 0.5f) / (1f + score / 60f)
            }
        }
        for (b in blocks) {
            b.y += speed(b.side) * dt
            if (!b.passed && abs(b.y - dotY) < 0.035f && b.lane == lane[b.side]) { over = true; return -1 }
            if (!b.passed && b.y > dotY + 0.035f) {
                b.passed = true
                // a close call: you left that very lane just before it went by
                if (b.lane == leftLane[b.side] && sinceSwitch[b.side] < 0.35f) close++
                score += mult; got += mult
            }
        }
        blocks.removeAll { it.y > 1.1f }
        return got
    }
}

@Composable
fun DualRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { DualGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        val clock = FrameClock()
        while (true) { val r = g.step(clock.tick(withFrameNanos { it })); frame++; if (r > 0) Sfx.tick(); if (r < 0) { Sfx.boom(); Sfx.buzz(300, true); onOver(g.score); break } }
    }
    val cols = listOf(C.cyan, C.pink)
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF0B0A12)).pointerInput(key) { detectTapGestures { o -> g.tap(if (o.x < size.width / 2) 0 else 1); Sfx.tap() } }) {
            frame.let { }
            val w = size.width; val h = size.height; val half = w / 2
            drawLine(Color.White.copy(alpha = 0.25f), Offset(half, 0f), Offset(half, h), 3f)
            fun laneX(side: Int, lane: Int) = side * half + half * (0.27f + 0.46f * lane)
            for (s in 0..1) for (l in 0..1) drawLine(cols[s].copy(alpha = 0.12f), Offset(laneX(s, l), 0f), Offset(laneX(s, l), h), half * 0.3f)
            g.blocks.forEach { b -> drawRoundRect(cols[b.side], Offset(laneX(b.side, b.lane) - half * 0.16f, b.y * h - 14f), Size(half * 0.32f, 28f), CornerRadius(8f)) }
            for (s in 0..1) drawCircle(Color.White, half * 0.08f, Offset(laneX(s, g.lane[s]), g.dotY * h))
        }
        Column { Hud(g.score, g.mult, t("ξυστά: ${g.close}", "close: ${g.close}")); ArcadeNote(t("Πάτα αριστερά ή δεξιά: αλλάζει λωρίδα η αντίστοιχη μπίλια.", "Tap left or right: that side's dot switches lane.")) }
    }
}

/* ======================= 💫 Αναπήδηση σε δακτύλιο ======================= */
class RingGame(private val rnd: Random = Random) {
    val sectors = 12
    var spikes = setOf(0, 6); private set
    var tokens = setOf(3); private set
    var ringA = 0f; private set
    var dir = 1f; private set
    var ballA = 0f; private set
    var ballR = 0f; private set
    var out = true; private set
    var bounces = 0; private set
    var score = 0; private set
    var over = false; private set
    val ringSpeed get() = 1.1f + 0.25f * (bounces / 10)
    val ballSpeed get() = 0.9f + 0.12f * (bounces / 10)

    fun tap() { if (!over) dir = -dir }

    /** Which sector of the ring is under angle [a] now. */
    fun sectorAt(a: Float): Int = ((wrap(a - ringA) / TAU) * sectors).toInt().coerceIn(0, sectors - 1)

    /** +points when the ball bounced off a safe part, -1 on a spike. */
    fun step(dt: Float): Int {
        if (over) return 0
        ringA = wrap(ringA + dir * ringSpeed * dt)
        ballR += (if (out) 1 else -1) * ballSpeed * dt
        if (out && ballR >= 1f) {
            ballR = 1f
            val s = sectorAt(ballA)
            if (s in spikes) { over = true; return -1 }
            bounces++
            var got = 1
            if (s in tokens) { got += 3; tokens = tokens - s }
            score += got; out = false
            if (bounces % 3 == 0) reshuffle()
            return got
        }
        if (!out && ballR <= 0f) { ballR = 0f; out = true; ballA = rnd.nextFloat() * TAU }
        return 0
    }

    private fun reshuffle() {
        val n = (2 + bounces / 10).coerceAtMost(5)
        spikes = (0 until sectors).shuffled(rnd).take(n).toSet()
        tokens = ((0 until sectors).toSet() - spikes).shuffled(rnd).take(2).toSet()
    }
}

@Composable
fun RingRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { RingGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        val clock = FrameClock()
        while (true) { val r = g.step(clock.tick(withFrameNanos { it })); frame++; if (r > 1) { Sfx.correct(); Sfx.buzz(20) } else if (r > 0) Sfx.tick(); if (r < 0) { Sfx.boom(); Sfx.buzz(300, true); onOver(g.score); break } }
    }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF0B0A12)).pointerInput(key) { detectTapGestures { g.tap(); Sfx.tap() } }) {
            frame.let { }
            val c = Offset(size.width / 2, size.height * 0.55f)
            val R = minOf(size.width, size.height) * 0.38f
            val sw = R * 0.12f
            val step = 360f / g.sectors
            for (s in 0 until g.sectors) {
                val start = Math.toDegrees((g.ringA).toDouble()).toFloat() + s * step
                val col = when (s) { in g.spikes -> C.red; else -> C.ghost.copy(alpha = 0.55f) }
                drawArc(col, start + 1.5f, step - 3f, false, Offset(c.x - R, c.y - R), Size(2 * R, 2 * R), style = Stroke(sw))
                if (s in g.tokens) {
                    val mid = Math.toRadians((start + step / 2).toDouble())
                    drawCircle(C.amber, sw * 0.35f, Offset(c.x + (R - sw * 1.3f) * cos(mid).toFloat(), c.y + (R - sw * 1.3f) * sin(mid).toFloat()))
                }
            }
            // where the ball is heading
            if (g.out) drawLine(Color.White.copy(alpha = 0.12f), c, Offset(c.x + R * cos(g.ballA), c.y + R * sin(g.ballA)), 4f)
            val br = g.ballR * (R - sw / 2)
            drawCircle(Color.White, sw * 0.45f, Offset(c.x + br * cos(g.ballA), c.y + br * sin(g.ballA)))
            drawCircle(Color.White.copy(alpha = 0.3f), 6f, c)
        }
        Column { Hud(g.score, 1, t("αναπηδήσεις: ${g.bounces}", "bounces: ${g.bounces}")); ArcadeNote(t("Πάτα για να αλλάξει φορά ο δακτύλιος. Η μπίλια να πέφτει στα ήσυχα κομμάτια, όχι στα κόκκινα. Τα κίτρινα: +3.", "Tap to reverse the ring. Let the ball hit the calm parts, not the red ones. Yellow: +3.")) }
    }
}

/* ======================= 🟥 Πλέγμα λέιζερ ======================= */
class LaserGame(private val rnd: Random = Random) {
    data class Laser(val row: Boolean, val index: Int, var t: Float)
    data class Core(val r: Int, val c: Int, var left: Float)
    var size = 3; private set
    var r = 1; private set
    var c = 1; private set
    val lasers = mutableListOf<Laser>()
    var core: Core? = null; private set
    var score = 0; private set
    var lives = 3; private set
    var over = false; private set
    private var next = 1.2f
    private var nextCore = 1.5f
    val warn get() = (0.9f - 0.03f * (score / 5)).coerceAtLeast(0.3f)
    val fire = 0.25f

    fun move(dr: Int, dc: Int) { if (over) return; r = (r + dr).coerceIn(0, size - 1); c = (c + dc).coerceIn(0, size - 1); collect() }

    private fun collect() { core?.let { if (it.r == r && it.c == c) { score += 5; core = null } } }

    fun firing(l: Laser) = l.t >= warn
    fun hits(l: Laser) = firing(l) && (if (l.row) l.index == r else l.index == c)

    /** +points, -1 when the game is over. */
    fun step(dt: Float): Int {
        if (over) return 0
        var got = 0
        if (score >= 20 && size == 3) size = 4
        next -= dt
        if (next <= 0f) { lasers += Laser(rnd.nextBoolean(), rnd.nextInt(size), 0f); next = (1.4f - score / 80f).coerceAtLeast(0.55f) * (0.7f + rnd.nextFloat() * 0.6f) }
        for (l in lasers) { l.t += dt; if (hits(l)) { over = true; return -1 } }
        val done = lasers.filter { it.t >= warn + fire }
        if (done.isNotEmpty()) { lasers.removeAll(done); score += done.size; got += done.size }
        nextCore -= dt
        val k = core
        if (k == null && nextCore <= 0f) {
            val cells = (0 until size * size).map { it / size to it % size }.filter { it != r to c }
            val (cr, cc) = cells[rnd.nextInt(cells.size)]
            core = Core(cr, cc, 4f)
        } else if (k != null) {
            k.left -= dt
            if (k.left <= 0f) { core = null; nextCore = 2f; lives--; if (lives <= 0) { over = true; return -1 } }
        }
        if (core == null && nextCore <= 0f) nextCore = 2f
        return got
    }
}

@Composable
fun LaserRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { LaserGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        val clock = FrameClock()
        while (true) { val r = g.step(clock.tick(withFrameNanos { it })); frame++; if (r > 0) Sfx.tick(); if (r < 0) { Sfx.boom(); Sfx.buzz(300, true); onOver(g.score); break } }
    }
    ArcadeBody {
        var drag = Offset.Zero
        Canvas(Modifier.fillMaxSize().background(Color(0xFF0B0A12)).pointerInput(key) {
            detectDragGestures(onDragStart = { drag = Offset.Zero }, onDragEnd = {
                if (drag.getDistance() > 30f) {
                    // isometric: right-down = next column, left-down = next row
                    when { drag.x >= 0 && drag.y >= 0 -> g.move(0, 1); drag.x < 0 && drag.y < 0 -> g.move(0, -1); drag.x < 0 -> g.move(1, 0); else -> g.move(-1, 0) }
                    Sfx.tap()
                }
            }) { ch, amount -> ch.consume(); drag += amount }
        }) {
            frame.let { }
            val n = g.size
            val tw = size.width * 0.8f / n; val th = tw * 0.55f
            val o = Offset(size.width / 2, size.height * 0.5f - th * n / 2)
            fun pt(rr: Float, cc: Float) = Offset(o.x + (cc - rr) * tw / 2, o.y + (cc + rr) * th / 2)
            fun cell(rr: Int, cc: Int, col: Color) {
                val p = Path().apply { pt(rr.toFloat(), cc.toFloat()).let { moveTo(it.x, it.y) }; pt(rr.toFloat(), cc + 1f).let { lineTo(it.x, it.y) }; pt(rr + 1f, cc + 1f).let { lineTo(it.x, it.y) }; pt(rr + 1f, cc.toFloat()).let { lineTo(it.x, it.y) }; close() }
                drawPath(p, col); drawPath(p, Color.White.copy(alpha = 0.15f), style = Stroke(2f))
            }
            for (rr in 0 until n) for (cc in 0 until n) cell(rr, cc, Color(0xFF1A1830))
            g.lasers.forEach { l ->
                val col = if (g.firing(l)) C.red else C.red.copy(alpha = if ((l.t * 10).toInt() % 2 == 0) 0.35f else 0.12f)
                for (k in 0 until n) if (l.row) cell(l.index, k, col) else cell(k, l.index, col)
            }
            g.core?.let { k -> val m = pt(k.r + 0.5f, k.c + 0.5f); drawCircle(C.amber, th * 0.28f * (0.6f + k.left / 10f), m) }
            val m = pt(g.r + 0.5f, g.c + 0.5f)
            drawRoundRect(C.cyan, Offset(m.x - th * 0.3f, m.y - th * 0.75f), Size(th * 0.6f, th * 0.75f), CornerRadius(6f))
        }
        Column { Hud(g.score, 1, "❤️".repeat(g.lives.coerceAtLeast(0))); ArcadeNote(t("Σύρε διαγώνια για ένα κελί. Φύγε από τη γραμμή που αναβοσβήνει· μάζεψε τους κίτρινους πυρήνες πριν σκάσουν.", "Swipe diagonally to move one cell. Leave the flashing line; grab the yellow cores before they blow.")) }
    }
}

/* ======================= 🌀 Σπείρα ======================= */
class SpiralGame(private val rnd: Random = Random) {
    data class Ball(var r: Float, var a: Float, var vr: Float, val w: Float, var alive: Boolean = true)
    data class Core(val r: Float, val a: Float)
    val balls = mutableListOf(Ball(0.05f, 0f, 0.35f, 1.4f))
    var shield = 0f
    val arc = 0.9f // radians
    val shieldR = 1f
    val cores = mutableListOf<Core>()
    var score = 0; private set
    var wave = 0; private set
    var over = false; private set
    var perfects = 0; private set

    init { newWave() }

    private fun newWave() { wave++; cores.clear(); repeat(5 + wave) { cores += Core(0.35f + rnd.nextFloat() * 0.4f, rnd.nextFloat() * TAU) } }

    fun pos(b: Ball) = Offset(b.r * cos(b.a), b.r * sin(b.a))

    /** +points, -1 when the last ball is lost. */
    fun step(dt: Float): Int {
        if (over) return 0
        var got = 0
        if ((score >= 30 && balls.size < 2) || (score >= 80 && balls.size < 3)) balls += Ball(0.05f, rnd.nextFloat() * TAU, 0.35f + 0.05f * balls.size, if (balls.size % 2 == 0) 1.4f else -1.6f)
        for (b in balls.filter { it.alive }) {
            b.a = wrap(b.a + b.w * dt * (1f + 0.08f * wave))
            b.r += b.vr * dt * (1f + 0.05f * wave)
            if (b.vr > 0 && b.r >= shieldR) {
                val gap = angleGap(b.a, shield)
                if (gap <= arc / 2) {
                    val perfect = gap <= 0.12f
                    if (perfect) perfects++
                    val p = if (perfect) 3 else 1
                    score += p; got += p; b.r = shieldR; b.vr = -abs(b.vr)
                } else if (b.r > shieldR + 0.12f) b.alive = false
            }
            if (b.vr < 0 && b.r <= 0.06f) b.vr = abs(b.vr)
            val bp = pos(b)
            val hit = cores.firstOrNull { k -> hypot(bp.x - k.r * cos(k.a), bp.y - k.r * sin(k.a)) < 0.07f }
            if (hit != null) { cores.remove(hit); score += 2; got += 2; b.vr = -b.vr }
        }
        if (cores.isEmpty()) newWave()
        if (balls.none { it.alive }) { over = true; return -1 }
        return got
    }
}

@Composable
fun SpiralRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { SpiralGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        val clock = FrameClock()
        while (true) { val r = g.step(clock.tick(withFrameNanos { it })); frame++; if (r >= 3) { Sfx.correct(); Sfx.buzz(20) } else if (r > 0) Sfx.tick(); if (r < 0) { Sfx.boom(); Sfx.buzz(300, true); onOver(g.score); break } }
    }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF0B0A12)).pointerInput(key) {
            fun aim(o: Offset) { g.shield = atan2(o.y - size.height * 0.55f, o.x - size.width / 2f) }
            detectDragGestures(onDragStart = { aim(it) }) { ch, _ -> ch.consume(); aim(ch.position) }
        }) {
            frame.let { }
            val c = Offset(size.width / 2, size.height * 0.55f)
            val R = minOf(size.width, size.height) * 0.4f
            drawCircle(Color.White.copy(alpha = 0.07f), R, c, style = Stroke(2f))
            g.cores.forEach { k -> drawCircle(C.lime, R * 0.05f, Offset(c.x + R * k.r * cos(k.a), c.y + R * k.r * sin(k.a))) }
            val deg = Math.toDegrees(g.shield.toDouble()).toFloat(); val arcDeg = Math.toDegrees(g.arc.toDouble()).toFloat()
            drawArc(C.cyan, deg - arcDeg / 2, arcDeg, false, Offset(c.x - R, c.y - R), Size(2 * R, 2 * R), style = Stroke(R * 0.06f))
            drawArc(C.amber, deg - 7f, 14f, false, Offset(c.x - R, c.y - R), Size(2 * R, 2 * R), style = Stroke(R * 0.06f))
            g.balls.filter { it.alive }.forEach { b -> val p = g.pos(b); drawCircle(Color.White, R * 0.035f, Offset(c.x + R * p.x, c.y + R * p.y)) }
        }
        Column { Hud(g.score, 1, t("κύμα ${g.wave}", "wave ${g.wave}")); ArcadeNote(t("Σύρε γύρω γύρω για να γυρίσεις την ασπίδα. Με το κίτρινο κέντρο: Perfect ×3. Σπάσε τους πράσινους πυρήνες.", "Drag around to turn the shield. With the yellow centre: Perfect ×3. Break the green cores.")) }
    }
}
