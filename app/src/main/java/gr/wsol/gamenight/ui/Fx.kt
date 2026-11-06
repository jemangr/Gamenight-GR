package gr.wsol.gamenight.ui

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import gr.wsol.gamenight.core.Store
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** True if the user enabled «Λιγότερη κίνηση» or the system disabled animations. */
@Composable
fun rememberReducedMotion(): Boolean {
    if (Store.settings.reduceMotion) return true
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

/** Triggers haptic feedback if the user has enabled haptics in Settings. */
fun haptic(haptic: HapticFeedback?, kind: HapticFeedbackType = HapticFeedbackType.TextHandleMove) {
    if (Store.settings.haptics) {
        haptic?.performHapticFeedback(kind)
    }
}

/** Composable wrapper for haptics. */
@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { kind -> haptic(h, kind) } }
}

/** Horizontal shake modifier (+-6dp, 250ms). No-op if reduced motion is active. */
@Composable
fun Modifier.shake(trigger: Any?): Modifier {
    val reduced = rememberReducedMotion()
    if (reduced || trigger == null) return this
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        // 250ms shake sequence
        offsetX.snapTo(0f)
        offsetX.animateTo(-6f, tween(40, easing = FastOutSlowInEasing))
        offsetX.animateTo(6f, tween(50, easing = FastOutSlowInEasing))
        offsetX.animateTo(-4f, tween(50, easing = FastOutSlowInEasing))
        offsetX.animateTo(4f, tween(50, easing = FastOutSlowInEasing))
        offsetX.animateTo(0f, tween(60, easing = FastOutSlowInEasing))
    }

    val density = LocalDensity.current
    return this.offset { IntOffset((offsetX.value * density.density).roundToInt(), 0) }
}

/** Gentle pulse scale (1.00 -> 1.06) for timers and alerts. No-op if reduced motion is active. */
@Composable
fun Modifier.pulse(active: Boolean): Modifier {
    val reduced = rememberReducedMotion()
    if (reduced || !active) return this

    val infinite = rememberInfiniteTransition(label = "pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    return this.scale(scale)
}

/** 3D card flip animation. Falls back to Crossfade when reduced motion is active. */
@Composable
fun FlipCard(
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
    flipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    if (reduced) {
        Crossfade(targetState = flipped, label = "flipCrossfade", modifier = modifier) { isFlipped ->
            if (isFlipped) front() else back()
        }
    } else {
        val rotation by animateFloatAsState(
            targetValue = if (flipped) 180f else 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "cardRotation",
        )
        Box(
            modifier = modifier.graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            },
        ) {
            if (rotation <= 90f) {
                back()
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }) {
                    front()
                }
            }
        }
    }
}

private class ConfettiPiece(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var rotation: Float = 0f,
    var vRot: Float = 0f,
    var width: Float = 10f,
    var height: Float = 6f,
    var color: Color = Color.White,
    var active: Boolean = false,
)

/** Confetti shower with up to 100 particles lasting 1.8 seconds. */
@Composable
fun Confetti(trigger: Any?, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    if (reduced || trigger == null) return

    val colors = listOf(C.amber, C.pink, C.ghost, C.green, C.cyan, C.lime)
    val pool = remember { Array(100) { ConfettiPiece() } }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        val random = Random(System.currentTimeMillis())
        for (p in pool) {
            p.x = random.nextFloat() * 1000f // will scale in canvas
            p.y = -random.nextFloat() * 200f
            p.vx = (random.nextFloat() - 0.5f) * 120f
            p.vy = 220f + random.nextFloat() * 320f
            p.rotation = random.nextFloat() * 360f
            p.vRot = (random.nextFloat() - 0.5f) * 360f
            p.width = 12f + random.nextFloat() * 10f
            p.height = 6f + random.nextFloat() * 6f
            p.color = colors[random.nextInt(colors.size)]
            p.active = true
        }
        running = true
        var elapsed = 0L
        var lastNanos = 0L
        while (elapsed < 1_800_000_000L) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = (now - lastNanos) / 1_000_000_000f
                    for (p in pool) {
                        p.x += p.vx * dt
                        p.y += p.vy * dt
                        p.rotation += p.vRot * dt
                    }
                }
                lastNanos = now
            }
            elapsed += 16_000_000L
        }
        running = false
    }

    if (running) {
        Canvas(modifier.fillMaxSize()) {
            val scaleX = size.width / 1000f
            for (p in pool) {
                if (!p.active) continue
                drawRect(
                    color = p.color,
                    topLeft = Offset(p.x * scaleX, p.y),
                    size = Size(p.width, p.height),
                )
            }
        }
    }
}

/** Pre-allocated particle pool for 60fps arcade graphics without GC allocations. */
class Particles(val maxCount: Int = 120) {
    class Particle {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 1f
        var color = Color.White
        var size = 4f
        var active = false
    }

    private val pool = Array(maxCount) { Particle() }
    private var nextIndex = 0

    fun emit(x: Float, y: Float, color: Color, count: Int, speed: Float = 120f, size: Float = 6f, life: Float = 0.6f) {
        val random = Random.Default
        val n = count.coerceAtMost(maxCount)
        for (i in 0 until n) {
            val p = pool[nextIndex]
            nextIndex = (nextIndex + 1) % maxCount
            p.x = x
            p.y = y
            val angle = random.nextFloat() * 6.2831853f
            val s = speed * (0.3f + random.nextFloat() * 0.7f)
            p.vx = cos(angle) * s
            p.vy = sin(angle) * s
            p.maxLife = life * (0.6f + random.nextFloat() * 0.4f)
            p.life = p.maxLife
            p.color = color
            p.size = size * (0.6f + random.nextFloat() * 0.5f)
            p.active = true
        }
    }

    fun update(dt: Float) {
        for (p in pool) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
        }
    }

    fun draw(drawScope: DrawScope) {
        for (p in pool) {
            if (!p.active) continue
            val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawScope.drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.size * alpha,
                center = Offset(p.x, p.y),
            )
        }
    }
}

/** Draws a glowing neon stroke along a path using 3 layered strokes: broad faint, medium, and sharp core. */
fun DrawScope.drawNeonPath(path: Path, color: Color, strokeWidth: Float = 3f) {
    drawPath(path, color.copy(alpha = 0.15f), style = Stroke(strokeWidth * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color.copy(alpha = 0.38f), style = Stroke(strokeWidth * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, lerp(color, Color.White, 0.45f), style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Draws a glowing neon line between two points. */
fun DrawScope.drawNeonLine(color: Color, start: Offset, end: Offset, strokeWidth: Float = 3f) {
    drawLine(color.copy(alpha = 0.15f), start, end, strokeWidth * 4f, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = 0.38f), start, end, strokeWidth * 2f, cap = StrokeCap.Round)
    drawLine(lerp(color, Color.White, 0.45f), start, end, strokeWidth, cap = StrokeCap.Round)
}

/** Draws a glowing neon rectangle. */
fun DrawScope.drawNeonRect(color: Color, topLeft: Offset, size: Size, cornerRadius: Float = 0f, strokeWidth: Float = 3f) {
    if (cornerRadius > 0f) {
        val cr = CornerRadius(cornerRadius)
        drawRoundRect(color.copy(alpha = 0.15f), topLeft, size, cr, style = Stroke(strokeWidth * 4f))
        drawRoundRect(color.copy(alpha = 0.38f), topLeft, size, cr, style = Stroke(strokeWidth * 2f))
        drawRoundRect(lerp(color, Color.White, 0.45f), topLeft, size, cr, style = Stroke(strokeWidth))
    } else {
        drawRect(color.copy(alpha = 0.15f), topLeft, size, style = Stroke(strokeWidth * 4f))
        drawRect(color.copy(alpha = 0.38f), topLeft, size, style = Stroke(strokeWidth * 2f))
        drawRect(lerp(color, Color.White, 0.45f), topLeft, size, style = Stroke(strokeWidth))
    }
}

/** Draws a glowing neon circle outline. */
fun DrawScope.drawNeonCircle(color: Color, center: Offset, radius: Float, strokeWidth: Float = 3f) {
    drawCircle(color.copy(alpha = 0.15f), radius, center, style = Stroke(strokeWidth * 4f))
    drawCircle(color.copy(alpha = 0.38f), radius, center, style = Stroke(strokeWidth * 2f))
    drawCircle(lerp(color, Color.White, 0.45f), radius, center, style = Stroke(strokeWidth))
}

/** Draws a glowing neon arc. */
fun DrawScope.drawNeonArc(color: Color, startAngle: Float, sweepAngle: Float, topLeft: Offset, size: Size, strokeWidth: Float = 3f) {
    drawArc(color.copy(alpha = 0.15f), startAngle, sweepAngle, false, topLeft, size, style = Stroke(strokeWidth * 4f))
    drawArc(color.copy(alpha = 0.38f), startAngle, sweepAngle, false, topLeft, size, style = Stroke(strokeWidth * 2f))
    drawArc(lerp(color, Color.White, 0.45f), startAngle, sweepAngle, false, topLeft, size, style = Stroke(strokeWidth))
}

/**
 * Draws a glowing arcade neon ball: radial gradient, small specular highlight top-left,
 * and a fading trail of past positions.
 */
fun DrawScope.drawNeonBall(color: Color, center: Offset, radius: Float, trail: List<Offset> = emptyList()) {
    if (trail.isNotEmpty()) {
        val count = trail.size
        for (i in trail.indices) {
            val progress = (i + 1f) / count
            val tr = radius * (0.35f + 0.65f * progress)
            val ta = progress * 0.4f
            drawCircle(color.copy(alpha = ta), tr, trail[i])
        }
    }
    // Outer glow
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.15f), Color.Transparent),
            center = center,
            radius = radius * 1.8f
        ),
        radius = radius * 1.8f,
        center = center
    )
    // Ball body with spherical radial gradient
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White, color, lerp(color, Color.Black, 0.45f)),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
            radius = radius
        ),
        radius = radius,
        center = center
    )
    // Specular shine dot
    drawCircle(
        Color.White.copy(alpha = 0.85f),
        radius * 0.22f,
        Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
    )
}

/**
 * Shared retro-arcade background: deep gradient, parallax starfield,
 * perspective grid, and optional edge vignette flash.
 */
@Composable
fun ArcadeBackground(modifier: Modifier = Modifier, flashRed: Boolean = false) {
    val reduced = rememberReducedMotion()
    val stars = remember {
        val rnd = Random(42)
        List(52) {
            Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() < 0.45f)
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "arcadeBackground")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starDrift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Deep space gradient: top #0B0A1F to bottom #05040D
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF0B0A1F), Color(0xFF080614), Color(0xFF05040D))
            )
        )

        // 2. Parallax stars (2 layers)
        val drift = if (reduced) 0f else time
        for ((sx, sy, isFast) in stars) {
            val speed = if (isFast) 0.16f else 0.08f
            val curY = ((sy + drift * speed) % 1f) * h
            val curX = sx * w
            val alpha = if (isFast) 0.6f else 0.25f
            val r = if (isFast) 1.8f else 1.1f
            drawCircle(Color.White.copy(alpha = alpha), r, Offset(curX, curY))
        }

        // 3. Perspective grid lines at the lower section
        val gridTop = h * 0.62f
        val vanishPoint = Offset(w / 2f, gridTop - 70f)
        val gridLines = 8
        var yOff = 0f
        var step = 12f
        while (gridTop + yOff < h) {
            val progress = yOff / (h - gridTop)
            val alpha = progress.coerceIn(0.03f, 0.14f)
            drawLine(
                Color(0xFF7C4DFF).copy(alpha = alpha),
                Offset(0f, gridTop + yOff),
                Offset(w, gridTop + yOff),
                1.5f
            )
            yOff += step
            step += 7f
        }
        for (i in 0..gridLines) {
            val bottomX = (w / gridLines) * i
            drawLine(
                Color(0xFF7C4DFF).copy(alpha = 0.07f),
                vanishPoint,
                Offset(bottomX, h),
                1.5f
            )
        }

        // 4. Red vignette flash
        if (flashRed) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color(0x66FF1744)),
                    center = Offset(w / 2, h / 2),
                    radius = maxOf(w, h) * 0.72f
                )
            )
        }
    }
}
