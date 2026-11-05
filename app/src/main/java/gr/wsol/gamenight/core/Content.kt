package gr.wsol.gamenight.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Cards of «Πες το αλλιώς»: the word and 5 words you may not say. */
@Serializable data class TabooCard(val w: String, val no: List<String>, val c: String = "", val a: Boolean = false)

/** A puzzle of «Τετράδες»: 4 groups of 4, level 0 (easy, yellow) to 3 (tricky, purple). */
@Serializable data class QuadGroup(val name: String, val words: List<String>, val level: Int)
@Serializable data class QuadPuzzle(val groups: List<QuadGroup>, val a: Boolean = false)

/** A plain prompt («Βόμβα», «Βρες 10», «Σχετικές λέξεις», «Λέξη σε τραγούδι»). [l]: «Βόμβα» adds a letter. */
@Serializable data class Prompt(val t: String, val l: Boolean = false, val a: Boolean = false)

/** «Μάντεψε την ταινία ή σειρά»: emoji, the title, our own hint, and whether it's a film or a series. */
@Serializable data class Movie(val e: String, val title: String, val hint: String, val series: Boolean = false, val gr: Boolean = false, val a: Boolean = false)

@Serializable
data class Content(
    val taboo: List<TabooCard> = emptyList(),
    val quads: List<QuadPuzzle> = emptyList(),
    val bomb: List<Prompt> = emptyList(),
    val find10: List<Prompt> = emptyList(),
    val related: List<Prompt> = emptyList(),
    val song: List<Prompt> = emptyList(),
    val movies: List<Movie> = emptyList(),
)

/** All the cards, from the JSON files in assets/content/<lang>/ (one per game). Loaded once. */
object Cards {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: Pair<String, Content>? = null

    fun get(c: Context): Content {
        val lang = if (Lang.english) "en" else "el"
        cache?.takeIf { it.first == lang }?.let { return it.second }
        val loaded = load(lang) { name -> runCatching { c.assets.open("content/$lang/$name.json").bufferedReader().use { it.readText() } }.getOrNull() }
        cache = lang to loaded
        return loaded
    }

    /** [read] returns a file's text, or null if there is none (tests read from the source folder). */
    fun load(lang: String, read: (String) -> String?): Content {
        fun <T> list(name: String, s: kotlinx.serialization.KSerializer<T>): List<T> =
            read(name)?.let { runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(s), it) }.getOrNull() }.orEmpty()
        return Content(
            taboo = list("taboo", TabooCard.serializer()),
            quads = list("quads", QuadPuzzle.serializer()),
            bomb = list("bomb", Prompt.serializer()),
            find10 = list("find10", Prompt.serializer()),
            related = list("related", Prompt.serializer()),
            song = list("song", Prompt.serializer()),
            movies = list("movies", Movie.serializer()),
        )
    }
}

/**
 * Draws cards without repeats: a card comes back only after the whole deck has been seen (across
 * evenings too). 18+ cards are left out unless the switch is on.
 */
class Deck<T>(private val name: String, all: List<T>, adult: (T) -> Boolean, allowAdult: Boolean = Store.settings.adult, private val rnd: kotlin.random.Random = kotlin.random.Random) {
    private val ids: List<Int> = all.indices.filter { allowAdult || !adult(all[it]) }
    private val items = all

    val size get() = ids.size

    fun next(): T? {
        if (ids.isEmpty()) return null
        var seen = Store.seen(name)
        var left = ids.filter { it !in seen }
        if (left.isEmpty()) { seen = emptySet(); left = ids }
        val id = left[rnd.nextInt(left.size)]
        Store.setSeen(name, seen + id)
        return items[id]
    }
}
