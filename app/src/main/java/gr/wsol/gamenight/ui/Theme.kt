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
object C {
    val bg = Color(0xFF0B0A12)
    val panel = Color(0xFF15131F)
    val panel2 = Color(0xFF1E1B2B)
    val line = Color(0xFF2B2740)
    val text = Color(0xFFF4F1FF)
    val muted = Color(0xFFA79FC4)
    val dim = Color(0xFF8A82AC) // ≥ 4.5:1 on bg
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

val Serif = FontFamily.Serif

object Type {
    val huge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Black, fontSize = 72.sp, lineHeight = 76.sp)
    val big = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 44.sp)
    val title = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp)
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
