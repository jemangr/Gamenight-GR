package gr.wsol.gamenight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** «Game night»: dark background, bright colours per game, big buttons for passing the phone. */
/** The background colours of a look (1.1: «Καλοκαίρι», «Χριστούγεννα»). Text stays ≥ 4.5:1 on bg. */
data class LookColors(val bg: Color, val panel: Color, val panel2: Color, val line: Color, val text: Color, val muted: Color, val dim: Color, val emoji: String)

object Looks {
    val night = LookColors(Color(0xFF0B0A12), Color(0xFF15131F), Color(0xFF1E1B2B), Color(0xFF2B2740), Color(0xFFF4F1FF), Color(0xFFA79FC4), Color(0xFF8A82AC), "🎲")
    val summer = LookColors(Color(0xFF05202B), Color(0xFF0B2E3B), Color(0xFF113A4A), Color(0xFF1B5165), Color(0xFFF2FBFF), Color(0xFFA6D3E0), Color(0xFF83B8C8), "☀️")
    val christmas = LookColors(Color(0xFF0D1A12), Color(0xFF15261B), Color(0xFF1C3224), Color(0xFF2D4C37), Color(0xFFFBF6EC), Color(0xFFC9D8C6), Color(0xFF9DB9A0), "🎄")
    val all = listOf(night, summer, christmas)
    val current get() = all.getOrElse(gr.wsol.gamenight.core.Store.settings.look) { night }
}

/** «Game night»: dark background, bright colours per game, big buttons for passing the phone. */
object C {
    val bg get() = Looks.current.bg
    val panel get() = Looks.current.panel
    val panel2 get() = Looks.current.panel2
    val line get() = Looks.current.line
    val text get() = Looks.current.text
    val muted get() = Looks.current.muted
    val dim get() = Looks.current.dim // ≥ 4.5:1 on bg
    val amber = Color(0xFFFFB020)
    val green = Color(0xFF39FF88)
    val ghost = Color(0xFFB48CFF)
    val candle = Color(0xFFFFCF7A)
    val pink = Color(0xFFFF5FA2)
    val cyan = Color(0xFF22D3EE)
    val red = Color(0xFFFF4D5E)
    val lime = Color(0xFFC6F432)
    // «Τετράδες» levels: yellow, green, blue, purple.
    val quad = listOf(Color(0xFFF5D547), Color(0xFF7BD389), Color(0xFF6FA8FF), Color(0xFFC08CFF))
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val DisplayFont = FontFamily(
    androidx.compose.ui.text.font.Font(gr.wsol.gamenight.R.font.manrope, FontWeight.Normal, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(400))),
    androidx.compose.ui.text.font.Font(gr.wsol.gamenight.R.font.manrope, FontWeight.Bold, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(700))),
    androidx.compose.ui.text.font.Font(gr.wsol.gamenight.R.font.manrope, FontWeight.ExtraBold, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(800))),
    androidx.compose.ui.text.font.Font(gr.wsol.gamenight.R.font.manrope, FontWeight.Black, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(800))),
)

val Serif = DisplayFont

object Type {
    val huge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 72.sp, lineHeight = 76.sp)
    val big = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 44.sp)
    val title = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp)
    val h = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 23.sp)
    val small = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp)
    val label = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.2.sp)
}

@Composable
fun GamenightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.amber, onPrimary = Color.Black, secondary = C.pink, background = C.bg, onBackground = C.text,
            surface = C.panel, onSurface = C.text, surfaceVariant = C.panel2, onSurfaceVariant = C.muted, outline = C.line, error = C.red,
        ),
        content = { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(C.bg)) { content() } },
    )
}
