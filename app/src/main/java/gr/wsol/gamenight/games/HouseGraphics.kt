package gr.wsol.gamenight.games

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.ui.DisplayFont
import gr.wsol.gamenight.ui.Type

/** Colors for each role in the Haunted House. */
object HousePalette {
    fun colors(role: Role): Pair<Color, Color> = when (role) {
        Role.Zombie -> Color(0xFF0B1F14) to Color(0xFF163C26)
        Role.Leader -> Color(0xFF1B200E) to Color(0xFF384319)
        Role.Healer -> Color(0xFF0C2028) to Color(0xFF184452)
        Role.Mage -> Color(0xFF1E1032) to Color(0xFF3C1F62)
        Role.Exorcist -> Color(0xFF1A1734) to Color(0xFF322C5A)
        Role.Medium -> Color(0xFF26190C) to Color(0xFF4C3317)
        Role.Hunter -> Color(0xFF281E10) to Color(0xFF503B1E)
        Role.Granny -> Color(0xFF261220) to Color(0xFF4C213D)
        Role.Possessed -> Color(0xFF2E0F15) to Color(0xFF5A1926)
        Role.Human -> Color(0xFF111A2C) to Color(0xFF1F3250)
    }

    fun accent(role: Role): Color = when (role) {
        Role.Zombie -> Color(0xFF48BB78)
        Role.Leader -> Color(0xFFECC94B)
        Role.Healer -> Color(0xFF38B2AC)
        Role.Mage -> Color(0xFFB794F4)
        Role.Exorcist -> Color(0xFFE2E8F0)
        Role.Medium -> Color(0xFFF6AD55)
        Role.Hunter -> Color(0xFFED8936)
        Role.Granny -> Color(0xFFF687B3)
        Role.Possessed -> Color(0xFFFC8181)
        Role.Human -> Color(0xFF63B3ED)
    }
}

/** Draws an ornate gothic card frame with arch peaks and filigree corners. */
fun DrawScope.drawGothicFrame(accent: Color, isDark: Boolean = false) {
    val w = size.width
    val h = size.height
    val strokeOuter = 2.5f
    val strokeInner = 1.2f

    val baseColor = accent.copy(alpha = if (isDark) 0.5f else 0.85f)
    val highlightColor = Color(0xFFF7FAFC).copy(alpha = 0.4f)

    // Outer border with subtle notched gothic corners
    val corner = w * 0.06f
    val path = Path().apply {
        moveTo(corner, 0f)
        lineTo(w - corner, 0f)
        lineTo(w, corner)
        lineTo(w, h - corner)
        lineTo(w - corner, h)
        lineTo(corner, h)
        lineTo(0f, h - corner)
        lineTo(0f, corner)
        close()
    }
    drawPath(path, baseColor, style = Stroke(strokeOuter))

    // Inner fine border
    val inset = w * 0.035f
    val inCorner = corner * 0.7f
    val innerPath = Path().apply {
        moveTo(inset + inCorner, inset)
        lineTo(w - inset - inCorner, inset)
        lineTo(w - inset, inset + inCorner)
        lineTo(w - inset, h - inset - inCorner)
        lineTo(w - inset - inCorner, h - inset)
        lineTo(inset + inCorner, h - inset)
        lineTo(inset, h - inset - inCorner)
        lineTo(inset, inset + inCorner)
        close()
    }
    drawPath(innerPath, baseColor.copy(alpha = 0.5f), style = Stroke(strokeInner))

    // Four corner decorative diamonds
    val dSize = w * 0.022f
    val corners = listOf(
        Offset(inset, inset),
        Offset(w - inset, inset),
        Offset(w - inset, h - inset),
        Offset(inset, h - inset)
    )
    corners.forEach { c ->
        val diamond = Path().apply {
            moveTo(c.x, c.y - dSize)
            lineTo(c.x + dSize, c.y)
            lineTo(c.x, c.y + dSize)
            lineTo(c.x - dSize, c.y)
            close()
        }
        drawPath(diamond, highlightColor)
        drawPath(diamond, baseColor, style = Stroke(1f))
    }

    // Top gothic arched flourish
    val topPeak = Path().apply {
        moveTo(w * 0.38f, inset)
        quadraticTo(w * 0.5f, inset - w * 0.03f, w * 0.62f, inset)
        moveTo(w * 0.44f, inset + w * 0.015f)
        lineTo(w * 0.5f, inset + w * 0.035f)
        lineTo(w * 0.56f, inset + w * 0.015f)
    }
    drawPath(topPeak, baseColor, style = Stroke(strokeInner))

    // Bottom decorative flourish
    val bottomFlourish = Path().apply {
        moveTo(w * 0.38f, h - inset)
        quadraticTo(w * 0.5f, h - inset + w * 0.03f, w * 0.62f, h - inset)
        moveTo(w * 0.44f, h - inset - w * 0.015f)
        lineTo(w * 0.5f, h - inset - w * 0.035f)
        lineTo(w * 0.56f, h - inset - w * 0.015f)
    }
    drawPath(bottomFlourish, baseColor, style = Stroke(strokeInner))
}

/** Draws the symmetrical Victorian haunted house card back. Identical for all roles. */
fun DrawScope.drawVictorianBack() {
    val w = size.width
    val h = size.height
    val gold = Color(0xFFD4AF37)
    val paleGold = Color(0xFFF5E6B8)

    // Gothic outer frame
    drawGothicFrame(gold, isDark = false)

    // Center circular medallion
    val center = Offset(w * 0.5f, h * 0.46f)
    val r = w * 0.28f

    // Concentric celestial rings
    drawCircle(gold.copy(alpha = 0.25f), r * 1.08f, center, style = Stroke(1.5f))
    drawCircle(gold.copy(alpha = 0.5f), r, center, style = Stroke(2f))
    drawCircle(Color(0xFF090812), r - 2f, center)

    // Full moon behind house
    drawCircle(paleGold.copy(alpha = 0.9f), r * 0.45f, Offset(center.x, center.y - r * 0.15f))
    drawCircle(paleGold.copy(alpha = 0.3f), r * 0.58f, Offset(center.x, center.y - r * 0.15f))

    // Victorian haunted house silhouette in the medallion
    val housePath = Path().apply {
        val hw = r * 0.65f
        val hh = r * 0.6f
        val bx = center.x - hw / 2f
        val by = center.y + r * 0.4f

        // Base
        moveTo(bx, by)
        lineTo(bx, by - hh * 0.5f)
        // Left roof peak
        lineTo(bx + hw * 0.2f, by - hh * 0.85f)
        lineTo(bx + hw * 0.35f, by - hh * 0.55f)
        // Center tower spire
        lineTo(center.x, by - hh * 1.15f)
        // Right roof peak
        lineTo(bx + hw * 0.65f, by - hh * 0.55f)
        lineTo(bx + hw * 0.8f, by - hh * 0.85f)
        lineTo(bx + hw, by - hh * 0.5f)
        lineTo(bx + hw, by)
        close()
    }
    drawPath(housePath, Color(0xFF13101E))

    // Windows in tower (glowing warm amber)
    drawRect(Color(0xFFFFD166), Offset(center.x - 3f, center.y - r * 0.22f), Size(6f, 10f))
    drawRect(Color(0xFFFFD166), Offset(center.x - r * 0.18f, center.y + r * 0.05f), Size(5f, 8f))
    drawRect(Color(0xFFFFD166), Offset(center.x + r * 0.12f, center.y + r * 0.05f), Size(5f, 8f))

    // Symmetrical bats flying around the spire
    listOf(-1f, 1f).forEach { dir ->
        val batCenter = Offset(center.x + dir * r * 0.42f, center.y - r * 0.45f)
        val bat = Path().apply {
            moveTo(batCenter.x, batCenter.y)
            quadraticTo(batCenter.x + dir * 12f, batCenter.y - 8f, batCenter.x + dir * 20f, batCenter.y - 2f)
            quadraticTo(batCenter.x + dir * 14f, batCenter.y + 4f, batCenter.x + dir * 8f, batCenter.y + 2f)
            quadraticTo(batCenter.x, batCenter.y + 6f, batCenter.x - dir * 4f, batCenter.y)
            close()
        }
        drawPath(bat, paleGold.copy(alpha = 0.85f))
    }

    // Stars scattered in card field
    val stars = listOf(
        Offset(w * 0.22f, h * 0.16f),
        Offset(w * 0.78f, h * 0.16f),
        Offset(w * 0.15f, h * 0.74f),
        Offset(w * 0.85f, h * 0.74f),
        Offset(w * 0.5f, h * 0.82f),
    )
    stars.forEach { s ->
        drawLine(gold.copy(alpha = 0.7f), Offset(s.x - 5f, s.y), Offset(s.x + 5f, s.y), 1.5f)
        drawLine(gold.copy(alpha = 0.7f), Offset(s.x, s.y - 5f), Offset(s.x, s.y + 5f), 1.5f)
    }
}

/** Draws the clean vector emblem for a given role. */
fun DrawScope.drawRoleEmblem(role: Role, accent: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension * 0.42f

    // Soft glow backdrop
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(cx, cy),
            radius = r * 1.3f
        ),
        radius = r * 1.3f,
        center = Offset(cx, cy)
    )

    when (role) {
        Role.Zombie -> {
            // Skeletal/decaying hand bursting upward from ragged earth
            val earth = Path().apply {
                moveTo(cx - r * 0.8f, cy + r * 0.7f)
                lineTo(cx - r * 0.3f, cy + r * 0.5f)
                lineTo(cx, cy + r * 0.65f)
                lineTo(cx + r * 0.4f, cy + r * 0.48f)
                lineTo(cx + r * 0.8f, cy + r * 0.7f)
                lineTo(cx + r * 0.8f, cy + r * 0.85f)
                lineTo(cx - r * 0.8f, cy + r * 0.85f)
                close()
            }
            drawPath(earth, Color(0xFF1E3A26))
            drawPath(earth, accent, style = Stroke(2f))

            // Arm and clawed fingers reaching up
            val arm = Path().apply {
                moveTo(cx - r * 0.18f, cy + r * 0.6f)
                lineTo(cx - r * 0.12f, cy - r * 0.1f)
                // Thumb
                lineTo(cx - r * 0.38f, cy - r * 0.22f)
                lineTo(cx - r * 0.28f, cy - r * 0.28f)
                lineTo(cx - r * 0.1f, cy - r * 0.2f)
                // Index
                lineTo(cx - r * 0.15f, cy - r * 0.65f)
                lineTo(cx - r * 0.06f, cy - r * 0.68f)
                lineTo(cx - r * 0.03f, cy - r * 0.25f)
                // Middle
                lineTo(cx + r * 0.02f, cy - r * 0.75f)
                lineTo(cx + r * 0.11f, cy - r * 0.76f)
                lineTo(cx + r * 0.09f, cy - r * 0.25f)
                // Ring
                lineTo(cx + r * 0.2f, cy - r * 0.62f)
                lineTo(cx + r * 0.28f, cy - r * 0.62f)
                lineTo(cx + r * 0.2f, cy - r * 0.2f)
                // Pinky
                lineTo(cx + r * 0.34f, cy - r * 0.42f)
                lineTo(cx + r * 0.38f, cy - r * 0.38f)
                lineTo(cx + r * 0.22f, cy - r * 0.1f)
                lineTo(cx + r * 0.18f, cy + r * 0.6f)
                close()
            }
            drawPath(arm, accent)
            drawPath(arm, Color(0xFF0F2618), style = Stroke(2f))
        }

        Role.Leader -> {
            // Zombie skull crowned with jagged spiked circlet and eerie aura
            // Skull base
            drawCircle(accent, r * 0.48f, Offset(cx, cy - r * 0.05f))
            drawRoundRect(
                accent,
                Offset(cx - r * 0.26f, cy + r * 0.2f),
                Size(r * 0.52f, r * 0.42f),
                CornerRadius(r * 0.1f)
            )

            // Deep hollow eye sockets and nose
            drawCircle(Color(0xFF141908), r * 0.13f, Offset(cx - r * 0.18f, cy - r * 0.02f))
            drawCircle(Color(0xFF141908), r * 0.13f, Offset(cx + r * 0.18f, cy - r * 0.02f))
            // Eerie glowing pupils
            drawCircle(Color(0xFFFFE082), r * 0.04f, Offset(cx - r * 0.18f, cy - r * 0.02f))
            drawCircle(Color(0xFFFFE082), r * 0.04f, Offset(cx + r * 0.18f, cy - r * 0.02f))

            // Teeth lines
            for (i in -2..2) {
                drawLine(
                    Color(0xFF141908),
                    Offset(cx + i * r * 0.08f, cy + r * 0.32f),
                    Offset(cx + i * r * 0.08f, cy + r * 0.52f),
                    2f
                )
            }

            // Jagged tattered crown on top
            val crown = Path().apply {
                moveTo(cx - r * 0.46f, cy - r * 0.32f)
                lineTo(cx - r * 0.48f, cy - r * 0.72f)
                lineTo(cx - r * 0.24f, cy - r * 0.45f)
                lineTo(cx, cy - r * 0.85f)
                lineTo(cx + r * 0.24f, cy - r * 0.45f)
                lineTo(cx + r * 0.48f, cy - r * 0.72f)
                lineTo(cx + r * 0.46f, cy - r * 0.32f)
                close()
            }
            drawPath(crown, Color(0xFFFFD54F))
            drawPath(crown, Color(0xFFB78103), style = Stroke(2f))
        }

        Role.Healer -> {
            // Antique leather medical satchel with radiant medical cross
            val bag = Path().apply {
                moveTo(cx - r * 0.65f, cy - r * 0.2f)
                lineTo(cx + r * 0.65f, cy - r * 0.2f)
                quadraticTo(cx + r * 0.72f, cy + r * 0.6f, cx + r * 0.55f, cy + r * 0.65f)
                lineTo(cx - r * 0.55f, cy + r * 0.65f)
                quadraticTo(cx - r * 0.72f, cy + r * 0.6f, cx - r * 0.65f, cy - r * 0.2f)
                close()
            }
            drawPath(bag, Color(0xFF164756))
            drawPath(bag, accent, style = Stroke(2.5f))

            // Bag handle
            val handle = Path().apply {
                moveTo(cx - r * 0.3f, cy - r * 0.2f)
                quadraticTo(cx, cy - r * 0.55f, cx + r * 0.3f, cy - r * 0.2f)
            }
            drawPath(handle, accent, style = Stroke(4f))

            // Glowing medical cross on bag
            val cw = r * 0.12f
            val ch = r * 0.34f
            drawRoundRect(Color(0xFFE6FFFA), Offset(cx - cw / 2f, cy + r * 0.2f - ch / 2f), Size(cw, ch), CornerRadius(4f))
            drawRoundRect(Color(0xFFE6FFFA), Offset(cx - ch / 2f, cy + r * 0.2f - cw / 2f), Size(ch, cw), CornerRadius(4f))
        }

        Role.Mage -> {
            // Glowing ornate crystal ball on carved three-claw brass stand
            // Radiant violet aura
            drawCircle(Color(0xFFB794F4).copy(alpha = 0.5f), r * 0.58f, Offset(cx, cy - r * 0.15f))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFD6BCFA), Color(0xFF6B46C1)),
                    center = Offset(cx - r * 0.12f, cy - r * 0.27f),
                    radius = r * 0.52f
                ),
                radius = r * 0.52f,
                center = Offset(cx, cy - r * 0.15f)
            )

            // Ornate tripod brass stand
            val stand = Path().apply {
                moveTo(cx - r * 0.42f, cy + r * 0.65f)
                quadraticTo(cx - r * 0.25f, cy + r * 0.35f, cx - r * 0.32f, cy + r * 0.25f)
                lineTo(cx + r * 0.32f, cy + r * 0.25f)
                quadraticTo(cx + r * 0.25f, cy + r * 0.35f, cx + r * 0.42f, cy + r * 0.65f)
                lineTo(cx + r * 0.28f, cy + r * 0.65f)
                lineTo(cx, cy + r * 0.42f)
                lineTo(cx - r * 0.28f, cy + r * 0.65f)
                close()
            }
            drawPath(stand, Color(0xFFD69E2E))
            drawPath(stand, Color(0xFF744210), style = Stroke(1.5f))

            // Magic sparkles
            listOf(
                Offset(cx + r * 0.25f, cy - r * 0.45f),
                Offset(cx - r * 0.32f, cy - r * 0.35f),
                Offset(cx + r * 0.15f, cy + r * 0.05f)
            ).forEach { pt ->
                drawLine(Color.White, Offset(pt.x - 4f, pt.y), Offset(pt.x + 4f, pt.y), 1.5f)
                drawLine(Color.White, Offset(pt.x, pt.y - 4f), Offset(pt.x, pt.y + 4f), 1.5f)
            }
        }

        Role.Exorcist -> {
            // Radiant ornamental silver gothic cross emitting rays of light
            // Radiant light rays behind cross
            for (i in 0 until 12) {
                val angle = Math.toRadians(i * 30.0)
                val rayStart = Offset(
                    (cx + kotlin.math.cos(angle) * r * 0.35f).toFloat(),
                    (cy + kotlin.math.sin(angle) * r * 0.35f).toFloat()
                )
                val rayEnd = Offset(
                    (cx + kotlin.math.cos(angle) * r * 0.85f).toFloat(),
                    (cy + kotlin.math.sin(angle) * r * 0.85f).toFloat()
                )
                drawLine(Color(0xFFE2E8F0).copy(alpha = 0.4f), rayStart, rayEnd, 2f)
            }

            // Cross body with flared gothic tips
            val crossV = Path().apply {
                val vw = r * 0.16f
                moveTo(cx - vw / 2f, cy - r * 0.75f)
                lineTo(cx + vw / 2f, cy - r * 0.75f)
                lineTo(cx + vw * 0.7f, cy - r * 0.85f)
                lineTo(cx - vw * 0.7f, cy - r * 0.85f)
                close()

                moveTo(cx - vw / 2f, cy - r * 0.75f)
                lineTo(cx + vw / 2f, cy - r * 0.75f)
                lineTo(cx + vw / 2f, cy + r * 0.75f)
                lineTo(cx - vw / 2f, cy + r * 0.75f)
                close()
            }
            drawPath(crossV, Color(0xFFF7FAFC))

            val crossH = Path().apply {
                val hw = r * 0.16f
                moveTo(cx - r * 0.55f, cy - r * 0.25f - hw / 2f)
                lineTo(cx + r * 0.55f, cy - r * 0.25f - hw / 2f)
                lineTo(cx + r * 0.55f, cy - r * 0.25f + hw / 2f)
                lineTo(cx - r * 0.55f, cy - r * 0.25f + hw / 2f)
                close()
            }
            drawPath(crossH, Color(0xFFF7FAFC))
            drawCircle(Color(0xFFCBD5E0), r * 0.14f, Offset(cx, cy - r * 0.25f), style = Stroke(2.5f))
        }

        Role.Medium -> {
            // Antique candlestick holding a lit tapered candle with flickering flame and smoke wisp
            // Candlestick base
            val base = Path().apply {
                moveTo(cx - r * 0.55f, cy + r * 0.65f)
                quadraticTo(cx, cy + r * 0.5f, cx + r * 0.55f, cy + r * 0.65f)
                lineTo(cx + r * 0.45f, cy + r * 0.72f)
                lineTo(cx - r * 0.45f, cy + r * 0.72f)
                close()
            }
            drawPath(base, Color(0xFFD69E2E))

            // Candle pillar
            drawRoundRect(
                Color(0xFFFFFDF5),
                Offset(cx - r * 0.12f, cy - r * 0.1f),
                Size(r * 0.24f, r * 0.65f),
                CornerRadius(3f)
            )

            // Wick
            drawLine(Color(0xFF2D3748), Offset(cx, cy - r * 0.1f), Offset(cx, cy - r * 0.2f), 2.5f)

            // Candle flame
            val flame = Path().apply {
                moveTo(cx, cy - r * 0.2f)
                quadraticTo(cx + r * 0.18f, cy - r * 0.35f, cx, cy - r * 0.55f)
                quadraticTo(cx - r * 0.18f, cy - r * 0.35f, cx, cy - r * 0.2f)
                close()
            }
            drawPath(flame, Color(0xFFF6AD55))
            drawCircle(Color(0xFFFFF5D6), r * 0.08f, Offset(cx, cy - r * 0.3f))

            // Ethereal ghostly smoke wisp rising
            val smoke = Path().apply {
                moveTo(cx, cy - r * 0.55f)
                quadraticTo(cx - r * 0.25f, cy - r * 0.7f, cx, cy - r * 0.85f)
                quadraticTo(cx + r * 0.25f, cy - r * 0.95f, cx + r * 0.1f, cy - r * 1.05f)
            }
            drawPath(smoke, Color(0xFFE2E8F0).copy(alpha = 0.65f), style = Stroke(3f))
        }

        Role.Hunter -> {
            // Vintage brass flashlight casting a wide angular beam into dust/mist
            // Flashlight body
            val torch = Path().apply {
                moveTo(cx - r * 0.55f, cy + r * 0.45f)
                lineTo(cx - r * 0.15f, cy + r * 0.05f)
                lineTo(cx - r * 0.05f, cy + r * 0.15f)
                lineTo(cx - r * 0.45f, cy + r * 0.55f)
                close()
            }
            drawPath(torch, Color(0xFFB7791F))

            // Flashlight head
            val head = Path().apply {
                moveTo(cx - r * 0.18f, cy)
                lineTo(cx - r * 0.02f, cy - r * 0.16f)
                lineTo(cx + r * 0.14f, cy)
                lineTo(cx - r * 0.02f, cy + r * 0.16f)
                close()
            }
            drawPath(head, Color(0xFFED8936))

            // Wide angular beam of light cutting forward
            val beam = Path().apply {
                moveTo(cx + r * 0.06f, cy - r * 0.08f)
                lineTo(cx + r * 0.85f, cy - r * 0.65f)
                lineTo(cx + r * 0.85f, cy + r * 0.35f)
                lineTo(cx + r * 0.06f, cy + r * 0.08f)
                close()
            }
            drawPath(
                beam,
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFFFFAF0).copy(alpha = 0.85f), Color(0xFFFFE082).copy(alpha = 0.15f)),
                    startX = cx,
                    endX = cx + r * 0.85f
                )
            )
        }

        Role.Granny -> {
            // Ornamental brass censer (θυμιατό) on chains with curling incense smoke
            // Hanging chains
            listOf(-r * 0.2f, 0f, r * 0.2f).forEach { dx ->
                drawLine(Color(0xFFD69E2E), Offset(cx + dx * 0.4f, cy - r * 0.75f), Offset(cx + dx, cy - r * 0.05f), 1.5f)
            }

            // Censer lid with cross top
            val lid = Path().apply {
                moveTo(cx - r * 0.35f, cy - r * 0.05f)
                quadraticTo(cx, cy - r * 0.45f, cx + r * 0.35f, cy - r * 0.05f)
                close()
            }
            drawPath(lid, Color(0xFFECC94B))
            drawPath(lid, Color(0xFF975A16), style = Stroke(1.5f))

            // Tiny cross on top of censer
            drawLine(Color(0xFFECC94B), Offset(cx, cy - r * 0.45f), Offset(cx, cy - r * 0.58f), 2.5f)
            drawLine(Color(0xFFECC94B), Offset(cx - 5f, cy - r * 0.52f), Offset(cx + 5f, cy - r * 0.52f), 2f)

            // Censer bowl
            val bowl = Path().apply {
                moveTo(cx - r * 0.38f, cy - r * 0.05f)
                lineTo(cx + r * 0.38f, cy - r * 0.05f)
                quadraticTo(cx + r * 0.3f, cy + r * 0.45f, cx, cy + r * 0.5f)
                quadraticTo(cx - r * 0.3f, cy + r * 0.45f, cx - r * 0.38f, cy - r * 0.05f)
                close()
            }
            drawPath(bowl, Color(0xFFD69E2E))
            drawPath(bowl, Color(0xFF744210), style = Stroke(1.5f))

            // Incense smoke spirals rising from perforations
            val smoke = Path().apply {
                moveTo(cx - r * 0.15f, cy - r * 0.25f)
                quadraticTo(cx - r * 0.45f, cy - r * 0.5f, cx - r * 0.25f, cy - r * 0.75f)
                quadraticTo(cx, cy - r * 0.95f, cx - r * 0.2f, cy - r * 1.05f)

                moveTo(cx + r * 0.15f, cy - r * 0.25f)
                quadraticTo(cx + r * 0.45f, cy - r * 0.5f, cx + r * 0.25f, cy - r * 0.75f)
                quadraticTo(cx, cy - r * 0.95f, cx + r * 0.2f, cy - r * 1.05f)
            }
            drawPath(smoke, Color(0xFFF7FAFC).copy(alpha = 0.55f), style = Stroke(2.5f))
        }

        Role.Possessed -> {
            // Menacing horned demon mask with glowing angled eyes and sinister grin
            val head = Path().apply {
                // Horns
                moveTo(cx, cy - r * 0.2f)
                quadraticTo(cx - r * 0.4f, cy - r * 0.5f, cx - r * 0.65f, cy - r * 0.75f)
                quadraticTo(cx - r * 0.45f, cy - r * 0.35f, cx - r * 0.35f, cy)
                // Left cheek to chin
                lineTo(cx, cy + r * 0.65f)
                // Right chin to cheek
                lineTo(cx + r * 0.35f, cy)
                quadraticTo(cx + r * 0.45f, cy - r * 0.35f, cx + r * 0.65f, cy - r * 0.75f)
                quadraticTo(cx + r * 0.4f, cy - r * 0.5f, cx, cy - r * 0.2f)
                close()
            }
            drawPath(head, Color(0xFF742A2A))
            drawPath(head, accent, style = Stroke(2.5f))

            // Angled glowing red eyes
            val leftEye = Path().apply {
                moveTo(cx - r * 0.28f, cy + r * 0.05f)
                lineTo(cx - r * 0.1f, cy + r * 0.12f)
                lineTo(cx - r * 0.22f, cy + r * 0.18f)
                close()
            }
            val rightEye = Path().apply {
                moveTo(cx + r * 0.28f, cy + r * 0.05f)
                lineTo(cx + r * 0.1f, cy + r * 0.12f)
                lineTo(cx + r * 0.22f, cy + r * 0.18f)
                close()
            }
            drawPath(leftEye, Color(0xFFFEB2B2))
            drawPath(rightEye, Color(0xFFFEB2B2))

            // Sinister sharp grin
            val smile = Path().apply {
                moveTo(cx - r * 0.2f, cy + r * 0.35f)
                lineTo(cx - r * 0.1f, cy + r * 0.42f)
                lineTo(cx, cy + r * 0.38f)
                lineTo(cx + r * 0.1f, cy + r * 0.42f)
                lineTo(cx + r * 0.2f, cy + r * 0.35f)
                lineTo(cx, cy + r * 0.48f)
                close()
            }
            drawPath(smile, Color(0xFFFEB2B2))
        }

        Role.Human -> {
            // Calm raised oil lantern with hopeful radiating warm light rays
            // Radiant light halo
            drawCircle(Color(0xFFFFD54F).copy(alpha = 0.35f), r * 0.65f, Offset(cx, cy - r * 0.05f))

            // Lantern roof and cap
            val cap = Path().apply {
                moveTo(cx - r * 0.35f, cy - r * 0.28f)
                lineTo(cx, cy - r * 0.55f)
                lineTo(cx + r * 0.35f, cy - r * 0.28f)
                close()
            }
            drawPath(cap, Color(0xFF2D3748))

            // Lantern handle
            val handle = Path().apply {
                moveTo(cx - r * 0.18f, cy - r * 0.55f)
                quadraticTo(cx, cy - r * 0.8f, cx + r * 0.18f, cy - r * 0.55f)
            }
            drawPath(handle, Color(0xFFE2E8F0), style = Stroke(3f))

            // Glass lantern chamber with warm candle flame inside
            drawRoundRect(
                Color(0xFFFFF6D6),
                Offset(cx - r * 0.28f, cy - r * 0.28f),
                Size(r * 0.56f, r * 0.58f),
                CornerRadius(4f)
            )
            drawCircle(Color(0xFFFF9800), r * 0.12f, Offset(cx, cy + r * 0.05f))

            // Lantern metal base
            drawRoundRect(
                Color(0xFF2D3748),
                Offset(cx - r * 0.32f, cy + r * 0.3f),
                Size(r * 0.64f, r * 0.15f),
                CornerRadius(3f)
            )
        }
    }
}

/** 3:4 Gothic Role Card (Front). */
@Composable
fun GothicRoleCard(
    role: Role,
    lover: String? = null,
    otherZombies: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val (bgTop, bgBottom) = HousePalette.colors(role)
    val accent = HousePalette.accent(role)

    Box(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGothicFrame(accent)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Role title at top
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = role.title,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = accent,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (role.zombie) t("ΟΜΑΔΑ ΖΟΜΠΙ", "ZOMBIE TEAM") else t("ΟΜΑΔΑ ΑΝΘΡΩΠΩΝ", "HUMAN TEAM"),
                    style = Type.label,
                    color = accent.copy(alpha = 0.7f),
                    letterSpacing = 1.2.sp
                )
            }

            // Center vector emblem
            Box(
                modifier = Modifier
                    .size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRoleEmblem(role, accent)
                }
            }

            // Description and partners at bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = role.what,
                    style = Type.body,
                    color = Color(0xFFF7FAFC),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )

                if (role.zombie && otherZombies.isNotEmpty()) {
                    Text(
                        text = t("Τα άλλα ζόμπι: ", "Other zombies: ") + otherZombies.joinToString(", "),
                        style = Type.small,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF68D391),
                        textAlign = TextAlign.Center
                    )
                }

                lover?.let {
                    Text(
                        text = t("💞 Είσαι ερωτευμένος/η με: $it", "💞 Bound to: $it"),
                        style = Type.small,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF687B3),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** 3:4 Gothic Role Card (Back). Identical for all roles. */
@Composable
fun GothicRoleCardBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C0A19), Color(0xFF16122C), Color(0xFF090714))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawVictorianBack()
        }
    }
}

/**
 * Animated background for the Haunted House:
 * Night: Midnight purple/blue sky, glowing moon with halo, haunted mansion silhouette, drifting fog.
 * Day: Cold dawn sky, crows perched, mist lifting.
 * Crossfades smoothly over 600ms.
 */
@Composable
fun HouseBackground(
    isNight: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val fogTransition = rememberInfiniteTransition(label = "fog")
    val fogShift by fogTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fogShift"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = isNight,
            animationSpec = tween(durationMillis = 600),
            label = "houseSkyCrossfade"
        ) { night ->
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                if (night) {
                    // Night sky gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF080614), Color(0xFF130E26), Color(0xFF1C1335))
                        ),
                        size = size
                    )

                    // Moon with soft atmospheric halo
                    val moonCenter = Offset(w * 0.8f, h * 0.14f)
                    val moonR = w * 0.1f
                    drawCircle(Color(0xFFE2E8F0).copy(alpha = 0.12f), moonR * 2.2f, moonCenter)
                    drawCircle(Color(0xFFF7FAFC).copy(alpha = 0.25f), moonR * 1.5f, moonCenter)
                    drawCircle(Color(0xFFFAF5FF), moonR, moonCenter)
                    // Crescent shadow to give subtle 3D crescent shape
                    drawCircle(Color(0xFF0E0B1F), moonR * 0.88f, Offset(moonCenter.x - moonR * 0.35f, moonCenter.y - moonR * 0.2f))

                    // Distant silhouette of Victorian Greek country house with towers, gable, chimneys
                    val houseY = h * 0.65f
                    val mansion = Path().apply {
                        moveTo(0f, h)
                        lineTo(0f, houseY + h * 0.08f)
                        // Left outbuilding
                        lineTo(w * 0.12f, houseY + h * 0.05f)
                        lineTo(w * 0.18f, houseY - h * 0.02f)
                        lineTo(w * 0.24f, houseY + h * 0.05f)
                        // Main mansion facade
                        lineTo(w * 0.3f, houseY)
                        // Main steep roof
                        lineTo(w * 0.48f, houseY - h * 0.12f)
                        // Gable spire
                        lineTo(w * 0.48f, houseY - h * 0.18f)
                        lineTo(w * 0.5f, houseY - h * 0.18f)
                        lineTo(w * 0.5f, houseY - h * 0.12f)
                        lineTo(w * 0.68f, houseY)
                        // Right tower with pointed roof
                        lineTo(w * 0.72f, houseY - h * 0.05f)
                        lineTo(w * 0.78f, houseY - h * 0.16f)
                        lineTo(w * 0.84f, houseY - h * 0.05f)
                        lineTo(w * 0.88f, houseY + h * 0.06f)
                        lineTo(w, houseY + h * 0.08f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(mansion, Color(0xFF070510))

                    // One faintly lit window in attic
                    drawRect(
                        Color(0xFFFFD166).copy(alpha = 0.65f),
                        Offset(w * 0.47f, houseY - h * 0.06f),
                        Size(w * 0.045f, h * 0.035f)
                    )

                    // Drifting animated fog layers along bottom
                    val fogH = h * 0.22f
                    for (layer in 0..1) {
                        val shift = (fogShift + layer * 0.5f) % 1f
                        val fogAlpha = if (layer == 0) 0.18f else 0.12f
                        val fx = -w + shift * w * 2f
                        drawOval(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFCBD5E0).copy(alpha = fogAlpha), Color.Transparent),
                                center = Offset(fx, h - fogH * 0.5f),
                                radius = w * 0.9f
                            ),
                            topLeft = Offset(fx - w * 0.6f, h - fogH),
                            size = Size(w * 1.5f, fogH * 1.2f)
                        )
                    }
                } else {
                    // Day / Dawn sky gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF2A2D3E), Color(0xFF45495E), Color(0xFF63687E))
                        ),
                        size = size
                    )

                    // Pale morning sun behind thin clouds
                    val sunCenter = Offset(w * 0.75f, h * 0.18f)
                    drawCircle(Color(0xFFFFFDF5).copy(alpha = 0.35f), w * 0.18f, sunCenter)
                    drawCircle(Color(0xFFFFFBEA).copy(alpha = 0.65f), w * 0.09f, sunCenter)

                    // Mansion silhouette in pale dawn light
                    val houseY = h * 0.65f
                    val mansion = Path().apply {
                        moveTo(0f, h)
                        lineTo(0f, houseY + h * 0.08f)
                        lineTo(w * 0.12f, houseY + h * 0.05f)
                        lineTo(w * 0.18f, houseY - h * 0.02f)
                        lineTo(w * 0.24f, houseY + h * 0.05f)
                        lineTo(w * 0.3f, houseY)
                        lineTo(w * 0.48f, houseY - h * 0.12f)
                        lineTo(w * 0.48f, houseY - h * 0.18f)
                        lineTo(w * 0.5f, houseY - h * 0.18f)
                        lineTo(w * 0.5f, houseY - h * 0.12f)
                        lineTo(w * 0.68f, houseY)
                        lineTo(w * 0.72f, houseY - h * 0.05f)
                        lineTo(w * 0.78f, houseY - h * 0.16f)
                        lineTo(w * 0.84f, houseY - h * 0.05f)
                        lineTo(w * 0.88f, houseY + h * 0.06f)
                        lineTo(w, houseY + h * 0.08f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(mansion, Color(0xFF1E202B))

                    // Crows perched on roof or flying
                    val crows = listOf(
                        Offset(w * 0.48f, houseY - h * 0.19f),
                        Offset(w * 0.78f, houseY - h * 0.17f),
                        Offset(w * 0.28f, houseY - h * 0.08f)
                    )
                    crows.forEach { crow ->
                        drawCircle(Color(0xFF111217), 3.5f, crow)
                        drawLine(Color(0xFF111217), crow, Offset(crow.x + 4f, crow.y - 2f), 1.5f)
                    }
                }
            }
        }

        // Foreground game content
        content()
    }
}
