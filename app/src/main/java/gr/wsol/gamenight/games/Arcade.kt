package gr.wsol.gamenight.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.ArcadeBackground
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Confetti
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.PassTo
import gr.wsol.gamenight.ui.Seg
import gr.wsol.gamenight.ui.Type
import gr.wsol.gamenight.ui.haptic
import gr.wsol.gamenight.ui.shake

/**
 * The group's high-score showdown: +3, +2, +1 to the best three runs. Equal scores get the same
 * points (two firsts: +3 each, then the next one is third: +1).
 */
object Showdown {
    fun points(scores: List<Pair<String, Int>>): Map<String, Int> =
        scores.mapNotNull { (p, s) -> val rank = scores.count { it.second > s }; if (rank < 3) p to (3 - rank) else null }.toMap()
}

private enum class AP { Intro, Pass, Run, Over, Podium }

/**
 * The frame of every arcade game: rules, solo (records of all time, today, this week) or the
 * group's showdown, one run each, and «Ξανά» with one tap. [run] plays one run and calls back with the score.
 */
@Composable
fun ArcadeShell(game: Game, host: GameHost, rules: List<String>, run: @Composable (key: Int, onOver: (Int) -> Unit) -> Unit) {
    val n = Store.night
    var versus by remember { mutableStateOf(n.players.size >= 2) }
    var phase by remember { mutableStateOf(AP.Intro) }
    var runNo by remember { mutableIntStateOf(0) }
    var last by remember { mutableIntStateOf(0) }
    var beat by remember { mutableStateOf(setOf<String>()) }
    val scores = remember { mutableStateListOf<Pair<String, Int>>() }
    val tally = remember { Tally() }
    val order = n.names
    val who = order.getOrNull(scores.size) ?: ""

    when (phase) {
        AP.Intro -> Intro(game, host, rules + listOf(
            t("Μόνος: κυνηγάς τα ρεκόρ σου (όλων των εποχών, της ημέρας, της εβδομάδας).", "Solo: chase your records (all time, today, this week)."),
            t("Κόντρα: ένα run ο καθένας. +3, +2, +1 στους τρεις καλύτερους.", "Showdown: one run each. +3, +2, +1 to the best three."),
        ), options = if (n.players.size >= 2) ({ Seg(listOf(false to t("Μόνος", "Solo"), true to t("Κόντρα παρέας", "Group showdown")), versus, { versus = it }, color = game.color) }) else null) {
            scores.clear(); runNo++; phase = if (versus) AP.Pass else AP.Run
        }

        AP.Pass -> { QuitGuard(host); PassTo(who, Color(n.colorOf(n.sideOf(who))), note = t("Το run σου. Καλή επιτυχία!", "Your run. Good luck!"), button = t("Πάμε!", "Go!")) { runNo++; phase = AP.Run } }

        AP.Run -> {
            QuitGuard(host)
            val haptic = LocalHapticFeedback.current
            run(runNo) { score ->
                last = score
                beat = if (!versus) Store.arcadeRecord(game.id, score) else emptySet()
                if (beat.contains("all")) Sfx.win() else Sfx.end()
                haptic(haptic, HapticFeedbackType.LongPress)
                if (versus) { scores.add(who to score); phase = if (scores.size >= order.size) AP.Podium else AP.Over } else phase = AP.Over
            }
        }

        AP.Over -> {
            val (all, day, week) = Store.arcadeBest(game.id)
            val isNewRecord = "all" in beat
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    Text(game.emoji, fontSize = 52.sp)
                    Text("$last", style = Type.huge, color = game.color)
                    if (versus) Text(t("${scores.lastOrNull()?.first}: $last πόντοι", "${scores.lastOrNull()?.first}: $last points"), style = Type.title, color = C.text)
                    else {
                        when {
                            isNewRecord -> Text(t("Νέο ρεκόρ! 🏆", "New record! 🏆"), style = Type.title, color = C.amber)
                            "week" in beat -> Text(t("Ρεκόρ της εβδομάδας! 🏆", "Record of the week! 🏆"), style = Type.title, color = C.amber)
                            "day" in beat -> Text(t("Ρεκόρ της ημέρας! 🏆", "Record of the day! 🏆"), style = Type.title, color = C.amber)
                        }
                        Panel {
                            Row { Text(t("Ρεκόρ", "Record"), style = Type.body, color = C.muted, modifier = Modifier.weight(1f)); Text("🏆 $all", style = Type.h, color = C.text) }
                            Row { Text(t("Σήμερα", "Today"), style = Type.body, color = C.muted, modifier = Modifier.weight(1f)); Text("$day", style = Type.h, color = C.text) }
                            Row { Text(t("Αυτή την εβδομάδα", "This week"), style = Type.body, color = C.muted, modifier = Modifier.weight(1f)); Text("$week", style = Type.h, color = C.text) }
                        }
                    }
                    if (versus) BigButton(t("Επόμενος: ${order.getOrNull(scores.size)}", "Next: ${order.getOrNull(scores.size)}"), { phase = AP.Pass }, color = game.color)
                    else BigButton(t("Ξανά", "Again"), { runNo++; phase = AP.Run }, color = game.color, height = 72.dp)
                    GhostButton(t("Στο μενού", "Menu"), { host.exit() }, Modifier.fillMaxWidth())
                }
                Confetti(trigger = if (isNewRecord) beat else null)
            }
        }

        AP.Podium -> {
            val pts = remember(runNo) { Showdown.points(scores).also { m -> m.forEach { (p, v) -> tally.add(host, listOf(p), v) }; host.log(t("${game.title}: κόντρα, πρώτος ${scores.maxByOrNull { it.second }?.first}", "${game.title}: showdown, won by ${scores.maxByOrNull { it.second }?.first}")) } }
            GameOver(game, host, tally, headline = t("Η κόντρα τελείωσε!", "Showdown over!"), extra = {
                Panel { scores.sortedByDescending { it.second }.forEachIndexed { i, (p, s) -> Row { Text("${i + 1}. $p", style = Type.body, color = C.text, modifier = Modifier.weight(1f)); Text("$s" + (pts[p]?.let { "  +$it" } ?: ""), style = Type.h, color = C.text) } } }
            }) { scores.clear(); tally.bySide.clear(); phase = AP.Intro }
        }
    }
}

/** The score and combo in the corner of every arcade game. */
@Composable
fun Hud(score: Int, mult: Int = 1, extra: String? = null, color: Color = C.text, record: Int? = null) {
    val comboColor = when {
        mult >= 5 -> Color(0xFFFF4081) // ροζ
        mult >= 3 -> Color(0xFFFF9800) // πορτοκαλί
        else -> Color(0xFFFFD54F)      // κίτρινο
    }
    val comboScale = remember { Animatable(1f) }
    var prevMult by remember { mutableIntStateOf(mult) }
    LaunchedEffect(mult) {
        if (mult > 1 && mult > prevMult) {
            comboScale.snapTo(1.35f)
            comboScale.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
        prevMult = mult
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$score", style = Type.big, color = color, modifier = Modifier.weight(1f))
        if (record != null && record > 0) {
            Text("🏆 $record  ", style = Type.body, color = C.muted)
        }
        if (extra != null) Text(extra, style = Type.h, color = C.muted)
        if (mult > 1) {
            Text(
                "  ×$mult",
                style = Type.title,
                color = comboColor,
                modifier = Modifier.scale(comboScale.value)
            )
        }
    }
}

/** A big «+x4» that pops when a combo grows. */
@Composable
fun ComboPop(text: String?, color: Color = C.amber) {
    if (text == null) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = Type.big, color = color, textAlign = TextAlign.Center)
    }
}

/** Helper for the frame loop of the arcade games: seconds since the last frame, at most 1/20 s. */
class FrameClock { private var last = 0L; fun tick(now: Long): Float { val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f); last = now; return dt } }

@Composable
fun ArcadeBody(
    shakeTrigger: Any? = null,
    flashRed: Boolean = false,
    content: @Composable () -> Unit
) = Box(
    Modifier
        .fillMaxSize()
        .shake(shakeTrigger)
) {
    ArcadeBackground(flashRed = flashRed)
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        content()
    }
}

@Composable fun ArcadeNote(s: String) = Body(s, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
