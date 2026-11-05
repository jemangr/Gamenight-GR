package gr.wsol.gamenight.chaos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Narrator
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.BigButton
import gr.wsol.gamenight.ui.Body
import gr.wsol.gamenight.ui.C
import gr.wsol.gamenight.ui.Chip
import gr.wsol.gamenight.ui.GhostButton
import gr.wsol.gamenight.ui.Panel
import gr.wsol.gamenight.ui.Type

/**
 * The surprise, on top of everything: the event, then (if needed) a duel, then «Δώσε το κινητό
 * στον/στην …» with nothing shown until that player says they have it.
 */
@Composable
fun ChaosOverlay(event: Chaos.Event, fromHome: Boolean = false, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember(event) { mutableIntStateOf(0) } // 0 event, 1 duel, 2 back to the holder
    LaunchedEffect(event) { Narrator.init(ctx); if (event.kind == Chaos.Kind.Penalty) Sfx.boom() else Sfx.bell(); Sfx.buzz(200, true); Narrator.say(event.title + " " + event.text) }
    Column(Modifier.fillMaxSize().background(Color(0xF20B0A12)).statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (step) {
            0 -> {
                Text(event.emoji, fontSize = 72.sp)
                Text(event.title, style = Type.big, color = if (event.kind == Chaos.Kind.Penalty) C.red else C.amber, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(event.text, style = Type.title, color = C.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                BigButton("OK", { Chaos.apply(event); if (event.duel && event.other != null) step = 1 else if (fromHome) onDone() else step = 2 }, color = C.amber)
            }
            1 -> {
                val p = event.player!!; val o = event.other!!
                val task = remember(event) { listOf(
                    t("Ποιος λέει πρώτος 3 φρούτα από Μ;", "Who says 3 fruits starting with M first?"), t("Ποιος λέει πρώτος 3 νησιά;", "Who names 3 islands first?"),
                    t("Ποιος κρατά περισσότερο την ανάσα του;", "Who holds their breath longer?"), t("Ποιος λέει πρώτος την αλφάβητο ανάποδα ως το Ψ;", "Who says the alphabet backwards to W first?"),
                    t("Κόντρα στο «πέτρα, ψαλίδι, χαρτί»!", "Rock, paper, scissors!"), t("Ποιος λέει πρώτος 4 ζώα με Κ;", "Who names 4 animals starting with C first?"),
                ).random() }
                Text("⚔️", fontSize = 64.sp)
                Text(t("Μονομαχία!", "Duel!"), style = Type.big, color = C.amber)
                Body(t("$p μπορεί να πάρει πίσω τον πόντο: μονομαχία με τον/την $o.", "$p can win the point back: a duel with $o."), color = C.text, align = TextAlign.Center)
                Panel { Text(task, style = Type.title, color = C.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                Text(t("Ποιος κέρδισε;", "Who won?"), style = Type.body, color = C.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(p, { Store.night = Store.night.award(listOf(p), 1).note("chaos", t("⚔️ $p κέρδισε τη μονομαχία", "⚔️ $p won the duel")); Sfx.win(); if (fromHome) onDone() else step = 2 }, Modifier.weight(1f), color = C.green)
                    BigButton(o, { Sfx.end(); if (fromHome) onDone() else step = 2 }, Modifier.weight(1f), color = C.cyan)
                }
                GhostButton(t("Χωρίς μονομαχία", "No duel"), { if (fromHome) onDone() else step = 2 }, Modifier.fillMaxWidth())
            }
            else -> {
                val h = Chaos.holder
                Text("📱", fontSize = 56.sp)
                Text(if (h != null) t("Δώσε το κινητό σε", "Give the phone back to") else t("Δώσε το κινητό σε όποιον το κρατούσε", "Give the phone back to whoever had it"), style = Type.body, color = C.muted, textAlign = TextAlign.Center)
                h?.let { Text(it, style = Type.big, color = Color(Store.night.colorOf(Store.night.sideOf(it))), textAlign = TextAlign.Center) }
                Spacer(Modifier.height(24.dp))
                BigButton(t("Το έχω", "I have it"), onDone, color = C.amber)
            }
        }
    }
}

/** On the home screen: «🎯 Κράξε» (once every 5 minutes) and the sheriff's one tax. */
@Composable
fun ChaosHomeBar() {
    val ctx = LocalContext.current
    val n = Store.night
    if (!Store.settings.chaos || n.players.size < 2) return
    var pick by remember { mutableStateOf<String?>(null) } // "roast" or "tax"
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(5000); tick++ } }
    tick.let { }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val wait = Chaos.roastWaitMs()
        GhostButton(if (wait > 0) t("🎯 Κράξε σε ${(wait / 60000) + 1}′", "🎯 Roast in ${(wait / 60000) + 1}′") else t("🎯 Κράξε κάποιον", "🎯 Roast someone"), { pick = "roast" }, Modifier.weight(1f), enabled = wait == 0L)
        Chaos.sheriff?.let { s -> GhostButton(t("🤠 $s: φορολόγησε", "🤠 $s: tax"), { pick = "tax" }, Modifier.weight(1f), color = C.amber) }
    }
    pick?.let { what ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text(if (what == "roast") t("Ποιον κράζουμε;", "Whom do we roast?") else t("Ποιον φορολογεί ο σερίφης;", "Whom does the sheriff tax?"), color = C.text) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    n.names.filter { what == "roast" || it != Chaos.sheriff }.forEach { p ->
                        Chip(p, color = Color(n.colorOf(n.sideOf(p))), onClick = {
                            if (what == "roast") Chaos.pending = Chaos.roast(ctx, p)
                            else { Chaos.pending = Chaos.Event(Chaos.Kind.Sheriff, "🤠", t("Φόρος!", "Tax!"), t("Ο σερίφης ${Chaos.sheriff} φορολόγησε τον/την $p: −1. Νόμος είναι αυτός.", "Sheriff ${Chaos.sheriff} taxed $p: −1. That's the law."), p, -1); Chaos.sheriff = null }
                            pick = null
                        })
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { pick = null }) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
            containerColor = C.panel,
        )
    }
}
