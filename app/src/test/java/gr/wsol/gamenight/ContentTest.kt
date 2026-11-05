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

    @Test fun noCapitalAccentsInTitles() {
        // House rule: no Greek capitals with accents in all-caps text; in mixed case they're fine.
        el.quads.flatMap { it.groups }.forEach { assertTrue(it.name, it.name != it.name.uppercase() || it.name.none { c -> c in "ΆΈΉΊΌΎΏ" }) }
    }
}
