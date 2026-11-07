package gr.wsol.gamenight.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import gr.wsol.gamenight.ui.CinemaScrim
import gr.wsol.gamenight.ui.rememberCinemaImage
import gr.wsol.gamenight.ui.rememberReducedMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PointsLine
import gr.wsol.gamenight.ui.Type

/** The first screen of every game: what it is, how it's played, its options, and «Ξεκινάμε». */
@Composable
fun Intro(game: Game, host: GameHost, rules: List<String>, startEnabled: Boolean = true, options: (@Composable ColumnScope.() -> Unit)? = null, onStart: () -> Unit) {
    gr.wsol.gamenight.chaos.CalmScreen()
    Page("${game.emoji} ${game.title}", onBack = { host.exit() }, bottom = {
        BigButton(t("Ξεκινάμε", "Let's go"), onStart, color = game.color, enabled = startEnabled)
    }) {
        val introImg = rememberCinemaImage("intro/${game.id}.webp")
        if (introImg != null) {
            val reduced = rememberReducedMotion()
            val zoomTransition = rememberInfiniteTransition(label = "kenBurns")
            val zoomScale by zoomTransition.animateFloat(
                initialValue = 1.00f,
                targetValue = if (reduced) 1.00f else 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 12000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "kenBurnsZoom"
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Image(
                    bitmap = introImg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                        }
                )
                CinemaScrim()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.radialGradient(listOf(game.color.copy(alpha = 0.32f), game.color.copy(alpha = 0.12f)))),
                contentAlignment = Alignment.Center
            ) {
                if (game.icon != 0) {
                    Icon(painterResource(game.icon), contentDescription = null, tint = game.color, modifier = Modifier.size(26.dp))
                } else {
                    Text(game.emoji, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(game.short, style = Type.title, color = C.text, modifier = Modifier.weight(1f))
        }
        Panel {
            Label(t("ΠΩΣ ΠΑΙΖΕΤΑΙ", "HOW TO PLAY"))
            rules.forEach { Body("• $it", color = C.text) }
        }
        if (options != null) Panel {
            Label(t("ΕΠΙΛΟΓΕΣ", "OPTIONS"))
            options()
        }
    }
}

/**
 * Inside a game the back button asks first, so a bumped phone doesn't end the round.
 * Returns the composable to show when asked (null otherwise).
 */
@Composable
fun QuitGuard(host: GameHost, enabled: Boolean = true) {
    var ask by remember { mutableStateOf(false) }
    BackHandler(enabled) { ask = true }
    if (ask) androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(t("Έξοδος από το παιχνίδι;", "Leave the game?"), color = C.text) },
        text = { Text(t("Οι πόντοι που δόθηκαν μένουν στον πίνακα.", "The points already given stay on the scoreboard."), color = C.muted) },
        confirmButton = { TextButton(onClick = { ask = false; host.exit() }) { Text(t("Έξοδος", "Leave"), color = C.amber) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(t("Συνέχεια", "Keep playing"), color = C.muted) } },
        containerColor = C.panel,
    )
}

/** Tracks the points a game gave, to show them at the end. */
class Tally {
    val bySide = mutableMapOf<String, Int>()
    fun add(host: GameHost, who: Collection<String>, pts: Int) {
        if (pts == 0 || who.isEmpty()) return
        host.award(who, pts)
        who.map { Store.night.sideOf(it) }.toSet().forEach { bySide[it] = (bySide[it] ?: 0) + pts }
    }

    /**
     * Points of a round, player → points: one award per value, so that in team mode a team gets
     * each value once and not once for every member of it.
     */
    fun addAll(host: GameHost, pts: Map<String, Int>) =
        pts.entries.groupBy({ it.value }, { it.key }).forEach { (v, who) -> add(host, who, v) }
}

/** The end of a game: who got what, then «Ξανά» or back to the menu. */
@Composable
fun GameOver(game: Game, host: GameHost, tally: Tally, headline: String? = null, extra: (@Composable ColumnScope.() -> Unit)? = null, onAgain: () -> Unit) {
    gr.wsol.gamenight.chaos.CalmScreen()
    val n = Store.night
    val lines = tally.bySide.entries.sortedByDescending { it.value }
    Page("${game.emoji} ${game.title}", onBack = { host.exit() }, bottom = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(t("Ξανά", "Again"), onAgain, Modifier.weight(1f), color = game.color)
            GhostButton(t("Στο μενού", "Menu"), { host.exit() }, Modifier.weight(1f))
        }
    }) {
        Text("🏁", fontSize = 48.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(headline ?: t("Τέλος!", "The end!"), style = Type.big, color = game.color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        extra?.invoke(this)
        if (lines.isNotEmpty()) Panel {
            Label(t("ΠΟΝΤΟΙ ΑΠΟ ΑΥΤΟ ΤΟ ΠΑΙΧΝΙΔΙ", "POINTS FROM THIS GAME"))
            lines.forEach { (s, p) -> PointsLine(n, s, p) }
        }
        Panel {
            Label(t("ΣΥΝΟΛΟ ΒΡΑΔΙΑΣ", "TONIGHT'S TOTAL"))
            n.ranking().forEach { (s, p) ->
                Row { Text(n.label(s), style = Type.body, color = C.text, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Text("$p", style = Type.h, color = C.text) }
            }
        }
    }
}

/** Header of a turn: whose turn and how far into the game. */
@Composable
fun TurnHeader(who: String, color: androidx.compose.ui.graphics.Color, progress: String, role: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(progress, style = Type.label, color = C.dim)
        Text("$role: $who", style = Type.h, color = color)
    }
}
