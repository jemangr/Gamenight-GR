package gr.wsol.gamenight.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Type
import kotlin.math.abs
import kotlin.random.Random

private val GATE_COLORS = listOf(Color(0xFFFF5FA2), Color(0xFF22D3EE), Color(0xFFFFB020), Color(0xFF39FF88))

/* ======================= 🌈 Χρωματική πύλη ======================= */
class GateGame(private val rnd: Random = Random) {
    data class Bar(var y: Float, val color: Int, var passed: Boolean = false)
    val bars = mutableListOf<Bar>()
    var ball = 0; private set
    var passes = 0; private set
    var score = 0; private set
    var streak = 0; private set
    var over = false; private set
    private var sinceTap = 9f
    val ballY = 0.8f
    val colors get() = 2 + (if (passes >= 50) 1 else 0) + (if (passes >= 100) 1 else 0)
    val speed get() = 0.32f + 0.03f * (passes / 10)
    /** ×2, ×4, ×8 for 3, 6, 9 perfect taps in a row. */
    val mult get() = when { streak >= 9 -> 8; streak >= 6 -> 4; streak >= 3 -> 2; else -> 1 }

    fun tap() { if (!over) { ball = (ball + 1) % colors; sinceTap = 0f } }

    /** Returns +points when a bar was passed this frame, -1 on game over, 0 otherwise. */
    fun step(dt: Float): Int {
        if (over) return 0
        sinceTap += dt
        if (bars.isEmpty() || bars.last().y > 0.42f) bars += Bar(-0.05f, rnd.nextInt(colors))
        var got = 0
        bars.forEach { b ->
            b.y += speed * dt
            if (!b.passed && b.y >= ballY) {
                b.passed = true
                if (b.color % colors != ball) { over = true; return -1 }
                passes++
                // Perfect: the colour was set just in time (the last tap less than ⅓″ before).
                if (sinceTap < 0.33f) streak++ else streak = 0
                score += mult; got = mult
            }
        }
        bars.removeAll { it.y > 1.1f }
        return got
    }
}

@Composable
fun GateRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { GateGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        val clock = FrameClock()
        while (true) { val r = g.step(clock.tick(withFrameNanos { it })); frame++; if (r > 0) { Sfx.tick(); if (g.mult > 1) Sfx.buzz(15) }; if (r < 0) { Sfx.boom(); Sfx.buzz(300, true); onOver(g.score); break } }
    }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF0B0A12)).pointerInput(key) { detectTapGestures { g.tap(); Sfx.tap() } }) {
            frame.let { }
            val w = size.width; val h = size.height
            g.bars.forEach { b -> drawRoundRect(GATE_COLORS[b.color % g.colors], Offset(w * 0.12f, b.y * h - 10f), Size(w * 0.76f, 20f), CornerRadius(10f)) }
            drawCircle(GATE_COLORS[g.ball], w * 0.05f, Offset(w / 2, g.ballY * h))
            drawCircle(Color.White.copy(alpha = 0.5f), w * 0.05f, Offset(w / 2, g.ballY * h), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        }
        Column { Hud(g.score, g.mult, t("χρώματα: ${g.colors}", "colours: ${g.colors}")); ArcadeNote(t("Πάτα για να αλλάξεις χρώμα. Πέρνα μόνο από το ίδιο χρώμα.", "Tap to change colour. Pass only through your colour.")) }
    }
}

/* ======================= 🏗️ Πύργος ======================= */
class TowerGame {
    data class Floor(val x: Float, val w: Float)
    val floors = mutableListOf(Floor(0.3f, 0.4f))
    var x = 0f; private set
    var w = 0.4f; private set
    private var dir = 1f
    var streak = 0; private set
    var score = 0; private set
    var over = false; private set
    val speed get() = 0.45f + 0.08f * (floors.size / 10)

    fun step(dt: Float) { if (over) return; x += dir * speed * dt; if (x + w > 1f) { x = 1f - w; dir = -1f }; if (x < 0f) { x = 0f; dir = 1f } }

    /** Drops the block. Returns "perfect", "ok" or "over". */
    fun drop(): String {
        if (over) return "over"
        val top = floors.last()
        val l = maxOf(x, top.x); val r = minOf(x + w, top.x + top.w)
        if (r <= l) { over = true; return "over" }
        val perfect = abs(x - top.x) < 0.012f
        if (perfect) {
            streak++
            var nw = top.w
            if (streak % 3 == 0) nw = minOf(top.w + 0.04f, 0.6f) // three perfect: it grows again
            floors += Floor(top.x - (nw - top.w) / 2, nw); score += 3
        } else { streak = 0; floors += Floor(l, r - l); score += 1 }
        w = floors.last().w; x = if (dir > 0) 0f else 1f - w
        return if (perfect) "perfect" else "ok"
    }
}

@Composable
fun TowerRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { TowerGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    // a quick double tap after the end must not report the run twice (in versus: a fake score for the next one)
    var reported by remember(key) { mutableStateOf(false) }
    var pop by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) { val clock = FrameClock(); while (!g.over) { g.step(clock.tick(withFrameNanos { it })); frame++ } }
    ArcadeBody {
        Canvas(Modifier.fillMaxSize().background(Color(0xFF0E1326)).pointerInput(key) {
            detectTapGestures {
                when (g.drop()) {
                    "perfect" -> { Sfx.correct(); Sfx.buzz(30); pop = if (g.streak % 3 == 0) t("ΤΕΛΕΙΟ! +πλάτος", "PERFECT! +width") else t("ΤΕΛΕΙΟ ×${g.streak}", "PERFECT ×${g.streak}") }
                    "ok" -> { Sfx.tap(); pop = null }
                    else -> if (!reported) { reported = true; Sfx.boom(); Sfx.buzz(300, true); onOver(g.score) }
                }
            }
        }) {
            frame.let { }
            val w = size.width; val h = size.height; val fh = h * 0.045f
            val visible = 14
            val base = (g.floors.size - visible).coerceAtLeast(0)
            g.floors.drop(base).forEachIndexed { i, f ->
                val y = h * 0.88f - i * fh
                drawRect(Color.hsv((200f + (base + i) * 9f) % 360f, 0.55f, 0.95f), Offset(f.x * w, y), Size(f.w * w, fh - 3f))
            }
            val y = h * 0.88f - (g.floors.size - base) * fh
            drawRect(C.lime, Offset(g.x * w, y), Size(g.w * w, fh - 3f))
        }
        Column { Hud(g.score, if (g.streak >= 2) g.streak else 1, t("όροφοι: ${g.floors.size - 1}", "floors: ${g.floors.size - 1}")); ComboPop(pop, C.lime) }
    }
}

/* ======================= 🔢 Γρήγορο πλέγμα ======================= */
class GridGame(private val rnd: Random = Random) {
    enum class Rule(val el: String, val en: String) { Even("Πάτα μόνο τους ζυγούς", "Tap only even numbers"), Odd("Πάτα μόνο τους μονούς", "Tap only odd numbers"), Max("Πάτα τον μεγαλύτερο", "Tap the biggest"), Min("Πάτα τον μικρότερο", "Tap the smallest"), Red("Βρες το κόκκινο", "Find the red one") }
    val cells = IntArray(16)
    var red = 0; private set
    var rule = Rule.Even; private set
    var time = 3f; private set
    var score = 0; private set
    var streak = 0; private set
    var over = false; private set
    private var sinceRule = 0f
    private var okInRule = 0
    val mult get() = 1 + minOf(streak / 5, 4)

    init { newRule() }

    private fun fill() {
        repeat(16) { cells[it] = rnd.nextInt(1, 100) }
        red = rnd.nextInt(16)
        when (rule) { Rule.Even -> if (cells.none { it % 2 == 0 }) cells[rnd.nextInt(16)] = 2 * rnd.nextInt(1, 50); Rule.Odd -> if (cells.none { it % 2 == 1 }) cells[rnd.nextInt(16)] = 2 * rnd.nextInt(0, 49) + 1; else -> {} }
        // Max/min must be one cell only.
        val mx = cells.max(); if (cells.count { it == mx } > 1) cells[cells.indexOfFirst { it == mx }] = mx + 1
        val mn = cells.min(); if (cells.count { it == mn } > 1) cells[cells.indexOfFirst { it == mn }] = (mn - 1).coerceAtLeast(0)
    }

    private fun newRule() { rule = Rule.entries.filter { it != rule }.random(rnd); sinceRule = 0f; okInRule = 0; fill() }

    fun ok(i: Int) = when (rule) { Rule.Even -> cells[i] % 2 == 0; Rule.Odd -> cells[i] % 2 == 1; Rule.Max -> cells[i] == cells.max(); Rule.Min -> cells[i] == cells.min(); Rule.Red -> i == red }

    fun tap(i: Int): Boolean {
        if (over) return false
        val right = ok(i)
        if (right) {
            streak++; score += mult; time = minOf(time + 0.6f, 6f); okInRule++
            if (rule == Rule.Even || rule == Rule.Odd) { cells[i] = rnd.nextInt(1, 100); if (cells.none { ok(cells.indexOf(it)) }) fill() } else fill()
            if (okInRule >= 6) newRule()
        } else { streak = 0; time -= 1f }
        if (time <= 0f) over = true
        return right
    }

    fun step(dt: Float) { if (over) return; time -= dt; sinceRule += dt; if (sinceRule > 7f) newRule(); if (time <= 0f) { time = 0f; over = true } }
}

@Composable
fun GridRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { GridGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) { val clock = FrameClock(); while (!g.over) { g.step(clock.tick(withFrameNanos { it })); frame++ }; Sfx.boom(); onOver(g.score) }
    ArcadeBody {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            frame.let { }
            Hud(g.score, g.mult, "%.1f″".format(g.time), C.amber)
            Text(t(g.rule.el, g.rule.en), style = Type.title, color = C.amber, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            (0 until 4).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until 4).forEach { c ->
                        val i = r * 4 + c
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(if (i == g.red) Color(0xFFE53935) else C.panel2)
                            .clickable(role = Role.Button) { if (g.tap(i)) { Sfx.tick(); Sfx.buzz(10) } else { Sfx.wrong(); Sfx.buzz(120) }; frame++ }, contentAlignment = Alignment.Center) {
                            Text("${g.cells[i]}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = C.text)
                        }
                    }
                }
            }
        }
    }
}

/* ======================= ✂️ Κόψε και ταίριαξε ======================= */
class SliceGame(private val rnd: Random = Random) {
    /** dir: 0 up, 1 right, 2 down, 3 left. Red: swipe with the arrow; blue: against it. */
    data class Shape(val x: Float, var y: Float, val dir: Int, val red: Boolean, var done: Boolean = false)
    val shapes = mutableListOf<Shape>()
    var mistakes = 0; private set
    var streak = 0; private set
    var score = 0; private set
    var over = false; private set
    private var spawn = 0f
    val speed get() = 0.22f + 0.012f * streak.coerceAtMost(30) + 0.004f * score.coerceAtMost(200) / 10
    val mult get() = 1 + streak / 4

    /** The shape closest to the line: shapes rise (y goes down to 0.45), so the one with the smallest y. */
    fun target() = shapes.filter { !it.done }.minByOrNull { it.y }

    /** A swipe in [dir]. True if right. */
    fun swipe(dir: Int): Boolean {
        if (over) return true
        val s = target() ?: return true
        s.done = true
        val right = if (s.red) dir == s.dir else dir == (s.dir + 2) % 4
        if (right) { streak++; score += mult } else miss()
        return right
    }

    private fun miss() { if (over) return; streak = 0; mistakes++; if (mistakes >= 3) over = true }

    fun step(dt: Float) {
        if (over) return
        spawn -= dt
        if (spawn <= 0f) { shapes += Shape(rnd.nextFloat() * 0.6f + 0.2f, 1.1f, rnd.nextInt(4), rnd.nextBoolean()); spawn = (1.4f - 0.02f * streak).coerceAtLeast(0.55f) }
        shapes.forEach { it.y -= speed * dt; if (!it.done && it.y < 0.45f && !over) { it.done = true; miss() } }
        shapes.removeAll { it.y < -0.1f || (it.done && it.y < 0.45f) }
    }
}

@Composable
fun SliceRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { SliceGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) { val clock = FrameClock(); while (!g.over) { g.step(clock.tick(withFrameNanos { it })); frame++ }; Sfx.boom(); onOver(g.score) }
    ArcadeBody {
        var drag by remember { mutableStateOf(Offset.Zero) }
        Canvas(Modifier.fillMaxSize().background(Color(0xFF120B16)).pointerInput(key) {
            detectDragGestures(onDragStart = { drag = Offset.Zero }, onDragEnd = {
                val d = if (abs(drag.x) > abs(drag.y)) (if (drag.x > 0) 1 else 3) else (if (drag.y > 0) 2 else 0)
                if (drag.getDistance() > 40f) { if (g.swipe(d)) { Sfx.pass(); Sfx.buzz(15) } else { Sfx.wrong(); Sfx.buzz(150) } }
            }) { ch, amount -> ch.consume(); drag += amount }
        }) {
            frame.let { }
            val w = size.width; val h = size.height
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, h * 0.45f), Offset(w, h * 0.45f), 4f)
            val tgt = g.target()
            g.shapes.filter { !it.done }.forEach { s ->
                val c = Offset(s.x * w, s.y * h); val r = w * 0.09f
                drawCircle(if (s.red) Color(0xFFE53935) else Color(0xFF1E63D6), r, c)
                if (s == tgt) drawCircle(Color.White, r + 6f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
                rotate(s.dir * 90f, c) {
                    val p = Path().apply { moveTo(c.x, c.y - r * 0.6f); lineTo(c.x + r * 0.45f, c.y + r * 0.1f); lineTo(c.x + r * 0.15f, c.y + r * 0.1f); lineTo(c.x + r * 0.15f, c.y + r * 0.55f); lineTo(c.x - r * 0.15f, c.y + r * 0.55f); lineTo(c.x - r * 0.15f, c.y + r * 0.1f); lineTo(c.x - r * 0.45f, c.y + r * 0.1f); close() }
                    drawPath(p, Color.White)
                }
            }
        }
        Column { Hud(g.score, g.mult, "❤️".repeat((3 - g.mistakes).coerceAtLeast(0))); ArcadeNote(t("Κόκκινο: σύρε προς το βέλος. Μπλε: ανάποδα. Πριν τη γραμμή!", "Red: swipe with the arrow. Blue: against it. Before the line!")) }
    }
}
