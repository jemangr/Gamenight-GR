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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import gr.wsol.gamenight.ui.Particles
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.card
import gr.wsol.gamenight.ui.drawNeonBall
import gr.wsol.gamenight.ui.drawNeonCircle
import gr.wsol.gamenight.ui.drawNeonLine
import gr.wsol.gamenight.ui.drawNeonRect
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

private fun DrawScope.drawColorMarker(index: Int, center: Offset, size: Float, color: Color = Color.White) {
    when (index % 4) {
        0 -> drawCircle(color, size * 0.42f, center)
        1 -> {
            val p = Path().apply {
                moveTo(center.x, center.y - size * 0.48f)
                lineTo(center.x + size * 0.48f, center.y)
                lineTo(center.x, center.y + size * 0.48f)
                lineTo(center.x - size * 0.48f, center.y)
                close()
            }
            drawPath(p, color)
        }
        2 -> {
            val p = Path().apply {
                moveTo(center.x, center.y - size * 0.48f)
                lineTo(center.x + size * 0.48f, center.y + size * 0.42f)
                lineTo(center.x - size * 0.48f, center.y + size * 0.42f)
                close()
            }
            drawPath(p, color)
        }
        3 -> drawRect(color, Offset(center.x - size * 0.35f, center.y - size * 0.35f), Size(size * 0.7f, size * 0.7f))
    }
}

private class FallingCut(
    var x: Float,
    var y: Float,
    var w: Float,
    val fh: Float,
    var vy: Float = -0.04f,
    var rot: Float = 0f,
    var vRot: Float = 90f,
    val color: Color,
    var alpha: Float = 1f
)

@Composable
fun GateRun(key: Int, onOver: (Int) -> Unit) {
    val g = remember(key) { GateGame() }
    var frame by remember(key) { mutableIntStateOf(0) }
    val particles = remember(key) { Particles(80) }
    var overTrigger by remember(key) { mutableStateOf<Any?>(null) }
    var flashRed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        val clock = FrameClock()
        while (true) {
            val dt = clock.tick(withFrameNanos { it })
            val r = g.step(dt)
            particles.update(dt)
            frame++
            if (r > 0) {
                Sfx.tick()
                if (g.mult > 1) Sfx.buzz(15)
                particles.emit(0.5f * 1000f, g.ballY * 1800f, GATE_COLORS[g.ball % GATE_COLORS.size], count = 16)
            }
            if (r < 0) {
                Sfx.boom()
                Sfx.buzz(300, true)
                flashRed = true
                overTrigger = Unit
                onOver(g.score)
                break
            }
        }
    }
    ArcadeBody(shakeTrigger = overTrigger, flashRed = flashRed) {
        Canvas(Modifier.fillMaxSize().pointerInput(key) { detectTapGestures { g.tap(); Sfx.tap() } }) {
            frame.let { }
            val w = size.width; val h = size.height
            g.bars.forEach { b ->
                val col = GATE_COLORS[b.color % g.colors]
                val bx = w * 0.12f; val by = b.y * h - 12f; val bw = w * 0.76f; val bh = 24f
                // Glow aura around tube
                drawRoundRect(col.copy(alpha = 0.22f), Offset(bx - 3f, by - 3f), Size(bw + 6f, bh + 6f), CornerRadius(12f))
                // Tube body with gradient
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(lerp(col, Color.White, 0.45f), col, lerp(col, Color.Black, 0.4f)),
                        startY = by, endY = by + bh
                    ),
                    Offset(bx, by), Size(bw, bh), CornerRadius(10f)
                )
                // Crisp highlight outline
                drawRoundRect(Color.White.copy(alpha = 0.65f), Offset(bx, by), Size(bw, bh), CornerRadius(10f), style = Stroke(1.5f))
                // Colorblind markers on both ends and center
                drawColorMarker(b.color % g.colors, Offset(w / 2f, by + bh / 2f), 12f, Color.White.copy(alpha = 0.9f))
                drawColorMarker(b.color % g.colors, Offset(bx + 18f, by + bh / 2f), 10f, Color.White.copy(alpha = 0.8f))
                drawColorMarker(b.color % g.colors, Offset(bx + bw - 18f, by + bh / 2f), 10f, Color.White.copy(alpha = 0.8f))
            }
            // Neon ball with colorblind marker
            val ballCenter = Offset(w / 2f, g.ballY * h)
            val ballR = w * 0.055f
            drawNeonBall(GATE_COLORS[g.ball], ballCenter, ballR)
            drawColorMarker(g.ball, ballCenter, 14f, Color.White)
            particles.draw(this)
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
    var reported by remember(key) { mutableStateOf(false) }
    var pop by remember(key) { mutableStateOf<String?>(null) }
    val cuts = remember(key) { mutableListOf<FallingCut>() }
    val particles = remember(key) { Particles(80) }
    var camBase by remember(key) { mutableFloatStateOf(0f) }
    var overTrigger by remember(key) { mutableStateOf<Any?>(null) }
    var flashRed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        val clock = FrameClock()
        while (!g.over) {
            val dt = clock.tick(withFrameNanos { it })
            g.step(dt)
            val targetBase = (g.floors.size - 14).coerceAtLeast(0).toFloat()
            camBase += (targetBase - camBase) * (dt * 7f).coerceAtMost(1f)
            for (c in cuts) {
                c.vy += 1.8f * dt
                c.y += c.vy * dt
                c.rot += c.vRot * dt
                c.alpha -= dt * 0.9f
            }
            cuts.removeAll { it.alpha <= 0f }
            particles.update(dt)
            frame++
        }
    }
    ArcadeBody(shakeTrigger = overTrigger, flashRed = flashRed) {
        Canvas(Modifier.fillMaxSize().pointerInput(key) {
            detectTapGestures {
                val prevTop = g.floors.last()
                val px = g.x; val pw = g.w
                when (g.drop()) {
                    "perfect" -> {
                        Sfx.correct(); Sfx.buzz(30)
                        pop = if (g.streak % 3 == 0) t("ΤΕΛΕΙΟ! +πλάτος", "PERFECT! +width") else t("ΤΕΛΕΙΟ ×${g.streak}", "PERFECT ×${g.streak}")
                        particles.emit(size.width * (prevTop.x + prevTop.w / 2f), size.height * 0.88f - (g.floors.size - 1 - camBase) * (size.height * 0.045f), C.lime, 18)
                    }
                    "ok" -> {
                        Sfx.tap(); pop = null
                        val fh = size.height * 0.045f
                        val curY = size.height * 0.88f - (g.floors.size - 1 - camBase) * fh
                        val blockCol = Color.hsv((200f + g.floors.size * 9f) % 360f, 0.55f, 0.95f)
                        if (px < prevTop.x) {
                            val cw = prevTop.x - px
                            cuts += FallingCut(px, curY, cw, fh, vy = 0.02f, vRot = -100f, color = blockCol)
                        } else if (px + pw > prevTop.x + prevTop.w) {
                            val cx = prevTop.x + prevTop.w
                            val cw = (px + pw) - cx
                            cuts += FallingCut(cx, curY, cw, fh, vy = 0.02f, vRot = 100f, color = blockCol)
                        }
                    }
                    else -> if (!reported) {
                        reported = true; Sfx.boom(); Sfx.buzz(300, true)
                        flashRed = true; overTrigger = Unit
                        onOver(g.score)
                    }
                }
            }
        }) {
            frame.let { }
            val w = size.width; val h = size.height; val fh = h * 0.045f
            fun drawBlock3D(bx: Float, by: Float, bw: Float, bh: Float, col: Color, alpha: Float = 1f) {
                val c = col.copy(alpha = alpha)
                // Glow
                drawRoundRect(c.copy(alpha = 0.2f * alpha), Offset(bx - 2f, by - 2f), Size(bw + 4f, bh + 4f), CornerRadius(4f))
                // Front
                drawRect(c, Offset(bx, by), Size(bw, bh - 3f))
                // Top highlight
                drawRect(lerp(c, Color.White, 0.4f), Offset(bx, by), Size(bw, (bh - 3f) * 0.25f))
                // Bottom shadow
                drawRect(lerp(c, Color.Black, 0.45f), Offset(bx, by + (bh - 3f) * 0.75f), Size(bw, (bh - 3f) * 0.25f))
                // Neon outline
                drawRect(Color.White.copy(alpha = 0.5f * alpha), Offset(bx, by), Size(bw, bh - 3f), style = Stroke(1.5f))
            }

            // Placed floors
            g.floors.forEachIndexed { i, f ->
                val y = h * 0.88f - (i - camBase) * fh
                if (y in -fh..(h + fh)) {
                    val col = Color.hsv((200f + i * 9f) % 360f, 0.55f, 0.95f)
                    drawBlock3D(f.x * w, y, f.w * w, fh, col)
                }
            }
            // Moving floor
            val curY = h * 0.88f - (g.floors.size - camBase) * fh
            drawBlock3D(g.x * w, curY, g.w * w, fh, C.lime)

            // Falling cut pieces
            for (c in cuts) {
                rotate(c.rot, Offset(c.x * w + c.w * w / 2f, c.y + c.fh / 2f)) {
                    drawBlock3D(c.x * w, c.y, c.w * w, c.fh, c.color, c.alpha)
                }
            }
            particles.draw(this)
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
    var overTrigger by remember(key) { mutableStateOf<Any?>(null) }
    var flashRed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        val clock = FrameClock()
        while (!g.over) {
            g.step(clock.tick(withFrameNanos { it }))
            frame++
        }
        flashRed = true
        overTrigger = Unit
        Sfx.boom()
        onOver(g.score)
    }
    ArcadeBody(shakeTrigger = overTrigger, flashRed = flashRed) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            frame.let { }
            Hud(g.score, g.mult, "%.1f″".format(g.time), C.amber)
            Text(t(g.rule.el, g.rule.en), style = Type.title, color = C.amber, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            (0 until 4).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until 4).forEach { c ->
                        val i = r * 4 + c
                        val isRed = i == g.red
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .card(accent = if (isRed) Color(0xFFE53935) else null)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isRed) Color(0xFFE53935) else C.panel2)
                                .clickable(role = Role.Button) {
                                    if (g.tap(i)) { Sfx.tick(); Sfx.buzz(10) }
                                    else { Sfx.wrong(); Sfx.buzz(120) }
                                    frame++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${g.cells[i]}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = C.text)
                        }
                    }
                }
            }
        }
    }
}

/* ======================= ✂️ Κόψε και ταίριαξε ======================= */
private class SlicedPiece(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rot: Float,
    var vRot: Float,
    val r: Float,
    val isRed: Boolean,
    val isLeft: Boolean,
    var alpha: Float = 1f
)

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
    val particles = remember(key) { Particles(90) }
    val sliced = remember(key) { mutableListOf<SlicedPiece>() }
    var overTrigger by remember(key) { mutableStateOf<Any?>(null) }
    var flashRed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        val clock = FrameClock()
        while (!g.over) {
            val dt = clock.tick(withFrameNanos { it })
            g.step(dt)
            particles.update(dt)
            for (p in sliced) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rot += p.vRot * dt
                p.alpha -= dt * 1.8f
            }
            sliced.removeAll { it.alpha <= 0f }
            frame++
        }
        flashRed = true
        overTrigger = Unit
        Sfx.boom()
        onOver(g.score)
    }
    ArcadeBody(shakeTrigger = overTrigger, flashRed = flashRed) {
        var drag by remember { mutableStateOf(Offset.Zero) }
        Canvas(Modifier.fillMaxSize().pointerInput(key) {
            detectDragGestures(onDragStart = { drag = Offset.Zero }, onDragEnd = {
                val d = if (abs(drag.x) > abs(drag.y)) (if (drag.x > 0) 1 else 3) else (if (drag.y > 0) 2 else 0)
                if (drag.getDistance() > 40f) {
                    val tgt = g.target()
                    val ok = g.swipe(d)
                    if (ok) {
                        Sfx.pass(); Sfx.buzz(15)
                        if (tgt != null) {
                            val cx = tgt.x * size.width; val cy = tgt.y * size.height; val r = size.width * 0.09f
                            particles.emit(cx, cy, if (tgt.red) Color(0xFFFF5252) else Color(0xFF448AFF), count = 22)
                            sliced += SlicedPiece(cx, cy, -140f, -60f, 0f, -180f, r, tgt.red, isLeft = true)
                            sliced += SlicedPiece(cx, cy, 140f, -60f, 0f, 180f, r, tgt.red, isLeft = false)
                        }
                    } else {
                        Sfx.wrong(); Sfx.buzz(150)
                    }
                }
            }) { ch, amount -> ch.consume(); drag += amount }
        }) {
            frame.let { }
            val w = size.width; val h = size.height

            // Cutting line as a glowing laser blade of light
            val cutY = h * 0.45f
            drawNeonLine(Color(0xFF00E5FF), Offset(0f, cutY), Offset(w, cutY), 3f)
            drawLine(Color.White, Offset(0f, cutY), Offset(w, cutY), 1.5f)

            val tgt = g.target()
            // Active rising shapes
            g.shapes.filter { !it.done }.forEach { s ->
                val c = Offset(s.x * w, s.y * h); val r = w * 0.09f
                val col = if (s.red) Color(0xFFE53935) else Color(0xFF1E63D6)

                // Outer glow
                drawCircle(col.copy(alpha = 0.25f), r * 1.25f, c)
                // Radial sphere gradient
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.6f), col, lerp(col, Color.Black, 0.4f)),
                        center = Offset(c.x - r * 0.25f, c.y - r * 0.25f),
                        radius = r
                    ),
                    r, c
                )
                // Target highlight ring
                if (s == tgt) {
                    drawNeonCircle(Color.White, c, r + 4f, 2.5f)
                }

                // Arrow inside
                rotate(s.dir * 90f, c) {
                    val p = Path().apply {
                        moveTo(c.x, c.y - r * 0.55f)
                        lineTo(c.x + r * 0.45f, c.y + r * 0.1f)
                        lineTo(c.x + r * 0.16f, c.y + r * 0.1f)
                        lineTo(c.x + r * 0.16f, c.y + r * 0.55f)
                        lineTo(c.x - r * 0.16f, c.y + r * 0.55f)
                        lineTo(c.x - r * 0.16f, c.y + r * 0.1f)
                        lineTo(c.x - r * 0.45f, c.y + r * 0.1f)
                        close()
                    }
                    drawPath(p, Color.White)
                }
            }

            // Sliced pieces flying apart
            for (p in sliced) {
                val col = (if (p.isRed) Color(0xFFE53935) else Color(0xFF1E63D6)).copy(alpha = p.alpha)
                rotate(p.rot, Offset(p.x, p.y)) {
                    val arcStart = if (p.isLeft) 90f else 270f
                    drawArc(col, arcStart, 180f, true, Offset(p.x - p.r, p.y - p.r), Size(p.r * 2f, p.r * 2f))
                }
            }

            // Laser sparks
            particles.draw(this)
        }
        Column { Hud(g.score, g.mult, "❤️".repeat((3 - g.mistakes).coerceAtLeast(0))); ArcadeNote(t("Κόκκινο: σύρε προς το βέλος. Μπλε: ανάποδα. Πριν τη γραμμή!", "Red: swipe with the arrow. Blue: against it. Before the line!")) }
    }
}
