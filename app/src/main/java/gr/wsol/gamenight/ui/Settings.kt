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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import gr.wsol.gamenight.BuildConfig
import gr.wsol.gamenight.core.Cinema
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Report
import gr.wsol.gamenight.update.Updates
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val s = Store.settings
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var checking by remember { mutableStateOf<String?>(null) }
    var showCinemaDialog by remember { mutableStateOf(false) }
    var cinemaDownloading by remember { mutableStateOf(false) }
    var cinemaProgress by remember { mutableStateOf(0 to 0) }
    var cinemaError by remember { mutableStateOf<String?>(null) }
    var remoteIndex by remember { mutableStateOf<Cinema.Index?>(null) }
    var checkingIndex by remember { mutableStateOf(false) }
    var noConnection by remember { mutableStateOf(false) }
    val cinemaBytes = Cinema.installedSize(ctx)

    Page(t("Ρυθμίσεις", "Settings"), onBack = { Nav.back() }) {
        Panel {
            Label(t("ΓΛΩΣΣΑ · LANGUAGE", "LANGUAGE · ΓΛΩΣΣΑ"))
            Seg(listOf(false to "Ελληνικά", true to "English"), s.english, { Store.settings = s.copy(english = it) })
            Text(t("Οι κάρτες αλλάζουν και αυτές· οι ελληνικές είναι περισσότερες.", "The cards change too; there are more in Greek."), style = Type.small, color = C.muted)
        }
        Panel {
            Label(t("ΕΜΦΑΝΙΣΗ", "LOOK"))
            Seg(
                listOf(
                    0 to t("🎲 Βραδιά", "🎲 Night"),
                    1 to t("☀️ Καλοκαίρι", "☀️ Summer"),
                    2 to t("🎄 Χριστούγεννα", "🎄 Christmas"),
                    3 to t("🎬 Σινεμά", "🎬 Cinema")
                ),
                s.look,
                { targetLook ->
                    if (targetLook == 3 && !Cinema.isInstalled(ctx)) {
                        cinemaError = null
                        noConnection = false
                        remoteIndex = null
                        showCinemaDialog = true
                    } else {
                        Store.settings = s.copy(look = targetLook)
                        gr.wsol.gamenight.extras.Sfx.win()
                    }
                }
            )
            Text(t("Αλλάζουν τα χρώματα και οι ήχοι της νίκης.", "The colours and the winning sounds change."), style = Type.small, color = C.muted)
            if (cinemaBytes > 0L) {
                Spacer(Modifier.height(4.dp))
                val mb = cinemaBytes / 1048576.0
                val sizeStr = if (s.english) "%.1f MB".format(mb) else "%.1f MB".format(mb).replace('.', ',')
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        t("Εικόνες Σινεμά: εγκατεστημένες ($sizeStr)", "Cinema images: installed ($sizeStr)"),
                        style = Type.small,
                        color = C.muted
                    )
                    TextButton(onClick = {
                        Cinema.delete(ctx)
                    }) {
                        Text(t("Διαγραφή", "Delete"), style = Type.small, color = C.red)
                    }
                }
            }
        }
        PacksPanel()
        Panel {
            Toggle(t("Παρέα 18+", "18+ group"), t("Πιο τολμηρές λέξεις και ερωτήσεις (αργκό, πειράγματα). Ποτέ σεξουαλικό περιεχόμενο.", "Bolder words and questions (slang, teasing). Never sexual content."), s.adult) { Store.settings = s.copy(adult = it) }
            Toggle(t("Ήχοι", "Sounds"), null, s.sounds) { Store.settings = s.copy(sounds = it) }
            Toggle(t("Δόνηση", "Vibration"), null, s.haptics) { Store.settings = s.copy(haptics = it) }
            Toggle(t("Λιγότερη κίνηση", "Reduce motion"), t("Χωρίς κομφετί, τραντάγματα και αναποδογυρίσματα.", "No confetti, shakes or flips."), s.reduceMotion) { Store.settings = s.copy(reduceMotion = it) }
            Toggle(t("Αναφορά προβλήματος με κούνημα", "Shake to report a problem"), t("Κούνα δυνατά το κινητό στο μενού. Ποτέ μέσα σε παιχνίδι.", "Shake the phone hard in the menus. Never during a game."), s.shakeReport) { Store.settings = s.copy(shakeReport = it) }
        }
        Panel {
            Toggle(t("🌀 Χάος", "🌀 Chaos"), t("Μικρές αστείες εκπλήξεις ανάμεσα στους γύρους: ποινές, δώρα, κατάρες, σερίφης, μονομαχίες. Και το κουμπί «🎯 Κράξε». Ποτέ μέσα σε χρονόμετρο ή σε κρυφή οθόνη.", "Small funny surprises between rounds: penalties, gifts, curses, a sheriff, duels. And the «🎯 Roast» button. Never during a timer or a secret screen."), s.chaos) { Store.settings = s.copy(chaos = it) }
            if (s.chaos) Seg(listOf(0 to t("Σπάνια", "Rarely"), 1 to t("Κανονικά", "Normal"), 2 to t("Συχνά", "Often")), s.chaosFreq, { Store.settings = s.copy(chaosFreq = it); gr.wsol.gamenight.chaos.Chaos.schedule() })
        }
        AiPanel()
        if (BuildConfig.UPDATE_CHECK) Panel {
            Toggle(t("Έλεγχος για νέα έκδοση", "Check for new versions"), t("Μία ερώτηση στο GitHub το πολύ κάθε ώρα, όταν ανοίγεις την εφαρμογή. Δεν στέλνεται τίποτα δικό σας.", "One request to GitHub at most every hour, when you open the app. Nothing of yours is sent."), s.updateCheck) { Store.settings = s.copy(updateCheck = it) }
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
        SharePanel()
        Panel(onClick = { Nav.go(Screen.Report) }) {
            Text(t("🐞 Αναφορά προβλήματος", "🐞 Report a problem"), style = Type.body, color = C.text)
            Text(t("Με email στο ${Report.EMAIL}. Χωρίς τα ονόματα των παικτών.", "By email to ${Report.EMAIL}. Without the players' names."), style = Type.small, color = C.muted)
        }
        Panel {
            Label(t("ΙΔΙΩΤΙΚΟΤΗΤΑ", "PRIVACY"))
            Body(t(
                "Όλα τα παιχνίδια δουλεύουν χωρίς internet. Στο κινητό μένουν μόνο τα ονόματα των παικτών, οι πόντοι και τα ρεκόρ. Δεν υπάρχει λογαριασμός, server, διαφημίσεις ή analytics. Το internet χρησιμοποιείται μόνο για τον έλεγχο νέας έκδοσης, αν είναι ανοιχτός, για τα πακέτα καρτών, αν τα κατεβάσεις, και για τον Χαβαλέ και τα παιχνίδια AI, αν βάλεις δικό σου κλειδί.",
                "Every game works offline. Only the players' names, the points and the records stay on the phone. No account, server, ads or analytics. The internet is used only for the update check, if it's on, and for Havales (AI), if you turn it on with your own key.",
            ))
        }
        Panel {
            Label(t("ΣΧΕΤΙΚΑ", "ABOUT"))
            Body("Gamenight GR ${BuildConfig.VERSION_NAME} · MIT © jemangr")
            Body(t("Οι μηχανισμοί θυμίζουν γνωστά παιχνίδια παρέας, όλο όμως το περιεχόμενο είναι δικό μας.", "The mechanics recall well-known party games, but all the content is our own."))
        }
    }

    if (showCinemaDialog) {
        LaunchedEffect(showCinemaDialog) {
            if (remoteIndex == null && !cinemaDownloading) {
                checkingIndex = true
                noConnection = false
                val idx = Cinema.fetchIndex()
                checkingIndex = false
                if (idx != null) {
                    remoteIndex = idx
                } else {
                    noConnection = true
                }
            }
        }

        AlertDialog(
            onDismissRequest = { if (!cinemaDownloading) showCinemaDialog = false },
            containerColor = C.panel,
            title = { Text(t("🎬 Σινεμά", "🎬 Cinema"), style = Type.title, color = C.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (noConnection) {
                        Text(
                            t(
                                "Χρειάζεται σύνδεση στο internet για το κατέβασμα.",
                                "An internet connection is required to download."
                            ),
                            style = Type.body,
                            color = C.red
                        )
                    } else if (checkingIndex || remoteIndex == null) {
                        Text(
                            t(
                                "Έλεγχος μεγέθους πακέτου…",
                                "Checking package size…"
                            ),
                            style = Type.body,
                            color = C.muted
                        )
                    } else {
                        val totalBytes = remoteIndex?.files?.sumOf { it.bytes } ?: 0L
                        val mb = totalBytes / 1048576.0
                        val sizeStr = if (Store.settings.english) {
                            String.format(java.util.Locale.US, "%.1f MB", mb)
                        } else {
                            String.format(java.util.Locale.forLanguageTag("el"), "%.1f MB", mb)
                        }
                        Text(
                            t(
                                "Το look Σινεμά έχει φωτογραφίες ($sizeStr). Κατέβασμα τώρα από το GitHub;",
                                "The Cinema look has photos ($sizeStr). Download now from GitHub?"
                            ),
                            style = Type.body,
                            color = C.text
                        )
                    }

                    if (cinemaDownloading) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            t(
                                "Κατέβασμα… ${cinemaProgress.first} / ${cinemaProgress.second}",
                                "Downloading… ${cinemaProgress.first} / ${cinemaProgress.second}"
                            ),
                            style = Type.small,
                            color = C.amber
                        )
                    }
                    cinemaError?.let {
                        Text(it, style = Type.small, color = C.red)
                    }
                }
            },
            confirmButton = {
                val canDownload = !cinemaDownloading && !checkingIndex && !noConnection && remoteIndex != null
                if (canDownload) {
                    TextButton(onClick = {
                        cinemaDownloading = true
                        cinemaError = null
                        scope.launch {
                            val ok = Cinema.download(ctx) { done, total ->
                                cinemaProgress = done to total
                            }
                            cinemaDownloading = false
                            if (ok) {
                                showCinemaDialog = false
                                Store.settings = s.copy(look = 3)
                                gr.wsol.gamenight.extras.Sfx.win()
                            } else {
                                cinemaError = t(
                                    "Δεν ολοκληρώθηκε, δοκιμάστε ξανά",
                                    "Download failed, please try again"
                                )
                            }
                        }
                    }) {
                        Text(t("Κατέβασμα", "Download"), color = C.amber)
                    }
                }
            },
            dismissButton = {
                if (!cinemaDownloading) {
                    TextButton(onClick = { showCinemaDialog = false }) {
                        Text(t("Άκυρο", "Cancel"), color = C.muted)
                    }
                }
            }
        )
    }
}

@Composable
fun ReportScreen() {
    val ctx = LocalContext.current
    val clip = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var what by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf<Boolean?>(null) }
    var sending by remember { mutableStateOf(false) }
    val text = Report.text(ctx, what)
    Page(t("Αναφορά προβλήματος", "Report a problem"), onBack = { Nav.back() }) {
        Body(t("Γράψε τι έγινε: σε ποιο παιχνίδι, τι πάτησες, τι περίμενες. Προστίθενται αυτόματα η έκδοση, το κινητό και τα τελευταία σφάλματα, ποτέ τα ονόματα των παικτών.", "Write what happened: which game, what you pressed, what you expected. The version, the phone and the last errors are added; never the players' names."))
        OutlinedTextField(
            what, { what = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
            placeholder = { Text(t("Τι έγινε;", "What happened?"), color = C.dim) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.amber, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.amber),
        )
        OutlinedTextField(
            email, { email = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(t("Το email σου (προαιρετικό, για απάντηση)", "Your email (optional, for a reply)"), color = C.dim) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.amber, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.amber),
        )
        BigButton(if (sending) t("Αποστολή…", "Sending…") else t("📤 Αποστολή", "📤 Send"), {
            if (!sending) { sending = true; scope.launch { sent = Report.send(text, email); sending = false } }
        })
        when (sent) {
            true -> Body(t("Στάλθηκε. Ευχαριστούμε!", "Sent. Thank you!"))
            false -> Body(t("Δεν στάλθηκε (χωρίς internet;). Δοκίμασε ξανά ή στείλ' το με email.", "Not sent (no internet?). Try again or send it by email."))
            null -> Body(t("Φεύγει κατευθείαν, χωρίς το email σου αν δεν το γράψεις.", "It goes directly, without your email unless you type it."))
        }
        GhostButton(t("✉️ Από το email μου", "✉️ From my email"), { Report.email(ctx, text) }, Modifier.fillMaxWidth())
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
            TextButton(onClick = {
                val tx = t("Η εφαρμογή έκλεισε απότομα.", "The app closed unexpectedly.") + "\n\n" + Report.text(ctx, "")
                scope.launch { if (!Report.send(tx, "")) Report.email(ctx, tx); onClose() }
            }) { Text(t("📤 Αποστολή", "📤 Send"), color = C.amber) }
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



/** 1.1.1: «Μοιράσου το Gamenight»: the download link, to send to friends. */
object ShareApp {
    const val LINK = "https://platebit.app/game"
    fun text() = t(
        "Gamenight GR: 54 παιχνίδια παρέας σε ένα κινητό, δωρεάν και χωρίς διαφημίσεις. Κατέβασέ το: $LINK",
        "Gamenight GR: 54 party games on one phone, free and without ads. Get it here: $LINK",
    )
}

@Composable
fun SharePanel() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    Panel {
        Text(t("📤 Μοιράσου το Gamenight", "📤 Share Gamenight"), style = Type.body, color = C.text)
        Text(t("Στείλε στην παρέα το link για να το κατεβάσουν: ${ShareApp.LINK}", "Send the group the download link: ${ShareApp.LINK}"), style = Type.small, color = C.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(t("Μοιράσου", "Share"), {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ShareApp.text())
                runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
            }, Modifier.weight(1f))
            GhostButton(if (copied) t("✓ Αντιγράφηκε", "✓ Copied") else t("Αντιγραφή link", "Copy link"), {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Gamenight GR", ShareApp.LINK))); copied = true }
            }, Modifier.weight(1f))
        }
    }
}
