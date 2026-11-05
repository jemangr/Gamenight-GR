package gr.wsol.gamenight.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.BuildConfig
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Report
import gr.wsol.gamenight.update.Updates
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val s = Store.settings
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf<String?>(null) }
    Page(t("Ρυθμίσεις", "Settings"), onBack = { Nav.back() }) {
        Panel {
            Toggle(t("Παρέα 18+", "18+ group"), t("Πιο τολμηρές λέξεις και ερωτήσεις (αργκό, πειράγματα). Ποτέ σεξουαλικό περιεχόμενο.", "Bolder words and questions (slang, teasing). Never sexual content."), s.adult) { Store.settings = s.copy(adult = it) }
            Toggle(t("Ήχοι", "Sounds"), null, s.sounds) { Store.settings = s.copy(sounds = it) }
            Toggle(t("Δόνηση", "Vibration"), null, s.haptics) { Store.settings = s.copy(haptics = it) }
        }
        if (BuildConfig.UPDATE_CHECK) Panel {
            Toggle(t("Έλεγχος για νέα έκδοση", "Check for new versions"), t("Μία ερώτηση στο GitHub το πολύ κάθε 6 ώρες. Δεν στέλνεται τίποτα δικό σας.", "One request to GitHub at most every 6 hours. Nothing of yours is sent."), s.updateCheck) { Store.settings = s.copy(updateCheck = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("Έλεγχος τώρα", "Check now"), {
                    checking = t("Έλεγχος…", "Checking…")
                    scope.launch {
                        val r = Updates.check(BuildConfig.VERSION_NAME)
                        Store.lastUpdateCheck = System.currentTimeMillis()
                        UpdateState.release = r
                        checking = if (r == null) t("Έχεις την τελευταία έκδοση (${BuildConfig.VERSION_NAME}), ή δεν υπάρχει internet.", "You have the latest version (${BuildConfig.VERSION_NAME}), or there's no internet.") else t("Υπάρχει η ${r.version}: δες την αρχική οθόνη.", "${r.version} is out: see the home screen.")
                    }
                }, Modifier.weight(1f))
                UpdateState.release?.let { r ->
                    GhostButton(t("Παράλειψη της ${r.version}", "Skip ${r.version}"), { Store.settings = s.copy(skipVersion = r.version); UpdateState.release = null }, Modifier.weight(1f))
                }
            }
            checking?.let { Body(it) }
        }
        Panel(onClick = { Nav.go(Screen.Report) }) {
            Text(t("🐞 Αναφορά προβλήματος", "🐞 Report a problem"), style = Type.body, color = C.text)
            Text(t("Με email στο ${Report.EMAIL}. Χωρίς τα ονόματα των παικτών.", "By email to ${Report.EMAIL}. Without the players' names."), style = Type.small, color = C.muted)
        }
        Panel {
            Label(t("ΙΔΙΩΤΙΚΟΤΗΤΑ", "PRIVACY"))
            Body(t(
                "Όλα τα παιχνίδια δουλεύουν χωρίς internet. Στο κινητό μένουν μόνο τα ονόματα των παικτών, οι πόντοι και τα ρεκόρ. Δεν υπάρχει λογαριασμός, server, διαφημίσεις ή analytics. Το internet χρησιμοποιείται μόνο για τον έλεγχο νέας έκδοσης, αν είναι ανοιχτός.",
                "Every game works offline. Only the players' names, the points and the records stay on the phone. No account, server, ads or analytics. The internet is used only for the update check, if it's on.",
            ))
        }
        Panel {
            Label(t("ΣΧΕΤΙΚΑ", "ABOUT"))
            Body("Gamenight GR ${BuildConfig.VERSION_NAME} · MIT © jemangr")
            Body(t("Οι μηχανισμοί θυμίζουν γνωστά παιχνίδια παρέας, όλο όμως το περιεχόμενο είναι δικό μας.", "The mechanics recall well-known party games, but all the content is our own."))
        }
    }
}

@Composable
fun ReportScreen() {
    val ctx = LocalContext.current
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var what by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val text = Report.text(ctx, what)
    Page(t("Αναφορά προβλήματος", "Report a problem"), onBack = { Nav.back() }) {
        Body(t("Γράψε τι έγινε: σε ποιο παιχνίδι, τι πάτησες, τι περίμενες. Προστίθενται αυτόματα η έκδοση, το κινητό και τα τελευταία σφάλματα, ποτέ τα ονόματα των παικτών.", "Write what happened: which game, what you pressed, what you expected. The version, the phone and the last errors are added; never the players' names."))
        OutlinedTextField(
            what, { what = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
            placeholder = { Text(t("Τι έγινε;", "What happened?"), color = C.dim) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.amber, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.amber),
        )
        BigButton(t("✉️ Αποστολή με email", "✉️ Send by email"), { Report.email(ctx, text) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(if (copied) t("Αντιγράφηκε ✓", "Copied ✓") else t("Αντιγραφή", "Copy"), {
                scope.launch { clip.setClipEntry(ClipEntry(ClipData.newPlainText("report", text))) }; copied = true
            }, Modifier.weight(1f))
            GhostButton(t("Κοινοποίηση", "Share"), { Report.share(ctx, text) }, Modifier.weight(1f))
        }
        GhostButton(t("Άνοιγμα θέματος στο GitHub", "Open a GitHub issue"), {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Report.issueUrl(text)))) }
        }, Modifier.fillMaxWidth())
        Label(t("ΤΙ ΘΑ ΣΤΑΛΕΙ", "WHAT WILL BE SENT"))
        Panel { Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = C.muted) }
    }
}

/** At the start after a crash: the technical details, to send or copy. */
@Composable
fun CrashDialog(report: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("Η εφαρμογή έκλεισε απότομα", "The app closed unexpectedly"), color = C.text) },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(t("Συγγνώμη! Στείλε μας το σφάλμα για να το διορθώσουμε. Δεν περιέχει ονόματα ή άλλα στοιχεία σας.", "Sorry! Send us the error so we can fix it. It has no names or other data of yours."), color = C.muted)
                Text(report.take(1500), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = C.dim)
            }
        },
        confirmButton = {
            TextButton(onClick = { Report.email(ctx, t("Η εφαρμογή έκλεισε απότομα.", "The app closed unexpectedly.") + "\n\n" + Report.text(ctx, "")); onClose() }) { Text(t("✉️ Email", "✉️ Email"), color = C.amber) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { scope.launch { clip.setClipEntry(ClipEntry(ClipData.newPlainText("crash", report))) } }) { Text(t("Αντιγραφή", "Copy"), color = C.text) }
                TextButton(onClick = onClose) { Text(t("Κλείσιμο", "Close"), color = C.muted) }
            }
        },
        containerColor = C.panel,
    )
}

