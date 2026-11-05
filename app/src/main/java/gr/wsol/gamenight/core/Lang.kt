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
        val ends = listOf("ουσ", "ων", "ου", "εσ", "ασ", "οσ", "ησ", "ια", "ιο", "ισ", "α", "ε", "η", "ι", "ο", "υ", "ω", "σ")
        val e = ends.firstOrNull { w.endsWith(it) && w.length - it.length >= 3 } ?: return w
        return w.dropLast(e.length)
    }
}
