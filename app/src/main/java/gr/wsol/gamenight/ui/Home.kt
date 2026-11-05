package gr.wsol.gamenight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.games.Category
import gr.wsol.gamenight.games.Game
import gr.wsol.gamenight.games.Games
import gr.wsol.gamenight.update.Installer
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val n = Store.night
    var why by remember { mutableStateOf<String?>(null) }
    Page(
        title = "",
        onBack = null,
        actions = {
            IconButton(onClick = { Nav.go(Screen.Score) }) { Icon(Icons.Filled.EmojiEvents, t("Πίνακας της βραδιάς", "Tonight's scores"), tint = C.amber) }
            IconButton(onClick = { Nav.go(Screen.Settings) }) { Icon(Icons.Filled.Settings, t("Ρυθμίσεις", "Settings"), tint = C.text) }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Brush.sweepGradient(listOf(C.amber, C.pink, C.ghost, C.green, C.amber))), contentAlignment = Alignment.Center) {
                Text(Looks.current.emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Gamenight GR", style = Type.title, color = C.text)
                Text(t("Ένα κινητό, όλη η παρέα", "One phone, the whole group"), style = Type.small, color = C.muted)
            }
        }
        UpdateBar()
        gr.wsol.gamenight.chaos.ChaosHomeBar()

        // Tonight's players
        Panel(onClick = { Nav.go(Screen.Players) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(t("ΑΠΟΨΕ ΠΑΙΖΟΥΝ", "PLAYING TONIGHT"), Modifier.weight(1f))
                Text(if (n.teams) t("Ομάδες", "Teams") else t("Ατομικά", "Solo"), style = Type.small, color = C.amber, fontWeight = FontWeight.Bold)
            }
            if (n.players.isEmpty()) Body(t("Πρόσθεσε τους παίκτες (1 έως 7) για να ξεκινήσετε.", "Add the players (1 to 7) to start."), color = C.text)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                n.players.forEach { p ->
                    Row(Modifier.clip(RoundedCornerShape(999.dp)).background(C.panel2).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(Color(if (n.teams) n.colorOf(n.sideOf(p.name)) else p.color), 10.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(p.name, color = C.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Text(t("Αλλαγή παικτών ›", "Change players ›"), style = Type.small, color = C.muted)
        }

        val count = n.players.size
        val fits = Games.all.filter { it.blocked(count) == null }
        if (fits.isNotEmpty()) GhostButton(t("🎲 Τυχαίο παιχνίδι", "🎲 Random game"), { Nav.go(Screen.Play(fits.random().id)) }, Modifier.fillMaxWidth())

        Category.entries.forEach { cat ->
            Spacer(Modifier.height(4.dp))
            Text("${cat.emoji}  ${cat.title}", style = Type.h, color = C.text)
            Text(cat.sub, style = Type.small, color = C.muted)
            Games.all.filter { it.category == cat }.forEach { g -> GameCard(g, count) { why = it } }
        }
        Spacer(Modifier.height(6.dp))
        Text(t("Χωρίς διαφημίσεις, χωρίς αγορές, χωρίς internet. Open source (MIT) © jemangr.", "No ads, no purchases, no internet needed. Open source (MIT) © jemangr."), style = Type.small, color = C.dim)
    }
    why?.let { InfoDialog(it) { why = null } }
}

@Composable
private fun GameCard(g: Game, count: Int, onBlocked: (String) -> Unit) {
    val block = g.blocked(if (count == 0) 1 else count)
    val noPlayers = count == 0
    Panel(
        Modifier.alpha(if (block == null) 1f else 0.55f),
        onClick = {
            when {
                noPlayers && g.ready -> Nav.go(Screen.Players)
                block != null -> onBlocked("${g.emoji} ${g.title}: $block")
                else -> Nav.go(Screen.Play(g.id))
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(g.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text(g.emoji, fontSize = 24.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = Type.body, color = C.text, fontWeight = FontWeight.ExtraBold)
                Text(if (block != null && !noPlayers) block else g.short, style = Type.small, color = C.muted)
            }
            Spacer(Modifier.width(8.dp))
            Text("👥 ${g.players}", style = Type.small, color = C.dim, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UpdateBar() {
    val r = UpdateState.release ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    Panel(color = C.panel2) {
        Text(t("Υπάρχει η έκδοση ${r.version}", "Version ${r.version} is out"), style = Type.h, color = C.amber)
        if (failed) Body(t("Το κατέβασμα δεν πέτυχε. Δοκίμασε ξανά με internet.", "The download failed. Try again online."), color = C.red)
        progress?.let { Body(t("Κατέβασμα… $it%", "Downloading… $it%")) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(t("Κατέβασμα", "Download"), {
                if (!Installer.canInstall(ctx)) { Installer.allowInstalls(ctx); return@BigButton }
                failed = false; progress = 0
                scope.launch {
                    val f = Installer.download(ctx, r.apk) { progress = it }
                    progress = null
                    if (f == null) failed = true else Installer.install(ctx, f)
                }
            }, Modifier.weight(1f), height = 50.dp, enabled = progress == null)
            GhostButton(t("Αργότερα", "Later"), { UpdateState.release = null }, Modifier.weight(1f))
        }
        Text(t("Την πρώτη φορά το Android ζητά να επιτρέψεις την εγκατάσταση από το Gamenight GR.", "The first time, Android asks you to allow installs from Gamenight GR."), style = Type.small, color = C.dim)
    }
}

@Composable
fun InfoDialog(text: String, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("OK", color = C.amber) } },
        text = { Text(text, style = Type.body, color = C.text) },
        containerColor = C.panel,
    )
}
