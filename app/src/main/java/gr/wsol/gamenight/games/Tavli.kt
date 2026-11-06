package gr.wsol.gamenight.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import kotlinx.coroutines.delay
import kotlin.random.Random

fun TavliKind.title() = when (this) {
    TavliKind.Portes -> t("Πόρτες", "Portes")
    TavliKind.Plakoto -> t("Πλακωτό", "Plakoto")
    TavliKind.Fevga -> t("Φεύγα", "Fevga")
}

/** Where a tap on the board landed: a point (absolute 0–23), the middle strip, or nothing. */
object TavliLayout {
    const val MID = 0.16f
    fun col(w: Float) = w * (1 - MID) / 2

    /** Absolute point at ([x],[y]) on a board [w]×[h], -1 for the middle strip, null outside. */
    fun hit(x: Float, y: Float, w: Float, h: Float): Int? {
        val c = col(w)
        val slot = h / 13f
        val row = (y / slot).toInt()
        if (row == 6 || row !in 0..12) return if (x in c..(w - c)) -1 else null
        val r = if (row > 6) row - 1 else row // 0..11 top to bottom
        return when {
            x < c -> 12 + r
            x > w - c -> 11 - r
            else -> -1
        }
    }

    /** Top-left y of the slot of absolute point [a]. */
    fun slotY(a: Int, h: Float): Float {
        val slot = h / 13f
        val r = if (a >= 12) a - 12 else 11 - a
        return (if (r >= 6) r + 1 else r) * slot
    }
}

private enum class Tav11 { Intro, Play, GameWon, Over }

@Composable
fun TavliGame(host: GameHost) {
    val game = Games.byId("tavli")!!
    val n = Store.night
    var phase by remember { mutableStateOf(Tav11.Intro) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(n.names.take(2)) } }
    var mode by remember { mutableIntStateOf(0) } // 0 = all three in turn, 1..3 = one kind
    var target by remember { mutableIntStateOf(5) }
    var match by remember { mutableStateOf(TavliMatch(5, TavliKind.entries)) }
    var board by remember { mutableStateOf(TBoard.start(TavliKind.Portes)) }
    var turn by remember { mutableIntStateOf(0) }
    var dice by remember { mutableStateOf(listOf<Int>()) }
    var rolled by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var sel by remember { mutableStateOf<Int?>(null) }
    val undo = remember { mutableStateListOf<Pair<TBoard, List<Int>>>() }
    var lastWin by remember { mutableIntStateOf(0) }
    var lastPts by remember { mutableIntStateOf(1) }
    var starter by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }
    val colors = listOf(Color(0xFFF4EAD5), Color(0xFF3A2E2A))

    fun newGame() {
        board = TBoard.start(match.kind); turn = starter; dice = emptyList(); rolled = null; sel = null; undo.clear()
    }

    when (phase) {
        Tav11.Intro -> Intro(game, host, listOf(
            t("Τάβλι για δύο σε ένα κινητό: Πόρτες, Πλακωτό και Φεύγα με τους κανονικούς κανόνες.", "Backgammon for two on one phone: Portes, Plakoto and Fevga with the real rules."),
            t("Πάτα «Ρίξε», μετά ένα πούλι σου και πού θα πάει (φωτίζονται οι επιτρεπτές θέσεις). Για μάζεμα, πάτα τη μεσαία λωρίδα.", "Press «Roll», then one of your checkers and where it goes (allowed spots light up). To bear off, tap the middle strip."),
            t("Το κινητό ελέγχει ότι παίζεις όσα ζάρια μπορείς. Μέχρι το «Τέλος σειράς» έχεις αναίρεση.", "The phone checks you play as many dice as you can. Until «End turn» you can undo."),
            t("Νίκη 1 πόντος, διπλή (αν ο άλλος δεν έχει μαζέψει κανένα) 2. Στο Πλακωτό, αν πλακώσεις τη «μάνα» του άλλου, παίρνεις διπλό.", "A win is 1 point, a double (if the other bore off none) 2. In Plakoto, pinning the other's «mother» is a double."),
            t("Όποιος κερδίσει τον αγώνα (τους πόντους που διαλέγετε) παίρνει +2 ακόμα. Το επόμενο παιχνίδι το ξεκινά όποιος κέρδισε το προηγούμενο.", "Whoever wins the match (to the points you choose) gets +2 more. The winner of a game starts the next one."),
        ), picked.size == 2, {
            Label(t("ΠΟΙΟΙ ΠΑΙΖΟΥΝ (ΔΥΟ)", "WHO PLAYS (TWO)"))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                n.names.forEach { p -> Chip(p, p in picked, colorOf(p), onClick = {
                    if (p in picked) picked.remove(p) else { if (picked.size == 2) picked.removeAt(0); picked.add(p) }
                }) }
            }
            Label(t("ΤΙ ΠΑΙΖΟΥΜΕ", "WHAT WE PLAY"))
            Seg(listOf(0 to t("Και τα 3", "All 3"), 1 to t("Πόρτες", "Portes"), 2 to t("Πλακωτό", "Plakoto"), 3 to t("Φεύγα", "Fevga")), mode, { mode = it }, color = game.color)
            Label(t("ΜΕΧΡΙ", "UP TO"))
            Seg(listOf(1 to "1", 3 to "3", 5 to "5", 7 to "7"), target, { target = it }, color = game.color)
        }) {
            match = TavliMatch(target, if (mode == 0) TavliKind.entries else listOf(TavliKind.entries[mode - 1]))
            starter = Random.nextInt(2); newGame(); phase = Tav11.Play
        }

        Tav11.Play -> {
            QuitGuard(host)
            var isRolling by remember(board, turn) { mutableStateOf(false) }
            val legal = remember(board, dice, turn) { board.legal(turn, dice) }
            val fromSet = legal.map { it.from }.toSet()
            val targets = sel?.let { s -> legal.filter { it.from == s } }.orEmpty()

            LaunchedEffect(rolled) {
                if (rolled != null) {
                    isRolling = true
                    delay(500)
                    isRolling = false
                }
            }

            fun play(m: TMove) {
                undo += board to dice
                board = board.move(turn, m); dice = dice - m.die; sel = null; Sfx.tap()
                board.winner?.let { w ->
                    lastWin = w; lastPts = board.points(); Sfx.win()
                    // the points count as soon as the game is won (leaving before «Continue» doesn't lose them)
                    match.won(w, lastPts); tally.add(host, listOf(picked[w]), lastPts)
                    host.log(t("Τάβλι (${board.kind.title()}): ${picked[w]} +$lastPts", "Backgammon (${board.kind.title()}): ${picked[w]} +$lastPts"))
                    starter = w
                    match.winner?.let { mw -> tally.add(host, listOf(picked[mw]), 2); host.stat(picked[mw], "tavli") }
                    phase = Tav11.GameWon
                }
            }
            fun endTurn() { turn = 1 - turn; dice = emptyList(); rolled = null; sel = null; undo.clear() }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${match.kind.title()} · ${t("παιχνίδι", "game")} ${match.gameNo + 1}", style = Type.h, color = game.color, modifier = Modifier.weight(1f))
                    Text("${picked[0]} ${match.score[0]} – ${match.score[1]} ${picked[1]}", style = Type.small, color = C.muted)
                }
                Text(picked[1] + t(" · σκούρα", " · dark") + if (turn == 1) "  ◀" else "", style = Type.small, color = if (turn == 1) C.text else C.dim)
                Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(board, dice, turn, sel) {
                    detectTapGestures { o ->
                        if (dice.isEmpty()) return@detectTapGestures
                        val a = TavliLayout.hit(o.x, o.y, size.width.toFloat(), size.height.toFloat()) ?: return@detectTapGestures
                        val pip = if (a < 0) -1 else board.pipOf(turn, a)
                        // middle strip: from the bar, or bear off
                        if (a < 0) {
                            targets.firstOrNull { it.to == 0 }?.let { play(it); return@detectTapGestures }
                            if (25 in fromSet) sel = 25
                            return@detectTapGestures
                        }
                        targets.filter { it.to == pip }.maxByOrNull { it.die }?.let { play(it); return@detectTapGestures }
                        sel = if (pip in fromSet) pip else null
                    }
                }) {
                    drawBoard(board, turn, sel, targets.map { it.to }.toSet(), colors, if (sel == null) fromSet else emptySet())
                }
                Text(picked[0] + t(" · ανοιχτά", " · light") + if (turn == 0) "  ◀" else "", style = Type.small, color = if (turn == 0) C.text else C.dim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val r = rolled
                    if (r == null) BigButton(t("🎲 Ρίξε, ${picked[turn]}", "🎲 Roll, ${picked[turn]}"), {
                        val a = Random.nextInt(1, 7); val b = Random.nextInt(1, 7)
                        rolled = a to b; dice = TBoard.dice(a, b); Sfx.buzz(40)
                    }, Modifier.weight(1f), color = game.color)
                    else {
                        TavliDicePair(r.first, r.second, isRolling)
                        Text(dice.joinToString(" "), style = Type.small, color = C.dim, modifier = Modifier.weight(1f))
                        if (undo.isNotEmpty()) GhostButton(t("↶", "↶"), { val (b, d) = undo.removeAt(undo.size - 1); board = b; dice = d; sel = null })
                        BigButton(t("Τέλος σειράς", "End turn"), { endTurn() }, Modifier.weight(1f), color = game.color, enabled = legal.isEmpty())
                    }
                }
                if (rolled != null && legal.isEmpty() && undo.isEmpty()) Body(t("Δεν υπάρχει κίνηση με αυτά τα ζάρια.", "No move with these dice."), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        Tav11.GameWon -> {
            QuitGuard(host)
            val w = picked[lastWin]
            Announce("🎲", w, (if (board.motherPinned >= 0) t("Πλάκωσε τη μάνα! ", "Pinned the mother! ") else "") + if (lastPts == 2) t("Διπλή νίκη: +2", "Double win: +2") else t("Νίκη: +1", "Win: +1"),
                t("Συνέχεια", "Continue"), game.color) {
                if (match.winner != null) phase = Tav11.Over else { newGame(); phase = Tav11.Play }
            }
        }

        Tav11.Over -> GameOver(game, host, tally, headline = t("Κέρδισε ο/η ${picked[match.winner ?: 0]}!", "${picked[match.winner ?: 0]} wins!"), extra = {
            Panel { Text("${picked[0]} ${match.score[0]} – ${match.score[1]} ${picked[1]}", style = Type.title, color = C.text) }
        }) { tally.bySide.clear(); phase = Tav11.Intro }
    }
}

/** Animated dice pair with 3D rounded corners and authentic pip positions. */
@Composable
private fun TavliDicePair(d1: Int, d2: Int, isRolling: Boolean) {
    var rollTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRolling) {
        while (isRolling) {
            rollTick = (rollTick + 1) % 6
            delay(70)
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (isRolling) if (rollTick % 2 == 0) 12f else -12f else 0f,
        animationSpec = tween(durationMillis = 70),
        label = "diceRot"
    )

    val val1 = if (isRolling) (rollTick % 6) + 1 else d1
    val val2 = if (isRolling) ((rollTick + 3) % 6) + 1 else d2

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TavliDie(val1, Modifier.graphicsLayer { rotationZ = rotation })
        TavliDie(val2, Modifier.graphicsLayer { rotationZ = -rotation })
    }
}

@Composable
private fun TavliDie(value: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(38.dp)) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(8f)

        // Drop shadow
        drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(2f, 2f), size = Size(w - 2f, h - 2f), cornerRadius = r)

        // Die body with ivory bevel gradient
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFF7F3E9), Color(0xFFE8DECB))
            ),
            size = Size(w - 2f, h - 2f),
            cornerRadius = r
        )
        drawRoundRect(Color(0xFFD4C8B2), size = Size(w - 2f, h - 2f), cornerRadius = r, style = Stroke(1.5f))

        // Pip dots
        val pipR = w * 0.08f
        val pipColor = if (value == 1) Color(0xFFE53935) else Color(0xFF1A1A1A)
        val cx = (w - 2f) / 2f
        val cy = (h - 2f) / 2f
        val l = (w - 2f) * 0.28f
        val rt = (w - 2f) * 0.72f
        val t = (h - 2f) * 0.28f
        val b = (h - 2f) * 0.72f

        when (value) {
            1 -> drawCircle(pipColor, pipR * 1.35f, Offset(cx, cy))
            2 -> {
                drawCircle(pipColor, pipR, Offset(rt, t))
                drawCircle(pipColor, pipR, Offset(l, b))
            }
            3 -> {
                drawCircle(pipColor, pipR, Offset(rt, t))
                drawCircle(pipColor, pipR, Offset(cx, cy))
                drawCircle(pipColor, pipR, Offset(l, b))
            }
            4 -> {
                drawCircle(pipColor, pipR, Offset(l, t))
                drawCircle(pipColor, pipR, Offset(rt, t))
                drawCircle(pipColor, pipR, Offset(l, b))
                drawCircle(pipColor, pipR, Offset(rt, b))
            }
            5 -> {
                drawCircle(pipColor, pipR, Offset(l, t))
                drawCircle(pipColor, pipR, Offset(rt, t))
                drawCircle(pipColor, pipR, Offset(cx, cy))
                drawCircle(pipColor, pipR, Offset(l, b))
                drawCircle(pipColor, pipR, Offset(rt, b))
            }
            6 -> {
                drawCircle(pipColor, pipR, Offset(l, t))
                drawCircle(pipColor, pipR, Offset(rt, t))
                drawCircle(pipColor, pipR, Offset(l, cy))
                drawCircle(pipColor, pipR, Offset(rt, cy))
                drawCircle(pipColor, pipR, Offset(l, b))
                drawCircle(pipColor, pipR, Offset(rt, b))
            }
        }
    }
}

private fun DrawScope.drawBoard(b: TBoard, turn: Int, sel: Int?, targets: Set<Int>, colors: List<Color>, movable: Set<Int> = emptySet()) {
    val w = size.width; val h = size.height
    val c = TavliLayout.col(w)
    val slot = h / 13f

    // Outer rich mahogany frame with 3D bevel
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF4A2A14), Color(0xFF381F0E), Color(0xFF281408))
        ),
        cornerRadius = CornerRadius(18f)
    )
    drawRoundRect(
        Color(0xFF6B3E1E),
        topLeft = Offset(2f, 2f),
        size = Size(w - 4f, h - 4f),
        cornerRadius = CornerRadius(16f),
        style = Stroke(2f)
    )

    // Left and right board playing field wood grain
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF5A351C), Color(0xFF4D2C16), Color(0xFF533019)),
            startX = 0f, endX = c
        ),
        topLeft = Offset(0f, 0f), size = Size(c, h)
    )
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF533019), Color(0xFF4D2C16), Color(0xFF5A351C)),
            startX = w - c, endX = w
        ),
        topLeft = Offset(w - c, 0f), size = Size(c, h)
    )

    // Center Bar with 3D drop shadow
    drawRect(Color.Black.copy(alpha = 0.45f), Offset(c - 6f, 0f), Size(6f, h))
    drawRect(Color.Black.copy(alpha = 0.45f), Offset(w - c, 0f), Size(6f, h))
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF2A150A), Color(0xFF3D2111), Color(0xFF241208)),
            startX = c, endX = w - c
        ),
        topLeft = Offset(c, 0f), size = Size(w - 2 * c, h)
    )

    val r = (slot * 0.42f).coerceAtMost(c / 11f)
    for (a in 0 until 24) {
        val y = TavliLayout.slotY(a, h)
        val left = a >= 12
        val base = if (left) 0f else w
        val tip = if (left) c * 0.96f else w - c * 0.96f
        val path = Path().apply { moveTo(base, y + 2); lineTo(tip, y + slot / 2); lineTo(base, y + slot - 2); close() }
        val pip = b.pipOf(turn, a)
        val hl = pip in targets

        // Point wood gradient: Amber maple for light, Dark walnut for dark
        val pointBrush = if (hl) {
            Brush.verticalGradient(listOf(Color(0xFF48BB78).copy(alpha = 0.85f), Color(0xFF2F855A).copy(alpha = 0.85f)))
        } else if (a % 2 == 0) {
            Brush.horizontalGradient(
                if (left) listOf(Color(0xFFD4A362), Color(0xFFB58042))
                else listOf(Color(0xFFB58042), Color(0xFFD4A362)),
                startX = base, endX = tip
            )
        } else {
            Brush.horizontalGradient(
                if (left) listOf(Color(0xFF6B3A1E), Color(0xFF4A2510))
                else listOf(Color(0xFF4A2510), Color(0xFF6B3A1E)),
                startX = base, endX = tip
            )
        }
        drawPath(path, pointBrush)
        drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(1f))

        val pt = b.pts[a]
        val stack = buildList { if (pt.pinned >= 0) add(pt.pinned); repeat(pt.count) { add(pt.owner) } }
        val showCount = stack.size.coerceAtMost(5)

        for (i in 0 until showCount) {
            val o = stack[i]
            val cx = if (left) r + 2 + i * 2 * r * 0.92f else w - r - 2 - i * 2 * r * 0.92f
            val cy = y + slot / 2

            // Drop shadow
            drawCircle(Color.Black.copy(alpha = 0.45f), r, Offset(cx + 2f, cy + 2f))

            if (o == 0) {
                // Ivory disc with concentric engraved ring
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFFDF5), Color(0xFFF4EAD5), Color(0xFFDCD2C0), Color(0xFFB8AA90)),
                        center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                        radius = r * 1.3f
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(Color(0xFFB8AA90), r, Offset(cx, cy), style = Stroke(1.5f))
                drawCircle(Color(0xFFDCD2C0), r * 0.65f, Offset(cx, cy), style = Stroke(1f))
            } else {
                // Ebony disc with concentric engraved ring
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF5A4842), Color(0xFF3A2E2A), Color(0xFF261D1A), Color(0xFF140E0C)),
                        center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                        radius = r * 1.3f
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(Color(0xFF140E0C), r, Offset(cx, cy), style = Stroke(1.5f))
                drawCircle(Color(0xFF5A4842).copy(alpha = 0.6f), r * 0.65f, Offset(cx, cy), style = Stroke(1f))
            }

            if (i == 0 && pt.pinned >= 0) {
                drawCircle(C.red, r * 0.35f, Offset(cx, cy))
                drawCircle(Color.White, r * 0.35f, Offset(cx, cy), style = Stroke(1.5f))
            }

            // Stack count if over 5
            if (i == showCount - 1 && stack.size > 5) {
                drawCircle(Color.Black.copy(alpha = 0.75f), r * 0.65f, Offset(cx, cy))
                label("${stack.size}", cx, cy, Color.White, slot * 0.35f)
            }
        }

        // Selection glow
        if (sel != null && sel in 1..24 && b.abs(turn, sel) == a) {
            drawPath(path, Color(0xFFFFD54F).copy(alpha = 0.35f))
            drawPath(path, Color(0xFFFFD54F), style = Stroke(5f))
        } else if (pip in movable) {
            drawPath(path, C.amber, style = Stroke(3.5f))
        }
    }

    // middle strip: off counts at the two ends, bar in the middle
    val mid = w / 2
    label(t("έξω ", "off ") + b.off[1], mid, slot * 0.6f, Color(0xFFE8D6B8), slot * 0.35f)
    label(t("έξω ", "off ") + b.off[0], mid, h - slot * 0.4f, Color(0xFFE8D6B8), slot * 0.35f)
    if (0 in targets) drawRect(C.green.copy(alpha = 0.35f), Offset(c, 0f), Size(w - 2 * c, h))

    for (p in 0..1) if (b.bar[p] > 0) {
        val cy = 6 * slot + slot / 2 + (if (p == 0) slot * 0.25f else -slot * 0.25f)
        // Draw checker on bar with radial gradient
        if (p == 0) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFFDF5), Color(0xFFF4EAD5), Color(0xFFDCD2C0), Color(0xFFB8AA90)),
                    center = Offset(mid - r * 0.3f, cy - r * 0.3f),
                    radius = r * 1.3f
                ),
                radius = r,
                center = Offset(mid, cy)
            )
            drawCircle(Color(0xFFB8AA90), r, Offset(mid, cy), style = Stroke(1.5f))
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF5A4842), Color(0xFF3A2E2A), Color(0xFF261D1A), Color(0xFF140E0C)),
                    center = Offset(mid - r * 0.3f, cy - r * 0.3f),
                    radius = r * 1.3f
                ),
                radius = r,
                center = Offset(mid, cy)
            )
            drawCircle(Color(0xFF140E0C), r, Offset(mid, cy), style = Stroke(1.5f))
        }
        label("${b.bar[p]}", mid, cy, if (p == 0) Color.Black else Color.White, slot * 0.3f)
        if (p == turn && (sel == 25 || 25 in movable)) drawCircle(if (sel == 25) Color(0xFFFFD54F) else C.amber, r, Offset(mid, cy), style = Stroke(4f))
    }
}

private fun DrawScope.label(s: String, x: Float, y: Float, color: Color, px: Float) {
    val paint = android.graphics.Paint().apply { this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()); textSize = px; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
    drawContext.canvas.nativeCanvas.drawText(s, x, y + px / 3, paint)
}
