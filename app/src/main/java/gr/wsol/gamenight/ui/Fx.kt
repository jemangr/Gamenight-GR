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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
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
