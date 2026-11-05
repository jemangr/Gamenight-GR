package gr.wsol.gamenight.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.ai.Ai
import gr.wsol.gamenight.ai.AiResult
import gr.wsol.gamenight.ai.Havales
import gr.wsol.gamenight.ai.Keys
import gr.wsol.gamenight.ai.Provider
import gr.wsol.gamenight.ai.Reason
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import kotlinx.coroutines.launch

fun reasonText(r: Reason): String = when (r) {
    Reason.BAD_KEY -> t("Το κλειδί δεν δέχτηκε. Αντέγραψέ το ξανά.", "The key was refused. Copy it again.")
    Reason.LIMIT -> t("Τέλειωσε το δωρεάν όριο για τώρα. Ξαναδοκίμασε αργότερα.", "The free limit is used up for now. Try later.")
    Reason.NETWORK -> t("Δεν υπάρχει internet.", "No internet.")
    Reason.BUSY -> t("Ο πάροχος είναι φορτωμένος. Ξαναδοκίμασε σε λίγο.", "The provider is busy. Try again soon.")
    Reason.BLOCKED -> t("Ο πάροχος δεν απάντησε σε αυτό.", "The provider wouldn't answer that.")
    Reason.MODEL -> t("Το μοντέλο δεν βρέθηκε.", "Model not found.")
    Reason.OTHER -> t("Κάτι πήγε στραβά.", "Something went wrong.")
}

/** «🎙️ Χαβαλές»: the optional AI, with the user's own free key. Nothing works differently without it. */
@Composable
fun AiPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Store.settings
    val p = Havales.provider()
    var key by remember(p) { mutableStateOf(Keys.get(p)) }
    var status by remember(p) { mutableStateOf<String?>(null) }
    Panel {
        Toggle(t("🎙️ Ο Χαβαλές (AI)", "🎙️ Havales (AI)"), t(
            "Ένας AI πειραχτήρι που πού και πού πετάγεται ανάμεσα στους γύρους, κράζει όποιον πατήσεις στο «🎯 Κράξε» και γράφει την ιστορία της βραδιάς. Θέλει δικό σου δωρεάν κλειδί. Στον πάροχο πάνε μόνο τα ονόματα, οι πόντοι και τι έγινε απόψε.",
            "An AI joker who now and then pops in between rounds, roasts whoever you pick in «🎯 Roast» and writes the story of the night. Needs your own free key. Only the names, the points and what happened tonight go to the provider.",
        ), s.havales) { Store.settings = s.copy(havales = it) }
        if (!s.havales) return@Panel
        Label(t("ΠΑΡΟΧΟΣ", "PROVIDER"))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Provider.entries.forEach { o -> Chip(o.label, selected = o == p, onClick = { Store.settings = s.copy(aiProvider = o.name) }) }
        }
        Body(if (p.free) t("Δωρεάν κλειδί, χωρίς κάρτα.", "Free key, no card.") else t("Θέλει λίγα χρήματα στον λογαριασμό σου.", "Needs a little credit on your account."))
        GhostButton(t("1. Πάρε κλειδί από ${p.label} ↗", "1. Get a key from ${p.label} ↗"), {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyPage)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth())
        OutlinedTextField(
            key, { key = it.trim() }, Modifier.fillMaxWidth(),
            placeholder = { Text(t("2. Επικόλλησε το κλειδί", "2. Paste the key"), color = C.dim) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.text, unfocusedTextColor = C.text, focusedBorderColor = C.amber, unfocusedBorderColor = C.line),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(t("📋 Επικόλληση", "📋 Paste"), {
                val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                val found = p.findKey(clip)
                if (found != null) key = found else status = t("Δεν βρήκα κλειδί ${p.label} στο πρόχειρο.", "No ${p.label} key on the clipboard.")
            }, Modifier.weight(1f))
            GhostButton(t("3. Δοκιμή", "3. Test"), {
                val k = p.findKey(key) ?: key
                Keys.set(p, k); key = k
                status = t("Δοκιμή…", "Testing…")
                scope.launch {
                    status = when (val r = Ai.testKey(p, k)) {
                        is AiResult.Ok -> t("✓ Δουλεύει. Ο Χαβαλές είναι έτοιμος.", "✓ Works. Havales is ready.")
                        is AiResult.Fail -> "✗ " + reasonText(r.reason)
                    }
                }
            }, Modifier.weight(1f), color = C.amber)
        }
        status?.let { Body(it) }
        if (key.isNotBlank()) GhostButton(t("Σβήσε το κλειδί", "Delete the key"), { Keys.set(p, ""); key = ""; status = null }, Modifier.fillMaxWidth(), color = C.red)
        Toggle(t("Με βρισιές", "With swearing"), t("Ελαφριές, φιλικές, σαν μεταξύ κολλητών. Ποτέ για καταγωγή, εμφάνιση, θρησκεία ή κάτι σεξουαλικό.", "Mild and friendly, like between close friends. Never about origin, looks, religion or anything sexual."), s.havalesRude) { Store.settings = s.copy(havalesRude = it) }
        Body(t("Το κλειδί μένει μόνο σε αυτό το κινητό. Δεν μπαίνει σε backup ούτε στην αναφορά προβλήματος.", "The key stays on this phone only. It is not backed up and never goes into a problem report."))
    }
}
