package gr.wsol.gamenight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.History
import gr.wsol.gamenight.core.Packs
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** 1.1: card packs from GitHub and the group's own cards, in the settings. */
@Composable
fun PacksPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = if (Store.settings.english) "en" else "el"
    var open by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<Packs.Info>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var own by remember { mutableStateOf(Packs.custom().joinToString("\n")) }
    Panel {
        Label(t("📦 ΠΑΚΕΤΑ ΚΑΡΤΩΝ ΚΑΙ ΔΙΚΕΣ ΣΑΣ ΚΑΡΤΕΣ", "📦 CARD PACKS AND YOUR OWN CARDS"))
        Body(t("Θεματικά πακέτα («Ποδόσφαιρο», «Ταινίες 90s», «Κρήτη») από το GitHub της εφαρμογής. Μπαίνουν στις κάρτες όλων των παιχνιδιών. Μόνο κατεβαίνουν, δεν στέλνεται τίποτα.", "Theme packs («Football», «90s films») from the app's GitHub. They join the cards of every game. Download only, nothing is sent."))
        val installed = remember(tick, lang) { Packs.installed(lang) }
        installed.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓ $id", style = Type.body, color = C.text, modifier = Modifier.weight(1f))
                GhostButton(t("Αφαίρεση", "Remove"), { Packs.remove(ctx, lang, id); tick++ }, color = C.red)
            }
        }
        if (!open) GhostButton(t("Δες τα πακέτα", "Browse packs"), {
            open = true; status = t("Φόρτωση…", "Loading…")
            scope.launch { list = Packs.index(); status = if (list == null) t("Χωρίς σύνδεση με το GitHub.", "Can't reach GitHub.") else null }
        }, Modifier.fillMaxWidth())
        list?.filter { it.lang == lang }?.forEach { p ->
            val has = p.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' } in installed
            Panel(color = C.panel2) {
                Text(p.name, style = Type.h, color = C.text)
                if (p.about.isNotBlank()) Body(p.about)
                if (!has) GhostButton(if (busy == p.id) t("Κατέβασμα…", "Downloading…") else t("Κατέβασε", "Download"), {
                    busy = p.id
                    scope.launch { val ok = Packs.install(ctx, p); busy = null; tick++; status = if (ok) t("✓ Μπήκε: ${p.name}", "✓ Added: ${p.name}") else t("Δεν κατέβηκε. Ξαναδοκίμασε.", "Didn't download. Try again.") }
                }, Modifier.fillMaxWidth(), color = C.amber, enabled = busy == null)
            }
        }
        status?.let { Body(it) }
        Label(t("ΔΙΚΕΣ ΣΑΣ ΛΕΞΕΙΣ", "YOUR OWN WORDS"))
        Body(t("Μία λέξη ή φράση σε κάθε γραμμή, π.χ. «ο σκύλος του Νίκου». Μπαίνουν στο «Πες το αλλιώς», «Στο μέτωπο» και στις ζωγραφιές.", "One word or phrase per line, e.g. «Nick's dog». They join «Taboo», «On your forehead» and the drawings."))
        OutlinedTextField(own, { own = it.take(6000) }, Modifier.fillMaxWidth().heightIn(min = 110.dp), placeholder = { Text(t("ο σκύλος του Νίκου\nη ταβέρνα της Κατερίνας", "Nick's dog\nKate's tavern"), color = C.dim) },
            textStyle = Type.body.copy(color = C.text), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.amber, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.amber))
        val count = own.lines().map { it.trim() }.filter { it.length in 2..40 }.distinct().size
        GhostButton(t("Αποθήκευση ($count)", "Save ($count)"), { Packs.setCustom(own.lines()); status = t("✓ Αποθηκεύτηκαν $count λέξεις.", "✓ Saved $count words.") }, Modifier.fillMaxWidth(), color = C.green)
    }
}

/** 1.1: «📜 Ιστορικό βραδιών»: wins and streaks over the nights kept on this phone. */
@Composable
fun HistoryScreen() {
    var tick by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val nights = remember(tick) { History.all() }
    val stats = remember(tick) { History.stats(nights) }
    Page(t("📜 Ιστορικό βραδιών", "📜 Past nights"), onBack = { Nav.back() }) {
        if (nights.isEmpty()) Body(t("Ακόμα τίποτα. Μια βραδιά κρατιέται εδώ όταν πατήσετε «Νέα βραδιά» στον πίνακα.", "Nothing yet. A night is kept here when you press «New night» on the scoreboard."))
        if (stats.isNotEmpty()) Panel {
            Label(t("ΝΙΚΕΣ ΚΑΙ ΣΕΡΙ", "WINS AND STREAKS"))
            stats.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = Type.h, color = C.text, modifier = Modifier.weight(1f))
                    Text(t("🏆 ${s.wins}/${s.nights}", "🏆 ${s.wins}/${s.nights}"), style = Type.body, color = C.amber)
                    Text(if (s.streak >= 2) t("  🔥 ${s.streak} στη σειρά", "  🔥 ${s.streak} in a row") else "", style = Type.body, color = C.red)
                }
                if (s.best >= 2) Text(t("Καλύτερο σερί: ${s.best}", "Best streak: ${s.best}"), style = Type.small, color = C.muted)
            }
        }
        nights.asReversed().take(50).forEach { r ->
            Panel {
                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(r.at)), style = Type.label, color = C.dim)
                r.rows.forEachIndexed { i, row ->
                    Row { Text((if (row in r.winners) "🏆 " else "${i + 1}. ") + row.side, style = Type.body, color = if (row in r.winners) C.amber else C.text, modifier = Modifier.weight(1f)); Text("${row.pts}", style = Type.h, color = C.text) }
                }
            }
        }
        if (nights.isNotEmpty()) GhostButton(t("Σβήσε το ιστορικό", "Clear the history"), { confirm = true }, Modifier.fillMaxWidth(), color = C.red)
    }
    if (confirm) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t("Σβήσιμο ιστορικού;", "Clear the history?"), color = C.text) },
        text = { Text(t("Χάνονται όλες οι παλιές βραδιές και τα σερί.", "All past nights and streaks are lost."), color = C.muted) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { History.clear(); tick++; confirm = false }) { Text(t("Σβήσιμο", "Clear"), color = C.red) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
        containerColor = C.panel,
    )
}
