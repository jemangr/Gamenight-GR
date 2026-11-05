package gr.wsol.gamenight.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Label
import gr.wsol.gamenight.ui.Page
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import kotlinx.coroutines.delay

private val NODE_COLOR = mapOf(Node.INV to Color(0xFFFF5FA2), Node.SHF to Color(0xFFFFB020), Node.DLY to Color(0xFF8A82AC), Node.ADD to Color(0xFF39FF88), Node.SPL to Color(0xFF22D3EE))
private val ROW_COLOR = listOf(Color(0xFFFFB020), Color(0xFF22D3EE), Color(0xFFFF5FA2))

/** Saved progress: best score per level and the player's last layout. */
object CoreSave {
    fun solved(id: Int) = Store.best("core.$id.solved") > 0
    fun best(id: Int): CoreScore? = if (!solved(id)) null else CoreScore(true, Store.best("core.$id.nodes"), Store.best("core.$id.cycles"), Store.best("core.$id.robust"))
    fun unlocked(id: Int) = id == 1 || solved(id - 1) || solved(id)

    /** Keeps the best: fewer nodes, then fewer cycles, then more robust. */
    fun save(id: Int, s: CoreScore, nodes: Map<Int, Node>) {
        if (!s.solved) return
        val b = best(id)
        val better = b == null || s.nodes < b.nodes || (s.nodes == b.nodes && (s.cycles < b.cycles || (s.cycles == b.cycles && s.robust > b.robust)))
        if (!better) return
        Store.forceBest("core.$id.solved", 1); Store.forceBest("core.$id.nodes", s.nodes); Store.forceBest("core.$id.cycles", s.cycles); Store.forceBest("core.$id.robust", s.robust)
        Store.putText("core.$id.layout", CoreRules.encode(nodes))
    }
    fun layout(id: Int) = CoreRules.decode(Store.text("core.$id.layout"))
}

private enum class CP { Levels, Pass, Play, Podium }

@Composable
fun CoreGame(host: GameHost) {
    val game = Games.byId("core")!!
    val n = Store.night
    var phase by remember { mutableStateOf(CP.Levels) }
    var level by remember { mutableStateOf(CoreLevels.all[0]) }
    var versus by remember { mutableStateOf(false) }
    val runs = remember { mutableStateMapOf<String, CoreScore>() }
    val tally = remember { Tally() }
    val order = n.names
    val who = order.getOrNull(runs.size) ?: ""

    when (phase) {
        CP.Levels -> Page("${game.emoji} ${game.title}", onBack = { host.exit() }) {
            Body(t("Ένας υπερφορτωμένος πυρήνας. Βάλε κόμβους στο πλέγμα ώστε τα σήματα να φτάσουν δεξιά όπως ζητάει ο στόχος. Χωρίς τύχη: μετράνε οι κόμβοι, οι κύκλοι και η αντοχή σε 3 δοκιμές με spikes, ίδιες για όλους.", "An overloaded core. Place nodes on the grid so the signals reach the right as the target asks. No luck: nodes, cycles and robustness over 3 spike tests count, the same for everyone."), color = C.text)
            if (n.players.size >= 2) Seg(listOf(false to t("Μόνος", "Solo"), true to t("Κόντρα παρέας", "Group showdown")), versus, { versus = it }, color = C.cyan)
            if (versus) Body(t("Η παρέα παίζει το ίδιο επίπεδο με τη σειρά. Κερδίζει όποιος έχει τους λιγότερους κόμβους· σε ισοβαθμία, οι λιγότεροι κύκλοι.", "The group plays the same level in turn. Fewest nodes wins; on a tie, fewest cycles."))
            CoreLevels.chapters.forEachIndexed { ch, (el, en) ->
                Label("${ch + 1}. ${t(el, en).uppercase(java.util.Locale("el")).let { s -> gr.wsol.gamenight.core.Greek.fold(s).uppercase() }}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoreLevels.all.filter { it.chapter == ch }.forEach { lv ->
                        val b = CoreSave.best(lv.id)
                        val open = versus || CoreSave.unlocked(lv.id)
                        Chip("${lv.id}" + (if (b != null) " ✓" else if (!open) " 🔒" else ""), selected = b != null, color = C.cyan, onClick = if (open) ({ level = lv; runs.clear(); phase = if (versus) CP.Pass else CP.Play }) else null)
                    }
                }
            }
        }

        CP.Pass -> { QuitGuard(host); PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Επίπεδο ${level.id}: ${level.name}", "Level ${level.id}: ${level.name}")) { phase = CP.Play } }

        CP.Play -> CoreBoard(level, onBack = { phase = CP.Levels }, versusName = if (versus) who else null) { score, nodes ->
            if (versus) { runs[who] = score; phase = if (runs.size >= order.size) CP.Podium else CP.Pass }
            else { CoreSave.save(level.id, score, nodes) }
        }

        CP.Podium -> {
            val ranked = runs.entries.filter { it.value.solved }.sortedWith(compareBy({ it.value.nodes }, { it.value.cycles }, { -it.value.robust }))
            remember(level.id) {
                // equal results (nodes, cycles, robustness) share the same place
                val key = { e: Map.Entry<String, *> -> runs[e.key]!!.let { Triple(it.nodes, it.cycles, -it.robust) } }
                val score = ranked.map { e -> e.key to -ranked.map(key).distinct().indexOf(key(e)) }
                tally.addAll(host, Showdown.points(score))
                host.log(t("Πυρήνας ${level.id}: κόντρα", "Core ${level.id}: showdown"))
            }
            GameOver(game, host, tally, headline = t("Επίπεδο ${level.id}", "Level ${level.id}"), extra = {
                Panel { ranked.forEachIndexed { i, (p, s) -> Text("${i + 1}. $p · ${s.nodes} ${t("κόμβοι", "nodes")} · ${s.cycles} ${t("κύκλοι", "cycles")} · ${t("αντοχή", "robust")} ${s.robust}/3", style = Type.body, color = C.text) }
                    runs.filter { !it.value.solved }.keys.forEach { Text("$it · ${t("δεν το έλυσε", "not solved")}", style = Type.body, color = C.muted) } }
            }) { runs.clear(); tally.bySide.clear(); phase = CP.Levels }
        }
    }
}

@Composable
private fun CoreBoard(lv: CoreLevel, onBack: () -> Unit, versusName: String?, onSolved: (CoreScore, Map<Int, Node>) -> Unit) {
    val nodes = remember(lv.id, versusName) { mutableStateMapOf<Int, Node>().apply { if (versusName == null) putAll(CoreSave.layout(lv.id)) } }
    var pick by remember(lv.id) { mutableStateOf(lv.palette.keys.first()) }
    val inputs = remember(lv.id) { lv.inputSets()[0] }
    var sim by remember(lv.id, versusName) { mutableStateOf(CoreSim(lv, nodes.toMap(), inputs)) }
    var frame by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var result by remember(lv.id, versusName) { mutableStateOf<CoreScore?>(null) }
    val par = remember(lv.id) { CoreRules.par(lv) }
    fun reset() { playing = false; sim = CoreSim(lv, nodes.toMap(), inputs); result = null; frame++ }

    LaunchedEffect(playing, sim) {
        while (playing && !sim.done) { sim.step(); frame++; Sfx.tick(); delay(260) }
        if (playing && sim.done) {
            playing = false
            val s = CoreRules.score(lv, nodes.toMap())
            result = s
            if (s.solved) { Sfx.win(); onSolved(s, nodes.toMap()) } else Sfx.wrong()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${lv.id}. ${lv.name}", style = Type.h, color = C.cyan, modifier = Modifier.weight(1f))
            versusName?.let { Text(it, style = Type.h, color = C.amber) }
        }
        Panel {
            Label(t("ΣΤΟΧΟΣ", "TARGET"))
            lv.targets.forEach { (row, e) -> Text("→ ${t("γραμμή", "row")} ${row + 1}: ${e.text(lv.names)}", style = Type.body, color = C.text, fontFamily = FontFamily.Monospace) }
            if (lv.hint.isNotEmpty()) Text(lv.hint, style = Type.small, color = C.muted)
        }
        frame.let { }
        CoreGrid(lv, nodes, sim) { cell ->
            if (cell in lv.blocked || playing) return@CoreGrid
            val cur = nodes[cell]
            if (cur != null) nodes.remove(cell) else if (CoreRules.left(lv, nodes, pick) > 0) nodes[cell] = pick
            Sfx.tap(); reset()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lv.palette.keys.forEach { k -> Chip("${k.sym} ${k.title} ×${CoreRules.left(lv, nodes, k)}", selected = pick == k, color = NODE_COLOR[k]!!, onClick = { pick = k }) }
        }
        Text(pick.what, style = Type.small, color = C.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(if (playing) "⏸" else "▶ " + t("Τρέξε", "Run"), { if (sim.done) reset(); playing = !playing }, Modifier.weight(1.4f), color = C.cyan, height = 52.dp)
            GhostButton(t("Βήμα", "Step"), { if (!sim.done) { sim.step(); frame++ } }, Modifier.weight(1f))
            GhostButton("⏪", { if (sim.back()) frame++ }, Modifier.weight(0.7f))
            GhostButton("↺", { reset() }, Modifier.weight(0.7f))
        }
        Panel {
            Label(t("ΕΙΣΟΔΟΙ ΚΑΙ ΕΞΟΔΟΙ · ΚΥΚΛΟΣ ${sim.tick}", "INPUTS AND OUTPUTS · CYCLE ${sim.tick}"))
            lv.inputs.keys.sorted().forEach { r -> Text("${lv.names[r]} (${t("γρ.", "row")} ${r + 1}): ${inputs[r]!!.joinToString(" ")}" + (lv.inputs[r]!!.takeIf { it > 0 }?.let { t("  (ξεκινά στον κύκλο $it)", "  (starts at cycle $it)") } ?: ""), style = Type.small, color = C.text, fontFamily = FontFamily.Monospace) }
            lv.targets.keys.sorted().forEach { r ->
                val exp = sim.expected(r); val got = sim.out[r].orEmpty()
                Text("→ ${t("γρ.", "row")} ${r + 1} ${t("θέλει", "wants")}: ${exp.joinToString(" ")}", style = Type.small, color = C.muted, fontFamily = FontFamily.Monospace)
                Text("   ${t("βγήκε", "got")}: ${got.joinToString(" ")}", style = Type.small, color = if (got == exp.take(got.size)) C.green else C.red, fontFamily = FontFamily.Monospace)
            }
        }
        result?.let { s ->
            Panel {
                if (s.solved) {
                    Text(t("Λύθηκε! ⚛️", "Solved! ⚛️"), style = Type.title, color = C.green)
                    Text(t("Κόμβοι: ${s.nodes} (par ${par.nodes})", "Nodes: ${s.nodes} (par ${par.nodes})"), style = Type.body, color = if (s.nodes <= par.nodes) C.green else C.text)
                    Text(t("Κύκλοι: ${s.cycles} (par ${par.cycles})", "Cycles: ${s.cycles} (par ${par.cycles})"), style = Type.body, color = if (s.cycles <= par.cycles) C.green else C.text)
                    Text(t("Αντοχή: ${s.robust}/3 δοκιμές με spikes", "Robustness: ${s.robust}/3 spike tests"), style = Type.body, color = if (s.robust == 3) C.green else C.amber)
                    CoreSave.best(lv.id)?.takeIf { versusName == null }?.let { b -> Text(t("Το καλύτερό σου: ${b.nodes} κόμβοι, ${b.cycles} κύκλοι", "Your best: ${b.nodes} nodes, ${b.cycles} cycles"), style = Type.small, color = C.muted) }
                } else Text(t("Όχι ακόμα: δες ποια έξοδος δεν ταιριάζει, και πήγαινε βήμα βήμα.", "Not yet: see which output doesn't match, and go step by step."), style = Type.body, color = C.red)
            }
            if (s.solved) {
                if (versusName != null) BigButton(t("Επόμενος", "Next"), { onSolved(s, nodes.toMap()) }, color = C.cyan)
                else CoreLevels.byId(lv.id + 1)?.let { nx -> BigButton(t("Επόμενο επίπεδο", "Next level"), { onBack() }, color = C.cyan) }
            }
        }
        if (versusName != null && result?.solved != true) GhostButton(t("Παραιτούμαι", "Give up"), { onSolved(CoreScore(false, 99, 999, 0), emptyMap()) }, Modifier.fillMaxWidth())
        GhostButton(t("Επίπεδα", "Levels"), onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CoreGrid(lv: CoreLevel, nodes: Map<Int, Node>, sim: CoreSim, onTap: (Int) -> Unit) {
    Canvas(Modifier.fillMaxWidth().aspectRatio((lv.w + 1.2f) / lv.h).background(Color(0xFF070B14)).semantics { contentDescription = t("Το πλέγμα του πυρήνα", "The core grid") }
        .pointerInput(lv.id) {
            detectTapGestures { o ->
                val cell = size.width / (lv.w + 1.2f); val ox = cell * 0.6f
                val c = ((o.x - ox) / cell).toInt(); val r = (o.y / cell).toInt()
                if (o.x >= ox && c in 0 until lv.w && r in 0 until lv.h) onTap(lv.cell(r, c))
            }
        }) {
        val cell = size.width / (lv.w + 1.2f); val ox = cell * 0.6f
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = cell * 0.42f; isFakeBoldText = true }
        for (r in 0 until lv.h) {
            val inRow = lv.inputs.containsKey(r); val outRow = lv.targets.containsKey(r)
            val rowColor = if (inRow) ROW_COLOR[lv.inputs.keys.sorted().indexOf(r) % 3] else Color(0xFF223047)
            drawLine(rowColor.copy(alpha = if (inRow) 0.6f else 0.25f), Offset(0f, (r + 0.5f) * cell), Offset(size.width, (r + 0.5f) * cell), 3f)
            if (inRow) { paint.color = android.graphics.Color.argb(255, (rowColor.red * 255).toInt(), (rowColor.green * 255).toInt(), (rowColor.blue * 255).toInt()); drawContext.canvas.nativeCanvas.drawText(lv.names[r] ?: "", ox * 0.5f, (r + 0.65f) * cell, paint) }
            if (outRow) drawRect(C.green.copy(alpha = 0.3f), Offset(size.width - ox * 0.9f, r * cell + 6f), Size(ox * 0.8f, cell - 12f))
            for (c in 0 until lv.w) {
                val i = lv.cell(r, c)
                val tl = Offset(ox + c * cell + 4f, r * cell + 4f); val sz = Size(cell - 8f, cell - 8f)
                drawRoundRect(if (i in lv.blocked) Color(0xFF2A2F3A) else Color(0xFF0F1626), tl, sz, CornerRadius(10f))
                if (sim.interfered(r, c)) drawRoundRect(C.ghost.copy(alpha = 0.12f), tl, sz, CornerRadius(10f))
                nodes[i]?.let { n ->
                    drawRoundRect(NODE_COLOR[n]!!.copy(alpha = 0.25f), tl, sz, CornerRadius(10f)); drawRoundRect(NODE_COLOR[n]!!, tl, sz, CornerRadius(10f), style = Stroke(4f))
                    paint.color = android.graphics.Color.WHITE; drawContext.canvas.nativeCanvas.drawText(n.sym, ox + (c + 0.5f) * cell, (r + 0.66f) * cell, paint)
                }
            }
        }
        sim.packets.forEach { p ->
            if (p.c < 0) return@forEach
            val center = Offset(ox + (p.c + 0.5f) * cell, (p.r + 0.5f) * cell)
            drawCircle(Color.White, cell * 0.24f, center); drawCircle(if (p.wait > 0) C.ghost else C.amber, cell * 0.24f, center, style = Stroke(5f))
            paint.color = android.graphics.Color.BLACK; paint.textSize = cell * 0.3f
            drawContext.canvas.nativeCanvas.drawText("${p.v}", center.x, center.y + cell * 0.1f, paint); paint.textSize = cell * 0.42f
        }
    }
}
