package gr.wsol.gamenight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Deterministic vector monster avatar.
 * Generates body, horns/ears (8 types), eyes (6 types), and mouth (6 types)
 * based on player name and avatar variant.
 * 8 × 6 × 6 = 288 combinations.
 */
@Composable
fun MonsterAvatar(
    name: String,
    color: Color,
    variant: Int = 0,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            drawMonster(name = name, baseColor = color, variant = variant)
        }
    }
}

private fun DrawScope.drawMonster(name: String, baseColor: Color, variant: Int) {
    val w = size.width
    val h = size.height

    // Deterministic seed based on name and variant
    val rawHash = name.fold(0L) { acc, c -> (acc * 31L + c.code) and 0x7FFFFFFFL }
    val seed = abs(rawHash + variant * 37L)

    val hornType = (seed % 8).toInt()
    val eyeType = ((seed / 8) % 6).toInt()
    val mouthType = ((seed / 48) % 6).toInt()
    val bodyType = ((seed / 288) % 4).toInt()

    // 1. Draw Horns/Ears (Behind body)
    drawHorns(hornType, baseColor, w, h)

    // 2. Draw Body
    drawBody(bodyType, baseColor, w, h)

    // 3. Draw Eyes
    drawEyes(eyeType, w, h)

    // 4. Draw Cheeks
    val cheekR = w * 0.055f
    val cheekY = h * 0.58f
    val blushColor = Color(0xFFFF5FA2).copy(alpha = 0.55f)
    drawCircle(blushColor, radius = cheekR, center = Offset(w * 0.28f, cheekY))
    drawCircle(blushColor, radius = cheekR, center = Offset(w * 0.72f, cheekY))

    // 5. Draw Mouth
    drawMouth(mouthType, w, h)
}

private fun DrawScope.drawHorns(type: Int, baseColor: Color, w: Float, h: Float) {
    val strokeW = (w * 0.045f).coerceAtLeast(1.5f)
    val hornColor = Color(
        red = (baseColor.red * 0.85f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.85f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.85f).coerceIn(0f, 1f),
        alpha = 1f
    )

    when (type) {
        // 0: Two curved horns pointing up and out
        0 -> {
            val p1 = Path().apply {
                moveTo(w * 0.32f, h * 0.30f)
                quadraticTo(w * 0.20f, h * 0.12f, w * 0.15f, h * 0.10f)
                quadraticTo(w * 0.28f, h * 0.20f, w * 0.38f, h * 0.28f)
                close()
            }
            val p2 = Path().apply {
                moveTo(w * 0.68f, h * 0.30f)
                quadraticTo(w * 0.80f, h * 0.12f, w * 0.85f, h * 0.10f)
                quadraticTo(w * 0.72f, h * 0.20f, w * 0.62f, h * 0.28f)
                close()
            }
            drawPath(p1, hornColor)
            drawPath(p1, Color.Black.copy(alpha = 0.35f), style = Stroke(strokeW))
            drawPath(p2, hornColor)
            drawPath(p2, Color.Black.copy(alpha = 0.35f), style = Stroke(strokeW))
        }

        // 1: Cute pointed cat / bear ears
        1 -> {
            val ear1 = Path().apply {
                moveTo(w * 0.22f, h * 0.36f)
                lineTo(w * 0.24f, h * 0.14f)
                lineTo(w * 0.40f, h * 0.26f)
                close()
            }
            val ear2 = Path().apply {
                moveTo(w * 0.78f, h * 0.36f)
                lineTo(w * 0.76f, h * 0.14f)
                lineTo(w * 0.60f, h * 0.26f)
                close()
            }
            drawPath(ear1, hornColor)
            drawPath(ear2, hornColor)
            drawPath(ear1, Color.Black.copy(alpha = 0.35f), style = Stroke(strokeW))
            drawPath(ear2, Color.Black.copy(alpha = 0.35f), style = Stroke(strokeW))
            // Inner pink ear
            drawCircle(Color(0xFFFF94B8).copy(alpha = 0.6f), radius = w * 0.04f, center = Offset(w * 0.28f, h * 0.24f))
            drawCircle(Color(0xFFFF94B8).copy(alpha = 0.6f), radius = w * 0.04f, center = Offset(w * 0.72f, h * 0.24f))
        }

        // 2: Single antenna with glowing bulb
        2 -> {
            drawLine(
                color = hornColor,
                start = Offset(w * 0.5f, h * 0.28f),
                end = Offset(w * 0.5f, h * 0.11f),
                strokeWidth = strokeW * 1.4f,
                cap = StrokeCap.Round
            )
            drawCircle(Color(0xFFFFD700), radius = w * 0.065f, center = Offset(w * 0.5f, h * 0.10f))
            drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.48f, h * 0.085f))
        }

        // 3: Droopy bunny / puppy ears
        3 -> {
            drawRoundRect(
                color = hornColor,
                topLeft = Offset(w * 0.10f, h * 0.32f),
                size = Size(w * 0.15f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.075f)
            )
            drawRoundRect(
                color = hornColor,
                topLeft = Offset(w * 0.75f, h * 0.32f),
                size = Size(w * 0.15f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.075f)
            )
        }

        // 4: Spiky dinosaur ridges (3 spikes on top)
        4 -> {
            val spikes = listOf(
                Offset(w * 0.38f, h * 0.26f) to Offset(w * 0.42f, h * 0.14f),
                Offset(w * 0.48f, h * 0.24f) to Offset(w * 0.50f, h * 0.10f),
                Offset(w * 0.58f, h * 0.26f) to Offset(w * 0.58f, h * 0.14f),
            )
            spikes.forEach { (base, tip) ->
                val p = Path().apply {
                    moveTo(base.x - w * 0.04f, base.y)
                    lineTo(tip.x, tip.y)
                    lineTo(base.x + w * 0.04f, base.y)
                    close()
                }
                drawPath(p, hornColor)
                drawPath(p, Color.Black.copy(alpha = 0.3f), style = Stroke(strokeW))
            }
        }

        // 5: Two alien antennae with bulbs
        5 -> {
            drawLine(hornColor, Offset(w * 0.38f, h * 0.26f), Offset(w * 0.28f, h * 0.12f), strokeWidth = strokeW * 1.2f, cap = StrokeCap.Round)
            drawLine(hornColor, Offset(w * 0.62f, h * 0.26f), Offset(w * 0.72f, h * 0.12f), strokeWidth = strokeW * 1.2f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF39FF88), radius = w * 0.05f, center = Offset(w * 0.28f, h * 0.11f))
            drawCircle(Color(0xFF39FF88), radius = w * 0.05f, center = Offset(w * 0.72f, h * 0.11f))
        }

        // 6: Fluffy ear puffs on sides
        6 -> {
            drawCircle(hornColor, radius = w * 0.09f, center = Offset(w * 0.16f, h * 0.45f))
            drawCircle(hornColor, radius = w * 0.09f, center = Offset(w * 0.84f, h * 0.45f))
            drawCircle(Color.White.copy(alpha = 0.2f), radius = w * 0.04f, center = Offset(w * 0.15f, h * 0.43f))
            drawCircle(Color.White.copy(alpha = 0.2f), radius = w * 0.04f, center = Offset(w * 0.83f, h * 0.43f))
        }

        // 7: Mini crown / tiara
        else -> {
            val crown = Path().apply {
                moveTo(w * 0.34f, h * 0.27f)
                lineTo(w * 0.32f, h * 0.15f)
                lineTo(w * 0.42f, h * 0.22f)
                lineTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.58f, h * 0.22f)
                lineTo(w * 0.68f, h * 0.15f)
                lineTo(w * 0.66f, h * 0.27f)
                close()
            }
            drawPath(crown, Color(0xFFFFD700))
            drawPath(crown, Color(0xFFB39200), style = Stroke(strokeW))
        }
    }
}

private fun DrawScope.drawBody(type: Int, baseColor: Color, w: Float, h: Float) {
    val lightTint = Color(
        red = (baseColor.red * 1.15f).coerceIn(0f, 1f),
        green = (baseColor.green * 1.15f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 1.15f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val darkTint = Color(
        red = (baseColor.red * 0.75f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.75f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.75f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val bodyBrush = Brush.verticalGradient(
        listOf(lightTint, baseColor, darkTint),
        startY = h * 0.22f,
        endY = h * 0.90f
    )
    val strokeW = (w * 0.045f).coerceAtLeast(1.5f)
    val borderCol = Color.Black.copy(alpha = 0.35f)

    when (type) {
        // 0: Rounded capsule / bean
        0 -> {
            val rect = Size(w * 0.64f, h * 0.66f)
            val top = Offset(w * 0.18f, h * 0.24f)
            val corner = CornerRadius(w * 0.32f, h * 0.33f)
            drawRoundRect(bodyBrush, top, rect, corner)
            drawRoundRect(borderCol, top, rect, corner, style = Stroke(strokeW))
        }

        // 1: Squoval with soft curves
        1 -> {
            val rect = Size(w * 0.64f, h * 0.64f)
            val top = Offset(w * 0.18f, h * 0.25f)
            val corner = CornerRadius(w * 0.22f, w * 0.22f)
            drawRoundRect(bodyBrush, top, rect, corner)
            drawRoundRect(borderCol, top, rect, corner, style = Stroke(strokeW))
        }

        // 2: Pear / egg shape (narrow top, wide bottom)
        2 -> {
            val p = Path().apply {
                moveTo(w * 0.32f, h * 0.30f)
                quadraticTo(w * 0.50f, h * 0.20f, w * 0.68f, h * 0.30f)
                quadraticTo(w * 0.86f, h * 0.55f, w * 0.82f, h * 0.78f)
                quadraticTo(w * 0.50f, h * 0.94f, w * 0.18f, h * 0.78f)
                quadraticTo(w * 0.14f, h * 0.55f, w * 0.32f, h * 0.30f)
                close()
            }
            drawPath(p, bodyBrush)
            drawPath(p, borderCol, style = Stroke(strokeW))
        }

        // 3: Round chubby ball
        else -> {
            val center = Offset(w * 0.50f, h * 0.58f)
            val radius = w * 0.34f
            drawCircle(bodyBrush, radius = radius, center = center)
            drawCircle(borderCol, radius = radius, center = center, style = Stroke(strokeW))
        }
    }

    // Belly soft oval highlight
    drawOval(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(w * 0.35f, h * 0.60f),
        size = Size(w * 0.30f, h * 0.22f)
    )
}

private fun DrawScope.drawEyes(type: Int, w: Float, h: Float) {
    val eyeY = h * 0.44f
    val strokeW = (w * 0.04f).coerceAtLeast(1.5f)

    when (type) {
        // 0: Two large round friendly eyes
        0 -> {
            val r = w * 0.09f
            drawCircle(Color.White, radius = r, center = Offset(w * 0.36f, eyeY))
            drawCircle(Color.White, radius = r, center = Offset(w * 0.64f, eyeY))
            // Pupils
            drawCircle(Color(0xFF1B1926), radius = r * 0.55f, center = Offset(w * 0.37f, eyeY))
            drawCircle(Color(0xFF1B1926), radius = r * 0.55f, center = Offset(w * 0.65f, eyeY))
            // Specular highlights
            drawCircle(Color.White, radius = r * 0.22f, center = Offset(w * 0.34f, eyeY - r * 0.25f))
            drawCircle(Color.White, radius = r * 0.22f, center = Offset(w * 0.62f, eyeY - r * 0.25f))
        }

        // 1: Giant friendly Cyclops eye
        1 -> {
            val r = w * 0.15f
            val eyeCenter = Offset(w * 0.50f, eyeY - h * 0.02f)
            drawCircle(Color.White, radius = r, center = eyeCenter)
            drawCircle(Color(0xFF00E5FF), radius = r * 0.65f, center = eyeCenter)
            drawCircle(Color(0xFF1B1926), radius = r * 0.40f, center = eyeCenter)
            drawCircle(Color.White, radius = r * 0.22f, center = Offset(eyeCenter.x - r * 0.28f, eyeCenter.y - r * 0.28f))
        }

        // 2: Happy squint blinking eyes (^ ^)
        2 -> {
            val arc1 = Path().apply {
                moveTo(w * 0.28f, eyeY + h * 0.02f)
                quadraticTo(w * 0.36f, eyeY - h * 0.05f, w * 0.44f, eyeY + h * 0.02f)
            }
            val arc2 = Path().apply {
                moveTo(w * 0.56f, eyeY + h * 0.02f)
                quadraticTo(w * 0.64f, eyeY - h * 0.05f, w * 0.72f, eyeY + h * 0.02f)
            }
            drawPath(arc1, Color(0xFF1B1926), style = Stroke(strokeW * 1.5f, cap = StrokeCap.Round))
            drawPath(arc2, Color(0xFF1B1926), style = Stroke(strokeW * 1.5f, cap = StrokeCap.Round))
        }

        // 3: Three alien eyes
        3 -> {
            val r = w * 0.065f
            val eyes = listOf(
                Offset(w * 0.50f, eyeY - h * 0.08f),
                Offset(w * 0.34f, eyeY + h * 0.01f),
                Offset(w * 0.66f, eyeY + h * 0.01f),
            )
            eyes.forEach { c ->
                drawCircle(Color.White, radius = r, center = c)
                drawCircle(Color(0xFF39FF88), radius = r * 0.6f, center = c)
                drawCircle(Color(0xFF1B1926), radius = r * 0.35f, center = c)
                drawCircle(Color.White, radius = r * 0.18f, center = Offset(c.x - r * 0.2f, c.y - r * 0.2f))
            }
        }

        // 4: Round glasses / goggles
        4 -> {
            val r = w * 0.11f
            val c1 = Offset(w * 0.35f, eyeY)
            val c2 = Offset(w * 0.65f, eyeY)
            // Lens glow
            drawCircle(Color(0xFF00E5FF).copy(alpha = 0.3f), radius = r, center = c1)
            drawCircle(Color(0xFF00E5FF).copy(alpha = 0.3f), radius = r, center = c2)
            // Eyes behind lens
            drawCircle(Color.White, radius = r * 0.7f, center = c1)
            drawCircle(Color.White, radius = r * 0.7f, center = c2)
            drawCircle(Color(0xFF1B1926), radius = r * 0.35f, center = c1)
            drawCircle(Color(0xFF1B1926), radius = r * 0.35f, center = c2)
            // Frames
            drawCircle(Color(0xFFFFB020), radius = r, center = c1, style = Stroke(strokeW * 1.3f))
            drawCircle(Color(0xFFFFB020), radius = r, center = c2, style = Stroke(strokeW * 1.3f))
            // Bridge
            drawLine(Color(0xFFFFB020), Offset(c1.x + r, c1.y), Offset(c2.x - r, c2.y), strokeWidth = strokeW * 1.3f)
        }

        // 5: Googly mismatched eyes (one big, one small)
        else -> {
            val r1 = w * 0.11f
            val r2 = w * 0.07f
            val c1 = Offset(w * 0.35f, eyeY - h * 0.01f)
            val c2 = Offset(w * 0.65f, eyeY + h * 0.01f)
            drawCircle(Color.White, radius = r1, center = c1)
            drawCircle(Color.White, radius = r2, center = c2)
            drawCircle(Color(0xFF1B1926), radius = r1 * 0.5f, center = Offset(c1.x + r1 * 0.15f, c1.y + r1 * 0.1f))
            drawCircle(Color(0xFF1B1926), radius = r2 * 0.5f, center = Offset(c2.x - r2 * 0.15f, c2.y))
            drawCircle(Color.White, radius = r1 * 0.2f, center = Offset(c1.x, c1.y - r1 * 0.25f))
            drawCircle(Color.White, radius = r2 * 0.2f, center = Offset(c2.x - r2 * 0.2f, c2.y - r2 * 0.2f))
        }
    }
}

private fun DrawScope.drawMouth(type: Int, w: Float, h: Float) {
    val mouthY = h * 0.68f
    val strokeW = (w * 0.04f).coerceAtLeast(1.5f)

    when (type) {
        // 0: Grin with cute little vampire fangs
        0 -> {
            val smile = Path().apply {
                moveTo(w * 0.34f, mouthY)
                quadraticTo(w * 0.50f, mouthY + h * 0.10f, w * 0.66f, mouthY)
            }
            drawPath(smile, Color(0xFF1B1926), style = Stroke(strokeW * 1.3f, cap = StrokeCap.Round))
            // Tiny white fangs
            val fang1 = Path().apply {
                moveTo(w * 0.42f, mouthY + h * 0.02f)
                lineTo(w * 0.44f, mouthY + h * 0.06f)
                lineTo(w * 0.46f, mouthY + h * 0.02f)
                close()
            }
            val fang2 = Path().apply {
                moveTo(w * 0.54f, mouthY + h * 0.02f)
                lineTo(w * 0.56f, mouthY + h * 0.06f)
                lineTo(w * 0.58f, mouthY + h * 0.02f)
                close()
            }
            drawPath(fang1, Color.White)
            drawPath(fang2, Color.White)
        }

        // 1: Big open happy smile with pink tongue
        1 -> {
            val mouth = Path().apply {
                moveTo(w * 0.35f, mouthY)
                quadraticTo(w * 0.50f, mouthY + h * 0.12f, w * 0.65f, mouthY)
                close()
            }
            drawPath(mouth, Color(0xFF5A1A28))
            // Tongue inside
            val tongue = Path().apply {
                moveTo(w * 0.42f, mouthY + h * 0.06f)
                quadraticTo(w * 0.50f, mouthY + h * 0.12f, w * 0.58f, mouthY + h * 0.06f)
                close()
            }
            drawPath(tongue, Color(0xFFFF5FA2))
            drawPath(mouth, Color(0xFF1B1926), style = Stroke(strokeW))
        }

        // 2: Silly zigzag teeth smile
        2 -> {
            val zig = Path().apply {
                moveTo(w * 0.34f, mouthY)
                lineTo(w * 0.40f, mouthY + h * 0.04f)
                lineTo(w * 0.46f, mouthY)
                lineTo(w * 0.52f, mouthY + h * 0.04f)
                lineTo(w * 0.58f, mouthY)
                lineTo(w * 0.64f, mouthY + h * 0.04f)
                lineTo(w * 0.68f, mouthY)
            }
            drawPath(zig, Color(0xFF1B1926), style = Stroke(strokeW * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // 3: Cat / bunny mouth (:3)
        3 -> {
            val m1 = Path().apply {
                moveTo(w * 0.36f, mouthY)
                quadraticTo(w * 0.43f, mouthY + h * 0.05f, w * 0.50f, mouthY + h * 0.02f)
            }
            val m2 = Path().apply {
                moveTo(w * 0.50f, mouthY + h * 0.02f)
                quadraticTo(w * 0.57f, mouthY + h * 0.05f, w * 0.64f, mouthY)
            }
            drawPath(m1, Color(0xFF1B1926), style = Stroke(strokeW * 1.3f, cap = StrokeCap.Round))
            drawPath(m2, Color(0xFF1B1926), style = Stroke(strokeW * 1.3f, cap = StrokeCap.Round))
        }

        // 4: Surprised round 'O' mouth
        4 -> {
            val center = Offset(w * 0.50f, mouthY + h * 0.02f)
            drawOval(Color(0xFF5A1A28), topLeft = Offset(w * 0.44f, mouthY - h * 0.01f), size = Size(w * 0.12f, h * 0.08f))
            drawOval(Color(0xFF1B1926), topLeft = Offset(w * 0.44f, mouthY - h * 0.01f), size = Size(w * 0.12f, h * 0.08f), style = Stroke(strokeW))
        }

        // 5: Playful tongue sticking out (:P)
        else -> {
            drawLine(Color(0xFF1B1926), Offset(w * 0.35f, mouthY), Offset(w * 0.65f, mouthY), strokeWidth = strokeW * 1.3f, cap = StrokeCap.Round)
            // Tongue
            val tongue = Path().apply {
                moveTo(w * 0.48f, mouthY)
                lineTo(w * 0.48f, mouthY + h * 0.07f)
                quadraticTo(w * 0.54f, mouthY + h * 0.10f, w * 0.60f, mouthY + h * 0.07f)
                lineTo(w * 0.60f, mouthY)
                close()
            }
            drawPath(tongue, Color(0xFFFF5FA2))
            drawPath(tongue, Color(0xFF1B1926), style = Stroke(strokeW))
        }
    }
}
