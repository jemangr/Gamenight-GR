package gr.wsol.gamenight.games

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Prompt
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.OnShake
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.shake
import kotlinx.coroutines.delay
import kotlin.random.Random

object BombRules {
    /** Letters that start enough Greek words for any category. */
    const val LETTERS = "ΑΒΓΔΕΚΛΜΝΠΣΤΦΧ"
    /** The same for English (no Q, X, Z, …). */
    const val LETTERS_EN = "ABCDEFGHLMNPRST"
    const val MIN_S = 10
    const val MAX_S = 45

    fun fuseMs(rnd: Random = Random) = rnd.nextLong(MIN_S * 1000L, MAX_S * 1000L + 1)
    fun letter(rnd: Random = Random, not: Char? = null): Char = (if (gr.wsol.gamenight.core.Lang.english) LETTERS_EN else LETTERS).filter { it != not }.random(rnd)

    /** The heartbeat gets faster as the fuse burns: from 1″ to a quarter of a second. */
    fun beatMs(elapsed: Long, fuse: Long): Long = (1000 - 750 * (elapsed.toDouble() / fuse).coerceIn(0.0, 1.0)).toLong()

    /** The next holder after [from], skipping those who are out. */
    fun nextHolder(order: List<String>, out: Set<String>, from: String): String {
        val alive = order.filter { it !in out }
        if (alive.isEmpty()) return from
        val i = order.indexOf(from)
        for (k in 1..order.size) { val c = order[(i + k) % order.size]; if (c !in out) return c }
        return from
    }
}

private enum class BPhase { Intro, Ready, Live, Boom, Over }

@Composable
fun BombGame(host: GameHost) {
    val game = Games.byId("bomb")!!
    val ctx = LocalContext.current
    val n = Store.night
    val order = remember { n.names }
    var sudden by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(BPhase.Intro) }
    val deck = remember { Deck("bomb", Cards.get(ctx).bomb, { it.a }) }
    var prompt by remember { mutableStateOf<Prompt?>(null) }
    var letter by remember { mutableStateOf<Char?>(null) }
    var holder by remember { mutableStateOf(order.firstOrNull() ?: "") }
    var round by remember { mutableIntStateOf(0) }
    var fuse by remember { mutableStateOf(0L) }
    val out = remember { mutableStateListOf<String>() }
    val tally = remember { Tally() }
    val rounds = order.size.coerceAtLeast(3)

    fun pass() {
        holder = BombRules.nextHolder(order, out.toSet(), holder)
        if (prompt?.l == true) letter = BombRules.letter(not = letter)
        Sfx.pass(); Sfx.buzz(20)
    }

    when (phase) {
        BPhase.Intro -> Intro(
            game, host,
            rules = listOf(
                t("Η κάρτα δίνει μια κατηγορία, και συχνά ένα γράμμα.", "The card gives a category, often with a letter."),
                t("Λες μια λέξη που ταιριάζει, πατάς την οθόνη ή κουνάς το κινητό, και το δίνεις αμέσως στον επόμενο.", "Say a word that fits, tap the screen or shake the phone, and pass it on at once."),
                t("Το φιτίλι είναι κρυφό: από 10 έως 45″. Ο παλμός δυναμώνει όσο πλησιάζει.", "The fuse is hidden: 10 to 45″. The heartbeat speeds up as it gets close."),
                if (sudden) t("Ξαφνικός θάνατος: όποιος κρατάει το κινητό όταν σκάσει βγαίνει έξω. Ο τελευταίος παίρνει +3.", "Sudden death: whoever holds it when it blows is out. The last one gets +3.")
                else t("Όποιος κρατάει το κινητό όταν σκάσει παίρνει −1. $rounds γύροι.", "Whoever holds it when it blows gets −1. $rounds rounds."),
                t("Δεν επιτρέπονται επαναλήψεις. Αν η παρέα πει «άκυρο», ξαναλές.", "No repeats. If the group says «no», say another."),
            ),
            startEnabled = deck.size > 0 && order.size >= 2,
            options = {
                Seg(listOf(false to t("Κανονικό", "Normal"), true to t("Ξαφνικός θάνατος", "Sudden death")), sudden, { sudden = it }, color = C.red)
            },
        ) { round = 0; out.clear(); holder = order.random(); phase = BPhase.Ready }

        BPhase.Ready -> {
            QuitGuard(host)
            Page(t("Γύρος ${round + 1}", "Round ${round + 1}"), onBack = null, bottom = {
                BigButton(t("💣 Άναψε το φιτίλι", "💣 Light the fuse"), {
                    prompt = deck.next(); letter = if (prompt?.l == true) BombRules.letter() else null
                    fuse = BombRules.fuseMs(); phase = BPhase.Live
                }, color = C.red)
            }) {
                Body(t("Ξεκινά ο/η", "Starting:"))
                Text(holder, style = Type.big, color = Color(n.colorOf(n.sideOf(holder))))
                if (out.isNotEmpty()) Body(t("Έξω: ", "Out: ") + out.joinToString(", "))
                Body(t("Κρατήστε το κινητό σταθερά πάνω από τραπέζι ή καναπέ.", "Hold the phone over a table or sofa."))
            }
        }

        BPhase.Live -> {
            QuitGuard(host)
            var elapsed by remember(round) { mutableStateOf(0L) }
            LaunchedEffect(round) {
                // Compose's frame clock (real time on the phone; the tests can move it on)
                val start = androidx.compose.runtime.withFrameMillis { it }
                var nextBeat = 0L
                while (true) {
                    elapsed = androidx.compose.runtime.withFrameMillis { it } - start
                    if (elapsed >= fuse) break
                    if (elapsed >= nextBeat) { Sfx.heartbeat(); Sfx.buzz(35); nextBeat = elapsed + BombRules.beatMs(elapsed, fuse) }
                    delay(30)
                }
                Sfx.boom(); Sfx.pattern(300, 80, 500)
                phase = BPhase.Boom
            }
            OnShake { pass() }
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.08f, infiniteRepeatable(tween((BombRules.beatMs(elapsed, fuse) / 2).toInt().coerceAtLeast(120)), RepeatMode.Reverse), label = "p")
            Column(
                Modifier.fillMaxSize().background(C.bg).clickable(role = Role.Button) { pass() }.statusBarsPadding().navigationBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                BombGraphic(scale = pulse)
                Spacer(Modifier.height(16.dp))
                Text(prompt?.t ?: "", style = Type.title, color = C.text, textAlign = TextAlign.Center)
                letter?.let { Text(t("από ", "starting with ") + it, style = Type.huge, color = C.amber, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(24.dp))
                Text(t("Κρατάει:", "Holding:"), style = Type.small, color = C.muted)
                Text(holder, style = Type.big, color = Color(n.colorOf(n.sideOf(holder))), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Text(t("Πες λέξη, πάτα οπουδήποτε (ή κούνα) και πάσα!", "Say a word, tap anywhere (or shake) and pass!"), style = Type.body, color = C.muted, textAlign = TextAlign.Center)
            }
        }

        BPhase.Boom -> {
            QuitGuard(host)
            val haptic = LocalHapticFeedback.current
            val loser = holder
            val alive = order.filter { it !in out && it != loser }
            var boomTrigger by remember(round) { mutableIntStateOf(0) }
            remember(round) {
                boomTrigger++
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                host.stat(loser, "bomb"); gr.wsol.gamenight.chaos.Chaos.tease("bomb", loser)
                if (sudden) out.add(loser) else tally.add(host, listOf(loser), -1)
                host.log(t("Βόμβα: έσκασε στα χέρια του/της $loser", "Bomb: blew up in $loser's hands"))
            }
            val finished = if (sudden) alive.size <= 1 else round + 1 >= rounds
            Page("💥", onBack = null, bottom = {
                BigButton(if (finished) t("Αποτελέσματα", "Results") else t("Επόμενος γύρος", "Next round"), {
                    if (finished) {
                        if (sudden && alive.size == 1) { tally.add(host, alive, 3); host.log(t("Βόμβα: νικητής ${alive[0]}", "Bomb: ${alive[0]} wins")) }
                        phase = BPhase.Over
                    } else { round++; holder = if (sudden) BombRules.nextHolder(order, out.toSet(), loser) else loser; phase = BPhase.Ready }
                }, color = C.red)
            }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shake(boomTrigger)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t("ΜΠΑΜ!", "BOOM!"), style = Type.huge, color = C.red, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text(loser, style = Type.big, color = Color(n.colorOf(n.sideOf(loser))), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Body(if (sudden) t("Βγαίνει έξω.", "is out.") else t("−1 πόντος", "−1 point"), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Body(t("Ήταν: ", "It was: ") + (prompt?.t ?: "") + (letter?.let { t(" από $it", " starting with $it") } ?: ""), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        BPhase.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = BPhase.Intro }
    }
}

/** Procedural vector bomb graphic with sparkling fuse and metallic specular shine. */
@Composable
fun BombGraphic(scale: Float, modifier: Modifier = Modifier) {
    val sparkTransition = rememberInfiniteTransition(label = "sparks")
    val sparkTime by sparkTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(250), RepeatMode.Restart),
        label = "st"
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(150.dp)
            .scale(scale)
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.58f
        val r = w * 0.33f

        // Hemp rope fuse (fixed length and curves to keep time secret)
        val neckY = cy - r - h * 0.04f
        val fuseEnd = Offset(cx + r * 0.45f, cy - r - h * 0.22f)
        val fusePath = Path().apply {
            moveTo(cx, neckY)
            cubicTo(
                cx - r * 0.2f, neckY - h * 0.1f,
                cx + r * 0.6f, neckY - h * 0.14f,
                fuseEnd.x, fuseEnd.y
            )
        }
        drawPath(fusePath, Color(0xFFD69E2E), style = Stroke(w * 0.035f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(fusePath, Color(0xFF975A16), style = Stroke(w * 0.012f, cap = androidx.compose.ui.graphics.StrokeCap.Round))

        // Sizzling sparks at fuseEnd
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFFFDF5), Color(0xFFFFD54F), Color(0xFFFF5722), Color.Transparent),
                center = fuseEnd,
                radius = w * 0.12f
            ),
            radius = w * 0.12f,
            center = fuseEnd
        )
        // Radiating flying spark dots
        val sparkColors = listOf(Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFF5722), Color.White)
        for (i in 0 until 8) {
            val angle = ((i * 45f + sparkTime * 360f) * (Math.PI / 180f)).toFloat()
            val dist = (0.04f + ((sparkTime + i * 0.125f) % 1f) * 0.06f) * w
            val sx = fuseEnd.x + kotlin.math.cos(angle) * dist
            val sy = fuseEnd.y + kotlin.math.sin(angle) * dist
            drawCircle(sparkColors[i % sparkColors.size], w * 0.012f, Offset(sx, sy))
        }

        // Brass collar/neck
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFD69E2E), Color(0xFFFFF9C4), Color(0xFF975A16))
            ),
            topLeft = Offset(cx - r * 0.18f, neckY),
            size = Size(r * 0.36f, h * 0.05f),
            cornerRadius = CornerRadius(4f)
        )

        // Drop shadow under bomb
        drawOval(
            Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(cx - r * 0.8f, cy + r * 0.82f),
            size = Size(r * 1.6f, r * 0.35f)
        )

        // Bomb metallic sphere
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFF4A4E58),
                    Color(0xFF23252B),
                    Color(0xFF121317),
                    Color(0xFF090A0C)
                ),
                center = Offset(cx - r * 0.32f, cy - r * 0.32f),
                radius = r * 1.35f
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // Specular highlight crescent glint
        drawArc(
            Color.White.copy(alpha = 0.35f),
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.72f, cy - r * 0.72f),
            size = Size(r * 1.44f, r * 1.44f),
            style = Stroke(w * 0.035f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}
