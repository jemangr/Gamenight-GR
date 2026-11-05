package gr.wsol.gamenight.games

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.ai.Ai
import gr.wsol.gamenight.ai.AiResult
import gr.wsol.gamenight.ai.Havales
import gr.wsol.gamenight.ai.Keys
import gr.wsol.gamenight.ai.Provider
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Content
import gr.wsol.gamenight.core.Deck
import gr.wsol.gamenight.core.Packs
import gr.wsol.gamenight.core.Prompt
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.TabooCard
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.Word
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Narrator
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.DrawPad
import gr.wsol.gamenight.ui.Drawing
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Input
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Line
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.inkOf
import gr.wsol.gamenight.ui.reasonText
import gr.wsol.gamenight.ui.rememberCountdown
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.text.Normalizer

/** 1.1: what the AI games share. The user's own key (Ρυθμίσεις → 🎙️), nothing of ours in between. */
object AiGames {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun ready() = Keys.get(Havales.provider()).isNotBlank()
    fun seesPictures() = Havales.provider() == Provider.GEMINI

    fun system(role: String) = buildString {
        append(role).append(' ')
        append(if (Store.settings.english) "You answer in English." else "Απαντάς στα ελληνικά, φυσικά και χαλαρά.")
        append(' ')
        append(if (Store.settings.english) "Rules you never break: never mock origin, religion, disability, illness, body or appearance; nothing sexual; nothing cruel; no real people's private lives. Keep it friendly, a game among friends." else "Κανόνες που δεν σπας ποτέ: δεν κοροϊδεύεις καταγωγή, θρησκεία, αναπηρία, ασθένεια, σώμα ή εμφάνιση· τίποτα σεξουαλικό· τίποτα σκληρό· όχι προσωπική ζωή πραγματικών προσώπων. Φιλικό, παιχνίδι μεταξύ φίλων.")
        append(' ')
        append(if (Store.settings.havalesRude) t("Επιτρέπονται ελαφριές φιλικές βρισιές.", "Mild friendly swearing is allowed.") else t("Χωρίς βρισιές.", "No swearing."))
    }

    suspend fun ask(role: String, prompt: String): AiResult = Ai.ask(Havales.provider(), Keys.get(Havales.provider()), system(role), prompt)

    /** The first JSON object or array inside [text] (models like to wrap it in ```json). */
    fun parse(text: String): JsonElement? {
        val a = text.indexOfFirst { it == '{' || it == '[' }
        if (a < 0) return null
        val close = if (text[a] == '{') '}' else ']'
        val b = text.lastIndexOf(close)
        if (b <= a) return null
        return runCatching { json.parseToJsonElement(text.substring(a, b + 1)) }.getOrNull()
    }

    fun str(o: JsonElement?, key: String): String = ((o as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    fun bool(o: JsonElement?, key: String): Boolean? = ((o as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull

    /** Lower case, no accents, only letters and digits: «Η Γάτα!» → «η γατα». */
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace('ς', 'σ').replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Does the AI's [guess] name [word]? Same stem is enough («γάτα» ~ «γατούλα»), articles don't count. */
    fun matches(guess: String, word: String): Boolean {
        val stop = setOf("ο", "η", "το", "οι", "τα", "ενα", "ενασ", "μια", "the", "a", "an")
        val w = norm(word).split(' ').filter { it !in stop }
        val g = norm(guess).split(' ').filter { it !in stop }
        if (w.isEmpty() || g.isEmpty()) return false
        fun stem(s: String) = if (s.length <= 4) s.take(maxOf(3, s.length - 1)) else s.take(maxOf(4, s.length - 2))
        return w.all { ww -> g.any { gg -> gg == ww || (ww.length >= 4 && gg.length >= 4 && (gg.startsWith(stem(ww)) || ww.startsWith(stem(gg)))) } }
    }

    fun png(lines: List<Line>): String {
        val out = ByteArrayOutputStream()
        Drawing.bitmap(lines, 512).compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

@Composable
fun AiNeeded() = Body(t("Θέλει κλειδί AI: Ρυθμίσεις → 🎙️ Ο Χαβαλές (AI). Δωρεάν από Gemini, Groq ή OpenRouter.", "Needs an AI key: Settings → 🎙️ Havales (AI). Free from Gemini, Groq or OpenRouter."), color = C.red)

@Composable
fun AiWait(text: String = t("Σκέφτεται…", "Thinking…")) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        CircularProgressIndicator(color = C.cyan)
        Text(text, style = Type.title, color = C.text, textAlign = TextAlign.Center)
    }
}

@Composable
fun AiFailed(r: AiResult.Fail?, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Text("🔌", fontSize = 56.sp)
        Text(t("Το AI δεν απάντησε", "The AI didn't answer"), style = Type.title, color = C.red, textAlign = TextAlign.Center)
        Body(r?.let { reasonText(it.reason) } ?: t("Η απάντηση δεν ήταν σωστή. Ξαναδοκίμασε.", "The answer was garbled. Try again."), align = TextAlign.Center, color = C.text)
        BigButton(t("Ξανά", "Again"), onRetry, color = C.cyan)
        GhostButton(t("Πίσω", "Back"), onBack, Modifier.fillMaxWidth())
    }
}

/** A bigger, several-line text field (questions, arguments). */
@Composable
fun LongInput(value: String, onChange: (String) -> Unit, placeholder: String, max: Int = 300) {
    OutlinedTextField(value, { onChange(it.take(max)) }, Modifier.fillMaxWidth().heightIn(min = 90.dp), placeholder = { Text(placeholder, color = C.dim) }, textStyle = Type.body.copy(color = C.text),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.cyan, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.cyan))
}

/** A chat bubble: the AI on the left, a player on the right. */
@Composable
fun Bubble(who: String, text: String, ai: Boolean, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (ai) Arrangement.Start else Arrangement.End) {
        Column(Modifier.fillMaxWidth(0.86f).clip(RoundedCornerShape(16.dp)).background(if (ai) C.panel2 else color.copy(alpha = 0.18f)).padding(12.dp)) {
            Text(who, style = Type.label, color = color)
            Text(text, style = Type.body, color = C.text)
        }
    }
}

/* ======================= 🕵️ Ο Ανακριτής ======================= */
object InterroRules {
    const val QUESTIONS = 12
    const val STRIKES = 3

    fun casePrompt() = t(
        "Φτιάξε ένα ήπιο, αστείο μυστήριο για μια παρέα. Ένας χαρακτήρας (εσύ θα τον παίξεις) κρύβει ένα μυστικό. Απάντησε ΜΟΝΟ JSON: {\"who\": \"ποιος είσαι, π.χ. ο σερβιτόρος του γάμου\", \"case\": \"τι ψάχνει η παρέα, π.χ. Ποιος έφαγε την τούρτα πριν κοπεί;\", \"secret\": \"η απάντηση σε μία πρόταση\"}. Κάθε φορά κάτι διαφορετικό.",
        "Make up a mild, funny mystery for a group of friends. A character (you'll play them) hides a secret. Answer ONLY JSON: {\"who\": \"who you are, e.g. the wedding waiter\", \"case\": \"what the group is after, e.g. Who ate the cake before it was cut?\", \"secret\": \"the answer in one sentence\"}. Something different each time.",
    )

    fun role(who: String, case: String, secret: String) = t(
        "Παίζεις τον ρόλο: $who. Η παρέα σε ανακρίνει για: «$case». Το μυστικό σου (που δεν το λες ποτέ ευθέως): «$secret». Απαντάς σε κάθε ερώτηση με 1–3 προτάσεις, μέσα στον ρόλο, υπεκφεύγεις και αστειεύεσαι, αλλά δεν λες ψέματα για τα γεγονότα: αφήνεις μικρά στοιχεία ώστε να μπορούν να το βρουν.",
        "You play: $who. The group questions you about: «$case». Your secret (never said outright): «$secret». Answer each question in 1–3 sentences, in character, dodging and joking, but don't lie about the facts: leave small clues so they can find it.",
    )

    fun judgePrompt(secret: String, guess: String) = t(
        "Το μυστικό είναι: «$secret». Η παρέα μαντεύει: «$guess». Είναι ουσιαστικά σωστό (αρκεί το βασικό, όχι κάθε λεπτομέρεια); Απάντησε ΜΟΝΟ JSON: {\"ok\": true ή false, \"say\": \"μια αστεία φράση μέσα στον ρόλο\"}.",
        "The secret is: «$secret». The group guesses: «$guess». Is it essentially right (the main point is enough)? Answer ONLY JSON: {\"ok\": true or false, \"say\": \"a funny line in character\"}.",
    )

    fun suspectCase() = t(
        "Φτιάξε μια μικρή αστεία «υπόθεση» για ανάκριση σε παρέα, π.χ. «Χάθηκε το τελευταίο κομμάτι πίτσα από το ψυγείο». Απάντησε ΜΟΝΟ JSON: {\"case\": \"…\"}. Κάθε φορά κάτι διαφορετικό.",
        "Make up a small funny «case» to question someone about, e.g. «The last slice of pizza vanished from the fridge». Answer ONLY JSON: {\"case\": \"…\"}. Something different each time.",
    )

    fun askerRole(case: String, name: String) = t(
        "Είσαι ένας θεατρικός, αστείος ανακριτής. Η υπόθεση: «$case». Ανακρίνεις τον/την $name. Κάνε ΜΙΑ σύντομη ερώτηση κάθε φορά (έως 20 λέξεις), που να πιέζει για λεπτομέρειες ώστε να φανεί αν λέει ψέματα.",
        "You are a theatrical, funny interrogator. The case: «$case». You question $name. Ask ONE short question at a time (up to 20 words) that presses for details to show whether they're lying.",
    )

    fun verdictPrompt(transcript: String) = t(
        "Αυτή ήταν η ανάκριση:\n$transcript\nΛέει ψέματα ο ύποπτος (είναι ένοχος); Απάντησε ΜΟΝΟ JSON: {\"guilty\": true ή false, \"why\": \"αστεία αιτιολογία σε 1–2 προτάσεις\"}.",
        "This was the questioning:\n$transcript\nIs the suspect lying (guilty)? Answer ONLY JSON: {\"guilty\": true or false, \"why\": \"a funny reason in 1–2 sentences\"}.",
    )
}

private enum class Interro11 { Intro, Load, Ask, Guess, Judge, Over, SLoad, SPass, SQuestion, SVote, SVerdict, Failed }

@Composable
fun InterroGame(host: GameHost) {
    val tts = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { Narrator.init(tts); onDispose { Narrator.stop() } }
    val game = Games.byId("interro")!!
    val n = Store.night
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Interro11.Intro) }
    var mode by remember { mutableIntStateOf(0) }
    var who by remember { mutableStateOf("") }
    var case by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val chat = remember { mutableStateListOf<Triple<String, String, Boolean>>() } // who, text, ai
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var strikes by remember { mutableIntStateOf(0) }
    var turn by remember { mutableIntStateOf(0) }
    var fail by remember { mutableStateOf<AiResult.Fail?>(null) }
    var retry by remember { mutableStateOf<() -> Unit>({}) }
    var won by remember { mutableStateOf(false) }
    var say by remember { mutableStateOf("") }
    // suspect mode
    var suspect by remember { mutableStateOf("") }
    var guilty by remember { mutableStateOf(false) }
    var asked by remember { mutableIntStateOf(0) }
    var aiGuilty by remember { mutableStateOf<Boolean?>(null) }
    var votes by remember { mutableStateOf(mapOf<String, String>()) }
    var round by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }
    val color = game.color
    val transcript = { chat.joinToString("\n") { (w, s, ai) -> (if (ai) t("Ανακριτής", "Interrogator") else w) + ": " + s } }

    fun failed(r: AiResult.Fail?, again: () -> Unit) { fail = r; retry = again; phase = Interro11.Failed }

    fun loadCase() {
        phase = Interro11.Load
        scope.launch {
            when (val r = AiGames.ask(t("Είσαι ο συγγραφέας ενός παιχνιδιού μυστηρίου.", "You write a mystery party game."), InterroRules.casePrompt())) {
                is AiResult.Ok -> {
                    val o = AiGames.parse(r.text)
                    who = AiGames.str(o, "who"); case = AiGames.str(o, "case"); secret = AiGames.str(o, "secret")
                    if (case.isBlank() || secret.isBlank()) failed(null) { loadCase() } else { chat.clear(); strikes = 0; turn = 0; text = ""; phase = Interro11.Ask }
                }
                is AiResult.Fail -> failed(r) { loadCase() }
            }
        }
    }

    fun askAi(q: String) {
        val asker = Turns.player(n, turn)
        chat += Triple(asker, q, false); text = ""; busy = true; turn++
        scope.launch {
            val hist = chat.joinToString("\n") { (w, s, ai) -> (if (ai) who else w) + ": " + s }
            val r = AiGames.ask(InterroRules.role(who, case, secret), t("Η ανάκριση ως τώρα:\n", "The questioning so far:\n") + hist + t("\nΑπάντησε στην τελευταία ερώτηση.", "\nAnswer the last question."))
            busy = false
            if (r is AiResult.Ok) { chat += Triple(who, r.text.take(500), true); Narrator.say(r.text.take(300)) } else chat += Triple(who, t("(δεν απάντησε, ρωτήστε ξανά)", "(no answer, ask again)"), true)
        }
    }

    fun judge(g: String) {
        busy = true
        scope.launch {
            val r = AiGames.ask(InterroRules.role(who, case, secret), InterroRules.judgePrompt(secret, g))
            busy = false
            val o = (r as? AiResult.Ok)?.let { AiGames.parse(it.text) }
            val ok = AiGames.bool(o, "ok") ?: AiGames.matches(g, secret)
            say = AiGames.str(o, "say")
            if (ok) { won = true; tally.add(host, n.names, 1); tally.add(host, listOf(Turns.player(n, turn)), 2); host.log(t("Ανακριτής: λύσατε «$case»", "Interrogator: you solved «$case»")); Sfx.win(); phase = Interro11.Judge }
            else { strikes++; Sfx.wrong(); if (strikes >= InterroRules.STRIKES) { won = false; phase = Interro11.Judge } else { chat += Triple(who, say.ifBlank { t("Όχι! Λάθος.", "Nope! Wrong.") }, true); text = ""; phase = Interro11.Ask } }
        }
    }

    fun loadSuspect() {
        phase = Interro11.SLoad
        scope.launch {
            when (val r = AiGames.ask(t("Γράφεις υποθέσεις για ένα αστείο παιχνίδι ανάκρισης.", "You write cases for a funny questioning game."), InterroRules.suspectCase())) {
                is AiResult.Ok -> {
                    case = AiGames.str(AiGames.parse(r.text), "case")
                    if (case.isBlank()) failed(null) { loadSuspect() } else {
                        suspect = n.names[round % n.names.size]; guilty = kotlin.random.Random.nextBoolean(); asked = 0; chat.clear(); aiGuilty = null; phase = Interro11.SPass
                    }
                }
                is AiResult.Fail -> failed(r) { loadSuspect() }
            }
        }
    }

    fun nextQuestion() {
        busy = true
        scope.launch {
            val r = AiGames.ask(InterroRules.askerRole(case, suspect), if (chat.isEmpty()) t("Κάνε την πρώτη ερώτηση.", "Ask the first question.") else t("Ως τώρα:\n", "So far:\n") + transcript() + t("\nΚάνε την επόμενη ερώτηση.", "\nAsk the next question."))
            busy = false
            if (r is AiResult.Ok) { chat += Triple("", r.text.take(300), true); Narrator.say(r.text.take(300)) } else failed(r as AiResult.Fail) { nextQuestion(); phase = Interro11.SQuestion }
        }
    }

    when (phase) {
        Interro11.Intro -> Intro(game, host, listOf(
            t("🕵️ «Ανακρίνετε το AI»: το AI παίζει έναν χαρακτήρα που κρύβει ένα μυστικό. Με τη σειρά, κάνετε ερωτήσεις (γράφοντας). Όταν νομίζετε ότι το βρήκατε, το λέτε.", "🕵️ «Question the AI»: the AI plays a character hiding a secret. In turn, you type questions. When you think you've got it, say it."),
            t("${InterroRules.QUESTIONS} ερωτήσεις και ${InterroRules.STRIKES} λάθος μαντεψιές. Αν το βρείτε, όλοι +1 και +2 όποιος το είπε.", "${InterroRules.QUESTIONS} questions and ${InterroRules.STRIKES} wrong guesses. Solve it: everyone +1, +2 to whoever said it."),
            t("🔦 «Το AI ανακρίνει»: ένας ύποπτος μαθαίνει κρυφά αν είναι ένοχος (τότε λέει ψέματα) ή αθώος. Το AI τον ρωτάει, εκείνος απαντά δυνατά και γράφει την απάντηση. Μετά ψηφίζετε κι εσείς κι το AI.", "🔦 «The AI questions»: a suspect learns in secret if they're guilty (then they lie) or innocent. The AI asks, they answer out loud and type it. Then you and the AI give a verdict."),
            t("Εκεί: σωστή ψήφος +1· ο ύποπτος +1 για κάθε λάθος ψήφο και +2 αν ξεγελάσει το AI.", "There: right vote +1; the suspect +1 per wrong vote and +2 for fooling the AI."),
        ), AiGames.ready(), {
            if (!AiGames.ready()) AiNeeded()
            Seg(listOf(0 to t("Ανακρίνετε το AI", "Question the AI"), 1 to t("Το AI ανακρίνει", "The AI questions")), mode, { mode = it }, color = color)
        }) { round = 0; if (mode == 0) loadCase() else loadSuspect() }

        Interro11.Load, Interro11.SLoad -> AiWait(t("Το AI στήνει την υπόθεση…", "The AI sets up the case…"))

        Interro11.Failed -> { QuitGuard(host); AiFailed(fail, { retry() }) { phase = Interro11.Intro } }

        Interro11.Ask -> {
            QuitGuard(host)
            val qLeft = InterroRules.QUESTIONS - chat.count { !it.third }
            val asker = Turns.player(n, turn)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Panel { Label(t("ΥΠΟΘΕΣΗ", "CASE")); Text(case, style = Type.h, color = C.text); Text("🕵️ $who", style = Type.small, color = color) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), reverseScrolling = true), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chat.forEach { (w, s, ai) -> Bubble(w, s, ai, if (ai) color else colorOf(w)) }
                    if (busy) Text(t("…", "…"), style = Type.h, color = C.dim)
                }
                Text(t("Ρωτάει: $asker · ερωτήσεις που μένουν $qLeft · λάθη $strikes/${InterroRules.STRIKES}", "Asking: $asker · questions left $qLeft · misses $strikes/${InterroRules.STRIKES}"), style = Type.small, color = C.muted)
                if (qLeft > 0) LongInput(text, { text = it }, t("Η ερώτησή σου…", "Your question…"), 200)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(t("💡 Το βρήκαμε!", "💡 Got it!"), { text = ""; phase = Interro11.Guess }, Modifier.weight(1f), enabled = !busy)
                    if (qLeft > 0) BigButton(t("Ρώτα", "Ask"), { askAi(text.trim()) }, Modifier.weight(1f), color = color, enabled = !busy && text.isNotBlank())
                }
            }
        }

        Interro11.Guess -> {
            QuitGuard(host)
            if (busy) AiWait(t("Το AI κρίνει…", "The AI judges…")) else
            Page(t("Ποιο είναι το μυστικό;", "What's the secret?"), onBack = { phase = Interro11.Ask }, bottom = {
                BigButton(t("Αυτό είναι!", "That's it!"), { judge(text.trim()) }, color = color, enabled = text.isNotBlank())
            }) {
                Body(case, color = C.text)
                LongInput(text, { text = it }, t("Γράψτε τι πιστεύετε ότι κρύβει", "Type what you think it hides"), 200)
            }
        }

        Interro11.Judge -> {
            QuitGuard(host)
            Announce(if (won) "🎉" else "🕵️", if (won) t("Το λύσατε!", "Solved!") else t("Δεν το βρήκατε", "Not solved"), (say.ifBlank { "" } + "\n\n" + t("Το μυστικό: ", "The secret: ") + secret).trim(), t("Αποτελέσματα", "Results"), color) { phase = Interro11.Over }
        }

        Interro11.SPass -> { QuitGuard(host); RevealRound(listOf(suspect), key = round, title = t("Είσαι ο ύποπτος", "You are the suspect"), secret = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Body(case, color = C.text, align = TextAlign.Center)
                Text(if (guilty) t("ΕΝΟΧΟΣ", "GUILTY") else t("ΑΘΩΟΣ", "INNOCENT"), style = Type.big, color = if (guilty) C.red else C.green)
                Body(if (guilty) t("Το έκανες. Λες ψέματα με σιγουριά.", "You did it. Lie with confidence.") else t("Δεν το έκανες. Λες την αλήθεια (κι ας ακούγεται ύποπτη).", "You didn't. Tell the truth (even if it sounds fishy)."), align = TextAlign.Center)
            }
        }) { nextQuestion(); phase = Interro11.SQuestion } }

        Interro11.SQuestion -> {
            QuitGuard(host)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Panel { Label(t("ΥΠΟΘΕΣΗ", "CASE")); Text(case, style = Type.h, color = C.text); Text(t("Ύποπτος: $suspect · ερώτηση ${asked + 1} / 4", "Suspect: $suspect · question ${asked + 1} / 4"), style = Type.small, color = colorOf(suspect)) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), reverseScrolling = true), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chat.forEach { (w, s, ai) -> Bubble(if (ai) t("Ανακριτής", "Interrogator") else w, s, ai, if (ai) color else colorOf(w)) }
                    if (busy) Text("…", style = Type.h, color = C.dim)
                }
                LongInput(text, { text = it }, t("$suspect απαντά δυνατά και γράφει εδώ", "$suspect answers out loud and types here"), 200)
                BigButton(if (asked + 1 < 4) t("Απάντηση", "Answer") else t("Τέλος ανάκρισης", "End questioning"), {
                    chat += Triple(suspect, text.trim(), false); text = ""; asked++
                    if (asked < 4) nextQuestion() else phase = Interro11.SVote
                }, color = color, enabled = !busy && text.isNotBlank())
            }
        }

        Interro11.SVote -> {
            QuitGuard(host)
            SecretPick(n.names.filter { it != suspect }, listOf(t("Ένοχος", "Guilty"), t("Αθώος", "Innocent")), key = round, question = t("$suspect: ένοχος ή αθώος;", "$suspect: guilty or innocent?")) { v ->
                votes = v; busy = true; phase = Interro11.SVerdict
                scope.launch {
                    val r = AiGames.ask(t("Είσαι ένας αστείος δικαστής ανακρίσεων.", "You are a funny judge of interrogations."), InterroRules.verdictPrompt(transcript()))
                    val o = (r as? AiResult.Ok)?.let { AiGames.parse(it.text) }
                    aiGuilty = AiGames.bool(o, "guilty") ?: kotlin.random.Random.nextBoolean(); say = AiGames.str(o, "why"); busy = false
                    val g = t("Ένοχος", "Guilty")
                    var wrong = 0
                    v.forEach { (p, c) -> if ((c == g) == guilty) tally.add(host, listOf(p), 1) else wrong++ }
                    tally.add(host, listOf(suspect), wrong + if (aiGuilty != guilty) 2 else 0)
                    host.log(t("Ανακριτής: $suspect ήταν ${if (guilty) "ένοχος" else "αθώος"}", "Interrogator: $suspect was ${if (guilty) "guilty" else "innocent"}"))
                }
            }
        }

        Interro11.SVerdict -> {
            QuitGuard(host)
            if (busy) AiWait(t("Το AI βγάζει απόφαση…", "The AI decides…")) else
            Page(t("Η αλήθεια", "The truth"), onBack = null, bottom = {
                BigButton(if (round + 1 < n.names.size) t("Επόμενος ύποπτος", "Next suspect") else t("Αποτελέσματα", "Results"), { if (round + 1 < n.names.size) { round++; loadSuspect() } else phase = Interro11.Over }, color = color)
            }) {
                Text(suspect + ": " + if (guilty) t("ΕΝΟΧΟΣ", "GUILTY") else t("ΑΘΩΟΣ", "INNOCENT"), style = Type.big, color = if (guilty) C.red else C.green)
                Panel { Label(t("ΤΟ AI ΕΙΠΕ", "THE AI SAID")); Text(if (aiGuilty == true) t("Ένοχος", "Guilty") else t("Αθώος", "Innocent"), style = Type.h, color = color); if (say.isNotBlank()) Body(say, color = C.text) }
                Panel { Tally11.counts(votes).forEach { (c, k) -> Row { Text(c, style = Type.body, color = C.text, modifier = Modifier.weight(1f)); Text("$k", style = Type.h, color = C.text) } } }
            }
        }

        Interro11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Interro11.Intro }
    }
}

/* ======================= 🖍️ Ζωγράφισε για το AI ======================= */
object DrawAiRules {
    fun points(seconds: Int) = when { seconds < 20 -> 3; seconds < 40 -> 2; else -> 1 }
    fun prompt() = t("Τι δείχνει αυτή η πρόχειρη ζωγραφιά; Απάντησε ΜΟΝΟ με την καλύτερη μαντεψιά σου, 1–3 λέξεις στα ελληνικά, χωρίς τελεία.", "What does this rough drawing show? Answer ONLY with your best guess, 1–3 words, no full stop.")
}

private enum class DrawAi11 { Intro, Pass, Draw, Result, Over }

@Composable
fun DrawAiGame(host: GameHost) {
    val game = Games.byId("drawai")!!
    val ctx = LocalContext.current
    val n = Store.night
    val scope = rememberCoroutineScope()
    val deck = remember { Deck("drawai", Cards.get(ctx).draw, { it.a }) }
    var phase by remember { mutableStateOf(DrawAi11.Intro) }
    var each by remember { mutableIntStateOf(2) }
    var turn by remember { mutableIntStateOf(0) }
    var word by remember { mutableStateOf<Word?>(null) }
    val lines = remember { mutableStateListOf<Line>() }
    val guesses = remember { mutableStateListOf<String>() }
    var asking by remember { mutableStateOf(false) }
    var lastAsk by remember { mutableStateOf(0L) }
    var startAt by remember { mutableStateOf(0L) }
    var found by remember { mutableIntStateOf(-1) }
    var note by remember { mutableStateOf<String?>(null) }
    val tally = remember { Tally() }
    val total = maxOf(1, Turns.count(n, each))
    val artist = Turns.player(n, turn)

    fun ask() {
        if (asking || lines.isEmpty() || found >= 0) return
        asking = true; lastAsk = System.currentTimeMillis()
        val img = AiGames.png(lines.toList())
        scope.launch {
            val r = Ai.askImage(Havales.provider(), Keys.get(Havales.provider()), AiGames.system(t("Μαντεύεις ζωγραφιές σε παιχνίδι παρέας, σαν το Pictionary.", "You guess drawings in a party game, like Pictionary.")), DrawAiRules.prompt(), img)
            asking = false
            when (r) {
                is AiResult.Ok -> {
                    val g = r.text.lines().first().trim().trimEnd('.').take(40)
                    guesses.add(0, g)
                    if (word?.let { AiGames.matches(g, it.w) } == true && found < 0) { found = ((System.currentTimeMillis() - startAt) / 1000).toInt(); Sfx.correct() }
                }
                is AiResult.Fail -> note = reasonText(r.reason)
            }
        }
    }

    fun finish() {
        val pts = if (found >= 0) DrawAiRules.points(found) else 0
        if (pts > 0) tally.add(host, listOf(artist), pts)
        host.log(t("Ζωγράφισε για το AI: $artist «${word?.w}» ${if (found >= 0) "σε ${found}″" else "δεν βρέθηκε"}", "Draw for the AI: $artist «${word?.w}» ${if (found >= 0) "in ${found}″" else "not found"}"))
        phase = DrawAi11.Result
    }

    when (phase) {
        DrawAi11.Intro -> Intro(game, host, listOf(
            t("Βλέπεις κρυφά μια λέξη και τη ζωγραφίζεις. Το AI κοιτάει τη ζωγραφιά σου και μαντεύει, κάθε φορά που τελειώνεις μια γραμμή.", "You see a word in secret and draw it. The AI looks at your drawing and guesses each time you finish a line."),
            t("Όσο πιο γρήγορα το βρει, τόσο πιο πολλοί πόντοι: κάτω από 20″ +3, κάτω από 40″ +2, μέχρι 60″ +1. Χωρίς γράμματα και αριθμούς!", "The faster it gets it, the more points: under 20″ +3, under 40″ +2, up to 60″ +1. No letters or numbers!"),
            t("Θέλει Gemini (βλέπει εικόνες). Η ζωγραφιά πάει μόνο στο Gemini, με το δικό σου κλειδί.", "Needs Gemini (it sees pictures). The drawing goes only to Gemini, with your own key."),
        ), AiGames.ready() && AiGames.seesPictures() && deck.size > 0, {
            if (!AiGames.ready()) AiNeeded() else if (!AiGames.seesPictures()) Body(t("Διάλεξε Gemini στις Ρυθμίσεις → 🎙️ για αυτό το παιχνίδι.", "Pick Gemini in Settings → 🎙️ for this game."), color = C.red)
            Body(t("Γύροι ο καθένας", "Turns each"), color = C.text)
            Seg(listOf(1 to "1", 2 to "2", 3 to "3"), each, { each = it }, color = game.color)
        }) { turn = 0; word = deck.next(); phase = DrawAi11.Pass }

        DrawAi11.Pass -> { QuitGuard(host); RevealRound(listOf(artist), key = turn, title = t("Ζωγράφισε αυτό", "Draw this"), secret = { Text(word?.w ?: "", style = Type.big, color = game.color, textAlign = TextAlign.Center) }) {
            lines.clear(); guesses.clear(); found = -1; note = null; startAt = System.currentTimeMillis(); phase = DrawAi11.Draw
        } }

        DrawAi11.Draw -> {
            QuitGuard(host)
            val left = rememberCountdown(60, running = found < 0, key = turn) { finish() }
            LaunchedEffect(found) { if (found >= 0) { kotlinx.coroutines.delay(1500); finish() } }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(artist, style = Type.h, color = colorOf(artist), modifier = Modifier.weight(1f))
                    Text("${((left + 999) / 1000)}″", style = Type.big, color = if (left < 10_000) C.red else game.color)
                }
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                    DrawPad(lines, inkOf(Store.night.colorOf(Store.night.sideOf(artist))), enabled = found < 0, Modifier.fillMaxSize()) { l ->
                        lines += l
                        if (System.currentTimeMillis() - lastAsk > 3500) ask()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(t("↶ Σβήσε γραμμή", "↶ Undo line"), { if (lines.isNotEmpty()) lines.removeAt(lines.size - 1) }, Modifier.weight(1f))
                    GhostButton(t("🤖 Μάντεψε!", "🤖 Guess!"), { ask() }, Modifier.weight(1f), color = game.color, enabled = !asking && lines.isNotEmpty())
                }
                Label(t("ΤΟ AI ΛΕΕΙ", "THE AI SAYS") + if (asking) " …" else "")
                guesses.take(4).forEachIndexed { i, g -> Text(g, style = if (i == 0) Type.title else Type.body, color = if (i == 0) C.text else C.dim) }
                note?.let { Body(it, color = C.red) }
                if (guesses.isNotEmpty() && found < 0) GhostButton(t("✓ Ήταν σωστό (το λέει αλλιώς)", "✓ That was right (said differently)"), { found = ((System.currentTimeMillis() - startAt) / 1000).toInt(); Sfx.correct() }, Modifier.fillMaxWidth(), color = C.green)
            }
        }

        DrawAi11.Result -> {
            QuitGuard(host)
            Announce(if (found >= 0) "🤖" else "🙈", word?.w ?: "", if (found >= 0) t("Το βρήκε σε ${found}″: +${DrawAiRules.points(found)}", "Found in ${found}″: +${DrawAiRules.points(found)}") else t("Το AI δεν το βρήκε.", "The AI didn't get it."),
                if (turn + 1 < total) t("Επόμενος", "Next") else t("Αποτελέσματα", "Results"), game.color) {
                if (turn + 1 < total) { turn++; word = deck.next(); phase = DrawAi11.Pass } else phase = DrawAi11.Over
            }
        }

        DrawAi11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = DrawAi11.Intro }
    }
}

/* ======================= ♾️ Ατελείωτες κάρτες ======================= */
object EndlessRules {
    enum class Kind { Taboo, Forehead, Draw }

    fun prompt(kind: Kind, theme: String, count: Int, avoid: List<String>) = when (kind) {
        Kind.Taboo -> t("Φτιάξε $count κάρτες για το «Πες το αλλιώς» (Taboo) με θέμα «$theme». Κάθε κάρτα: μια λέξη ή φράση και 5 απαγορευμένες λέξεις που θα έλεγε κανείς πρώτα για να την περιγράψει. Απάντησε ΜΟΝΟ JSON: [{\"w\": \"…\", \"no\": [\"…\", \"…\", \"…\", \"…\", \"…\"]}].",
            "Make $count cards for «Taboo» on «$theme». Each card: a word or phrase and the 5 banned words one would say first to describe it. Answer ONLY JSON: [{\"w\": \"…\", \"no\": [\"…\", \"…\", \"…\", \"…\", \"…\"]}].")
        Kind.Forehead -> t("Φτιάξε $count λέξεις ή ονόματα για το «Στο μέτωπο» με θέμα «$theme»: γνωστά, να μαντεύονται με στοιχεία. Απάντησε ΜΟΝΟ JSON: [\"…\", \"…\"].",
            "Make $count words or names for «On your forehead» on «$theme»: well known, guessable from clues. Answer ONLY JSON: [\"…\", \"…\"].")
        Kind.Draw -> t("Φτιάξε $count λέξεις για ζωγραφική (σαν Pictionary) με θέμα «$theme»: πράγματα που ζωγραφίζονται. Απάντησε ΜΟΝΟ JSON: [\"…\", \"…\"].",
            "Make $count words to draw (like Pictionary) on «$theme»: things you can draw. Answer ONLY JSON: [\"…\", \"…\"].")
    } + (if (avoid.isNotEmpty()) t(" Όχι αυτές (υπάρχουν ήδη): ", " Not these (they exist already): ") + avoid.takeLast(80).joinToString(", ") else "") +
        t(" Όλες διαφορετικές μεταξύ τους.", " All different from each other.")

    /** Cards from the AI's answer, without any that are already in [have] (same letters, any accents). */
    fun read(kind: Kind, el: JsonElement?, have: Collection<String>): List<TabooCard> {
        val seen = have.map { AiGames.norm(it) }.toMutableSet()
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val card = when (e) {
                is JsonPrimitive -> TabooCard(e.contentOrNull?.trim().orEmpty(), emptyList())
                is JsonObject -> TabooCard(AiGames.str(e, "w"), ((e["no"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }).orEmpty().take(5))
                else -> null
            } ?: return@mapNotNull null
            if (card.w.length !in 2..40 || !seen.add(AiGames.norm(card.w))) null
            else if (kind == Kind.Taboo && card.no.size < 3) null else card
        }
    }
}

private enum class Endless11 { Intro, Load, List, Pass, Play, Turn, Over, Failed }

@Composable
fun EndlessGame(host: GameHost) {
    val game = Games.byId("endless")!!
    val ctx = LocalContext.current
    val n = Store.night
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Endless11.Intro) }
    var kind by remember { mutableStateOf(EndlessRules.Kind.Taboo) }
    var theme by remember { mutableStateOf("") }
    val cards = remember { mutableStateListOf<TabooCard>() }
    var fail by remember { mutableStateOf<AiResult.Fail?>(null) }
    var saved by remember { mutableStateOf(false) }
    var turn by remember { mutableIntStateOf(0) }
    var idx by remember { mutableIntStateOf(0) }
    var got by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }
    val lang = if (Store.settings.english) "en" else "el"

    fun have(): List<String> {
        val c = Cards.get(ctx)
        return cards.map { it.w } + when (kind) { EndlessRules.Kind.Taboo -> c.taboo.map { it.w }; EndlessRules.Kind.Forehead -> c.forehead.map { it.w }; EndlessRules.Kind.Draw -> c.draw.map { it.w } }
    }

    fun load(more: Boolean) {
        if (!more) cards.clear()
        saved = false; phase = Endless11.Load
        scope.launch {
            val r = AiGames.ask(t("Φτιάχνεις κάρτες για ελληνικά παιχνίδια παρέας. Μόνο JSON.", "You write cards for party games. JSON only."), EndlessRules.prompt(kind, theme.trim(), 20, cards.map { it.w }))
            when (r) {
                is AiResult.Ok -> { val new = EndlessRules.read(kind, AiGames.parse(r.text), have()); if (new.isEmpty() && cards.isEmpty()) { fail = null; phase = Endless11.Failed } else { cards += new; phase = Endless11.List } }
                is AiResult.Fail -> { fail = r; phase = Endless11.Failed }
            }
        }
    }

    when (phase) {
        Endless11.Intro -> Intro(game, host, listOf(
            t("Γράψε ένα θέμα («μόνο ποδόσφαιρο», «ταινίες των 90s», «Κρήτη») και το AI φτιάχνει 20 νέες κάρτες, χωρίς να επαναλαμβάνει όσες υπάρχουν ήδη.", "Type a theme («football only», «90s films», «Crete») and the AI makes 20 new cards, without repeating those that exist."),
            t("Παίξτε τες αμέσως (περιγράφεις, η ομάδα σου μαντεύει, 60″), ή κράτα τες για να μπαίνουν στα παιχνίδια όπως τα πακέτα.", "Play them right away (you describe, your team guesses, 60″), or keep them so they join the games like the packs do."),
        ), AiGames.ready(), {
            if (!AiGames.ready()) AiNeeded()
            Label(t("ΓΙΑ ΠΟΙΟ ΠΑΙΧΝΙΔΙ", "FOR WHICH GAME"))
            Seg(listOf(EndlessRules.Kind.Taboo to t("Πες το αλλιώς", "Taboo"), EndlessRules.Kind.Forehead to t("Στο μέτωπο", "Forehead"), EndlessRules.Kind.Draw to t("Ζωγραφιά", "Drawing")), kind, { kind = it }, color = game.color)
            Label(t("ΘΕΜΑ", "THEME"))
            Input(theme, { theme = it }, t("π.χ. μόνο ποδόσφαιρο", "e.g. football only"))
        }) { if (theme.isNotBlank()) load(false) }

        Endless11.Load -> AiWait(t("Το AI γράφει κάρτες για «${theme.trim()}»…", "The AI writes cards on «${theme.trim()}»…"))

        Endless11.Failed -> { AiFailed(fail, { load(cards.isNotEmpty()) }) { phase = Endless11.Intro } }

        Endless11.List -> Page("♾️ ${theme.trim()} · ${cards.size}", onBack = { phase = Endless11.Intro }, bottom = {
            BigButton(t("Παίξτε τώρα", "Play now"), { turn = 0; idx = 0; tally.bySide.clear(); phase = Endless11.Pass }, color = game.color)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(t("+20 ακόμα", "+20 more"), { load(true) }, Modifier.weight(1f))
                GhostButton(if (saved) t("✓ Κρατήθηκαν", "✓ Kept") else t("Κράτα τες", "Keep them"), {
                    val id = "ai-" + AiGames.norm(theme).replace(' ', '-').take(24) + "-" + kind.name.lowercase()
                    val tag = theme.trim().take(24)
                    val content = when (kind) {
                        EndlessRules.Kind.Taboo -> Content(taboo = cards.map { it.copy(c = tag) })
                        EndlessRules.Kind.Forehead -> Content(forehead = cards.map { Word(it.w, tag) })
                        EndlessRules.Kind.Draw -> Content(draw = cards.map { Word(it.w, tag) })
                    }
                    Packs.saveOwn(ctx, lang, id, content); saved = true; Sfx.correct()
                }, Modifier.weight(1f), color = C.green, enabled = !saved)
            }
        }) {
            cards.forEach { c -> Panel { Text(c.w, style = Type.h, color = C.text); if (c.no.isNotEmpty()) Text(c.no.joinToString(" · "), style = Type.small, color = C.red) } }
        }

        Endless11.Pass -> { val who = Turns.player(n, turn); PassTo(who, colorOf(who), note = t("Περιγράφεις· μαντεύει η ομάδα σου (ή όλοι).", "You describe; your team (or everyone) guesses.")) { got = 0; phase = Endless11.Play } }

        Endless11.Play -> {
            QuitGuard(host)
            val who = Turns.player(n, turn)
            val left = rememberCountdown(60, running = true, key = turn) { phase = Endless11.Turn }
            val c = cards.getOrNull(idx % maxOf(1, cards.size))
            CardScreen(who, colorOf(who), "♾️", c?.w ?: "", c?.no?.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.let { t("Χωρίς: ", "Without: ") + it }, game.color) {
                TimerText(left, color = game.color, modifier = Modifier.fillMaxWidth())
                JudgeButtons(game.color, yes = t("✓ Το βρήκαν", "✓ Got it"), no = t("Πάσο", "Pass")) { ok -> if (ok) got++; idx++ }
            }
        }

        Endless11.Turn -> {
            val who = Turns.player(n, turn)
            remember(turn) { if (got > 0) tally.add(host, listOf(who), got); 0 }
            val total = Turns.count(n, 1)
            Announce("⏱️", who, t("$got σωστές", "$got right"), if (turn + 1 < total) t("Επόμενος", "Next") else t("Αποτελέσματα", "Results"), game.color) {
                if (turn + 1 < total) { turn++; phase = Endless11.Pass } else phase = Endless11.Over
            }
        }

        Endless11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Endless11.List }
    }
}

/* ======================= ⚖️ Δικαστήριο ======================= */
object CourtRules {
    fun judge() = t("Είσαι ένας θεατρικός, αστείος δικαστής σε ένα δικαστήριο για χαζές διαφωνίες. Κρίνεις με χιούμορ, αλλά δίκαια, με βάση τα επιχειρήματα.", "You are a theatrical, funny judge in a court for silly disputes. You judge with humour, but fairly, on the arguments.")

    fun prompt(case: String, a: String, b: String, args: List<Pair<String, String>>) = t(
        "Υπόθεση: «$case». Πλευρά Α: $a. Πλευρά Β: $b.\n" + args.joinToString("\n") { "${it.first}: ${it.second}" } +
            "\nΒγάλε απόφαση. Απάντησε ΜΟΝΟ JSON: {\"winner\": \"A\" ή \"B\", \"verdict\": \"η αστεία απόφαση σε 2–4 προτάσεις, με τα ονόματα\"}.",
        "Case: «$case». Side A: $a. Side B: $b.\n" + args.joinToString("\n") { "${it.first}: ${it.second}" } +
            "\nGive a verdict. Answer ONLY JSON: {\"winner\": \"A\" or \"B\", \"verdict\": \"the funny ruling in 2–4 sentences, with the names\"}.",
    )
}

private enum class Court11 { Intro, Case, Argue, Load, Verdict, Over, Failed }

@Composable
fun CourtGame(host: GameHost) {
    val tts = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { Narrator.init(tts); onDispose { Narrator.stop() } }
    val game = Games.byId("court")!!
    val ctx = LocalContext.current
    val n = Store.night
    val scope = rememberCoroutineScope()
    val deck = remember { Deck("court", Cards.get(ctx).court, { it.a }) }
    var phase by remember { mutableStateOf(Court11.Intro) }
    var cases by remember { mutableIntStateOf(3) }
    var round by remember { mutableIntStateOf(0) }
    var case by remember { mutableStateOf("") }
    var own by remember { mutableStateOf("") }
    val args = remember { mutableStateListOf<Pair<String, String>>() }
    var text by remember { mutableStateOf("") }
    var winner by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf("") }
    var fail by remember { mutableStateOf<AiResult.Fail?>(null) }
    val tally = remember { Tally() }
    val names = n.names
    val a = names.getOrElse((round * 2) % maxOf(1, names.size)) { "" }
    val b = names.getOrElse((round * 2 + 1) % maxOf(1, names.size)) { "" }
    val order = listOf(a, b, a, b)

    fun decide() {
        phase = Court11.Load
        scope.launch {
            when (val r = AiGames.ask(CourtRules.judge(), CourtRules.prompt(case, a, b, args.toList()))) {
                is AiResult.Ok -> {
                    val o = AiGames.parse(r.text)
                    winner = if (AiGames.str(o, "winner").uppercase().startsWith("B")) b else a
                    verdict = AiGames.str(o, "verdict").ifBlank { r.text.take(500) }
                    tally.add(host, listOf(winner), 2); host.log(t("Δικαστήριο: «$case» · νίκη $winner", "Courtroom: «$case» · $winner wins"))
                    Narrator.say(verdict.take(400)); Sfx.win(); phase = Court11.Verdict
                }
                is AiResult.Fail -> { fail = r; phase = Court11.Failed }
            }
        }
    }

    when (phase) {
        Court11.Intro -> Intro(game, host, listOf(
            t("Δύο παίκτες μαλώνουν για κάτι χαζό («ανανάς στην πίτσα»). Ο καθένας γράφει το επιχείρημά του (και το λέει δυνατά), δύο φορές.", "Two players argue about something silly («pineapple on pizza»). Each types their argument (and says it out loud), twice."),
            t("Το AI-δικαστής διαβάζει και βγάζει αστεία απόφαση. Ο νικητής +2.", "The AI judge reads it and gives a funny ruling. The winner +2."),
            t("Μπορείτε να γράψετε και δική σας υπόθεση.", "You can also type your own case."),
        ), AiGames.ready() && names.size >= 2, {
            if (!AiGames.ready()) AiNeeded()
            Body(t("Υποθέσεις", "Cases"), color = C.text)
            Seg(listOf(1 to "1", 3 to "3", 5 to "5"), cases, { cases = it }, color = game.color)
        }) { round = 0; case = deck.next()?.t ?: ""; own = ""; phase = Court11.Case }

        Court11.Case -> { QuitGuard(host); Page(t("⚖️ Υπόθεση ${round + 1} / $cases", "⚖️ Case ${round + 1} / $cases"), onBack = null, bottom = {
            BigButton(t("Ξεκινάει η δίκη", "The trial begins"), { if (own.isNotBlank()) case = own.trim(); args.clear(); text = ""; phase = Court11.Argue }, color = game.color)
        }) {
            Text(case, style = Type.title, color = C.text)
            Panel { Text(t("Α: $a", "A: $a"), style = Type.h, color = colorOf(a)); Text(t("Β: $b", "B: $b"), style = Type.h, color = colorOf(b)) }
            GhostButton(t("Άλλη υπόθεση", "Another case"), { case = deck.next()?.t ?: case }, Modifier.fillMaxWidth())
            Input(own, { own = it }, t("ή γράψτε δική σας", "or type your own"))
        } }

        Court11.Argue -> {
            QuitGuard(host)
            val who = order[args.size]
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(case, style = Type.h, color = C.text)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), reverseScrolling = true), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    args.forEach { (w, s) -> Bubble(w, s, w == a, colorOf(w)) }
                }
                Text(if (args.size < 2) t("Αγόρευση: $who", "Opening: $who") else t("Απάντηση: $who", "Reply: $who"), style = Type.h, color = colorOf(who))
                LongInput(text, { text = it }, t("Το επιχείρημά σου…", "Your argument…"))
                BigButton(if (args.size < 3) t("Επόμενος", "Next") else t("Στον δικαστή", "To the judge"), { args += who to text.trim(); text = ""; if (args.size == 4) decide() }, color = game.color, enabled = text.isNotBlank())
            }
        }

        Court11.Load -> AiWait(t("Ο δικαστής αποσύρεται να σκεφτεί…", "The judge retires to think…"))

        Court11.Failed -> AiFailed(fail, { decide() }) { phase = Court11.Intro }

        Court11.Verdict -> Page(t("⚖️ Η απόφαση", "⚖️ The verdict"), onBack = null, bottom = {
            BigButton(if (round + 1 < cases) t("Επόμενη υπόθεση", "Next case") else t("Αποτελέσματα", "Results"), { Narrator.stop(); if (round + 1 < cases) { round++; case = deck.next()?.t ?: ""; own = ""; phase = Court11.Case } else phase = Court11.Over }, color = game.color)
        }) {
            Text(t("Κερδίζει: $winner", "Winner: $winner"), style = Type.big, color = colorOf(winner))
            Panel { Body(verdict, color = C.text) }
        }

        Court11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Court11.Intro }
    }
}

/* ======================= 🤖 Ποιος είναι το AI; ======================= */
object AiqRules {
    fun role() = t("Παίζεις σε παιχνίδι παρέας όπου πρέπει να περάσεις για άνθρωπος. Γράφεις σαν κανονικός Έλληνας φίλος στο κινητό: σύντομα, χαλαρά, χωρίς τέλεια στίξη, μερικές φορές με μικρά λάθη, χωρίς emoji.", "You play a party game where you must pass as human. Write like a normal friend texting: short, casual, imperfect punctuation, sometimes small typos, no emoji.")
    fun prompt(q: String, examples: List<String>) = t("Ερώτηση: «$q». Γράψε ΜΟΝΟ την απάντησή σου, έως 12 λέξεις.", "Question: «$q». Write ONLY your answer, up to 12 words.") +
        (if (examples.isNotEmpty()) t(" Για ύφος, οι άλλοι έγραψαν: ", " For style, the others wrote: ") + examples.joinToString(" | ") else "")

    /** +2 for finding the AI; +1 to each player for every vote their answer got. */
    fun points(votes: Map<String, String>, ai: String, byAnswer: Map<String, String>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        votes.forEach { (v, pick) ->
            if (pick == ai) out[v] = (out[v] ?: 0) + 2
            else byAnswer[pick]?.let { owner -> if (owner != v) out[owner] = (out[owner] ?: 0) + 1 }
        }
        return out
    }
}

private enum class Aiq11 { Intro, Card, Write, Load, Vote, Reveal, Over, Failed }

@Composable
fun AiqGame(host: GameHost) {
    val game = Games.byId("aiq")!!
    val ctx = LocalContext.current
    val n = Store.night
    val scope = rememberCoroutineScope()
    val deck = remember { Deck("aiq", Cards.get(ctx).aiq, { it.a }) }
    var phase by remember { mutableStateOf(Aiq11.Intro) }
    var rounds by remember { mutableIntStateOf(3) }
    var round by remember { mutableIntStateOf(0) }
    var card by remember { mutableStateOf<Prompt?>(null) }
    var answers by remember { mutableStateOf(mapOf<String, String>()) } // player → text
    var aiText by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(listOf<String>()) }
    var votes by remember { mutableStateOf(mapOf<String, String>()) }
    var fail by remember { mutableStateOf<AiResult.Fail?>(null) }
    val tally = remember { Tally() }

    fun askAi() {
        phase = Aiq11.Load
        scope.launch {
            when (val r = AiGames.ask(AiqRules.role(), AiqRules.prompt(card?.t ?: "", answers.values.take(3)))) {
                is AiResult.Ok -> { aiText = r.text.lines().first().trim().trim('"', '«', '»').take(80); shown = (answers.values + aiText).distinct().shuffled(); phase = Aiq11.Vote }
                is AiResult.Fail -> { fail = r; phase = Aiq11.Failed }
            }
        }
    }

    when (phase) {
        Aiq11.Intro -> Intro(game, host, listOf(
            t("Μια ερώτηση, π.χ. «Τι δεν λες ποτέ σε πρώτο ραντεβού;». Ο καθένας γράφει κρυφά μια σύντομη απάντηση. Γράφει και το AI μία, προσπαθώντας να περάσει για άνθρωπος.", "A question, e.g. «What do you never say on a first date?». Everyone writes a short answer in secret. The AI writes one too, trying to pass as human."),
            t("Βλέπετε όλες τις απαντήσεις ανακατεμένες και ψηφίζετε κρυφά ποια είναι του AI.", "You see all the answers mixed up and vote in secret which is the AI's."),
            t("Βρήκες το AI: +2. Κάποιος πέρασε τη δική σου για AI: +1 για κάθε τέτοια ψήφο.", "Found the AI: +2. Someone took yours for the AI: +1 for each such vote."),
        ), AiGames.ready() && deck.size > 0 && n.names.size >= 2, {
            if (!AiGames.ready()) AiNeeded()
            Body(t("Γύροι", "Rounds"), color = C.text)
            Seg(listOf(3 to "3", 5 to "5", 8 to "8"), rounds, { rounds = it }, color = game.color)
        }) { round = 0; card = deck.next(); phase = Aiq11.Card }

        Aiq11.Card -> { QuitGuard(host); CardScreen(t("Γύρος ${round + 1} / $rounds", "Round ${round + 1} / $rounds"), C.dim, "🤖", card?.t ?: "", t("Ο καθένας θα γράψει κρυφά την απάντησή του.", "Everyone will write their answer in secret."), game.color) {
            BigButton(t("Γράφουμε", "Write"), { phase = Aiq11.Write }, color = game.color)
        } }

        Aiq11.Write -> { QuitGuard(host); SecretInputRound(n.names, key = round, prompt = { card?.t ?: "" }, hint = t("Σύντομα, όπως θα το έγραφες στο κινητό", "Short, like you'd text it")) { answers = it; askAi() } }

        Aiq11.Load -> AiWait(t("Γράφει και το AI…", "The AI writes too…"))

        Aiq11.Failed -> AiFailed(fail, { askAi() }) { phase = Aiq11.Intro }

        Aiq11.Vote -> {
            QuitGuard(host)
            var i by remember(round) { mutableIntStateOf(0) }
            var ready by remember(round) { mutableStateOf(false) }
            val got = remember(round) { mutableMapOf<String, String>() }
            val who = n.names.getOrNull(i) ?: return
            if (!ready) { PassTo(who, colorOf(who), note = t("Κρυφή ψηφοφορία.", "Secret vote.")) { ready = true }; return }
            Page(card?.t ?: "", onBack = null) {
                Text(t("$who, ποια έγραψε το AI;", "$who, which did the AI write?"), style = Type.h, color = colorOf(who))
                shown.filter { it != answers[who] }.forEach { s ->
                    GhostButton(s, {
                        got[who] = s; Sfx.tap()
                        if (i + 1 < n.names.size) { i++; ready = false } else { votes = got.toMap(); phase = Aiq11.Reveal }
                    }, Modifier.fillMaxWidth())
                }
            }
        }

        Aiq11.Reveal -> {
            QuitGuard(host)
            val byAnswer = answers.entries.associate { it.value to it.key }
            remember(round) {
                AiqRules.points(votes, aiText, byAnswer).forEach { (p, v) -> tally.add(host, listOf(p), v) }
                if (votes.values.none { it == aiText }) host.log(t("Ποιος είναι το AI: το AI ξεγέλασε όλους με «$aiText»", "Who is the AI: the AI fooled everyone with «$aiText»"))
                0
            }
            Page(t("Το AI έγραψε…", "The AI wrote…"), onBack = null, bottom = {
                BigButton(if (round + 1 < rounds) t("Επόμενος γύρος", "Next round") else t("Αποτελέσματα", "Results"), { if (round + 1 < rounds) { round++; card = deck.next(); phase = Aiq11.Card } else phase = Aiq11.Over }, color = game.color)
            }) {
                Text("«$aiText»", style = Type.title, color = game.color)
                shown.forEach { s ->
                    val owner = if (s == aiText) "🤖 AI" else byAnswer[s] ?: ""
                    val k = votes.values.count { it == s }
                    Panel { Text(s, style = Type.body, color = C.text); Row { Text(owner, style = Type.small, color = if (s == aiText) game.color else colorOf(owner), modifier = Modifier.weight(1f)); Text(t("ψήφοι $k", "votes $k"), style = Type.small, color = C.muted) } }
                }
            }
        }

        Aiq11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Aiq11.Intro }
    }
}

/* ======================= 🔮 Ο Μάντης ======================= */
object OracleRules {
    val themes get() = listOf(t("Απόψε", "Tonight"), t("Αύριο", "Tomorrow"), t("Σε 10 χρόνια", "In 10 years"), t("Στις διακοπές", "On holiday"))
    fun role() = t("Είσαι ο «Μάντης», ένας θεατρικός, αστείος μάντης σε βραδιά παιχνιδιών. Οι προβλέψεις σου είναι παιχνιδιάρικες, καλοπροαίρετες, ποτέ για υγεία, χρήματα, σχέσεις ή εμφάνιση.", "You are «the Oracle», a theatrical, funny fortune teller at a game night. Your predictions are playful and kind, never about health, money, relationships or looks.")
    fun prompt(names: List<String>, theme: String, facts: String) = t(
        "Κάνε μια αστεία πρόβλεψη (1–2 προτάσεις) για κάθε παίκτη, για το «$theme». Παίκτες: ${names.joinToString()}. Στοιχεία της βραδιάς: $facts. Απάντησε ΜΟΝΟ JSON: {\"όνομα\": \"πρόβλεψη\"}.",
        "Make a funny prediction (1–2 sentences) for each player, about «$theme». Players: ${names.joinToString()}. Facts from tonight: $facts. Answer ONLY JSON: {\"name\": \"prediction\"}.",
    )
}

private enum class Oracle11 { Intro, Load, Show, Vote, Over, Failed }

@Composable
fun OracleGame(host: GameHost) {
    val tts = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { Narrator.init(tts); onDispose { Narrator.stop() } }
    val game = Games.byId("oracle")!!
    val n = Store.night
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Oracle11.Intro) }
    var theme by remember { mutableIntStateOf(0) }
    var preds by remember { mutableStateOf(mapOf<String, String>()) }
    var open by remember { mutableIntStateOf(0) }
    var fail by remember { mutableStateOf<AiResult.Fail?>(null) }
    var gameNo by remember { mutableIntStateOf(0) }
    val tally = remember { Tally() }

    fun load() {
        phase = Oracle11.Load
        scope.launch {
            when (val r = AiGames.ask(OracleRules.role(), OracleRules.prompt(n.names, OracleRules.themes[theme], Havales.summary(n)))) {
                is AiResult.Ok -> {
                    val o = AiGames.parse(r.text) as? JsonObject
                    val m = n.names.associateWith { name -> o?.entries?.firstOrNull { AiGames.norm(it.key) == AiGames.norm(name) }?.value?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty() }.filterValues { it.isNotBlank() }
                    if (m.isEmpty()) { fail = null; phase = Oracle11.Failed } else { preds = m; open = 0; gameNo++; phase = Oracle11.Show }
                }
                is AiResult.Fail -> { fail = r; phase = Oracle11.Failed }
            }
        }
    }

    when (phase) {
        Oracle11.Intro -> Intro(game, host, listOf(
            t("Ο Μάντης κοιτάει τη σφαίρα του και λέει τι θα γίνει στον καθένα: απόψε, αύριο ή σε 10 χρόνια.", "The Oracle looks into the ball and says what will happen to each of you: tonight, tomorrow or in 10 years."),
            t("Πάντα καλοπροαίρετα: τίποτα για υγεία, χρήματα, σχέσεις, εμφάνιση ή καταγωγή.", "Always kind: nothing about health, money, relationships, looks or origin."),
            t("Στο τέλος ψηφίζετε την πιο αστεία πρόβλεψη: +1 σε αυτόν που την πήρε.", "At the end you vote the funniest prediction: +1 to whoever got it."),
        ), AiGames.ready() && n.names.isNotEmpty(), {
            if (!AiGames.ready()) AiNeeded()
            Label(t("ΓΙΑ ΠΟΤΕ", "FOR WHEN"))
            Seg(OracleRules.themes.indices.map { it to OracleRules.themes[it] }, theme, { theme = it }, color = game.color)
        }) { load() }

        Oracle11.Load -> AiWait(t("🔮 Ο Μάντης κοιτάει τη σφαίρα…", "🔮 The Oracle gazes into the ball…"))

        Oracle11.Failed -> AiFailed(fail, { load() }) { phase = Oracle11.Intro }

        Oracle11.Show -> {
            QuitGuard(host)
            val list = preds.entries.toList()
            Page(t("🔮 ${OracleRules.themes[theme]}", "🔮 ${OracleRules.themes[theme]}"), onBack = null, bottom = {
                if (open < list.size) BigButton(t("Δείξε για ${list[open].key}", "Show for ${list[open].key}"), { Narrator.say(list[open].key + ". " + list[open].value); open++ }, color = game.color)
                else BigButton(if (n.names.size >= 3) t("Ψηφοφορία", "Vote") else t("Τέλος", "Done"), { Narrator.stop(); phase = if (n.names.size >= 3) Oracle11.Vote else Oracle11.Over }, color = game.color)
            }) {
                list.take(open).forEach { (p, s) -> Panel { Text(p, style = Type.h, color = colorOf(p)); Body(s, color = C.text) } }
            }
        }

        Oracle11.Vote -> { QuitGuard(host); SecretVote(n.names, preds.keys.toList(), key = gameNo, question = t("Ποια πρόβλεψη ήταν η πιο αστεία;", "Which prediction was the funniest?")) { v ->
            Tally11.tops(v).forEach { tally.add(host, listOf(it), 1) }
            host.log(t("Μάντης: η πιο αστεία για ${Tally11.tops(v).joinToString()}", "Oracle: funniest for ${Tally11.tops(v).joinToString()}"))
            phase = Oracle11.Over
        } }

        Oracle11.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = Oracle11.Intro }
    }
}
