package gr.wsol.gamenight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Palette
import gr.wsol.gamenight.core.Player
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.Turns
import gr.wsol.gamenight.core.t

const val MAX_PLAYERS = 7

@Composable
fun PlayersScreen() {
    val n = Store.night
    var name by remember { mutableStateOf("") }
    fun add() {
        // a leading «#» is how teams are named inside the app («#0», «#1»): never in a player's name
        val v = name.trim().trimStart('#').trim().take(16)
        if (v.isEmpty() || n.players.size >= MAX_PLAYERS || n.players.any { it.name.equals(v, true) }) return
        val used = n.players.map { it.color }.toSet()
        val color = Palette.players.indices.firstOrNull { Palette.players[it] !in used }?.let { Palette.players[it] } ?: Palette.players[0]
        val players = n.players + Player(v, color)
        Store.night = n.copy(players = players, teamOf = if (n.teams) Turns.randomTeams(players.map { it.name }, n.teamCount) else n.teamOf)
        name = ""
    }
    Page(t("Παίκτες", "Players"), onBack = { Nav.back() }, bottom = {
        BigButton(t("Έτοιμοι", "Done"), { Nav.back() }, enabled = n.players.isNotEmpty())
    }) {
        Body(t("1 έως 7 ονόματα. Κρατιούνται για την επόμενη φορά.", "1 to 7 names. Kept for next time."))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                name, { name = it.take(16) }, Modifier.weight(1f),
                placeholder = { Text(t("Όνομα", "Name"), color = C.dim) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                enabled = n.players.size < MAX_PLAYERS,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.amber, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.amber),
            )
            Spacer(Modifier.width(10.dp))
            BigButton("+", { add() }, Modifier.width(64.dp), enabled = name.isNotBlank() && n.players.size < MAX_PLAYERS)
        }
        n.players.forEach { p ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.panel).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) {
                            val nextAvatar = (p.avatar + 1) % 48
                            Store.night = n.copy(players = n.players.map { if (it.name == p.name) it.copy(avatar = nextAvatar) else it })
                        }
                        .padding(4.dp)
                ) {
                    MonsterAvatar(p.name, Color(p.color), variant = p.avatar, size = 38.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(p.name, style = Type.body, color = C.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (n.teams) {
                    val tm = n.teamOf[p.name] ?: 0
                    Text(
                        Night.teamName(tm), color = Color(Palette.teamColor(tm)), fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                            Store.night = n.copy(teamOf = n.teamOf + (p.name to (tm + 1) % n.teamCount))
                        }.padding(10.dp),
                    )
                }
                IconButton(onClick = {
                    val left = n.players - p
                    Store.night = n.copy(players = left, teamOf = n.teamOf - p.name)
                }) { Icon(Icons.Filled.Close, t("Αφαίρεση ${p.name}", "Remove ${p.name}"), tint = C.muted) }
            }
        }
        if (n.players.size >= MAX_PLAYERS) Body(t("Έως 7 παίκτες.", "Up to 7 players."))

        Spacer(Modifier.heightIn(min = 6.dp))
        Label(t("ΠΩΣ ΜΕΤΡΑΜΕ ΠΟΝΤΟΥΣ", "HOW WE SCORE"))
        Seg(listOf(false to t("Ατομικά", "Solo"), true to t("Ομάδες", "Teams")), n.teams, { teams ->
            Store.night = n.copy(teams = teams, teamOf = if (teams) Turns.randomTeams(n.names, n.teamCount) else emptyMap(), points = emptyMap())
        })
        if (n.teams) {
            Seg(listOf(2 to t("2 ομάδες", "2 teams"), 3 to t("3 ομάδες", "3 teams")), n.teamCount, { c ->
                Store.night = n.copy(teamCount = c, teamOf = Turns.randomTeams(n.names, c), points = emptyMap())
            }, color = C.cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(t("🔀 Τυχαίες ομάδες", "🔀 Random teams"), { Store.night = n.copy(teamOf = Turns.randomTeams(n.names, n.teamCount)) }, Modifier.weight(1f))
            }
            Body(t("Πάτα το όνομα της ομάδας δίπλα σε κάθε παίκτη για να τον αλλάξεις ομάδα. Η αλλαγή σε ομάδες μηδενίζει τους πόντους.", "Tap the team next to a player to move them. Switching to teams resets the points."))
        }
    }
}
