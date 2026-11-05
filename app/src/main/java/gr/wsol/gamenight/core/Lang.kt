package gr.wsol.gamenight.core

/**
 * The app's language: Greek first, English from 1.1. Every text on screen is written as
 * t("Ελληνικά", "English"), so switching needs no lookup tables.
 */
object Lang {
    @Volatile var english = false
}

fun t(el: String, en: String): String = if (Lang.english) en else el

/** Greek words for comparing what people typed: no accents, lower case, final sigma as σ. */
object Greek {
    private val plain = mapOf(
        'ά' to 'α', 'έ' to 'ε', 'ή' to 'η', 'ί' to 'ι', 'ϊ' to 'ι', 'ΐ' to 'ι', 'ό' to 'ο', 'ύ' to 'υ', 'ϋ' to 'υ', 'ΰ' to 'υ', 'ώ' to 'ω', 'ς' to 'σ',
    )

    fun fold(s: String): String = s.trim().lowercase().map { plain[it] ?: it }.joinToString("").replace(Regex("\\s+"), " ")

    /** A rough stem, so «ψάρια», «ψαριού» and «ψάρι» count as the same word (Ναρκοπέδιο, content checks). */
    fun stem(s: String): String {
        val w = fold(s)
        if (w.length <= 3) return w
        // English: plurals («fishes», «cities», «cats»)
        if (w.all { it in 'a'..'z' || it == ' ' }) return when {
            w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
            w.endsWith("es") && w.length > 4 -> w.dropLast(2)
            w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            else -> w
        }
        // the longer endings first: «ψαριού» → «ψαρ», like «ψάρι» and «ψάρια»
        val ends = listOf("ιου", "ιων", "ιασ", "ιεσ", "ιοσ", "ουσ", "ων", "ου", "εσ", "ασ", "οσ", "ησ", "ια", "ιο", "ισ", "α", "ε", "η", "ι", "ο", "υ", "ω", "σ")
        val e = ends.firstOrNull { w.endsWith(it) && w.length - it.length >= 3 } ?: return w
        return w.dropLast(e.length)
    }
}
