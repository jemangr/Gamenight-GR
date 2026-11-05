package gr.wsol.gamenight.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.TimerText
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.rememberCountdown
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A colour with its name, for the scene and the questions. */
data class Hue(val el: String, val en: String, val argb: Long) { val name get() = t(el, en); val color get() = Color(argb) }

object Hues {
    val all = listOf(
        Hue("κόκκινο", "red", 0xFFE53935), Hue("μπλε", "blue", 0xFF1E63D6), Hue("πράσινο", "green", 0xFF2E9E4F), Hue("κίτρινο", "yellow", 0xFFF4C430),
        Hue("μωβ", "purple", 0xFF8E44AD), Hue("πορτοκαλί", "orange", 0xFFF57C00), Hue("ροζ", "pink", 0xFFF06292), Hue("μαύρο", "black", 0xFF222222),
    )
    val cats = listOf(Hue("μαύρη", "black", 0xFF222222), Hue("άσπρη", "white", 0xFFF2F2F2), Hue("πορτοκαλί", "orange", 0xFFE8892B), Hue("γκρι", "grey", 0xFF8A8A8A))
}

/**
 * «Ο Μάρτυρας»: a scene drawn by the code with random details, so there are endless scenes and
 * the questions are always right. Everything is in 0–1 coordinates.
 */
data class WScene(
    val place: Int, // 0 street, 1 beach, 2 park
    val night: Boolean,
    val clouds: Int,
    val birds: Int,
    val people: List<Person>,
    val cats: List<Pair<Float, Hue>>,
    val car: Hue?,
    val balloons: Int,
    val balloonHue: Hue,
    val umbrella: Hue,
    val litWindows: Int,
    val clock: Int, // 3, 6, 9 or 12
    val trees: Int,
) {
    data class Person(val x: Float, val shirt: Hue, val hat: Hue?)
}

data class WQuestion(val q: String, val options: List<String>, val answer: String)

object WitnessScenes {
    fun make(rnd: Random = Random): WScene {
        val shirts = Hues.all.shuffled(rnd)
        val nPeople = rnd.nextInt(2, 5)
        val hatAt = rnd.nextInt(nPeople)
        val xs = listOf(0.12f, 0.34f, 0.56f, 0.78f).shuffled(rnd).take(nPeople).sorted()
        val people = xs.mapIndexed { i, x -> WScene.Person(x, shirts[i], if (i == hatAt) shirts[(i + 4) % shirts.size] else null) }
        val nCats = rnd.nextInt(0, 5)
        val cats = (0 until nCats).map { 0.08f + it * 0.2f + rnd.nextFloat() * 0.06f to Hues.cats[rnd.nextInt(Hues.cats.size)] }
        val place = rnd.nextInt(3)
        return WScene(
            place = place, night = rnd.nextInt(3) == 0, clouds = rnd.nextInt(0, 4), birds = rnd.nextInt(0, 6), people = people, cats = cats,
            car = if (place == 0) Hues.all[rnd.nextInt(Hues.all.size)] else null,
            balloons = rnd.nextInt(1, 6), balloonHue = Hues.all[rnd.nextInt(Hues.all.size)], umbrella = Hues.all[rnd.nextInt(Hues.all.size)],
            litWindows = rnd.nextInt(0, 5), clock = listOf(3, 6, 9, 12)[rnd.nextInt(4)], trees = rnd.nextInt(1, 4),
        )
    }

    private fun count(q: String, right: Int, rnd: Random, max: Int = 6): WQuestion {
        val opts = ((0..max).filter { it != right }.shuffled(rnd).take(3) + right).shuffled(rnd).map { "$it" }
        return WQuestion(q, opts, "$right")
    }

    private fun hue(q: String, right: Hue, rnd: Random, pool: List<Hue> = Hues.all): WQuestion {
        val opts = (pool.filter { it != right }.shuffled(rnd).take(3) + right).shuffled(rnd).map { it.name }
        return WQuestion(q, opts, right.name)
    }

    /** Three questions about details of [s], each with 4 different options and the answer among them. */
    fun questions(s: WScene, rnd: Random = Random): List<WQuestion> {
        val all = mutableListOf(
            count(t("Πόσες γάτες υπήρχαν;", "How many cats were there?"), s.cats.size, rnd, 5),
            count(t("Πόσα μπαλόνια υπήρχαν;", "How many balloons were there?"), s.balloons, rnd),
            hue(t("Τι χρώμα ήταν τα μπαλόνια;", "What colour were the balloons?"), s.balloonHue, rnd),
            count(t("Πόσα πουλιά πετούσαν;", "How many birds were flying?"), s.birds, rnd),
            count(t("Πόσοι άνθρωποι υπήρχαν;", "How many people were there?"), s.people.size, rnd, 6),
            hue(t("Τι χρώμα ήταν η ομπρέλα;", "What colour was the umbrella?"), s.umbrella, rnd),
            hue(t("Τι χρώμα μπλούζα φορούσε αυτός με το καπέλο;", "What colour shirt did the one with the hat wear?"), s.people.first { it.hat != null }.shirt, rnd),
            hue(t("Τι χρώμα ήταν το καπέλο;", "What colour was the hat?"), s.people.first { it.hat != null }.hat!!, rnd),
            count(t("Πόσα παράθυρα είχαν φως;", "How many windows were lit?"), s.litWindows, rnd, 5),
            WQuestion(t("Τι ώρα έδειχνε το ρολόι;", "What time did the clock show?"), listOf("3", "6", "9", "12"), "${s.clock}"),
            WQuestion(t("Ήταν μέρα ή νύχτα;", "Was it day or night?"), listOf(t("Μέρα", "Day"), t("Νύχτα", "Night")), if (s.night) t("Νύχτα", "Night") else t("Μέρα", "Day")),
            count(t("Πόσα δέντρα υπήρχαν;", "How many trees were there?"), s.trees, rnd, 5),
        )
        s.car?.let { all += hue(t("Τι χρώμα ήταν το αυτοκίνητο;", "What colour was the car?"), it, rnd) }
        if (s.cats.isNotEmpty()) all += hue(t("Τι χρώμα ήταν η πρώτη γάτα από αριστερά;", "What colour was the first cat from the left?"), s.cats.minBy { it.first }.second, rnd, Hues.cats)
        return all.shuffled(rnd).take(3)
    }
}

@Composable
fun WitnessScene(s: WScene, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)).semantics { contentDescription = t("Η σκηνή", "The scene") }) {
        val w = size.width; val h = size.height
        val sky = if (s.night) Color(0xFF16204A) else Color(0xFF9FD3F5)
        drawRect(sky, size = Size(w, h * 0.62f))
        // Sun or moon
        if (s.night) { drawCircle(Color(0xFFF5F0C8), w * 0.07f, Offset(w * 0.85f, h * 0.1f)); drawCircle(sky, w * 0.06f, Offset(w * 0.82f, h * 0.09f)) }
        else drawCircle(Color(0xFFFFD54F), w * 0.07f, Offset(w * 0.85f, h * 0.1f))
        repeat(s.clouds) { i -> val cx = w * (0.15f + i * 0.25f); val cy = h * (0.08f + (i % 2) * 0.05f); listOf(-0.04f, 0f, 0.04f).forEach { dx -> drawCircle(Color.White.copy(alpha = if (s.night) 0.35f else 0.9f), w * 0.04f, Offset(cx + dx * w, cy)) } }
        repeat(s.birds) { i -> val bx = w * (0.1f + i * 0.13f); val by = h * (0.2f + (i % 3) * 0.04f); val p = Path().apply { moveTo(bx - w * 0.025f, by); quadraticTo(bx - w * 0.012f, by - h * 0.015f, bx, by); quadraticTo(bx + w * 0.012f, by - h * 0.015f, bx + w * 0.025f, by) }; drawPath(p, Color(0xFF333333), style = Stroke(w * 0.006f)) }
        // Ground
        val ground = when (s.place) { 1 -> Color(0xFFF2D59B); 2 -> Color(0xFF7CC56B); else -> Color(0xFFB9B9B9) }
        if (s.place == 1) drawRect(Color(0xFF3C8DD9), Offset(0f, h * 0.52f), Size(w, h * 0.1f))
        drawRect(ground, Offset(0f, h * 0.62f), Size(w, h * 0.38f))
        // House with windows and a clock
        val hx = w * 0.05f; val hy = h * 0.3f; val hw = w * 0.4f; val hh = h * 0.32f
        drawRect(Color(0xFFE9D8C4), Offset(hx, hy), Size(hw, hh))
        drawPath(Path().apply { moveTo(hx - w * 0.02f, hy); lineTo(hx + hw / 2, hy - h * 0.1f); lineTo(hx + hw + w * 0.02f, hy); close() }, Color(0xFFB5523B))
        for (i in 0 until 4) { val wx = hx + w * 0.04f + (i % 2) * w * 0.2f; val wy = hy + h * 0.04f + (i / 2) * h * 0.11f; drawRect(if (i < s.litWindows) Color(0xFFFFE082) else Color(0xFF4A5568), Offset(wx, wy), Size(w * 0.12f, h * 0.07f)) }
        val cc = Offset(hx + hw / 2, hy - h * 0.035f); drawCircle(Color.White, w * 0.035f, cc); drawCircle(Color.Black, w * 0.035f, cc, style = Stroke(w * 0.004f))
        drawLine(Color.Black, cc, cc + Offset(0f, -w * 0.028f), w * 0.004f) // minute hand at 12
        val a = Math.toRadians((s.clock % 12) * 30.0 - 90); drawLine(Color.Black, cc, cc + Offset((cos(a) * w * 0.02f).toFloat(), (sin(a) * w * 0.02f).toFloat()), w * 0.006f)
        // Trees
        repeat(s.trees) { i -> val tx = w * (0.55f + i * 0.14f); drawRect(Color(0xFF7A4E2D), Offset(tx - w * 0.012f, h * 0.48f), Size(w * 0.024f, h * 0.14f)); drawCircle(Color(0xFF2F8F3A), w * 0.06f, Offset(tx, h * 0.46f)) }
        // Car
        s.car?.let { c -> drawRoundRect(c.color, Offset(w * 0.55f, h * 0.66f), Size(w * 0.32f, h * 0.07f), androidx.compose.ui.geometry.CornerRadius(w * 0.02f)); drawRect(c.color, Offset(w * 0.61f, h * 0.62f), Size(w * 0.17f, h * 0.05f)); listOf(0.61f, 0.81f).forEach { drawCircle(Color.Black, w * 0.03f, Offset(w * it, h * 0.735f)) } }
        // Umbrella
        val ux = w * 0.9f; val uy = h * 0.7f
        drawArc(s.umbrella.color, 180f, 180f, true, Offset(ux - w * 0.08f, uy - h * 0.06f), Size(w * 0.16f, h * 0.12f)); drawLine(Color(0xFF333333), Offset(ux, uy), Offset(ux, uy + h * 0.12f), w * 0.006f)
        // People
        s.people.forEach { p -> person(p, w, h) }
        // Balloons, held by the first person
        s.people.firstOrNull()?.let { p -> repeat(s.balloons) { i -> val bx = w * p.x + (i - s.balloons / 2f) * w * 0.035f; val by = h * 0.58f - (i % 2) * h * 0.04f; drawLine(Color(0xFF555555), Offset(bx, by + h * 0.03f), Offset(w * p.x + w * 0.03f, h * 0.76f), w * 0.002f); drawOval(s.balloonHue.color, Offset(bx - w * 0.022f, by - h * 0.03f), Size(w * 0.044f, h * 0.06f)) } }
        // Cats
        s.cats.forEach { (x, c) -> cat(x, c, w, h) }
    }
}

private fun DrawScope.person(p: WScene.Person, w: Float, h: Float) {
    val x = w * p.x; val top = h * 0.7f
    drawCircle(Color(0xFFF1C27D), w * 0.03f, Offset(x, top))
    p.hat?.let { drawRect(it.color, Offset(x - w * 0.035f, top - h * 0.035f), Size(w * 0.07f, h * 0.012f)); drawRect(it.color, Offset(x - w * 0.022f, top - h * 0.06f), Size(w * 0.044f, h * 0.03f)) }
    drawRoundRect(p.shirt.color, Offset(x - w * 0.035f, top + h * 0.025f), Size(w * 0.07f, h * 0.09f), androidx.compose.ui.geometry.CornerRadius(w * 0.015f))
    drawRect(Color(0xFF34495E), Offset(x - w * 0.03f, top + h * 0.115f), Size(w * 0.025f, h * 0.08f)); drawRect(Color(0xFF34495E), Offset(x + w * 0.005f, top + h * 0.115f), Size(w * 0.025f, h * 0.08f))
}

private fun DrawScope.cat(x0: Float, c: Hue, w: Float, h: Float) {
    val x = w * x0; val y = h * 0.93f
    drawOval(c.color, Offset(x - w * 0.035f, y - h * 0.025f), Size(w * 0.07f, h * 0.04f))
    drawCircle(c.color, w * 0.02f, Offset(x + w * 0.04f, y - h * 0.03f))
    drawPath(Path().apply { moveTo(x + w * 0.025f, y - h * 0.04f); lineTo(x + w * 0.03f, y - h * 0.065f); lineTo(x + w * 0.04f, y - h * 0.045f); close(); moveTo(x + w * 0.045f, y - h * 0.045f); lineTo(x + w * 0.055f, y - h * 0.065f); lineTo(x + w * 0.058f, y - h * 0.035f); close() }, c.color)
    drawLine(c.color, Offset(x - w * 0.035f, y - h * 0.005f), Offset(x - w * 0.06f, y - h * 0.04f), w * 0.008f)
    if (c.argb == 0xFFF2F2F2) drawOval(Color(0xFF999999), Offset(x - w * 0.035f, y - h * 0.025f), Size(w * 0.07f, h * 0.04f), style = Stroke(w * 0.002f))
}

private enum class W { Intro, Pass, Look, Tell, Ask, Result, Over }

@Composable
fun WitnessGame(host: GameHost) {
    val game = Games.byId("witness")!!
    val n = Store.night
    val players = remember { n.names }
    var phase by remember { mutableStateOf(W.Intro) }
    var round by remember { mutableIntStateOf(0) }
    var scene by remember { mutableStateOf(WitnessScenes.make()) }
    var qs by remember { mutableStateOf(listOf<WQuestion>()) }
    val answers = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val tally = remember { Tally() }
    val witness = players[round % players.size]
    val asked = players[(round + 1) % players.size]

    when (phase) {
        W.Intro -> Intro(game, host, listOf(
            t("Ο μάρτυρας βλέπει μια σκηνή για 4″ μόνο. Μετά η οθόνη κλειδώνει.", "The witness sees a scene for just 4″. Then the screen locks."),
            t("Περιγράφει δυνατά στον επόμενο ό,τι θυμάται: χρώματα, πόσα, πού.", "They describe out loud to the next player all they remember: colours, how many, where."),
            t("Ο επόμενος απαντά σε 3 ερωτήσεις λεπτομερειών, χωρίς να έχει δει τίποτα.", "The next one answers 3 detail questions without having seen anything."),
            t("+1 για κάθε σωστή απάντηση, και στον μάρτυρα και σε αυτόν που απάντησε.", "+1 for each right answer, to the witness and to the one who answered."),
            t("Οι σκηνές φτιάχνονται από την εφαρμογή, κάθε φορά διαφορετικές.", "The app makes the scenes, different every time."),
        ), players.size >= 2) { round = 0; phase = W.Pass }

        W.Pass -> { QuitGuard(host); PassTo(witness, Color(n.colorOf(n.sideOf(witness))), note = t("Θα δεις τη σκηνή για 4″. Κοίτα καλά!", "You'll see the scene for 4″. Look carefully!"), button = t("Δείξε τη σκηνή", "Show the scene")) {
            scene = WitnessScenes.make(); qs = WitnessScenes.questions(scene); answers.clear(); phase = W.Look
        } }

        W.Look -> {
            QuitGuard(host)
            val left = rememberCountdown(4, running = true, key = round, tickLast = 0) { phase = W.Tell }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${(left + 999) / 1000}″", style = Type.title, color = C.cyan)
                WitnessScene(scene)
            }
        }

        W.Tell -> { QuitGuard(host); Page(t("🔒 Κλείδωσε", "🔒 Locked"), onBack = null, bottom = {
            BigButton(t("Το έδωσα στον/στην $asked", "Gave it to $asked"), { phase = W.Ask }, color = C.cyan)
        }) {
            Text("🗣️", style = Type.huge)
            Body(t("$witness, περίγραψε δυνατά στον/στην $asked ό,τι θυμάσαι. Μετά δώσ' του/της το κινητό.", "$witness, describe out loud to $asked all you remember. Then give them the phone."), color = C.text)
        } }

        W.Ask -> {
            QuitGuard(host)
            val i = answers.size
            val q = qs[i]
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("$asked · ερώτηση ${i + 1} από 3", "$asked · question ${i + 1} of 3"), style = Type.label, color = C.dim)
                Text(q.q, style = Type.title, color = C.text)
                q.options.forEach { o -> GhostButton(o, { answers.add(o); Sfx.tap(); if (answers.size >= qs.size) phase = W.Result }, Modifier.fillMaxWidth()) }
            }
        }

        W.Result -> {
            QuitGuard(host)
            val right = qs.indices.count { answers.getOrNull(it) == qs[it].answer }
            remember(round) { if (right > 0) { tally.add(host, listOf(witness), right); tally.add(host, listOf(asked), right) }; if (right == 3) Sfx.win() else Sfx.end() }
            Page(t("$right από 3 σωστές", "$right of 3 right"), onBack = null, bottom = {
                BigButton(if (round + 1 < players.size) t("Επόμενος μάρτυρας", "Next witness") else t("Αποτελέσματα", "Results"), {
                    host.log(t("Μάρτυρας: $witness → $asked, $right/3", "Witness: $witness → $asked, $right/3"))
                    if (round + 1 < players.size) { round++; phase = W.Pass } else phase = W.Over
                }, color = C.cyan)
            }) {
                WitnessScene(scene)
                qs.forEachIndexed { k, q ->
                    val ok = answers.getOrNull(k) == q.answer
                    Text((if (ok) "✓ " else "✗ ") + q.q, style = Type.body, color = if (ok) C.green else C.red)
                    Text(t("Σωστό: ${q.answer}", "Right: ${q.answer}") + if (!ok) t(" · είπε: ${answers.getOrNull(k)}", " · said: ${answers.getOrNull(k)}") else "", style = Type.small, color = C.muted)
                }
            }
        }

        W.Over -> GameOver(game, host, tally) { tally.bySide.clear(); phase = W.Intro }
    }
}
