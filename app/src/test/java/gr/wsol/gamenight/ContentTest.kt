package gr.wsol.gamenight

import gr.wsol.gamenight.core.Cards
import gr.wsol.gamenight.core.Content
import gr.wsol.gamenight.core.Greek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks every card in assets/content: no duplicates, banned words not the word itself, puzzles well formed. */
class ContentTest {
    private fun load(lang: String): Content {
        val dir = listOf("src/main/assets/content/$lang", "app/src/main/assets/content/$lang").map(::File).first { it.exists() }
        return Cards.load(lang) { name -> File(dir, "$name.json").takeIf { it.exists() }?.readText() }
    }

    private val el = load("el")

    private fun dupes(items: List<String>) = items.groupingBy { Greek.fold(it) }.eachCount().filter { it.value > 1 }.keys

    @Test fun enoughCards() {
        assertTrue("taboo ${el.taboo.size}", el.taboo.size >= 300)
        assertTrue("quads ${el.quads.size}", el.quads.size >= 30)
        assertTrue(el.bomb.size >= 150); assertTrue(el.find10.size >= 100); assertTrue(el.related.size >= 100)
        assertTrue(el.song.size >= 150); assertTrue(el.movies.size >= 70)
    }

    @Test fun tabooCardsAreValid() {
        assertEquals(emptySet<String>(), dupes(el.taboo.map { it.w }))
        el.taboo.forEach { c ->
            assertEquals("${c.w}: 5 banned words", 5, c.no.size)
            assertEquals("${c.w}: banned words repeat", 5, c.no.map { Greek.fold(it) }.toSet().size)
            val w = Greek.fold(c.w)
            c.no.forEach { b ->
                val f = Greek.fold(b)
                assertTrue("${c.w}: banned «$b» is the word itself", f != w)
            }
        }
    }

    @Test fun quadPuzzlesAreWellFormed() {
        el.quads.forEachIndexed { i, p ->
            assertEquals("puzzle $i: 4 groups", 4, p.groups.size)
            assertEquals("puzzle $i: levels", setOf(0, 1, 2, 3), p.groups.map { it.level }.toSet())
            p.groups.forEach { assertEquals("puzzle $i ${it.name}: 4 words", 4, it.words.size) }
            val words = p.groups.flatMap { it.words }.map { Greek.fold(it) }
            assertEquals("puzzle $i (${p.groups[0].name}): 16 different words", 16, words.toSet().size)
        }
    }

    @Test fun promptsHaveNoDuplicates() {
        listOf(el.bomb, el.find10, el.related, el.song).forEach { list -> assertEquals(emptySet<String>(), dupes(list.map { it.t })) }
        assertEquals(emptySet<String>(), dupes(el.movies.map { it.title }))
        el.movies.forEach { assertTrue(it.title, it.e.isNotBlank() && it.hint.isNotBlank()) }
    }

    @Test fun phase2Content() {
        assertTrue("alien ${el.alien.size}", el.alien.size >= 150)
        el.alien.forEach { assertTrue("${it.h}/${it.x}", Greek.fold(it.h) != Greek.fold(it.x)) }
        assertEquals(emptySet<String>(), dupes(el.alien.map { it.h + "|" + it.x }))
        assertTrue(el.forehead.size >= 300); assertEquals(emptySet<String>(), dupes(el.forehead.map { it.c + "|" + it.w }))
        assertTrue(el.facts.size >= 80); el.facts.forEach { assertTrue(it.q, it.q.endsWith("?") || it.q.endsWith(";") && it.ans.isNotBlank()) }
        assertTrue(el.scenes.size >= 50); el.scenes.forEach { assertTrue(it.t, it.lo.isNotBlank() && it.hi.isNotBlank()) }
        assertTrue(el.mines.size >= 70)
        el.mines.forEach { m ->
            assertEquals(m.w, 3, m.mines.size)
            // A mine must not be the start word itself.
            m.mines.flatten().forEach { assertTrue("${m.w}: $it", Greek.stem(it) != Greek.stem(m.w)) }
        }
        assertEquals(emptySet<String>(), dupes(el.mines.map { it.w }))
    }

    @Test fun phase3Content() {
        assertTrue("draw ${el.draw.size}", el.draw.size >= 150); assertEquals(emptySet<String>(), dupes(el.draw.map { it.w }))
        assertTrue(el.phrases.size >= 60); assertEquals(emptySet<String>(), dupes(el.phrases.map { it.t }))
        assertTrue(el.missions.size >= 70)
        el.missions.forEach { m -> assertTrue(m.t, m.story || m.trap.isNotBlank()) }
        assertTrue(el.missions.count { it.story } >= 25)
    }

    @Test fun englishContentIsValid() {
        val en = load("en")
        assertTrue("en taboo ${en.taboo.size}", en.taboo.size >= 150)
        assertEquals(emptySet<String>(), dupes(en.taboo.map { it.w }))
        en.taboo.forEach { c -> assertEquals(c.w, 5, c.no.size); c.no.forEach { assertTrue("${c.w}: $it", Greek.fold(it) != Greek.fold(c.w)) } }
        en.quads.forEach { p -> assertEquals(4, p.groups.size); assertEquals(16, p.groups.flatMap { it.words }.map { Greek.fold(it) }.toSet().size); p.groups.forEach { assertEquals(it.name, 4, it.words.size) } }
        listOf(en.bomb, en.find10, en.related, en.song, en.phrases, en.penalties, en.gifts, en.curses, en.roasts).forEach { assertTrue(it.size >= 10); assertEquals(emptySet<String>(), dupes(it.map { p -> p.t })) }
        en.mines.forEach { assertEquals(it.w, 3, it.mines.size) }
        assertTrue(en.movies.size >= 50 && en.alien.size >= 100 && en.forehead.size >= 200 && en.facts.size >= 60 && en.scenes.size >= 40 && en.draw.size >= 100 && en.missions.size >= 40)
    }

    @Test fun noCapitalAccentsInTitles() {
        // House rule: no Greek capitals with accents in all-caps text; in mixed case they're fine.
        el.quads.flatMap { it.groups }.forEach { assertTrue(it.name, it.name != it.name.uppercase() || it.name.none { c -> c in "ΆΈΉΊΌΎΏ" }) }
    }
}
