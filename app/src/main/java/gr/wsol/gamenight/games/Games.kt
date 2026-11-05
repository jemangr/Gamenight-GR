package gr.wsol.gamenight.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.ui.C

enum class Category(val emoji: String, val el: String, val en: String, val subEl: String, val subEn: String) {
    Roles("🕵️", "Κρυφοί ρόλοι", "Hidden roles", "Κάποιος στην παρέα κρύβει κάτι. Βρείτε ποιος.", "Someone is hiding something. Find out who."),
    Words("🗣️", "Λέξεις", "Words", "Περιγράφεις, μαντεύεις, συνδέεις λέξεις.", "Describe, guess, connect words."),
    Bluff("🤥", "Μπλόφα και γνώσεις", "Bluff and trivia", "Ποιος λέει αλήθεια και ποιος θυμάται καλύτερα.", "Who tells the truth, who remembers best."),
    Stage("🎭", "Θέατρο, τραγούδι, ζωγραφική", "Acting, singing, drawing", "Παίζεις, τραγουδάς, ζωγραφίζεις.", "Act, sing, draw."),
    Reflex("⚡", "Αντανακλαστικά", "Reflexes", "Γρήγορα χέρια, γρήγορο μυαλό, σταθερό χέρι.", "Quick hands, quick mind, steady hand."),
    Arcade("🎮", "Arcade", "Arcade", "Μόνος σου για ρεκόρ, ή κόντρα στην παρέα.", "Alone for a record, or against the group."),
    Logic("🧠", "Λογική", "Logic", "Χωρίς τύχη: μετράει μόνο ο συλλογισμός.", "No luck: only reasoning counts.");

    val title get() = t(el, en)
    val sub get() = t(subEl, subEn)
}

/** What a game gets from the app: tonight's points, and a way back to the menu. */
interface GameHost {
    /** [pts] to each of [who] (a team gets them once), and a line in tonight's log. */
    fun award(who: Collection<String>, pts: Int)
    fun log(note: String)
    fun exit()
}

class Game(
    val id: String,
    val emoji: String,
    private val titleEl: String,
    private val titleEn: String,
    val category: Category,
    val min: Int,
    val max: Int,
    private val shortEl: String,
    private val shortEn: String,
    val color: Color,
    val play: (@Composable (GameHost) -> Unit)? = null,
) {
    val title get() = t(titleEl, titleEn)
    val short get() = t(shortEl, shortEn)
    val ready get() = play != null
    val players get() = if (min == max) "$min" else "$min–$max"

    /** Why it can't be played with [n] players, or null if it can. */
    fun blocked(n: Int): String? = when {
        !ready -> t("Έρχεται σύντομα", "Coming soon")
        n < min -> t("Θέλει τουλάχιστον $min άτομα", "Needs at least $min players")
        n > max -> t("Έως $max άτομα", "Up to $max players")
        else -> null
    }
}

/** All 28 games, in the order of the menu. The ones without [Game.play] show «Έρχεται σύντομα». */
object Games {
    val all: List<Game> = listOf(
        // 🕵️ Hidden roles
        Game("house", "🏚️", "Το στοιχειωμένο σπίτι", "The haunted house", Category.Roles, 4, 7, "Ζόμπι, θεραπευτές και μάγοι, με αφηγητή.", "Zombies, healers and mages, with a narrator.", C.ghost),
        Game("alien", "👽", "Ο Εξωγήινος", "The Alien", Category.Roles, 3, 7, "Όλοι έχουν την ίδια λέξη. Σχεδόν όλοι.", "Everyone has the same word. Almost.", C.green, { AlienGameScreen(it) }),
        Game("saboteur", "🧨", "Ο Σαμποτέρ", "The Saboteur", Category.Roles, 3, 7, "Κοινή αποστολή, ένας τη χαλάει κρυφά.", "A shared mission, one wrecks it in secret.", C.red),
        Game("fake", "🎨", "Ένας λείπει", "One is missing", Category.Roles, 3, 7, "Μια ζωγραφιά, ένας δεν ξέρει τι ζωγραφίζει.", "One drawing, one doesn't know what it is.", C.pink),
        // 🗣️ Words
        Game("taboo", "🗣️", "Πες το αλλιώς", "Say it otherwise", Category.Words, 2, 7, "Περίγραψε τη λέξη χωρίς τις 5 απαγορευμένες.", "Describe the word without the 5 banned ones.", C.amber, { TabooGame(it) }),
        Game("forehead", "🤳", "Στο μέτωπο", "On your forehead", Category.Words, 2, 7, "Το κινητό στο μέτωπο, η παρέα σου δίνει στοιχεία.", "Phone on your forehead, the group gives clues.", C.cyan, { ForeheadGame(it) }),
        Game("quads", "🟨", "Τετράδες", "Fours", Category.Words, 1, 7, "16 λέξεις, 4 ομάδες. Βρες τι τις δένει.", "16 words, 4 groups. Find what links them.", C.quad[0], { QuadsGame(it) }),
        Game("minefield", "💣", "Ναρκοπέδιο", "Minefield", Category.Words, 2, 7, "Αλυσίδα λέξεων, πρόσεχε τις νάρκες.", "A chain of words, mind the mines.", C.red, { MinefieldGame(it) }),
        Game("find10", "🔟", "Βρες 10", "Find 10", Category.Words, 2, 7, "Πες 10 σε 30 δευτερόλεπτα.", "Name 10 in 30 seconds.", C.lime, { Find10Game(it) }),
        Game("related", "🔗", "Σχετικές λέξεις", "Related words", Category.Words, 2, 7, "5″ ο καθένας για μια σχετική λέξη.", "5″ each for a related word.", C.cyan, { RelatedGame(it) }),
        // 🤥 Bluff and trivia
        Game("bluffer", "🃏", "Ο Μπλοφατζής", "The Bluffer", Category.Bluff, 3, 7, "Γράψε μπλόφα, βρες την αλήθεια.", "Write a bluff, find the truth.", C.ghost, { BlufferGame(it) }),
        Game("lie", "🤥", "Ψέμα υπό πίεση", "Lie under pressure", Category.Bluff, 2, 7, "Αλήθεια ή ψέμα; Ο επόμενος κρίνει.", "Truth or lie? The next one judges.", C.pink, { LieGame(it) }),
        Game("witness", "🔍", "Ο Μάρτυρας", "The Witness", Category.Bluff, 2, 7, "4″ για μια σκηνή. Τι θυμάσαι;", "4″ to see a scene. What do you remember?", C.cyan),
        Game("movies", "🎬", "Μάντεψε την ταινία ή σειρά", "Guess the film or series", Category.Bluff, 2, 7, "Ο τίτλος σε emoji.", "The title in emoji.", C.amber, { MoviesGame(it) }),
        // 🎭 Acting, singing, drawing
        Game("number", "🔢", "Το νούμερό σου", "Your number", Category.Stage, 3, 7, "Παίξε το σενάριο όπως το νούμερό σου.", "Act the scene like your number.", C.amber, { NumberGame(it) }),
        Game("telephone", "📞", "Χαλασμένο τηλέφωνο", "Broken telephone", Category.Stage, 3, 7, "Φράση, σχέδιο, φράση… τι έμεινε στο τέλος;", "Phrase, drawing, phrase… what's left at the end?", C.lime),
        Game("song", "🎤", "Λέξη σε τραγούδι", "Word in a song", Category.Stage, 2, 7, "Τραγούδησε κομμάτι με αυτή τη λέξη.", "Sing a song with this word.", C.pink, { SongGame(it) }),
        // ⚡ Reflexes
        Game("bomb", "⏱️", "Βόμβα", "Bomb", Category.Reflex, 2, 7, "Πες λέξη και πάσαρε, πριν σκάσει.", "Say a word and pass, before it blows.", C.red, { BombGame(it) }),
        Game("balance", "⚖️", "Ισορροπία", "Balance", Category.Reflex, 2, 7, "Πέρνα το κινητό χωρίς να πέσει η μπίλια.", "Pass the phone without dropping the ball.", C.cyan),
        // 🎮 Arcade
        Game("gate", "🌈", "Χρωματική πύλη", "Colour gate", Category.Arcade, 1, 7, "Tap για χρώμα, πέρνα από το ίδιο.", "Tap to change colour, pass the same one.", C.pink),
        Game("drop", "🕳️", "Βουτιά", "Dive", Category.Arcade, 1, 7, "Γείρε το κινητό, πέσε όσο πιο βαθιά.", "Tilt the phone, fall as deep as you can.", C.cyan),
        Game("grid", "🔢", "Γρήγορο πλέγμα", "Quick grid", Category.Arcade, 1, 7, "Ακολούθα την εντολή, γρήγορα.", "Follow the rule, fast.", C.amber),
        Game("orbit", "🪐", "Τροχιά", "Orbit", Category.Arcade, 1, 7, "Κράτα για κύκλο, άφησε για εκτόξευση.", "Hold to orbit, release to launch.", C.ghost),
        Game("slice", "✂️", "Κόψε και ταίριαξε", "Slice and match", Category.Arcade, 1, 7, "Κόκκινο με το βέλος, μπλε ανάποδα.", "Red with the arrow, blue against it.", C.red),
        Game("tower", "🏗️", "Πύργος", "Tower", Category.Arcade, 1, 7, "Ένα tap, ένας όροφος. Στο κέντρο.", "One tap, one floor. Dead centre.", C.lime),
        Game("path", "✨", "Φωτεινή διαδρομή", "Light path", Category.Arcade, 1, 7, "Θυμήσου τη διαδρομή και σύρε το δάχτυλο.", "Remember the path and trace it.", C.green),
        Game("sling", "🎯", "Σφεντόνα", "Slingshot", Category.Arcade, 1, 7, "Τράβα, ρίξε, αναπήδησε για combo.", "Pull, shoot, bounce for a combo.", C.amber),
        // 🧠 Logic
        Game("core", "⚛️", "Ο Πυρήνας", "The Core", Category.Logic, 1, 7, "Κόμβοι, σήματα, κύκλοι. Χωρίς τύχη.", "Nodes, signals, cycles. No luck.", C.cyan),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
