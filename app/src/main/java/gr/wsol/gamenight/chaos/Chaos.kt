package gr.wsol.gamenight.chaos

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import kotlin.random.Random

/**
 * «🌀 Χάος»: small, silly surprises between rounds (a penalty, a gift, a curse, the sheriff, a crazy
 * card), and the «🎯 Κράξε» button. Nothing appears on a timed or secret screen: only while a calm
 * screen (pass the phone, announcements, the rules, the end of a game) is showing and no clock runs.
 */
object Chaos {
    /** What a chaos screen shows. [player] gets [points]; [duel] offers a duel to win it back. */
    data class Event(val kind: Kind, val emoji: String, val title: String, val text: String, val player: String?, val points: Int = 0, val other: String? = null, val duel: Boolean = false)
    enum class Kind { Penalty, Gift, Curse, Sheriff, Crazy, Roast }

    var pending by mutableStateOf<Event?>(null)
    /** Calm screens on show and clocks running right now (counted by the screens themselves). */
    var calm by mutableIntStateOf(0)
    var timed by mutableIntStateOf(0)
    /** Who has the phone: the last one who pressed «It's me». */
    var holder by mutableStateOf<String?>(null)
    /** The sheriff of this game night, who may tax someone once. */
    var sheriff by mutableStateOf<String?>(null)
    var nextAt = 0L
    var lastRoast = 0L

    const val ROAST_EVERY_MS = 5 * 60 * 1000L

    fun minutes(freq: Int): IntRange = when (freq) { 0 -> 15..25; 2 -> 4..8; else -> 8..15 }

    fun schedule(now: Long = System.currentTimeMillis(), rnd: Random = Random) {
        val m = minutes(Store.settings.chaosFreq)
        nextAt = now + (m.first + rnd.nextInt(m.last - m.first + 1)) * 60_000L
    }

    val canShow get() = pending != null && calm > 0 && timed == 0

    /** Called after each round (from GameHost.log): maybe a surprise is due. */
    fun maybe(c: Context, now: Long = System.currentTimeMillis(), rnd: Random = Random) {
        val s = Store.settings
        val n = Store.night
        if (!s.chaos || n.players.size < 2 || pending != null) return
        if (nextAt == 0L) { schedule(now, rnd); return }
        if (now < nextAt) return
        pending = make(c, n, rnd)
        schedule(now, rnd)
    }

    fun make(c: Context, n: Night, rnd: Random = Random): Event {
        val cards = Cards.get(c)
        val adult = Store.settings.adult
        val p = n.names.random(rnd)
        val roll = rnd.nextInt(100)
        return when {
            roll < 45 -> {
                val why = cards.penalties.filter { adult || !it.a }.random(rnd).t
                Event(Kind.Penalty, "🚨", t("Ποινή!", "Penalty!"), t("$p: −1, γιατί $why.", "$p: −1, because $why."), p, -1, other = n.names.filter { it != p }.randomOrNull(rnd), duel = true)
            }
            roll < 62 -> Event(Kind.Gift, "🎁", t("Δώρο από τον ουρανό!", "A gift from the sky!"), t("$p: +1, γιατί ${cards.gifts.random(rnd).t}.", "$p: +1, because ${cards.gifts.random(rnd).t}."), p, 1)
            roll < 77 -> Event(Kind.Curse, "🧿", t("Κατάρα!", "A curse!"), cards.curses.random(rnd).t.replace("{name}", p), null)
            roll < 87 && sheriff == null -> { sheriff = p; Event(Kind.Sheriff, "🤠", t("Νέος σερίφης!", "A new sheriff!"), t("$p είναι ο σερίφης της βραδιάς: μία φορά μπορεί να «φορολογήσει» όποιον θέλει με −1, για όποιον λόγο θέλει. Το κουμπί είναι στην αρχική οθόνη.", "$p is tonight's sheriff: once, they may «tax» anyone −1, for any reason. The button is on the home screen."), p) }
            else -> crazy(n, rnd)
        }
    }

    private fun crazy(n: Night, rnd: Random): Event {
        val r = n.ranking()
        val leader = r.firstOrNull()?.first; val last = r.lastOrNull()?.first
        return if (n.teams || leader == null || leader == last || rnd.nextBoolean()) {
            val two = n.names.shuffled(rnd).take(2)
            Event(Kind.Crazy, "🔀", t("Τρελή κάρτα!", "Crazy card!"), t("${two[0]} δίνει έναν πόντο στον/στην ${two[1]}. Έτσι, χωρίς λόγο.", "${two[0]} gives one point to ${two[1]}. Just because."), two[0], -1, two[1])
        } else Event(Kind.Crazy, "🔀", t("Τρελή κάρτα!", "Crazy card!"), t("Ο/Η πρώτος/η (${n.label(leader)}) δίνει έναν πόντο στον/στην τελευταίο/α (${n.label(last!!)}), για να ζεσταθεί η κόντρα!", "The leader (${n.label(leader)}) gives one point to the last (${n.label(last!!)}), to heat things up!"), n.membersOf(leader).first(), -1, n.membersOf(last).first())
    }

    /** Applies an event's points to tonight. */
    fun apply(e: Event) {
        var n = Store.night
        e.player?.let { if (e.points != 0) n = n.award(listOf(it), e.points) }
        if (e.kind == Kind.Crazy) e.other?.let { n = n.award(listOf(it), 1) }
        Store.night = n.note("chaos", "${e.emoji} ${e.text}")
    }

    fun roast(c: Context, name: String, rnd: Random = Random): Event {
        lastRoast = System.currentTimeMillis()
        val line = Cards.get(c).roasts.random(rnd).t.replace("{name}", name)
        return Event(Kind.Roast, "🎯", t("Κράξιμο!", "Roasted!"), line, null)
    }

    fun roastWaitMs(now: Long = System.currentTimeMillis()) = (lastRoast + ROAST_EVERY_MS - now).coerceAtLeast(0)

    /** The narrator teases after an event that keeps happening to the same player. */
    fun tease(key: String, who: String) {
        if (!Store.settings.chaos) return
        val times = Store.night.stats[who]?.get(key) ?: 0
        if (times < 2) return
        val line = when (key) {
            "bomb" -> listOf(t("Πάλι ο/η $who στη βόμβα; Κλασικός/ή $who.", "$who and the bomb again? Classic $who."), t("$times φορές η βόμβα στα χέρια του/της $who. Τη συμπαθεί, φαίνεται.", "$times times the bomb in $who's hands. It likes them, clearly."))
            else -> listOf(t("$who, οι νάρκες σε αγαπάνε.", "$who, the mines love you."), t("Και πάλι $who! Μαζεύει νάρκες για συλλογή.", "$who again! Collecting mines."))
        }.random()
        gr.wsol.gamenight.extras.Narrator.say(line)
    }
}

/** Marks a calm screen (a surprise may appear on it). */
@Composable
fun CalmScreen() { DisposableEffect(Unit) { Chaos.calm++; onDispose { Chaos.calm-- } } }

/** Marks a running clock (no surprise while it runs). */
@Composable
fun TimedScreen(running: Boolean) { DisposableEffect(running) { if (running) Chaos.timed++; onDispose { if (running) Chaos.timed-- } } }

/** The funniest drawing of the night, to share from the scoreboard. */
object Moments { var last: Pair<String, List<gr.wsol.gamenight.ui.Line>>? = null }

/** End-of-night awards from tonight's counters. */
object Awards {
    data class Award(val emoji: String, val title: String, val who: String, val why: String)

    fun of(n: Night): List<Award> {
        val out = mutableListOf<Award>()
        val r = n.ranking()
        r.firstOrNull()?.takeIf { it.second > 0 }?.let { out += Award("👑", t("Ο θρύλος της βραδιάς", "Legend of the night"), n.label(it.first), t("${it.second} πόντοι", "${it.second} points")) }
        if (r.size >= 2) r.last().let { out += Award("🐢", t("Ο ουραγός με αξιοπρέπεια", "Last, with dignity"), n.label(it.first), t("${it.second} πόντοι, αλλά τι στυλ!", "${it.second} points, but what style!")) }
        fun top(key: String) = n.stats.mapNotNull { (p, m) -> m[key]?.let { p to it } }.maxByOrNull { it.second }
        top("bomb")?.let { out += Award("💣", t("Μαγνήτης της βόμβας", "Bomb magnet"), it.first, t("του/της έσκασε ${it.second} φορές", "blew up ${it.second} times")) }
        top("liar")?.let { out += Award("🤥", t("Ο πιο πειστικός ψεύτης", "Most convincing liar"), it.first, t("ξεγέλασε ${it.second} φορές", "fooled them ${it.second} times")) }
        top("sneaky")?.let { out += Award("🕶️", t("Ο πιο αθώος", "The most innocent"), it.first, t("δεν τον/την έπιασε κανείς (${it.second})", "nobody caught them (${it.second})")) }
        top("banned")?.let { out += Award("🙊", t("Ο φλύαρος", "The chatterbox"), it.first, t("είπε ${it.second} απαγορευμένες", "said ${it.second} banned words")) }
        top("mine")?.let { out += Award("💥", t("Ναρκαλιευτής", "Mine finder"), it.first, t("πάτησε ${it.second} νάρκες", "stepped on ${it.second} mines")) }
        return out
    }

    fun text(n: Night) = (listOf(t("🏆 Gamenight GR: τα βραβεία της βραδιάς", "🏆 Gamenight GR: tonight's awards")) + of(n).map { "${it.emoji} ${it.title}: ${it.who} (${it.why})" }).joinToString("\n")
}
