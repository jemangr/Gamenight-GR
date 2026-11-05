package gr.wsol.gamenight.games

import gr.wsol.gamenight.core.t

/** What the narrator says, with variants so it doesn't repeat itself. */
object HouseTexts {
    private fun pick(vararg o: Pair<String, String>) = o.random().let { t(it.first, it.second) }

    fun story() = t(
        "Ήρθαν τα φαντάσματα. Μαζευτήκαμε όλοι στο σπίτι και κλειδώσαμε. Όταν σβήνουν τα φώτα, κλείνουμε τα μάτια, γιατί αν τα ανοίξουμε θα μας φάνε την ψυχή. Μόνο που κάποιοι από εμάς έχουν ήδη δαγκωθεί…",
        "The ghosts have come. We all gathered in the house and locked the door. When the lights go out we close our eyes, because if we open them they'll eat our souls. Only some of us have already been bitten…",
    )

    fun lightsOut() = pick(
        "Σβήνουν τα φώτα. Όλοι κλείστε τα μάτια." to "The lights go out. Everyone close your eyes.",
        "Το ρεύμα κόπηκε. Κλείστε τα μάτια σας, τώρα." to "The power is out. Close your eyes, now.",
        "Τα φαντάσματα ξύνουν τα παράθυρα. Μάτια κλειστά." to "The ghosts scratch at the windows. Eyes shut.",
        "Κάτι τρίζει στη σκάλα. Κλείστε τα μάτια και μην κουνηθείτε." to "Something creaks on the stairs. Close your eyes and don't move.",
        "Ο αέρας σφυρίζει στην καμινάδα. Σβήνουν τα κεριά. Μάτια κλειστά." to "The wind whistles down the chimney. The candles die. Eyes shut.",
        "Κάποιος χτυπάει την πόρτα. Μην ανοίξετε. Κλείστε τα μάτια." to "Someone knocks on the door. Don't open. Close your eyes.",
    )

    fun wake(r: Role) = when (r) {
        Role.Granny -> pick("Γιαγιά, ξύπνα. Θα θυμιάσεις απόψε το σπίτι;" to "Granny, wake up. Will you cense the house tonight?", "Γιαγιά, άνοιξε τα μάτια. Το θυμιατό σε περιμένει." to "Granny, open your eyes. The censer is waiting.")
        Role.Zombie, Role.Leader -> pick("Ζόμπι, ανοίξτε τα μάτια. Ποιον δαγκώνετε απόψε;" to "Zombies, open your eyes. Whom do you bite tonight?", "Ζόμπι, ξυπνήστε. Πεινάτε. Διαλέξτε μαζί." to "Zombies, wake up. You're hungry. Choose together.", "Τα ζόμπι σέρνονται στον διάδρομο. Ζόμπι, διαλέξτε." to "The zombies drag along the hallway. Zombies, choose.")
        Role.Healer -> pick("Θεραπευτή, ξύπνα. Ποιον προστατεύεις;" to "Healer, wake up. Whom do you protect?", "Θεραπευτή, άνοιξε το βαλιτσάκι σου. Ποιον φυλάς απόψε;" to "Healer, open your bag. Whom do you guard tonight?")
        Role.Mage -> pick("Μάγε, ξύπνα. Κοίτα στη σφαίρα σου." to "Mage, wake up. Look into your orb.", "Μάγε, η σφαίρα φέγγει. Ποιον θέλεις να δεις;" to "Mage, the orb glows. Whom do you want to see?")
        Role.Exorcist -> pick("Εξορκιστή, ξύπνα. Θα ξορκίσεις κάποιον;" to "Exorcist, wake up. Will you exorcise someone?", "Εξορκιστή, πάρε τον σταυρό σου. Απόψε ή άλλη φορά;" to "Exorcist, take your cross. Tonight or another time?")
        Role.Medium -> pick("Μέντιουμ, ξύπνα. Ποιο φάντασμα θα ρωτήσεις;" to "Medium, wake up. Which ghost will you ask?", "Μέντιουμ, τα φαντάσματα ψιθυρίζουν. Άκου ένα." to "Medium, the ghosts whisper. Listen to one.")
        else -> ""
    }

    fun sleep(r: Role) = when (r) {
        Role.Zombie, Role.Leader -> t("Ζόμπι, κλείστε τα μάτια.", "Zombies, close your eyes.")
        else -> t("${r.title}, κλείσε τα μάτια.", "${r.title}, close your eyes.")
    }

    fun morning() = pick(
        "Ξημερώνει. Ο ήλιος μπαίνει από τις χαραμάδες." to "Dawn breaks. Sunlight comes through the cracks.",
        "Λαλάει ο κόκορας. Ήρθε το πρωί." to "The rooster crows. Morning has come.",
        "Η καμπάνα του χωριού χτυπάει. Ξημέρωσε." to "The village bell rings. It's morning.",
    ) + " " + t("Πρωινός έλεγχος: το κινητό περνάει από όλους.", "Morning check: the phone goes round.")

    fun lightsOn() = pick(
        "Ανάβουν τα φώτα. Ανοίξτε τα μάτια." to "The lights come on. Open your eyes.",
        "Ήρθε το ρεύμα. Κοιτάξτε γύρω σας." to "The power is back. Look around.",
    )

    fun eaten(p: String) = pick(
        "Απόψε τα φαντάσματα πήραν την ψυχή του $p." to "Tonight the ghosts took $p's soul.",
        "Το πρωί βρήκαμε τον $p ακίνητο στον καναπέ. Τον έφαγαν." to "In the morning we found $p still on the sofa. They got them.",
        "Η καρέκλα του $p είναι άδεια. Έγινε φάντασμα." to "$p's chair is empty. They became a ghost.",
    )

    fun nobody() = pick(
        "Απόψε δεν πείραξαν κανέναν. Κάποιος μας φύλαξε…" to "Tonight nobody was touched. Someone watched over us…",
        "Όλοι ζωντανοί! Τα φαντάσματα έφυγαν με άδεια χέρια." to "Everyone's alive! The ghosts left empty-handed.",
        "Ήσυχη νύχτα. Πολύ ήσυχη…" to "A quiet night. Too quiet…",
    )

    fun talk() = pick(
        "Ποιος είναι ζόμπι; Συζητήστε, και μετά κρυφή ψηφοφορία." to "Who's a zombie? Talk, then a secret vote.",
        "Κάποιος εδώ μέσα δεν είναι αυτό που λέει. Ψάξτε τον." to "Someone in here isn't what they say. Find them.",
    )

    fun out(p: String) = pick(
        "Η παρέα πέταξε τον $p έξω, στα φαντάσματα." to "The group threw $p out, to the ghosts.",
        "Ανοίγει η πόρτα, βγαίνει ο $p, κλειδώνουμε ξανά." to "The door opens, $p goes out, we lock again.",
    )

    fun tie() = t("Ισοψηφία. Απόψε δεν βγαίνει κανείς.", "A tie. Nobody goes out tonight.")
}
