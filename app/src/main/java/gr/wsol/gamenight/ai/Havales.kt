package gr.wsol.gamenight.ai

import android.content.Context
import gr.wsol.gamenight.chaos.Chaos
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.games.Games
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * «Ο Χαβαλές»: an AI character who now and then pops in between rounds and teases the players,
 * from what really happened tonight (names, points, which games). Only that leaves the phone,
 * and only with the user's own key. Same safety rules as Binelik AI.
 */
object Havales {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastAt = 0L
    private var busy = false
    const val EVERY_MS = 6 * 60 * 1000L

    fun provider(): Provider = runCatching { Provider.valueOf(Store.settings.aiProvider) }.getOrDefault(Provider.GEMINI)
    fun ready(): Boolean = Store.settings.havales && Keys.get(provider()).isNotBlank()

    private suspend fun ask(prompt: String, short: Boolean = true): AiResult =
        Ai.ask(provider(), Keys.get(provider()), system(Store.settings.havalesRude, Store.settings.english, short), prompt)

    fun system(rude: Boolean, english: Boolean, short: Boolean = true) = buildString {
        if (english) {
            append("You are «Havales», the cheeky joker of a game night among friends, in the app Gamenight GR. ")
            append("You speak English, casually, like a friend who teases everyone. ")
        } else {
            append("Είσαι ο «Χαβαλές», ο πειραχτήρι της παρέας σε μια βραδιά παιχνιδιών, στην εφαρμογή Gamenight GR. ")
            append("Μιλάς ελληνικά, χαλαρά, σαν φίλος που τους πειράζει όλους. ")
        }
        if (short) append(if (english) "Always very short: one or two sentences, at most 30 words. No emoji lists, no quotes, no hashtags. " else "Πάντα πολύ σύντομα: μία ή δύο προτάσεις, έως 30 λέξεις. Χωρίς λίστες, χωρίς εισαγωγικά, χωρίς hashtags. ")
        append(if (english) "Rules you never break: never mock origin, religion, disability, illness, body or appearance; nothing sexual; nothing cruel. It's all friendly teasing about the game. " else "Κανόνες που δεν σπας ποτέ: δεν κοροϊδεύεις καταγωγή, θρησκεία, αναπηρία, ασθένεια, σώμα ή εμφάνιση· τίποτα σεξουαλικό· τίποτα σκληρό. Είναι μόνο φιλικό πείραγμα για το παιχνίδι. ")
        append(if (rude) (if (english) "Mild swearing is allowed, friendly, like among close friends (never at the person's identity)." else "Επιτρέπονται ελαφριές βρισιές, φιλικά, όπως μεταξύ κολλητών (ποτέ για την ταυτότητα κάποιου).") else (if (english) "No swearing." else "Χωρίς βρισιές."))
    }

    fun summary(n: Night): String = buildString {
        append(t("Παίκτες και πόντοι: ", "Players and points: "))
        append(n.ranking().joinToString(", ") { "${n.label(it.first)} ${it.second}" })
        val games = n.log.takeLast(12).joinToString(" | ") { e -> (Games.byId(e.game)?.title ?: "") + ": " + e.note }
        if (games.isNotBlank()) append(t(". Τελευταία γεγονότα: ", ". Latest events: ")).append(games)
        val st = n.stats.entries.joinToString(", ") { (p, m) -> "$p " + m.entries.joinToString("/") { "${it.key}×${it.value}" } }
        if (st.isNotBlank()) append(t(". Μετρητές: ", ". Counters: ")).append(st)
    }

    /** After a round: maybe a tease (at most every 4 minutes, if chaos and the AI are on). */
    fun maybe(c: Context) {
        val s = Store.settings
        if (!s.chaos || !ready() || busy || Chaos.pending != null || Store.night.players.size < 2) return
        val now = System.currentTimeMillis()
        if (now - lastAt < EVERY_MS / (1 + s.chaosFreq)) return
        lastAt = now; busy = true
        scope.launch {
            val r = ask(t("Πες ένα πείραγμα για κάποιον από την παρέα, με βάση αυτά: ", "Tease someone in the group, based on this: ") + summary(Store.night))
            busy = false
            if (r is AiResult.Ok && Chaos.pending == null) Chaos.pending = Chaos.Event(Chaos.Kind.Roast, "🎙️", t("Ο Χαβαλές", "Havales"), r.text.take(300), null)
        }
    }

    /** «Κράξε» with the AI: a tease for [name]. */
    suspend fun roast(name: String): String? {
        if (!ready()) return null
        val r = ask(t("Κράξε φιλικά τον/την $name για το πώς παίζει απόψε. Στοιχεία: ", "Roast $name, in a friendly way, for how they play tonight. Facts: ") + summary(Store.night))
        return (r as? AiResult.Ok)?.text?.take(300)
    }

    /** «Η ιστορία της βραδιάς»: a short funny report, to read and share. */
    suspend fun story(): AiResult =
        ask(t("Γράψε ένα σύντομο αστείο «ρεπορτάζ» της βραδιάς (έως 120 λέξεις), σαν αθλητικός σχολιαστής, με τα ονόματα και τα γεγονότα: ", "Write a short funny «report» of the night (up to 120 words), like a sports commentator, with the names and events: ") + summary(Store.night), short = false)
}

/** The key stays on this phone only (app backups are off, it is never in a report). */
object Keys {
    fun get(p: Provider): String = Store.text("aikey.${p.name}").orEmpty()
    fun set(p: Provider, key: String) = Store.putText("aikey.${p.name}", key.trim())
}
