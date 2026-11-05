package gr.wsol.gamenight.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.extras.rememberTilt
import gr.wsol.gamenight.ui.C
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/* ======================= 🕳️ Βουτιά ======================= */
class DropGame(private val rnd: Random = Random) {
    /** A row moving up the tunnel: a floor with a gap, or a laser (deadly) with a gap. A coin may sit in the gap. */
    data class Row(var y: Float, val gap: Float, val laser: Boolean, var coin: Boolean, var passed: Boolean = false)
    val rows = mutableListOf<Row>()
    var x = 0.5f; private set
    var y = 0.3f; private set
    private var vy = 0f
    var depth = 0; private set
    var coins = 0; private set
    var streak = 0; private set
    var over = false; private set
    val gapW = 0.24f
    val speed get() = 0.16f + 0.006f * depth
    val score get() = depth + coins * 2
    val mult get() = 1 + streak / 3

    init { var yy = 0.6f; repeat(5) { rows += newRow(yy); yy += 0.24f } }

    private fun newRow(at: Float) = Row(at, rnd.nextFloat() * (1 - gapW), laser = depth > 8 && rnd.nextInt(4) == 0, coin = rnd.nextInt(3) == 0)

    /** [steer] −1…1 (tilt or touch). */
    fun step(dt: Float, steer: Float) {
        if (over) return
        x = (x + steer * 0.9f * dt).coerceIn(0.02f, 0.98f)
        vy += 2.2f * dt
        var ny = y + vy * dt
        rows.forEach { r ->
            val before = r.y
            r.y -= speed * dt
            val inGap = x > r.gap && x < r.gap + gapW
            // Lands on (or is pushed up by) a floor it was above a moment ago.
            if (!inGap && y <= before + 0.001f && ny >= r.y) { if (r.laser) { over = true; return }; ny = r.y; vy = 0f }
            if (inGap && !r.passed && ny > r.y) {
                r.passed = true; depth++
                if (r.coin) { r.coin = false; coins += mult; streak++ } else streak = 0
            }
        }
        y = ny
        if (y < 0.02f) over = true
        if (y > 0.92f) { y = 0.92f; vy = 0f }
        rows.removeAll { it.y < -0.05f }
        if (rows.last().y < 1.0f) rows += newRow(rows.last().y + 0.24f)
    }
}

@Composable
fun DropRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { DropGame() }
    val tilt by rememberTilt()
    var touch by remember(key) { mutableStateOf(0f) }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        val clock = FrameClock(); var lastDepth = 0
        while (!g.over) { g.step(clock.tick(withFrameNanos { it }), (tilt.x / 4f).coerceIn(-1f, 1f).let { if (abs(it) < 0.05f) touch else it }); if (g.depth != lastDepth) { lastDepth = g.depth; Sfx.tick() }; frame++ }
        Sfx.boom(); Sfx.buzz(300, true); onOver(g.score)
    }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF07131B)).pointerInput(key) {
            awaitEachGesture { val d = awaitFirstDown(); touch = if (d.position.x < size.width / 2) -1f else 1f; do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed }); touch = 0f }
        }) {
            frame.let { }
            val w = size.width; val h = size.height
            g.rows.forEach { r ->
                val c = if (r.laser) Color(0xFFFF4D5E) else Color(0xFF22D3EE)
                val th = if (r.laser) 6f else 18f
                drawRect(c, Offset(0f, r.y * h), Size(r.gap * w, th)); drawRect(c, Offset((r.gap + g.gapW) * w, r.y * h), Size((1 - r.gap - g.gapW) * w, th))
                if (r.coin) drawCircle(C.amber, w * 0.025f, Offset((r.gap + g.gapW / 2) * w, r.y * h + 30f))
            }
            drawCircle(C.lime, w * 0.035f, Offset(g.x * w, g.y * h - w * 0.035f))
            drawRect(Color(0x55FF4D5E), Offset.Zero, Size(w, h * 0.02f))
        }
        Column { Hud(g.score, g.mult, t("βάθος ${g.depth}", "depth ${g.depth}"), C.cyan); ArcadeNote(t("Γείρε το κινητό (ή πάτα αριστερά/δεξιά). Πέσε από τα κενά, πρόσεχε τα λέιζερ και το ταβάνι.", "Tilt the phone (or tap left/right). Drop through the gaps; mind the lasers and the ceiling.")) }
    }
}

/* ======================= 🪐 Τροχιά ======================= */
class OrbitGame(private val rnd: Random = Random) {
    data class Anchor(val x: Float, val y: Float, val mine: Offset?)
    val anchors = mutableListOf<Anchor>()
    var pos = Offset(0.5f, 0f); private set
    var vel = Offset(0f, 0.45f); private set
    var hold: Anchor? = null; private set
    private var dirSign = 1f
    private var radius = 0f
    var best = 0f; private set
    var bonus = 0; private set
    var streak = 0; private set
    var over = false; private set
    private val counted = mutableSetOf<Anchor>()
    val score get() = (best * 10).toInt() + bonus
    val mult get() = 1 + streak

    init { var yy = 0.35f; repeat(8) { anchors += newAnchor(yy); yy += 0.38f } }

    private fun newAnchor(at: Float) = Anchor(0.2f + rnd.nextFloat() * 0.6f, at, if (at > 1.2f && rnd.nextInt(2) == 0) Offset(rnd.nextFloat() * 0.8f + 0.1f, at + 0.17f) else null)

    fun press() {
        if (over) return
        val a = anchors.minByOrNull { hypot(it.x - pos.x, it.y - pos.y) } ?: return
        val d = hypot(a.x - pos.x, a.y - pos.y)
        if (d > 0.42f || d < 0.02f) return
        hold = a; radius = d
        val rx = pos.x - a.x; val ry = pos.y - a.y
        dirSign = if (rx * vel.y - ry * vel.x >= 0) 1f else -1f
    }

    fun release() { hold = null }

    private companion object { const val GRAVITY = 0.5f }

    fun step(dt: Float) {
        if (over) return
        val speed = (0.45f + 0.02f * best).coerceAtMost(0.9f)
        val h = hold
        if (h != null) {
            val ang = atan2(pos.y - h.y, pos.x - h.x) + dirSign * speed / radius * dt
            val np = Offset(h.x + cos(ang) * radius, h.y + sin(ang) * radius)
            vel = Offset(-sin(ang) * dirSign, cos(ang) * dirSign) * speed
            pos = np
        } else {
            // free flight slows down and falls back (gravity): only holding the anchors takes you up,
            // so doing nothing can't beat playing
            vel += Offset(0f, -GRAVITY * dt)
            val v = hypot(vel.x, vel.y)
            if (v > speed) vel = vel * (speed / v)
            pos += vel * dt
        }
        if (pos.y > best) best = pos.y
        anchors.forEach { a ->
            a.mine?.let { m ->
                val d = hypot(m.x - pos.x, m.y - pos.y)
                if (d < 0.03f) { over = true; return }
                if (d < 0.08f && a !in counted) { counted += a; streak++; bonus += 5 * mult }
            }
        }
        if (pos.x < 0f || pos.x > 1f || pos.y < best - 0.9f) over = true
        if (anchors.last().y < pos.y + 2.5f) anchors += newAnchor(anchors.last().y + 0.38f)
        anchors.removeAll { it.y < pos.y - 1.5f }
    }
}

@Composable
fun OrbitRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { OrbitGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) { val clock = FrameClock(); var last = 0; while (!g.over) { g.step(clock.tick(withFrameNanos { it })); if (g.streak != last) { last = g.streak; Sfx.correct(); Sfx.buzz(20) }; frame++ }; Sfx.boom(); Sfx.buzz(300, true); onOver(g.score) }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF05040A)).pointerInput(key) {
            awaitEachGesture { awaitFirstDown(); g.press(); Sfx.tap(); do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed }); g.release() }
        }) {
            frame.let { }
            val w = size.width; val h = size.height
            val camY = g.pos.y - 0.35f
            fun p(x: Float, y: Float) = Offset(x * w, h - (y - camY) * w)
            g.anchors.forEach { a ->
                drawCircle(C.ghost, 10f, p(a.x, a.y)); drawCircle(C.ghost.copy(alpha = 0.18f), 0.42f * w, p(a.x, a.y), style = Stroke(2f))
                a.mine?.let { m -> drawCircle(C.red, 0.03f * w, p(m.x, m.y)); drawCircle(C.red.copy(alpha = 0.3f), 0.08f * w, p(m.x, m.y), style = Stroke(2f)) }
            }
            g.hold?.let { a -> drawLine(C.cyan, p(a.x, a.y), p(g.pos.x, g.pos.y), 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
            drawCircle(C.cyan, 14f, p(g.pos.x, g.pos.y)); drawCircle(C.cyan.copy(alpha = 0.3f), 28f, p(g.pos.x, g.pos.y))
        }
        Column { Hud(g.score, g.mult, null, C.cyan); ArcadeNote(t("Κράτα πατημένο για να γυρίσεις γύρω από την πιο κοντινή άγκυρα, άφησε για να εκτοξευτείς. Πέρνα ξυστά από τις κόκκινες νάρκες για bonus.", "Hold to orbit the nearest anchor, release to launch. Skim past the red mines for a bonus.")) }
    }
}

/* ======================= ✨ Φωτεινή διαδρομή ======================= */
class PathGame(private val rnd: Random = Random) {
    var length = 3; private set
    var path = listOf<Int>(); private set
    var lives = 3; private set
    var streak = 0; private set
    var score = 0; private set
    val over get() = lives <= 0
    /** How long the path stays lit: shorter as the score grows. */
    val showMs get() = (2200 - 60 * score.coerceAtMost(25)).toLong().coerceAtLeast(700)
    val mult get() = 1 + streak / 2

    init { newPath() }

    fun newPath() {
        val p = mutableListOf(rnd.nextInt(25))
        var tries = 0
        while (p.size < length && tries < 200) {
            tries++
            val c = p.last(); val r = c / 5; val col = c % 5
            val next = listOf(c - 5 to (r > 0), c + 5 to (r < 4), c - 1 to (col > 0), c + 1 to (col < 4)).filter { it.second && it.first !in p }.map { it.first }
            if (next.isEmpty()) { p.clear(); p += rnd.nextInt(25); continue }
            p += next.random(rnd)
        }
        path = p
    }

    /** The cells the finger went through. True if it was the path. */
    fun trace(cells: List<Int>): Boolean {
        val ok = cells == path
        if (ok) { streak++; score += length * mult; length = minOf(length + 1, 12) } else { streak = 0; lives-- }
        newPath()
        return ok
    }
}

@Composable
fun PathRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { PathGame() }
    var showing by remember(key) { mutableStateOf(true) }
    var round by remember(key) { mutableIntStateOf(0) }
    var traced by remember(key) { mutableStateOf(listOf<Int>()) }
    var flash by remember(key) { mutableStateOf<Boolean?>(null) }
    // the new path waits for the green/red flash of the last trace (400 ms), then shows for its full time
    LaunchedEffect(key, round) { showing = true; if (round > 0) delay(450); delay(g.showMs); showing = false }
    LaunchedEffect(flash) { if (flash != null) { delay(400); flash = null } }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF07110C)).pointerInput(key, round, showing) {
            if (showing) return@pointerInput
            val cell = size.width / 5f; val top = (size.height - size.width) / 2
            fun at(o: Offset): Int? { val c = (o.x / cell).toInt(); val r = ((o.y - top) / cell).toInt(); return if (c in 0..4 && r in 0..4) r * 5 + c else null }
            detectDragGestures(onDragStart = { o -> traced = listOfNotNull(at(o)) }, onDragEnd = {
                val ok = g.trace(traced)
                flash = ok; if (ok) { Sfx.correct(); Sfx.buzz(30) } else { Sfx.wrong(); Sfx.buzz(200) }
                traced = emptyList()
                if (g.over) onOver(g.score) else round++
            }) { ch, _ -> at(ch.position)?.let { c -> if (traced.lastOrNull() != c && c !in traced) { traced = traced + c; Sfx.tick() } } }
        }) {
            val w = size.width; val cell = w / 5f; val top = (size.height - w) / 2
            for (i in 0 until 25) {
                val r = i / 5; val c = i % 5
                val lit = (showing && i in g.path) || i in traced
                val color = when { flash == true -> C.green; flash == false -> C.red; lit -> C.green; else -> Color(0xFF16261C) }
                // (while the flash lasts the path isn't drawn: it starts right after, see the LaunchedEffect above)
                drawRoundRect(color, Offset(c * cell + 6f, top + r * cell + 6f), Size(cell - 12f, cell - 12f), androidx.compose.ui.geometry.CornerRadius(18f))
            }
            if (showing && g.path.isNotEmpty()) { val s = g.path.first(); drawCircle(Color.White, cell * 0.15f, Offset((s % 5 + 0.5f) * cell, top + (s / 5 + 0.5f) * cell)) }
        }
        Column { Hud(g.score, g.mult, "❤️".repeat(g.lives.coerceAtLeast(0)), C.green); ArcadeNote(if (showing) t("Θυμήσου τη διαδρομή (ξεκινά από τη λευκή τελεία)…", "Remember the path (it starts at the white dot)…") else t("Σύρε το δάχτυλο πάνω στην ίδια διαδρομή, χωρίς να το σηκώσεις.", "Trace the same path without lifting your finger.")) }
    }
}

/* ======================= 🎯 Σφεντόνα ======================= */
class SlingGame(private val rnd: Random = Random) {
    data class Target(val x: Float, val y: Float, var hp: Int)
    val targets = mutableListOf<Target>()
    val start = Offset(0.5f, 1.15f)
    var ball = start; private set
    var vel = Offset.Zero; private set
    var flying = false; private set
    var bounces = 0; private set
    var time = 60f; private set
    var score = 0; private set
    var boards = 0; private set
    var best = 0; private set
    val over get() = time <= 0f
    /** The room: 1 wide, 1.3 tall. */
    val H = 1.3f

    init { newBoard() }

    private fun newBoard() {
        targets.clear()
        repeat(3 + minOf(boards, 3)) { targets += Target(0.12f + rnd.nextFloat() * 0.76f, 0.12f + rnd.nextFloat() * 0.7f, 1 + rnd.nextInt(1 + minOf(boards, 2))) }
    }

    fun shoot(pull: Offset) {
        if (flying || over) return
        val v = Offset(-pull.x, -pull.y) * 3.2f
        val s = hypot(v.x, v.y)
        if (s < 0.2f) return
        vel = v * (minOf(s, 2.4f) / s); flying = true; bounces = 0
    }

    /** Returns points scored this frame (a hit is worth 2^bounces). */
    fun step(dt: Float): Int {
        if (over) return 0
        time -= dt
        if (!flying) return 0
        var got = 0
        ball += vel * dt
        if (ball.x < 0.03f) { ball = Offset(0.03f, ball.y); vel = Offset(-vel.x, vel.y); bounces++ }
        if (ball.x > 0.97f) { ball = Offset(0.97f, ball.y); vel = Offset(-vel.x, vel.y); bounces++ }
        if (ball.y < 0.03f) { ball = Offset(ball.x, 0.03f); vel = Offset(vel.x, -vel.y); bounces++ }
        if (ball.y > H - 0.03f && vel.y > 0) { ball = Offset(ball.x, H - 0.03f); vel = Offset(vel.x, -vel.y); bounces++ }
        vel *= (1f - 0.35f * dt)
        targets.forEach { t -> if (t.hp > 0 && hypot(t.x - ball.x, t.y - ball.y) < 0.075f) {
            t.hp--; val v = 1 shl minOf(bounces, 6); score += v; got += v; best = maxOf(best, v)
            val nx = ball.x - t.x; val ny = ball.y - t.y; val d = hypot(nx, ny).coerceAtLeast(0.001f)
            val dot = (vel.x * nx + vel.y * ny) / d; vel = Offset(vel.x - 2 * dot * nx / d, vel.y - 2 * dot * ny / d)
            ball = Offset(t.x + nx / d * 0.08f, t.y + ny / d * 0.08f)
        } }
        targets.removeAll { it.hp <= 0 }
        if (targets.isEmpty()) { boards++; time += 5f; newBoard() }
        if (hypot(vel.x, vel.y) < 0.08f || bounces > 12) { flying = false; ball = start; vel = Offset.Zero }
        return got
    }
}

@Composable
fun SlingRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { SlingGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    var pull by remember(key) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(key) { val clock = FrameClock(); while (!g.over) { val got = g.step(clock.tick(withFrameNanos { it })); if (got > 0) { Sfx.correct(); Sfx.buzz(if (got >= 4) 60 else 20) }; frame++ }; Sfx.end(); onOver(g.score) }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF120E07)).pointerInput(key) {
            val s = size.width.toFloat()
            detectDragGestures(onDragStart = { pull = Offset.Zero }, onDragEnd = { pull?.let { g.shoot(it); Sfx.pass() }; pull = null }) { ch, amount -> ch.consume(); pull = (pull ?: Offset.Zero) + amount / s }
        }) {
            frame.let { }
            val w = size.width; val scale = minOf(w, size.height / g.H); val ox = (w - scale) / 2; val oy = (size.height - g.H * scale) / 2
            fun p(o: Offset) = Offset(ox + o.x * scale, oy + o.y * scale)
            drawRect(Color(0xFF2A2214), Offset(ox, oy), Size(scale, g.H * scale), style = Stroke(6f))
            g.targets.forEach { t -> drawCircle(Color.hsv(30f + 30f * t.hp, 0.8f, 0.95f), 0.06f * scale, p(Offset(t.x, t.y))); drawCircle(Color.Black.copy(alpha = 0.5f), 0.06f * scale, p(Offset(t.x, t.y)), style = Stroke(3f)) }
            val pl = pull
            if (pl != null && !g.flying) {
                val dir = Offset(-pl.x, -pl.y)
                drawLine(C.amber.copy(alpha = 0.7f), p(g.start), p(g.start + dir * 1.6f), 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            drawCircle(C.amber, 0.03f * scale, p(if (g.flying) g.ball else g.start))
        }
        Column { Hud(g.score, if (g.bounces > 0 && g.flying) 1 shl minOf(g.bounces, 6) else 1, "${g.time.toInt().coerceAtLeast(0)}″", C.amber); ArcadeNote(t("Τράβα προς τα κάτω και άφησε. Κάθε αναπήδηση πριν το χτύπημα διπλασιάζει τους πόντους. Καθαρό ταμπλό: +5″.", "Pull down and release. Each bounce before a hit doubles the points. A clear board: +5″.")) }
    }
}
