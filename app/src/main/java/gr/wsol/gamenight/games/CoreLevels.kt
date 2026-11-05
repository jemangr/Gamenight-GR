package gr.wsol.gamenight.games

import gr.wsol.gamenight.games.Node.ADD
import gr.wsol.gamenight.games.Node.DLY
import gr.wsol.gamenight.games.Node.INV
import gr.wsol.gamenight.games.Node.SHF
import gr.wsol.gamenight.games.Node.SPL

private fun i(r: Int): Expr = Expr.In(r)
private fun inv(e: Expr): Expr = Expr.Inv(e)
private fun dbl(e: Expr): Expr = Expr.Dbl(e)
private fun add(a: Expr, b: Expr): Expr = Expr.Add(a, b)

/** «r,c,NODE;r,c,NODE» → cell → node, for a level [w] cells wide. */
private fun ref(w: Int, s: String): Map<Int, Node> = if (s.isBlank() || s.startsWith("REF")) emptyMap() else s.split(';').associate { p -> val (r, c, n) = p.split(','); (r.trim().toInt() * w + c.trim().toInt()) to Node.valueOf(n.trim()) }
private fun cells(w: Int, s: String): Set<Int> = if (s.isBlank()) emptySet() else s.split(';').map { p -> val (r, c) = p.split(','); r.trim().toInt() * w + c.trim().toInt() }.toSet()

/**
 * The 40 levels in 4 chapters, each with a new node or rule. Every level has a known solution
 * ([CoreLevel.reference], found by search: the fewest nodes, then the fewest cycles) that sets its par;
 * a test checks it on the main inputs and the 3 stress tests.
 */
object CoreLevels {
    val chapters = listOf(
        "Αντιστροφή και ολίσθηση" to "Invert and shift",
        "Καθυστέρηση και πρόσθεση" to "Delay and add",
        "Διακλάδωση" to "Branching",
        "Ο Πυρήνας" to "The Core",
    )

    private fun L(id: Int, ch: Int, el: String, en: String, inputs: Map<Int, Int>, targets: Map<Int, Expr>, palette: Map<Node, Int>, ref: String, w: Int = 5, h: Int = 5, blocked: String = "", hintEl: String = "", hintEn: String = "") =
        CoreLevel(id, ch, el, en, w, h, inputs, targets, palette, cells(w, blocked), ref(w, ref), hintEl, hintEn)

    val all: List<CoreLevel> = listOf(
        // ---- 1. Invert and shift ----
        L(1, 0, "Πρώτο σήμα", "First signal", mapOf(2 to 0), mapOf(2 to inv(i(2))), mapOf(INV to 1), "2,0,INV", hintEl = "Βάλε έναν αντιστροφέα πάνω στη γραμμή του σήματος και πάτα ▶.", hintEn = "Put an inverter on the signal's row and press ▶."),
        L(2, 0, "Διπλάσιο", "Double", mapOf(2 to 0), mapOf(2 to dbl(i(2))), mapOf(SHF to 1), "2,0,SHF", hintEl = "Ο ολισθητής διπλασιάζει (και κρατά μόνο το τελευταίο ψηφίο).", hintEn = "The shifter doubles (and keeps only the last digit)."),
        L(3, 0, "Τετραπλάσιο", "Four times", mapOf(2 to 0), mapOf(2 to dbl(dbl(i(2)))), mapOf(SHF to 2), "2,0,SHF;2,1,SHF"),
        L(4, 0, "Η σειρά μετράει", "Order matters", mapOf(2 to 0), mapOf(2 to inv(dbl(i(2)))), mapOf(INV to 1, SHF to 1), "2,0,SHF;2,1,INV", hintEl = "Πρώτα διπλασίασε, μετά αντίστρεψε. Το σήμα πάει από αριστερά προς τα δεξιά.", hintEn = "Double first, then invert. Signals flow left to right."),
        L(5, 0, "Ανάποδα", "The other way", mapOf(2 to 0), mapOf(2 to dbl(inv(i(2)))), mapOf(INV to 1, SHF to 1), "2,0,INV;2,1,SHF"),
        L(6, 0, "Δύο γραμμές", "Two rows", mapOf(1 to 0, 3 to 0), mapOf(1 to inv(i(1)), 3 to dbl(i(3))), mapOf(INV to 1, SHF to 1), "1,0,INV;3,0,SHF"),
        L(7, 0, "Οκτώ φορές", "Eight times", mapOf(2 to 0), mapOf(2 to dbl(dbl(dbl(i(2))))), mapOf(SHF to 3), "2,0,SHF;2,1,SHF;2,2,SHF"),
        L(8, 0, "Σάντουιτς", "Sandwich", mapOf(2 to 0), mapOf(2 to inv(dbl(inv(i(2))))), mapOf(INV to 2, SHF to 1), "2,0,INV;2,1,SHF;2,2,INV"),
        L(9, 0, "Τρεις δρόμοι", "Three roads", mapOf(0 to 0, 2 to 0, 4 to 0), mapOf(0 to inv(i(0)), 2 to inv(i(2)), 4 to dbl(i(4))), mapOf(INV to 2, SHF to 1), "0,0,INV;2,0,INV;4,0,SHF"),
        L(10, 0, "Στενός διάδρομος", "Narrow corridor", mapOf(2 to 0), mapOf(2 to inv(dbl(dbl(i(2))))), mapOf(INV to 1, SHF to 2), "2,0,SHF;2,2,SHF;2,4,INV", blocked = "2,1;2,3", hintEl = "Στα γκρι κελιά δεν μπαίνει κόμβος· το σήμα περνά κανονικά.", hintEn = "No node can go on grey cells; signals still pass."),
        // ---- 2. Delay and add ----
        L(11, 1, "Πρόσθεση", "Addition", mapOf(1 to 0, 2 to 0), mapOf(2 to add(i(2), i(1))), mapOf(ADD to 1), "2,0,ADD", hintEl = "Ο αθροιστής προσθέτει το σήμα που βρίσκεται ακριβώς από πάνω του την ίδια στιγμή. Προσοχή: ένα κελί δίπλα σε κόμβο καθυστερεί το σήμα που περνά.", hintEn = "The adder adds the signal right above it at the same moment. Careful: a cell next to a node delays a passing signal."),
        L(12, 1, "Αργοπορημένος", "Running late", mapOf(1 to 0, 2 to 1), mapOf(2 to add(i(2), i(1))), mapOf(ADD to 1, DLY to 2), "2,0,ADD", hintEl = "Το B ξεκινά έναν κύκλο αργότερα. Κράτα το A να το περιμένει.", hintEn = "B starts a cycle later. Make A wait for it."),
        L(13, 1, "Ανάποδο άθροισμα", "Inverted sum", mapOf(1 to 0, 2 to 0), mapOf(2 to inv(add(i(2), i(1)))), mapOf(ADD to 1, INV to 1), "2,0,ADD;2,1,INV"),
        L(14, 1, "Διπλό συν", "Double plus", mapOf(1 to 0, 2 to 0), mapOf(2 to add(dbl(i(1)), i(2))), mapOf(ADD to 1, SHF to 1), "1,0,SHF;2,0,ADD"),
        L(15, 1, "Πολύ αργά", "Very late", mapOf(1 to 2, 2 to 0), mapOf(2 to add(i(2), i(1))), mapOf(ADD to 1, DLY to 3), "2,2,ADD;3,0,DLY;3,1,DLY"),
        L(16, 1, "Τρία μαζί", "Three together", mapOf(1 to 0, 2 to 0, 3 to 0), mapOf(3 to add(add(i(1), i(2)), i(3))), mapOf(ADD to 2), "2,0,ADD;3,0,ADD"),
        L(17, 1, "Δύο αντιστροφές", "Two inversions", mapOf(1 to 0, 2 to 0), mapOf(2 to add(inv(i(1)), inv(i(2)))), mapOf(ADD to 1, INV to 2), "1,0,INV;2,0,INV;2,1,ADD"),
        L(18, 1, "Διπλό άθροισμα", "Double sum", mapOf(1 to 0, 2 to 0), mapOf(2 to dbl(add(i(1), i(2)))), mapOf(ADD to 1, SHF to 1), "2,0,ADD;2,1,SHF"),
        L(19, 1, "Κλειστή αρχή", "Closed start", mapOf(0 to 0, 1 to 0), mapOf(1 to add(i(0), i(1))), mapOf(ADD to 1, DLY to 1), "1,3,ADD", blocked = "1,0;1,1;1,2"),
        L(20, 1, "Διπλό και αργό", "Double and late", mapOf(1 to 0, 2 to 1), mapOf(2 to add(i(1), dbl(i(2)))), mapOf(ADD to 1, SHF to 1, DLY to 2), "2,0,SHF;2,1,ADD"),
        // ---- 3. Branching ----
        L(21, 2, "Αντίγραφο", "Copy", mapOf(2 to 0), mapOf(2 to i(2), 3 to i(2)), mapOf(SPL to 1), "2,0,SPL", hintEl = "Η διακλάδωση στέλνει ένα αντίγραφο στη γραμμή από κάτω· το αρχικό συνεχίζει.", hintEn = "The splitter sends a copy to the row below; the original carries on."),
        L(22, 2, "Αντίγραφο ανάποδα", "Inverted copy", mapOf(2 to 0), mapOf(2 to i(2), 3 to inv(i(2))), mapOf(SPL to 1, INV to 1), "2,0,SPL;3,1,INV"),
        L(23, 2, "Τρία από ένα", "Three from one", mapOf(1 to 0), mapOf(1 to i(1), 2 to dbl(i(1)), 3 to inv(i(1))), mapOf(SPL to 2, SHF to 1, INV to 1), "1,0,SPL;2,1,SPL;2,2,SHF;3,2,INV"),
        L(24, 2, "Χωρίς ολισθητή", "No shifter", mapOf(2 to 0), mapOf(3 to dbl(i(2))), mapOf(SPL to 1, ADD to 1), "2,0,SPL;3,1,ADD", hintEl = "Χωρίς ολισθητή: πώς αλλιώς γίνεται το διπλάσιο;", hintEn = "No shifter: how else can you double?"),
        L(25, 2, "Από ψηλά", "From above", mapOf(0 to 0, 2 to 0), mapOf(2 to add(i(0), i(2))), mapOf(SPL to 1, ADD to 1, DLY to 1), "0,0,SPL;2,1,ADD"),
        L(26, 2, "Δύο κλαδιά", "Two branches", mapOf(2 to 0), mapOf(3 to inv(i(2)), 4 to dbl(i(2))), mapOf(SPL to 2, INV to 1, SHF to 1), "2,0,SPL;3,1,SPL;3,2,INV;4,2,SHF"),
        L(27, 2, "Τρεις ίδιοι", "Three the same", mapOf(2 to 0), mapOf(2 to i(2), 3 to i(2), 4 to i(2)), mapOf(SPL to 2), "2,0,SPL;3,1,SPL"),
        L(28, 2, "Μακρύ ταξίδι", "Long trip", mapOf(1 to 0, 4 to 0), mapOf(4 to add(i(1), i(4))), mapOf(SPL to 2, ADD to 1, DLY to 1), "1,0,SPL;2,1,SPL;4,2,ADD"),
        L(29, 2, "Διπλό από ψηλά", "Double from above", mapOf(1 to 0, 3 to 0), mapOf(3 to dbl(add(i(1), i(3)))), mapOf(SPL to 1, ADD to 1, SHF to 1, DLY to 1), "1,0,SPL;3,1,ADD;3,2,SHF"),
        L(30, 2, "Μοίρασμα", "Sharing", mapOf(2 to 0, 4 to 0), mapOf(2 to inv(i(2)), 4 to add(i(2), i(4))), mapOf(SPL to 1, ADD to 1, INV to 1, DLY to 1), "2,0,SPL;2,1,INV;4,1,ADD"),
        // ---- 4. The Core (6×6) ----
        L(31, 3, "Καταρράκτης", "Cascade", mapOf(1 to 0, 3 to 0, 5 to 0), mapOf(5 to add(add(i(1), i(3)), i(5))), mapOf(SPL to 2, ADD to 2, DLY to 1), "1,0,SPL;3,1,ADD;3,2,SPL;5,3,ADD", w = 6, h = 6),
        L(32, 3, "Ανάποδο διπλό", "Inverted double", mapOf(2 to 1, 3 to 0), mapOf(3 to inv(add(i(2), dbl(i(3))))), mapOf(ADD to 1, SHF to 1, INV to 1, DLY to 2), "3,0,SHF;3,3,ADD;3,4,INV;4,1,DLY;4,2,DLY", w = 6, h = 6),
        L(33, 3, "Δίδυμα", "Twins", mapOf(3 to 0, 4 to 0), mapOf(4 to add(i(3), i(4)), 5 to add(i(3), i(4))), mapOf(ADD to 1, SPL to 1), "4,0,ADD;4,1,SPL", w = 6, h = 6),
        L(34, 3, "Λαβύρινθος", "Maze", mapOf(3 to 0), mapOf(3 to inv(dbl(inv(dbl(i(3)))))), mapOf(INV to 2, SHF to 2), "3,1,SHF;3,2,INV;3,4,SHF;3,5,INV", w = 6, h = 6, blocked = "3,0;3,3"),
        L(35, 3, "Παρεμβολή", "Interference", mapOf(1 to 2, 2 to 0), mapOf(2 to add(i(1), i(2))), mapOf(ADD to 1, DLY to 1, SHF to 1), "2,2,ADD;3,0,SHF;3,1,DLY", w = 6, h = 6, hintEl = "Μόνο μία καθυστέρηση… αλλά κάθε κόμβος καθυστερεί τα σήματα δίπλα του, ακόμα κι αν δεν πατά πάνω τους.", hintEn = "Only one delay… but every node delays the signals next to it, even if it isn't on their path."),
        L(36, 3, "Μείγμα", "Mix", mapOf(2 to 0, 3 to 0), mapOf(3 to add(inv(i(2)), dbl(i(3)))), mapOf(INV to 1, SHF to 1, ADD to 1, DLY to 1), "2,0,INV;3,0,SHF;3,1,ADD", w = 6, h = 6),
        L(37, 3, "Σκάλα", "Staircase", mapOf(3 to 0), mapOf(4 to i(3), 5 to inv(i(3))), mapOf(SPL to 2, INV to 1), "3,0,SPL;4,1,SPL;5,2,INV", w = 6, h = 6),
        L(38, 3, "Κλειστός δρόμος", "Road closed", mapOf(2 to 0, 5 to 0), mapOf(5 to add(i(2), i(5))), mapOf(SPL to 2, ADD to 1, DLY to 1), "2,0,SPL;3,2,SPL;5,3,ADD", w = 6, h = 6, blocked = "3,0;3,1;4,0;4,1"),
        L(39, 3, "Τριπλό ανάποδο", "Triple inverted", mapOf(1 to 0, 2 to 0, 3 to 0), mapOf(3 to inv(add(add(i(1), i(2)), i(3)))), mapOf(ADD to 2, INV to 1), "2,0,ADD;3,0,ADD;3,1,INV", w = 6, h = 6),
        L(40, 3, "Η καρδιά του πυρήνα", "Heart of the core", mapOf(2 to 1, 4 to 0), mapOf(4 to dbl(add(i(2), i(4)))), mapOf(SPL to 1, ADD to 1, SHF to 1, DLY to 2), "2,0,SPL;3,0,DLY;4,1,ADD;4,2,SHF", w = 6, h = 6),
    )

    fun byId(id: Int) = all.firstOrNull { it.id == id }
}
