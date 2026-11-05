package gr.wsol.gamenight.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Page(t("Πίνακας της βραδιάς", "Tonight's scores"), onBack = { Nav.back() }, bottom = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(t("Κοινοποίηση", "Share"), {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(n))
                runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
            }, Modifier.weight(1f), enabled = r.isNotEmpty())
            GhostButton(t("Νέα βραδιά", "New night"), { confirm = true }, Modifier.weight(1f))
        }
    }) {
        if (r.isEmpty()) Body(t("Πρόσθεσε παίκτες για να ξεκινήσει ο πίνακας.", "Add players to start the scoreboard."))
        r.firstOrNull()?.takeIf { it.second > 0 }?.let { (s, p) ->
            Panel(color = C.panel2) {
                Text("🏆", fontSize = 40.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(n.label(s), style = Type.big, color = Color(n.colorOf(s)), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(t("$p πόντοι · πρώτος απόψε", "$p points · leading tonight"), style = Type.body, color = C.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        r.forEachIndexed { i, (s, p) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontFamily = Serif, fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.dim, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    SideName(n, s)
                    if (n.teams) Text(n.membersOf(s).joinToString(", "), style = Type.small, color = C.muted)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.line)) {
                        Box(Modifier.fillMaxWidth(p.coerceAtLeast(0).toFloat() / top).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(n.colorOf(s))))
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
        text = { Text(t("Οι πόντοι μηδενίζονται. Οι παίκτες και οι ομάδες μένουν.", "Points go back to zero. Players and teams stay."), color = C.muted) },
        confirmButton = { TextButton(onClick = { Store.night = n.copy(points = emptyMap(), log = emptyList(), started = System.currentTimeMillis()); confirm = false }) { Text(t("Μηδενισμός", "Reset"), color = C.amber) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
        containerColor = C.panel,
    )
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
