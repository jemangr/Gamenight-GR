package gr.wsol.gamenight.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 1.1: card packs from the app's GitHub repo («Ποδόσφαιρο», «Ταινίες 90s», «Κρήτη»…) and the
 * group's own cards («ο σκύλος του Νίκου»). Both are mixed into the built-in decks, so every game
 * that uses a deck plays them too. Packs are plain JSON with the same keys as the built-in files;
 * nothing is sent anywhere, the app only downloads.
 */
object Packs {
    const val INDEX = "https://raw.githubusercontent.com/jemangr/Gamenight-GR/main/packs/index.json"
    private val json = Json { ignoreUnknownKeys = true }

    /** One pack in the list on GitHub. */
    @Serializable
    data class Info(val id: String, val lang: String, val name: String, val about: String = "", val file: String, val version: Int = 1)

    /** Downloaded packs: language → their cards (filled at start and after a download). */
    @Volatile private var loaded: Map<String, List<Pair<String, Content>>> = emptyMap()

    private fun dir(c: Context) = File(c.filesDir, "packs").apply { mkdirs() }

    /** Reads the downloaded packs from the phone (once at start). */
    fun init(c: Context) {
        val out = mutableMapOf<String, MutableList<Pair<String, Content>>>()
        dir(c).listFiles { f -> f.name.endsWith(".json") }.orEmpty().forEach { f ->
            val (lang, id) = f.nameWithoutExtension.split("__", limit = 2).let { (it.getOrNull(0) ?: "el") to (it.getOrNull(1) ?: f.nameWithoutExtension) }
            runCatching { json.decodeFromString(Content.serializer(), f.readText()) }.getOrNull()?.let { out.getOrPut(lang) { mutableListOf() } += id to it }
        }
        loaded = out
        Cards.reset()
    }

    fun installed(lang: String): List<String> = loaded[lang].orEmpty().map { it.first }

    /** The list of packs on GitHub, or null without internet. */
    suspend fun index(): List<Info>? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(ListSerializer(Info.serializer()), get(INDEX)) }.getOrNull()
    }

    /** Downloads [p] and keeps it on the phone. Only JSON that reads as cards is kept. */
    suspend fun install(c: Context, p: Info): Boolean = withContext(Dispatchers.IO) {
        val base = INDEX.substringBeforeLast('/')
        val text = runCatching { get("$base/${p.file}") }.getOrNull() ?: return@withContext false
        val content = runCatching { json.decodeFromString(Content.serializer(), text) }.getOrNull() ?: return@withContext false
        if (count(content) == 0) return@withContext false
        File(dir(c), "${p.lang}__${safe(p.id)}.json").writeText(text)
        init(c)
        true
    }

    /** 1.1, «Ατελείωτες κάρτες»: keeps cards the AI made as a pack of our own. */
    fun saveOwn(c: Context, lang: String, id: String, content: Content) {
        File(dir(c), "${lang}__${safe(id)}.json").writeText(json.encodeToString(Content.serializer(), content))
        init(c)
    }

    fun remove(c: Context, lang: String, id: String) { File(dir(c), "${lang}__${safe(id)}.json").delete(); init(c) }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }.also { require(it.length < 2_000_000) }
        } finally { c.disconnect() }
    }

    fun count(c: Content) = c.taboo.size + c.quads.size + c.bomb.size + c.find10.size + c.related.size + c.song.size + c.movies.size +
        c.alien.size + c.forehead.size + c.facts.size + c.scenes.size + c.mines.size + c.draw.size + c.phrases.size + c.missions.size +
        c.whois.size + c.knowme.size + c.tower.size + c.court.size + c.aiq.size + c.dice.size + c.mime.size + c.whisper.size +
        c.bottle.size + c.closest.size + c.spy.size + c.quiz.size

    // ---- the group's own cards ----
    /** One word or phrase per line, e.g. «ο σκύλος του Νίκου». */
    fun custom(): List<String> = Store.text("custom.words").orEmpty().lines().map { it.trim() }.filter { it.length in 2..40 }.distinct().take(300)

    fun setCustom(lines: List<String>) { Store.putText("custom.words", lines.joinToString("\n")); Cards.reset() }

    /** The built-in [base] with the packs of [lang] and the group's own words mixed in. */
    fun mix(base: Content, lang: String): Content {
        var c = base
        loaded[lang].orEmpty().forEach { (_, p) -> c = c.plus(p) }
        val own = custom()
        if (own.isNotEmpty()) c = c.copy(
            forehead = c.forehead + own.map { Word(it, if (lang == "en") "Ours" else "Δικές μας") },
            draw = c.draw + own.map { Word(it, if (lang == "en") "Ours" else "Δικές μας") },
            taboo = c.taboo + own.map { TabooCard(it, emptyList(), if (lang == "en") "Ours" else "Δικές μας") },
        )
        return c
    }
}

/** All the decks of [this] followed by those of [o]. */
fun Content.plus(o: Content) = Content(
    taboo + o.taboo, quads + o.quads, bomb + o.bomb, find10 + o.find10, related + o.related, song + o.song, movies + o.movies,
    alien + o.alien, forehead + o.forehead, facts + o.facts, scenes + o.scenes, mines + o.mines, draw + o.draw, phrases + o.phrases,
    missions + o.missions, penalties + o.penalties, gifts + o.gifts, curses + o.curses, roasts + o.roasts,
    whois + o.whois, knowme + o.knowme, tower + o.tower, court + o.court, aiq + o.aiq, dice + o.dice, mime + o.mime,
    whisper + o.whisper, bottle + o.bottle, closest + o.closest, spy + o.spy, quiz + o.quiz,
)

fun Content.withPacks(lang: String) = Packs.mix(this, lang)
