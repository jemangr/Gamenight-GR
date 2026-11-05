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

/** «Ο Εξωγήινος»: the humans' word and the alien's similar one. */
@Serializable data class AlienPair(val h: String, val x: String, val c: String = "", val a: Boolean = false)

/** «Στο μέτωπο»: a word and its category. */
@Serializable data class Word(val w: String, val c: String = "", val a: Boolean = false)

/** A strange but true question with its answer («Ο Μπλοφατζής», «Ψέμα υπό πίεση»). */
@Serializable data class Fact(val q: String, val ans: String, val a: Boolean = false)

/** «Το νούμερό σου»: a scene with a 1–10 scale. */
@Serializable data class Scene(val t: String, val lo: String, val hi: String, val a: Boolean = false)

/** «Ναρκοπέδιο»: the start word and its 3 mines, each with the forms that also count. */
@Serializable data class Minefield(val w: String, val mines: List<List<String>>, val a: Boolean = false)

/** «Ο Σαμποτέρ»: a shared mission. A list mission has a [trap] (a wrong answer that sounds right); a story mission has a [word] to steer to. */
@Serializable data class Mission(val t: String, val trap: String = "", val word: String = "", val a: Boolean = false) { val story get() = word.isNotEmpty() }

/** 1.1: a card with a kind ([c]): «Ζάρι» q/d/m/n, «Μίμηση» a/r, «Ψίθυρος» t/w, «Μπουκάλι» t/d. */
@Serializable data class Tagged(val t: String, val c: String = "", val a: Boolean = false)

/** «Το κοντινότερο»: a question with a number for an answer and its unit. */
@Serializable data class NumberQ(val q: String, val n: Double, val u: String = "", val a: Boolean = false)

/** «Ο κατάσκοπος στο σπίτι»: a place and the roles in it. */
@Serializable data class Place(val place: String, val roles: List<String>, val a: Boolean = false)

/** «Κουίζ γειτονιάς», «Trivia σε ισορροπία»: a question, the right answer and three wrong ones. */
@Serializable data class Quiz(val q: String, val ok: String, val no: List<String>, val a: Boolean = false)

@Serializable
data class Content(
    val taboo: List<TabooCard> = emptyList(),
    val quads: List<QuadPuzzle> = emptyList(),
    val bomb: List<Prompt> = emptyList(),
    val find10: List<Prompt> = emptyList(),
    val related: List<Prompt> = emptyList(),
    val song: List<Prompt> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val alien: List<AlienPair> = emptyList(),
    val forehead: List<Word> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val mines: List<Minefield> = emptyList(),
    val draw: List<Word> = emptyList(),
    val phrases: List<Prompt> = emptyList(),
    val missions: List<Mission> = emptyList(),
    val penalties: List<Prompt> = emptyList(),
    val gifts: List<Prompt> = emptyList(),
    val curses: List<Prompt> = emptyList(),
    val roasts: List<Prompt> = emptyList(),
    val whois: List<Prompt> = emptyList(),
    val knowme: List<Prompt> = emptyList(),
    val tower: List<Prompt> = emptyList(),
    val court: List<Prompt> = emptyList(),
    val aiq: List<Prompt> = emptyList(),
    val dice: List<Tagged> = emptyList(),
    val mime: List<Tagged> = emptyList(),
    val whisper: List<Tagged> = emptyList(),
    val bottle: List<Tagged> = emptyList(),
    val closest: List<NumberQ> = emptyList(),
    val spy: List<Place> = emptyList(),
    val quiz: List<Quiz> = emptyList(),
)

/** All the cards, from the JSON files in assets/content/<lang>/ (one per game). Loaded once. */
object Cards {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: Pair<String, Content>? = null

    /** After a pack or the group's own cards change. */
    fun reset() { cache = null }

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
            alien = list("alien", AlienPair.serializer()),
            forehead = list("forehead", Word.serializer()),
            facts = list("facts", Fact.serializer()),
            scenes = list("scenes", Scene.serializer()),
            mines = list("mines", Minefield.serializer()),
            draw = list("draw", Word.serializer()),
            phrases = list("phrases", Prompt.serializer()),
            missions = list("missions", Mission.serializer()),
            penalties = list("penalties", Prompt.serializer()),
            gifts = list("gifts", Prompt.serializer()),
            curses = list("curses", Prompt.serializer()),
            roasts = list("roasts", Prompt.serializer()),
            whois = list("whois", Prompt.serializer()),
            knowme = list("knowme", Prompt.serializer()),
            tower = list("tower", Prompt.serializer()),
            court = list("court", Prompt.serializer()),
            aiq = list("aiq", Prompt.serializer()),
            dice = list("dice", Tagged.serializer()),
            mime = list("mime", Tagged.serializer()),
            whisper = list("whisper", Tagged.serializer()),
            bottle = list("bottle", Tagged.serializer()),
            closest = list("closest", NumberQ.serializer()),
            spy = list("spy", Place.serializer()),
            quiz = list("quiz", Quiz.serializer()),
        ).withPacks(lang)
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
