package gr.wsol.gamenight.ui

import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.games.Games
import kotlinx.coroutines.launch

/** The text shared at the end of the night. */
fun shareText(n: Night): String = buildString {
    val r = n.ranking()
    appendLine(t("🎲 Gamenight GR: ο νικητής της βραδιάς", "🎲 Gamenight GR: tonight's winner"))
    r.firstOrNull()?.let { appendLine("🏆 ${n.label(it.first)} · ${it.second}") }
    appendLine()
    r.forEachIndexed { i, (s, p) -> appendLine("${i + 1}. ${n.label(s)}: $p") }
    val games = n.log.map { it.game }.distinct().mapNotNull { Games.byId(it) }
    if (games.isNotEmpty()) { appendLine(); append(t("Παίξαμε: ", "We played: ") + games.joinToString(", ") { "${it.emoji} ${it.title}" }) }
}.trim()

@Composable
fun ScoreScreen() {
    val n = Store.night
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val r = n.ranking()
    val top = (r.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Box(Modifier.fillMaxSize()) {
        Page(t("Πίνακας της βραδιάς", "Tonight's scores"), onBack = { Nav.back() }, bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(t("Κοινοποίηση", "Share"), {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(n))
                    runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
                }, Modifier.weight(1f), enabled = r.isNotEmpty())
                GhostButton(t("Νέα βραδιά", "New night"), { confirm = true }, Modifier.weight(1f))
            }
        }) {
            if (r.isEmpty()) {
                Body(t("Πρόσθεσε παίκτες για να ξεκινήσει ο πίνακας.", "Add players to start the scoreboard."))
            }

            // 3-tier rising podium when 3 or more players are participating
            if (r.size >= 3) {
                Panel(color = Color(0x33000000)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 2nd Place (Silver - Left)
                        PodiumPillar(
                            place = 2,
                            side = r[1].first,
                            points = r[1].second,
                            targetHeight = 115.dp,
                            entered = entered,
                            night = n,
                            modifier = Modifier.weight(1f)
                        )

                        // 1st Place (Gold - Center)
                        PodiumPillar(
                            place = 1,
                            side = r[0].first,
                            points = r[0].second,
                            targetHeight = 155.dp,
                            entered = entered,
                            night = n,
                            modifier = Modifier.weight(1.15f)
                        )

                        // 3rd Place (Bronze - Right)
                        PodiumPillar(
                            place = 3,
                            side = r[2].first,
                            points = r[2].second,
                            targetHeight = 85.dp,
                            entered = entered,
                            night = n,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (r.isNotEmpty() && r[0].second > 0) {
                // 1-2 players banner
                val (s, p) = r[0]
                Panel(color = C.panel2) {
                    Text("🏆", fontSize = 40.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    val player = n.players.firstOrNull { it.name == s }
                    if (player != null) {
                        MonsterAvatar(s, Color(n.colorOf(s)), variant = player.avatar, size = 48.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(n.label(s), fontFamily = DisplayFont, fontWeight = FontWeight.Bold, style = Type.big, color = Color(n.colorOf(s)), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(t("$p πόντοι · πρώτος απόψε", "$p points · leading tonight"), style = Type.body, color = C.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }

            // Players list: starting from 4th for podium, or from 1st if less than 3
            val listPlayers = if (r.size >= 3) r.drop(3) else r
            val startIndex = if (r.size >= 3) 3 else 0

            listPlayers.forEachIndexed { idx, (s, p) ->
                val rank = startIndex + idx + 1
                val targetFraction = (p.coerceAtLeast(0).toFloat() / top).coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = if (entered) targetFraction else 0f,
                    animationSpec = tween(durationMillis = 600, delayMillis = 200 + idx * 80),
                    label = "barFraction"
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.panel)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$rank",
                        fontFamily = Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = C.dim,
                        modifier = Modifier.width(28.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        SideName(n, s)
                        if (n.teams) Text(n.membersOf(s).joinToString(", "), style = Type.small, color = C.muted)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(C.line)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(animatedFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(n.colorOf(s)))
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("$p", style = Type.title, color = C.text)
                }
            }

            if (n.players.size >= 2 && r.any { it.second != 0 }) {
                var show by remember { mutableStateOf(false) }
                GhostButton(t("🏆 Απονομή βραβείων", "🏆 Awards"), { show = !show; gr.wsol.gamenight.extras.Sfx.win() }, Modifier.fillMaxWidth())
                if (show) Panel {
                    gr.wsol.gamenight.chaos.Awards.of(n).forEach { a -> Text("${a.emoji} ${a.title}", style = Type.h, color = C.amber); Text("${a.who} · ${a.why}", style = Type.body, color = C.text) }
                    GhostButton(t("Κοινοποίηση βραβείων", "Share the awards"), {
                        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, gr.wsol.gamenight.chaos.Awards.text(n))
                        runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
                    }, Modifier.fillMaxWidth())
                }
            }
            if (n.log.isNotEmpty() && gr.wsol.gamenight.ai.Havales.ready()) StoryOfTheNight()
            gr.wsol.gamenight.chaos.Moments.last?.let { (title, lines) ->
                GhostButton(t("📸 Στιγμή της βραδιάς: $title", "📸 Moment of the night: $title"), { gr.wsol.gamenight.ui.Drawing.share(ctx, lines, "Gamenight GR · $title") }, Modifier.fillMaxWidth())
            }
            GhostButton(t("📜 Ιστορικό βραδιών", "📜 Past nights"), { Nav.go(Screen.History) }, Modifier.fillMaxWidth())
            if (n.log.isNotEmpty()) {
                Label(t("ΤΙ ΕΓΙΝΕ ΑΠΟΨΕ", "WHAT HAPPENED TONIGHT"))
                n.log.asReversed().take(30).forEach { e ->
                    val g = Games.byId(e.game)
                    Text("${g?.emoji ?: "•"} ${e.note}", style = Type.small, color = C.muted)
                }
            }
        }
        if (confirm) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t("Νέα βραδιά;", "New night?"), color = C.text) },
            text = { Text(t("Οι πόντοι μηδενίζονται και η βραδιά μπαίνει στο ιστορικό. Οι παίκτες και οι ομάδες μένουν.", "Points go back to zero and the night goes into the history. Players and teams stay."), color = C.muted) },
            confirmButton = { TextButton(onClick = { gr.wsol.gamenight.core.History.add(n); Store.night = n.copy(points = emptyMap(), log = emptyList(), started = System.currentTimeMillis()); confirm = false }) { Text(t("Μηδενισμός", "Reset"), color = C.amber) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
            containerColor = C.panel,
        )
    }
}

/** «📜 Η ιστορία της βραδιάς», by the Χαβαλές (only with an AI key). */
@Composable
private fun StoryOfTheNight() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var story by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    GhostButton(if (busy) t("📜 Γράφει…", "📜 Writing…") else t("📜 Η ιστορία της βραδιάς", "📜 The story of the night"), {
        busy = true
        scope.launch {
            story = when (val r = gr.wsol.gamenight.ai.Havales.story()) {
                is gr.wsol.gamenight.ai.AiResult.Ok -> r.text
                is gr.wsol.gamenight.ai.AiResult.Fail -> reasonText(r.reason)
            }
            busy = false
        }
    }, Modifier.fillMaxWidth(), enabled = !busy)
    story?.let { text ->
        Panel {
            Text(text, style = Type.body, color = C.text)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("🔊 Διάβασέ τη", "🔊 Read it"), { gr.wsol.gamenight.extras.Narrator.init(ctx); gr.wsol.gamenight.extras.Narrator.say(text) }, Modifier.weight(1f))
                GhostButton(t("Κοινοποίηση", "Share"), {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text + "\n\nGamenight GR")
                    runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
                }, Modifier.weight(1f))
            }
        }
    }
}

/** Vector medal for 1st (gold), 2nd (silver), 3rd (bronze). */
@Composable
fun VectorMedal(place: Int, modifier: Modifier = Modifier) {
    val (primary, ring, ribbon) = when (place) {
        1 -> Triple(Color(0xFFFFD700), Color(0xFFB7791F), Color(0xFFE53E3E))
        2 -> Triple(Color(0xFFE2E8F0), Color(0xFF718096), Color(0xFF3182CE))
        else -> Triple(Color(0xFFED8936), Color(0xFF9C4221), Color(0xFF38A169))
    }

    Canvas(modifier.size(44.dp)) {
        val w = size.width
        val h = size.height

        // Ribbon
        val ribbonPath = Path().apply {
            moveTo(w * 0.25f, 0f)
            lineTo(w * 0.45f, h * 0.45f)
            lineTo(w * 0.55f, h * 0.45f)
            lineTo(w * 0.75f, 0f)
            close()
        }
        drawPath(ribbonPath, ribbon)

        // Medal disc
        val center = Offset(w * 0.5f, h * 0.62f)
        val r = w * 0.35f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, primary, ring),
                center = Offset(center.x - r * 0.3f, center.y - r * 0.3f),
                radius = r * 1.2f
            ),
            radius = r,
            center = center
        )
        drawCircle(ring, r, center, style = Stroke(1.5f))
        drawCircle(ring.copy(alpha = 0.5f), r * 0.78f, center, style = Stroke(1f))

        // Center star or number dot
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.35f, center)
    }
}

/** Rising pillar for the podium. */
@Composable
private fun PodiumPillar(
    place: Int,
    side: String,
    points: Int,
    targetHeight: androidx.compose.ui.unit.Dp,
    entered: Boolean,
    night: Night,
    modifier: Modifier = Modifier
) {
    val height by animateDpAsState(
        targetValue = if (entered) targetHeight else 0.dp,
        animationSpec = tween(durationMillis = 750, delayMillis = if (place == 1) 100 else if (place == 2) 250 else 400),
        label = "pillarHeight"
    )

    val pillarBrush = when (place) {
        1 -> Brush.verticalGradient(listOf(Color(0xFF4A3E15), Color(0xFF2E260A), Color(0xFF1A1505)))
        2 -> Brush.verticalGradient(listOf(Color(0xFF353942), Color(0xFF22252B), Color(0xFF131518)))
        else -> Brush.verticalGradient(listOf(Color(0xFF3F2A1D), Color(0xFF291B12), Color(0xFF170F0A)))
    }

    val medalAccent = when (place) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFE2E8F0)
        else -> Color(0xFFED8936)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Confetti for 1st place
        if (place == 1 && points > 0) {
            Confetti(trigger = entered)
        }

        // Medal above pillar
        VectorMedal(place)

        Spacer(Modifier.height(4.dp))

        val player = night.players.firstOrNull { it.name == side }
        if (player != null) {
            MonsterAvatar(
                name = side,
                color = Color(night.colorOf(side)),
                variant = player.avatar,
                size = if (place == 1) 44.dp else 36.dp
            )
            Spacer(Modifier.height(4.dp))
        } else if (side.startsWith("#")) {
            val tm = side.drop(1).toIntOrNull() ?: 0
            Box(
                Modifier.size(if (place == 1) 36.dp else 30.dp).clip(CircleShape).background(Color(night.colorOf(side))),
                contentAlignment = Alignment.Center
            ) {
                Text("${tm + 1}", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
        }

        // Name
        Text(
            text = night.label(side),
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = if (place == 1) 15.sp else 13.sp,
            color = Color(night.colorOf(side)),
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        // Points
        Text(
            text = "$points",
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Black,
            fontSize = if (place == 1) 18.sp else 15.sp,
            color = C.text,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        // Rising 3D pillar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(pillarBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$place",
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Black,
                fontSize = if (place == 1) 32.sp else 24.sp,
                color = medalAccent.copy(alpha = 0.5f)
            )
        }
    }
}
