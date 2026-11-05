package gr.wsol.gamenight.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.extras.rememberTilt
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Type
import kotlin.math.sqrt

/** «Ισορροπία»: the ball's physics and the rules, apart from the screen (tested). */
class BalanceBall(var sensitivity: Float = 1f) {
    /** Position and speed in platform units: the wall is at radius 1. */
    var pos = Offset.Zero; private set
    var vel = Offset.Zero; private set

    /** Moves the ball by [dt] seconds with [tilt] (m/s²). True if it hit the wall. */
    fun step(tilt: Offset, dt: Float): Boolean {
        val a = tilt * (0.09f * sensitivity)
        vel = (vel + a * dt) * (1f - 0.9f * dt) // a little friction
        pos += vel * dt
        return sqrt(pos.x * pos.x + pos.y * pos.y) >= 1f
    }

    fun reset() { pos = Offset.Zero; vel = Offset.Zero }

    companion object {
        const val SECONDS = 5
        const val LIVES = 3
        /** Each pass makes it 15% more sensitive. */
        fun sensitivityAt(pass: Int) = 1f + 0.15f * pass
    }
}

object BalanceRules {
    val challenges = listOf(
        "Πέρνα το στον διπλανό σου" to "Pass it to the one next to you",
        "Γύρνα το μία φορά γύρω από τον εαυτό σου, στο χέρι" to "Turn it once around in your hand",
        "Κράτα το με το άλλο χέρι" to "Hold it with your other hand",
        "Σήκω όρθιος και κάτσε ξανά (αν θες)" to "Stand up and sit down again (if you want)",
        "Πες το αλφάβητο ως το Θ" to "Say the alphabet up to I",
        "Κοίτα ψηλά για 3 δευτερόλεπτα" to "Look up for 3 seconds",
        "Μέτρα δυνατά ως το 10" to "Count out loud to 10",
        "Κράτα το με δύο δάχτυλα" to "Hold it with two fingers",
        "Κλείσε το ένα μάτι" to "Close one eye",
        "Πες ένα τραγούδι, μία γραμμή" to "Sing one line of a song",
        "Ακούμπησε τη μύτη σου με το ελεύθερο χέρι" to "Touch your nose with your free hand",
        "Πέρνα το πάνω από το κεφάλι σου" to "Pass it over your head",
        "Κράτα το όσο πιο ψηλά μπορείς" to "Hold it as high as you can",
        "Χαιρέτησε όλη την παρέα με το ελεύθερο χέρι" to "Wave to everyone with your free hand",
        "Πες τα ονόματα όλων στην παρέα" to "Say everyone's name",
    )
}

private enum class Bl { Intro, Ready, Live, Hit, Over }

@Composable
fun BalanceGame(host: GameHost) {
    val game = Games.byId("balance")!!
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(Bl.Intro) }
    var pass by remember { mutableIntStateOf(0) }
    var holder by remember { mutableIntStateOf(0) }
    val lives = remember { mutableStateMapOf<String, Int>() }
    var challenge by remember { mutableStateOf(BalanceRules.challenges[0]) }
    val tally = remember { Tally() }
    val alive = players.filter { (lives[it] ?: 0) > 0 }
    val who = players[holder % players.size]

    fun nextHolder() { var i = holder; repeat(players.size) { i = (i + 1) % players.size; if ((lives[players[i]] ?: 0) > 0) { holder = i; return } } }

    when (phase) {
        Bl.Intro -> Intro(game, host, listOf(
            t("Μια μπίλια ισορροπεί στην οθόνη. Κάθε παίκτης έχει 5″ να κάνει μια ήπια δοκιμασία, χωρίς να ακουμπήσει η μπίλια το τοίχωμα.", "A ball balances on the screen. Each player has 5″ for a gentle task without the ball touching the wall."),
            t("Σε κάθε πάσα η μπίλια γίνεται πιο ευαίσθητη. Αν ακουμπήσει: συναγερμός και χάνεις ζωή (3 ζωές ο καθένας).", "It gets more sensitive with each pass. If it touches: alarm, and you lose a life (3 lives each)."),
            t("Κερδίζει ο τελευταίος που μένει (+3).", "The last one standing wins (+3)."),
            t("Παίζετε πάντα πάνω από καναπέ ή τραπέζι, για να μην πέσει το κινητό.", "Always play over a sofa or table, so the phone can't fall."),
        ), players.size >= 2) { players.forEach { lives[it] = BalanceBall.LIVES }; pass = 0; holder = 0; challenge = BalanceRules.challenges.random(); phase = Bl.Ready }

        Bl.Ready -> { QuitGuard(host); Page(t("Σειρά του/της $who", "$who's turn"), onBack = null, bottom = {
            BigButton(t("Ξεκίνα τα 5″", "Start the 5″"), { phase = Bl.Live }, color = C.cyan)
        }) {
            Text(t(challenge.first, challenge.second), style = Type.title, color = C.text)
            Body(t("Κράτα το κινητό ίσια, σαν δίσκο. Ζωές: ", "Hold the phone flat, like a tray. Lives: ") + "❤️".repeat(lives[who] ?: 0), color = C.text)
            Text(t("Ευαισθησία ×%.2f".format(BalanceBall.sensitivityAt(pass)), "Sensitivity ×%.2f".format(BalanceBall.sensitivityAt(pass))), style = Type.small, color = C.dim)
            Body(t("Μένουν: ", "Left: ") + alive.joinToString(", "))
        } }

        Bl.Live -> {
            QuitGuard(host)
            val tilt by rememberTilt()
            val ball = remember(pass) { BalanceBall(BalanceBall.sensitivityAt(pass)) }
            var pos by remember(pass) { mutableStateOf(Offset.Zero) }
            var left by remember(pass) { mutableStateOf(BalanceBall.SECONDS * 1000L) }
            LaunchedEffect(pass) {
                var last = withFrameNanos { it }
                var elapsed = 0L
                var lastSec = BalanceBall.SECONDS
                while (true) {
                    val now = withFrameNanos { it }
                    val real = (now - last) / 1e9f; last = now
                    // the clock counts real time; only the physics step is capped when frames drop
                    val dt = real.coerceAtMost(0.05f)
                    elapsed += (real * 1000).toLong()
                    left = (BalanceBall.SECONDS * 1000L - elapsed).coerceAtLeast(0)
                    if (ball.step(tilt, dt)) { Sfx.wrong(); Sfx.pattern(200, 60, 200); lives[who] = (lives[who] ?: 1) - 1; phase = Bl.Hit; break }
                    pos = ball.pos
                    val s = ((left + 999) / 1000).toInt(); if (s != lastSec) { lastSec = s; Sfx.tick() }
                    if (left <= 0) { Sfx.correct(); Sfx.buzz(60); pass++; nextHolder(); challenge = BalanceRules.challenges.random(); phase = Bl.Ready; break }
                }
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Text(t(challenge.first, challenge.second), style = Type.h, color = C.text, textAlign = TextAlign.Center)
                Text("${(left + 999) / 1000}″", style = Type.huge, color = C.cyan)
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val r = size.minDimension / 2 * 0.92f
                    val c = center
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF1E2B3A), Color(0xFF0E1520)), c, r), r, c)
                    drawCircle(C.red, r, c, style = Stroke(10f))
                    drawCircle(C.cyan.copy(alpha = 0.25f), r * 0.5f, c, style = Stroke(3f))
                    val d = sqrt(pos.x * pos.x + pos.y * pos.y)
                    drawCircle(if (d > 0.75f) C.red else C.amber, r * 0.12f, c + Offset(pos.x * r * 0.88f, pos.y * r * 0.88f))
                }
            }
        }

        Bl.Hit -> {
            QuitGuard(host)
            val out = (lives[who] ?: 0) <= 0
            val stillIn = players.filter { (lives[it] ?: 0) > 0 }
            gr.wsol.gamenight.games.Announce("🚨", who, if (out) t("Ακούμπησε! Έμεινε χωρίς ζωές και βγαίνει.", "It touched! No lives left: out.") else t("Ακούμπησε! −1 ζωή. Μένουν ", "It touched! −1 life. Left: ") + "❤️".repeat(lives[who] ?: 0),
                if (stillIn.size <= 1) t("Αποτελέσματα", "Results") else t("Συνέχεια", "Continue"), C.red) {
                if (stillIn.size <= 1) {
                    stillIn.firstOrNull()?.let { tally.add(host, listOf(it), 3); host.log(t("Ισορροπία: νικητής $it", "Balance: $it wins")) }
                    phase = Bl.Over
                } else { pass++; if (out) nextHolder() else nextHolder(); challenge = BalanceRules.challenges.random(); phase = Bl.Ready }
            }
        }

        Bl.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Bl.Intro }
    }
}
