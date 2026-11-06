package gr.wsol.gamenight.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.ui.C

enum class Category(val emoji: String, val el: String, val en: String, val subEl: String, val subEn: String, val icon: Int = 0) {
    Roles("🕵️", "Κρυφοί ρόλοι", "Hidden roles", "Κάποιος στην παρέα κρύβει κάτι. Βρείτε ποιος.", "Someone is hiding something. Find out who.", gr.wsol.gamenight.R.drawable.ic_cat_roles),
    Words("🗣️", "Λέξεις", "Words", "Περιγράφεις, μαντεύεις, συνδέεις λέξεις.", "Describe, guess, connect words.", gr.wsol.gamenight.R.drawable.ic_cat_words),
    Party("🎉", "Παρέα", "Party", "Ψηφοφορίες, αλήθειες, ζάρια και μπουκάλι.", "Votes, truths, dice and the bottle.", gr.wsol.gamenight.R.drawable.ic_cat_party),
    Bluff("🤥", "Μπλόφα και γνώσεις", "Bluff and trivia", "Ποιος λέει αλήθεια και ποιος θυμάται καλύτερα.", "Who tells the truth, who remembers best.", gr.wsol.gamenight.R.drawable.ic_cat_bluff),
    Stage("🎭", "Θέατρο, τραγούδι, ζωγραφική", "Acting, singing, drawing", "Παίζεις, τραγουδάς, ζωγραφίζεις.", "Act, sing, draw.", gr.wsol.gamenight.R.drawable.ic_cat_stage),
    Reflex("⚡", "Αντανακλαστικά", "Reflexes", "Γρήγορα χέρια, γρήγορο μυαλό, σταθερό χέρι.", "Quick hands, quick mind, steady hand.", gr.wsol.gamenight.R.drawable.ic_cat_reflex),
    Motion("📱", "Με κίνηση", "Motion", "Γέρνεις, γυρίζεις, κρατάς σταθερά το κινητό.", "Tilt, turn and hold the phone steady.", gr.wsol.gamenight.R.drawable.ic_cat_motion),
    Arcade("🎮", "Arcade", "Arcade", "Μόνος σου για ρεκόρ, ή κόντρα στην παρέα.", "Alone for a record, or against the group.", gr.wsol.gamenight.R.drawable.ic_cat_arcade),
    Board("🎲", "Επιτραπέζια", "Board games", "Τάβλι για δύο σε ένα κινητό.", "Backgammon for two on one phone.", gr.wsol.gamenight.R.drawable.ic_cat_board),
    Logic("🧠", "Λογική", "Logic", "Χωρίς τύχη: μετράει μόνο ο συλλογισμός.", "No luck: only reasoning counts.", gr.wsol.gamenight.R.drawable.ic_cat_logic),
    Ai("🤖", "Με AI", "With AI", "Θέλουν δικό σου δωρεάν κλειδί AI. Όλα τα άλλα παίζονται χωρίς.", "Need your own free AI key. Everything else plays without.", gr.wsol.gamenight.R.drawable.ic_cat_ai);

    val title get() = t(el, en)
    val sub get() = t(subEl, subEn)
}

/** What a game gets from the app: tonight's points, and a way back to the menu. */
interface GameHost {
    /** [pts] to each of [who] (a team gets them once), and a line in tonight's log. */
    fun award(who: Collection<String>, pts: Int)
    fun log(note: String)
    fun exit()
    /** One more of [key] for [who] (for the awards at the end of the night). */
    fun stat(who: String, key: String) { Store.night = Store.night.stat(who, key) }
}

private fun gameIconOf(id: String): Int = when (id) {
    "house" -> gr.wsol.gamenight.R.drawable.ic_game_house
    "alien" -> gr.wsol.gamenight.R.drawable.ic_game_alien
    "saboteur" -> gr.wsol.gamenight.R.drawable.ic_game_saboteur
    "fake" -> gr.wsol.gamenight.R.drawable.ic_game_fake
    "whisper" -> gr.wsol.gamenight.R.drawable.ic_game_whisper
    "spy" -> gr.wsol.gamenight.R.drawable.ic_game_spy
    "lietower" -> gr.wsol.gamenight.R.drawable.ic_game_lietower
    "taboo" -> gr.wsol.gamenight.R.drawable.ic_game_taboo
    "forehead" -> gr.wsol.gamenight.R.drawable.ic_game_forehead
    "quads" -> gr.wsol.gamenight.R.drawable.ic_game_quads
    "minefield" -> gr.wsol.gamenight.R.drawable.ic_game_minefield
    "find10" -> gr.wsol.gamenight.R.drawable.ic_game_find10
    "related" -> gr.wsol.gamenight.R.drawable.ic_game_related
    "whois" -> gr.wsol.gamenight.R.drawable.ic_game_whois
    "knowme" -> gr.wsol.gamenight.R.drawable.ic_game_knowme
    "dice" -> gr.wsol.gamenight.R.drawable.ic_game_dice
    "bottle" -> gr.wsol.gamenight.R.drawable.ic_game_bottle
    "bluffer" -> gr.wsol.gamenight.R.drawable.ic_game_bluffer
    "lie" -> gr.wsol.gamenight.R.drawable.ic_game_lie
    "witness" -> gr.wsol.gamenight.R.drawable.ic_game_witness
    "movies" -> gr.wsol.gamenight.R.drawable.ic_game_movies
    "closest" -> gr.wsol.gamenight.R.drawable.ic_game_closest
    "quiz" -> gr.wsol.gamenight.R.drawable.ic_game_quiz
    "number" -> gr.wsol.gamenight.R.drawable.ic_game_number
    "telephone" -> gr.wsol.gamenight.R.drawable.ic_game_telephone
    "song" -> gr.wsol.gamenight.R.drawable.ic_game_song
    "chain" -> gr.wsol.gamenight.R.drawable.ic_game_chain
    "mime" -> gr.wsol.gamenight.R.drawable.ic_game_mime
    "bomb" -> gr.wsol.gamenight.R.drawable.ic_game_bomb
    "balance" -> gr.wsol.gamenight.R.drawable.ic_game_balance
    "drone" -> gr.wsol.gamenight.R.drawable.ic_game_drone
    "defuse" -> gr.wsol.gamenight.R.drawable.ic_game_defuse
    "icequiz" -> gr.wsol.gamenight.R.drawable.ic_game_icequiz
    "safe" -> gr.wsol.gamenight.R.drawable.ic_game_safe
    "gate" -> gr.wsol.gamenight.R.drawable.ic_game_gate
    "drop" -> gr.wsol.gamenight.R.drawable.ic_game_drop
    "grid" -> gr.wsol.gamenight.R.drawable.ic_game_grid
    "orbit" -> gr.wsol.gamenight.R.drawable.ic_game_orbit
    "slice" -> gr.wsol.gamenight.R.drawable.ic_game_slice
    "tower" -> gr.wsol.gamenight.R.drawable.ic_game_tower
    "path" -> gr.wsol.gamenight.R.drawable.ic_game_path
    "sling" -> gr.wsol.gamenight.R.drawable.ic_game_sling
    "dual" -> gr.wsol.gamenight.R.drawable.ic_game_dual
    "ring" -> gr.wsol.gamenight.R.drawable.ic_game_ring
    "laser" -> gr.wsol.gamenight.R.drawable.ic_game_laser
    "spiral" -> gr.wsol.gamenight.R.drawable.ic_game_spiral
    "tavli" -> gr.wsol.gamenight.R.drawable.ic_game_tavli
    "core" -> gr.wsol.gamenight.R.drawable.ic_game_core
    "interro" -> gr.wsol.gamenight.R.drawable.ic_game_interro
    "drawai" -> gr.wsol.gamenight.R.drawable.ic_game_drawai
    "endless" -> gr.wsol.gamenight.R.drawable.ic_game_endless
    "court" -> gr.wsol.gamenight.R.drawable.ic_game_court
    "aiq" -> gr.wsol.gamenight.R.drawable.ic_game_aiq
    "oracle" -> gr.wsol.gamenight.R.drawable.ic_game_oracle
    else -> 0
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
    val icon: Int = gameIconOf(id),
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

/** All 54 games, in the order of the menu. The ones without [Game.play] show «Έρχεται σύντομα». */
object Games {
    val all: List<Game> = listOf(
        // 🕵️ Hidden roles
        Game("house", "🏚️", "Το στοιχειωμένο σπίτι", "The haunted house", Category.Roles, 4, 7, "Ζόμπι, θεραπευτές και μάγοι, με αφηγητή.", "Zombies, healers and mages, with a narrator.", C.ghost, { HouseGame(it) }),
        Game("alien", "👽", "Ο Εξωγήινος", "The Alien", Category.Roles, 3, 7, "Όλοι έχουν την ίδια λέξη. Σχεδόν όλοι.", "Everyone has the same word. Almost.", C.green, { AlienGameScreen(it) }),
        Game("saboteur", "🧨", "Ο Σαμποτέρ", "The Saboteur", Category.Roles, 3, 7, "Κοινή αποστολή, ένας τη χαλάει κρυφά.", "A shared mission, one wrecks it in secret.", C.red, { SaboteurGame(it) }),
        Game("fake", "🎨", "Ένας λείπει", "One is missing", Category.Roles, 3, 7, "Μια ζωγραφιά, ένας δεν ξέρει τι ζωγραφίζει.", "One drawing, one doesn't know what it is.", C.pink, { FakeArtistGame(it) }),
        Game("whisper", "🤫", "Ψίθυρος στο σκοτάδι", "Whisper in the dark", Category.Roles, 4, 7, "Ένας λέει μια μυστική λέξη μέσα στην κουβέντα.", "One slips a secret word into the chat.", C.ghost, { WhisperGame(it) }),
        Game("spy", "🕶️", "Ο κατάσκοπος στο σπίτι", "The spy at home", Category.Roles, 3, 7, "Όλοι ξέρουν το μέρος, εκτός από έναν.", "Everyone knows the place, except one.", C.cyan, { SpyGame(it) }),
        Game("lietower", "🗼", "Πύργος από ψέματα", "Tower of lies", Category.Roles, 3, 7, "Μια ιστορία με κρυφούς ψεύτες.", "A story with hidden liars.", C.pink, { LieTowerGame(it) }),
        // 🗣️ Words
        Game("taboo", "🗣️", "Πες το αλλιώς", "Say it otherwise", Category.Words, 2, 7, "Περίγραψε τη λέξη χωρίς τις 5 απαγορευμένες.", "Describe the word without the 5 banned ones.", C.amber, { TabooGame(it) }),
        Game("forehead", "🤳", "Στο μέτωπο", "On your forehead", Category.Words, 2, 7, "Το κινητό στο μέτωπο, η παρέα σου δίνει στοιχεία.", "Phone on your forehead, the group gives clues.", C.cyan, { ForeheadGame(it) }),
        Game("quads", "🟨", "Τετράδες", "Fours", Category.Words, 1, 7, "16 λέξεις, 4 ομάδες. Βρες τι τις δένει.", "16 words, 4 groups. Find what links them.", C.quad[0], { QuadsGame(it) }),
        Game("minefield", "💣", "Ναρκοπέδιο", "Minefield", Category.Words, 2, 7, "Αλυσίδα λέξεων, πρόσεχε τις νάρκες.", "A chain of words, mind the mines.", C.red, { MinefieldGame(it) }),
        Game("find10", "🔟", "Βρες 10", "Find 10", Category.Words, 2, 7, "Πες 10 σε 30 δευτερόλεπτα.", "Name 10 in 30 seconds.", C.lime, { Find10Game(it) }),
        Game("related", "🔗", "Σχετικές λέξεις", "Related words", Category.Words, 2, 7, "5″ ο καθένας για μια σχετική λέξη.", "5″ each for a related word.", C.cyan, { RelatedGame(it) }),
        // 🎉 Party
        Game("whois", "👉", "Ποιος από εμάς;", "Who of us?", Category.Party, 3, 7, "Ψηφίστε κρυφά ποιος θα το έκανε.", "Vote in secret who would do it.", C.amber, { WhoGame(it) }),
        Game("knowme", "🪞", "Πόσο με ξέρεις;", "How well do you know me?", Category.Party, 2, 7, "Μάντεψε τι απάντησε κρυφά ο φίλος σου.", "Guess what your friend answered in secret.", C.pink, { KnowMeGame(it) }),
        Game("dice", "😳", "Ζάρι της ντροπής", "Dice of shame", Category.Party, 2, 7, "Ρίξε το ζάρι, κάνε τη δοκιμασία.", "Roll the die, do the challenge.", C.red, { DiceGame(it) }),
        Game("bottle", "🍾", "Ρουλέτα μπουκαλιού", "Spin the bottle", Category.Party, 3, 7, "Αλήθεια ή θάρρος, πάντα ήπια.", "Truth or dare, always mild.", C.lime, { BottleGame(it) }),
        // 🤥 Bluff and trivia
        Game("bluffer", "🃏", "Ο Μπλοφατζής", "The Bluffer", Category.Bluff, 3, 7, "Γράψε μπλόφα, βρες την αλήθεια.", "Write a bluff, find the truth.", C.ghost, { BlufferGame(it) }),
        Game("lie", "🤥", "Ψέμα υπό πίεση", "Lie under pressure", Category.Bluff, 2, 7, "Αλήθεια ή ψέμα; Ο επόμενος κρίνει.", "Truth or lie? The next one judges.", C.pink, { LieGame(it) }),
        Game("witness", "🔍", "Ο Μάρτυρας", "The Witness", Category.Bluff, 2, 7, "4″ για μια σκηνή. Τι θυμάσαι;", "4″ to see a scene. What do you remember?", C.cyan, { WitnessGame(it) }),
        Game("movies", "🎬", "Μάντεψε την ταινία ή σειρά", "Guess the film or series", Category.Bluff, 2, 7, "Ο τίτλος σε emoji.", "The title in emoji.", C.amber, { MoviesGame(it) }),
        Game("closest", "📏", "Το κοντινότερο", "Closest wins", Category.Bluff, 2, 7, "Ερωτήσεις με αριθμό, και στοίχημα.", "Number questions, with a bet.", C.cyan, { ClosestGame(it) }),
        Game("quiz", "🛎️", "Κουίζ γειτονιάς", "Neighbourhood quiz", Category.Bluff, 2, 7, "Buzzer στις γωνίες της οθόνης.", "A buzzer in each corner of the screen.", C.amber, { QuizGame(it) }),
        // 🎭 Acting, singing, drawing
        Game("number", "🔢", "Το νούμερό σου", "Your number", Category.Stage, 3, 7, "Παίξε το σενάριο όπως το νούμερό σου.", "Act the scene like your number.", C.amber, { NumberGame(it) }),
        Game("telephone", "📞", "Χαλασμένο τηλέφωνο", "Broken telephone", Category.Stage, 3, 7, "Φράση, σχέδιο, φράση… τι έμεινε στο τέλος;", "Phrase, drawing, phrase… what's left at the end?", C.lime, { TelephoneGame(it) }),
        Game("song", "🎤", "Λέξη σε τραγούδι", "Word in a song", Category.Stage, 2, 7, "Τραγούδησε κομμάτι με αυτή τη λέξη.", "Sing a song with this word.", C.pink, { SongGame(it) }),
        Game("chain", "🔊", "Αλυσίδα ήχων", "Sound chain", Category.Stage, 3, 7, "Επανάλαβε τους ήχους και πρόσθεσε έναν.", "Repeat the sounds and add one.", C.lime, { ChainGame(it) }),
        Game("mime", "🙊", "Μίμηση με κανόνα", "Mime with a rule", Category.Stage, 2, 7, "Παντομίμα, αλλά με έναν περιορισμό.", "Mime, but with a twist.", C.pink, { MimeGame(it) }),
        // ⚡ Reflexes
        Game("bomb", "⏱️", "Βόμβα", "Bomb", Category.Reflex, 2, 7, "Πες λέξη και πάσαρε, πριν σκάσει.", "Say a word and pass, before it blows.", C.red, { BombGame(it) }),
        Game("balance", "⚖️", "Ισορροπία", "Balance", Category.Reflex, 2, 7, "Πέρνα το κινητό χωρίς να πέσει η μπίλια.", "Pass the phone without dropping the ball.", C.cyan, { BalanceGame(it) }),
        // 📱 Motion
        Game("drone", "🛸", "Το ανθρώπινο drone", "The human drone", Category.Motion, 2, 7, "Τυφλός πιλότος, η παρέα δίνει οδηγίες.", "A blind pilot, the group gives directions.", C.cyan, { DroneGame(it) }),
        Game("defuse", "🤲", "Αφόπλισε τη βόμβα", "Defuse the bomb", Category.Motion, 2, 7, "Αποστολή και πάσα χωρίς να γείρει.", "A task and a pass without tilting.", C.red, { DefuseGame(it) }),
        Game("icequiz", "🧊", "Trivia σε ισορροπία", "Balance trivia", Category.Motion, 1, 7, "Απαντάς γέρνοντας το κινητό.", "Answer by tilting the phone.", C.cyan, { IceTriviaGame(it) }),
        Game("safe", "🔐", "Χρηματοκιβώτιο", "The safe", Category.Motion, 1, 7, "Γύρνα το κινητό σαν τιμόνι, βρες τον συνδυασμό.", "Turn the phone like a wheel, find the code.", C.amber, { SafeGame(it) }),
        // 🎮 Arcade
        Game("gate", "🌈", "Χρωματική πύλη", "Colour gate", Category.Arcade, 1, 7, "Tap για χρώμα, πέρνα από το ίδιο.", "Tap to change colour, pass the same one.", C.pink, { ArcadeShell(Games.byId("gate")!!, it, listOf(t("Μια μπίλια, μπάρες που κατεβαίνουν. Πάτα για να αλλάξεις χρώμα και πέρνα μόνο από μπάρα του χρώματός σου.", "A ball, bars coming down. Tap to change colour and pass only through bars of your colour."), t("Τέλεια πατήματα στη σειρά (λίγο πριν φτάσει η μπάρα): ×2, ×4, ×8. Κάθε 50 μπαίνει νέο χρώμα.", "Perfect taps in a row (just before the bar arrives): ×2, ×4, ×8. A new colour every 50."))) { k, done -> GateRun(k, done) } }),
        Game("drop", "🕳️", "Βουτιά", "Dive", Category.Arcade, 1, 7, "Γείρε το κινητό, πέσε όσο πιο βαθιά.", "Tilt the phone, fall as deep as you can.", C.cyan, { ArcadeShell(Games.byId("drop")!!, it, listOf(t("Γείρε το κινητό αριστερά-δεξιά (ή πάτα στις άκρες) και πέσε από τα κενά του τούνελ.", "Tilt the phone left-right (or tap the sides) and drop through the gaps."), t("Αν σε φτάσει το ταβάνι ή ακουμπήσεις λέιζερ, τελειώνει. Τα κέρματα στη σειρά ανεβάζουν τον πολλαπλασιαστή.", "If the ceiling catches you or you touch a laser, it's over. Coins in a row raise the multiplier."))) { k, done -> DropRun(k, done) } }),
        Game("grid", "🔢", "Γρήγορο πλέγμα", "Quick grid", Category.Arcade, 1, 7, "Ακολούθα την εντολή, γρήγορα.", "Follow the rule, fast.", C.amber, { ArcadeShell(Games.byId("grid")!!, it, listOf(t("Πλέγμα 4×4 με αριθμούς. Η εντολή αλλάζει: ζυγοί, μονοί, ο μεγαλύτερος, ο μικρότερος, το κόκκινο.", "A 4×4 grid of numbers. The rule changes: even, odd, biggest, smallest, the red one."), t("Ξεκινάς με 3″· κάθε σωστό δίνει λίγο χρόνο, κάθε λάθος παίρνει ένα δευτερόλεπτο και μηδενίζει το combo.", "You start with 3″; each right tap adds a little, each wrong one takes a second and resets the combo."))) { k, done -> GridRun(k, done) } }),
        Game("orbit", "🪐", "Τροχιά", "Orbit", Category.Arcade, 1, 7, "Κράτα για κύκλο, άφησε για εκτόξευση.", "Hold to orbit, release to launch.", C.ghost, { ArcadeShell(Games.byId("orbit")!!, it, listOf(t("Κράτα πατημένα: το σημείο πιάνεται από την πιο κοντινή άγκυρα και κάνει κύκλους. Άφησε: εκτοξεύεται.", "Hold: the dot grabs the nearest anchor and circles it. Release: it launches."), t("Μη βγεις από τα πλάγια. Πέρνα ξυστά από τις νάρκες χωρίς να τις ακουμπήσεις για bonus.", "Don't leave the sides. Skim past mines without touching for a bonus."))) { k, done -> OrbitRun(k, done) } }),
        Game("slice", "✂️", "Κόψε και ταίριαξε", "Slice and match", Category.Arcade, 1, 7, "Κόκκινο με το βέλος, μπλε ανάποδα.", "Red with the arrow, blue against it.", C.red, { ArcadeShell(Games.byId("slice")!!, it, listOf(t("Σχήματα ανεβαίνουν με ένα βέλος. Σύρε πριν περάσουν τη γραμμή.", "Shapes rise with an arrow. Swipe before they cross the line."), t("Κόκκινο: σύρε προς το βέλος. Μπλε: ανάποδα! 3 λάθη και τέλος.", "Red: swipe with the arrow. Blue: the opposite! 3 mistakes and it's over."))) { k, done -> SliceRun(k, done) } }),
        Game("tower", "🏗️", "Πύργος", "Tower", Category.Arcade, 1, 7, "Ένα tap, ένας όροφος. Στο κέντρο.", "One tap, one floor. Dead centre.", C.lime, { ArcadeShell(Games.byId("tower")!!, it, listOf(t("Ένα μπλοκ πηγαινοέρχεται. Πάτα για να πέσει στον πύργο. Ό,τι περισσεύει κόβεται.", "A block slides back and forth. Tap to drop it on the tower. The overhang is cut off."), t("Τέλειο πάτημα: +3. Τρία τέλεια στη σειρά: η πλατφόρμα μεγαλώνει ξανά.", "A perfect drop: +3. Three in a row: the platform grows again."))) { k, done -> TowerRun(k, done) } }),
        Game("path", "✨", "Φωτεινή διαδρομή", "Light path", Category.Arcade, 1, 7, "Θυμήσου τη διαδρομή και σύρε το δάχτυλο.", "Remember the path and trace it.", C.green, { ArcadeShell(Games.byId("path")!!, it, listOf(t("Σε πλέγμα 5×5 ανάβει για λίγο μια διαδρομή. Σύρε το δάχτυλο πάνω της, από την αρχή ως το τέλος.", "A path lights up on a 5×5 grid for a moment. Trace it with your finger, start to end."), t("Κάθε φορά μακραίνει και φαίνεται λιγότερο. 3 ζωές.", "Each time it gets longer and shows for less. 3 lives."))) { k, done -> PathRun(k, done) } }),
        Game("sling", "🎯", "Σφεντόνα", "Slingshot", Category.Arcade, 1, 7, "Τράβα, ρίξε, αναπήδησε για combo.", "Pull, shoot, bounce for a combo.", C.amber, { ArcadeShell(Games.byId("sling")!!, it, listOf(t("Τράβα πίσω και ρίξε τη σφαίρα. Αναπηδά στους τοίχους.", "Pull back and shoot the ball. It bounces off the walls."), t("Κάθε αναπήδηση πριν το χτύπημα διπλασιάζει την αξία του. 60″, και κάθε καθαρό ταμπλό δίνει +5″.", "Each bounce before a hit doubles its value. 60″, and each clear board gives +5″."))) { k, done -> SlingRun(k, done) } }),
        Game("dual", "↔️", "Διπλή αποφυγή", "Dual dodge", Category.Arcade, 1, 7, "Δύο μπίλιες, δύο χέρια, ένα μυαλό.", "Two dots, two hands, one brain.", C.cyan, { ArcadeShell(Games.byId("dual")!!, it, listOf(t("Η οθόνη χωρίζεται στα δύο, με μια μπίλια σε κάθε μισό. Πάτα αριστερά ή δεξιά: αλλάζει λωρίδα η μπίλια αυτής της πλευράς.", "The screen splits in two, a dot in each half. Tap left or right: that side's dot changes lane."), t("Τα εμπόδια έρχονται με άλλο ρυθμό σε κάθε πλευρά. Ξυστά περάσματα ανεβάζουν τον πολλαπλασιαστή.", "Blocks come at a different pace on each side. Close calls raise the multiplier."))) { k, done -> DualRun(k, done) } }),
        Game("ring", "💫", "Αναπήδηση σε δακτύλιο", "Orbit bounce", Category.Arcade, 1, 7, "Γύρνα τον δακτύλιο, απόφυγε τις αιχμές.", "Spin the ring, dodge the spikes.", C.ghost, { ArcadeShell(Games.byId("ring")!!, it, listOf(t("Η μπίλια πάει από το κέντρο στον δακτύλιο που γυρίζει. Πάτα για να αλλάξει φορά.", "The ball goes from the centre to the spinning ring. Tap to reverse it."), t("Να χτυπάει μόνο τα ήσυχα κομμάτια, όχι τα κόκκινα. Τα κίτρινα δίνουν +3. Κάθε 10 αναπηδήσεις πάει πιο γρήγορα.", "It must hit only the calm parts, not the red ones. Yellow gives +3. Every 10 bounces it gets faster."))) { k, done -> RingRun(k, done) } }),
        Game("laser", "🟥", "Πλέγμα λέιζερ", "Laser grid", Category.Arcade, 1, 7, "Ένα κελί τη φορά, πριν ανάψει το λέιζερ.", "One cell at a time, before the laser fires.", C.red, { ArcadeShell(Games.byId("laser")!!, it, listOf(t("Ένας κύβος σε πλέγμα 3×3 (μετά 4×4). Σύρε διαγώνια για να πάει ένα κελί.", "A cube on a 3×3 grid (later 4×4). Swipe diagonally to move one cell."), t("Γραμμές και στήλες αναβοσβήνουν και μετά ανάβουν λέιζερ. Μάζεψε τους κίτρινους πυρήνες πριν σκάσουν: 3 ζωές.", "Rows and columns flash, then a laser fires. Grab the yellow cores before they blow: 3 lives."))) { k, done -> LaserRun(k, done) } }),
        Game("spiral", "🛡️", "Σπείρα", "Spiral", Category.Arcade, 1, 7, "Γύρνα την ασπίδα, σπάσε τους πυρήνες.", "Turn the shield, break the cores.", C.lime, { ArcadeShell(Games.byId("spiral")!!, it, listOf(t("Μια σφαίρα βγαίνει σε σπείρα από το κέντρο. Σύρε γύρω γύρω για να γυρίσεις την ασπίδα και να την αποκρούσεις.", "A ball spirals out from the centre. Drag around to turn the shield and send it back."), t("Απόκρουση με το κίτρινο κέντρο: Perfect ×3. Σπάσε τους πράσινους πυρήνες. Μετά έρχεται 2η και 3η σφαίρα.", "Hit with the yellow centre: Perfect ×3. Break the green cores. Then a 2nd and a 3rd ball come."))) { k, done -> SpiralRun(k, done) } }),
        // 🎲 Board
        Game("tavli", "🎲", "Τάβλι", "Backgammon", Category.Board, 2, 7, "Πόρτες, Πλακωτό, Φεύγα για δύο.", "Portes, Plakoto, Fevga for two.", C.amber, { TavliGame(it) }),
        // 🧠 Logic
        Game("core", "⚛️", "Ο Πυρήνας", "The Core", Category.Logic, 1, 7, "Κόμβοι, σήματα, κύκλοι. Χωρίς τύχη.", "Nodes, signals, cycles. No luck.", C.cyan, { CoreGame(it) }),
        // 🤖 With AI
        Game("interro", "🔦", "Ο Ανακριτής", "The interrogator", Category.Ai, 2, 7, "Ανακρίνετε το AI, ή σας ανακρίνει.", "Question the AI, or it questions you.", C.cyan, { InterroGame(it) }),
        Game("drawai", "🖍️", "Ζωγράφισε για το AI", "Draw for the AI", Category.Ai, 1, 7, "Ζωγραφίζεις, το AI μαντεύει.", "You draw, the AI guesses.", C.pink, { DrawAiGame(it) }),
        Game("endless", "♾️", "Ατελείωτες κάρτες", "Endless cards", Category.Ai, 1, 7, "Νέες κάρτες σε όποιο θέμα θέλετε.", "New cards on any theme you like.", C.lime, { EndlessGame(it) }),
        Game("court", "🧑‍⚖️", "Δικαστήριο", "Courtroom", Category.Ai, 2, 7, "Δύο μαλώνουν, το AI δικάζει.", "Two argue, the AI judges.", C.amber, { CourtGame(it) }),
        Game("aiq", "🤖", "Ποιος είναι το AI;", "Who is the AI?", Category.Ai, 2, 7, "Βρες την απάντηση που έγραψε το AI.", "Spot the answer the AI wrote.", C.ghost, { AiqGame(it) }),
        Game("oracle", "🔮", "Ο Μάντης", "The Oracle", Category.Ai, 1, 7, "Αστείες προβλέψεις για τον καθένα.", "Funny predictions for everyone.", C.pink, { OracleGame(it) }),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
