package gr.wsol.gamenight.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.wsol.gamenight.core.Night
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.core.t
import gr.wsol.gamenight.extras.Sfx
import kotlinx.coroutines.delay

/** A page: back arrow, title, and the content in a scrolling column (or not, with [scroll] = false). */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Πίσω", "Back"), tint = C.text) }
            else Spacer(Modifier.width(12.dp))
            Text(title, style = Type.h, color = C.text, modifier = Modifier.weight(1f))
            actions()
        }
        val m = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
        Column(if (scroll) m.verticalScroll(rememberScrollState()) else m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            if (scroll) Spacer(Modifier.height(16.dp))
        }
        if (bottom != null) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = bottom)
    }
}

/** Card depth modifier: vertical gradient, highlight border, soft shadow, optional accent strip. */
fun Modifier.card(
    accent: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
): Modifier = this
    .shadow(2.dp, shape)
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(C.panel2, C.panel)
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color(
                    (C.line.red + (1f - C.line.red) * 0.20f).coerceIn(0f, 1f),
                    (C.line.green + (1f - C.line.green) * 0.20f).coerceIn(0f, 1f),
                    (C.line.blue + (1f - C.line.blue) * 0.20f).coerceIn(0f, 1f),
                    1f
                ),
                C.line
            )
        ),
        shape = shape,
    )
    .then(
        if (accent != null) {
            Modifier.drawWithContent {
                drawContent()
                drawRect(
                    color = accent,
                    topLeft = Offset.Zero,
                    size = Size(4.dp.toPx(), size.height)
                )
            }
        } else Modifier
    )

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.amber, enabled: Boolean = true, height: Dp = 60.dp) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && enabled) 0.97f else 1f, label = "btnScale")
    val hapticFeedback = LocalHapticFeedback.current

    val btnBg = if (enabled) {
        val mult = if (isPressed) 0.88f else 1.0f
        Brush.verticalGradient(
            listOf(
                Color((color.red * mult * 1.05f).coerceAtMost(1f), (color.green * mult * 1.05f).coerceAtMost(1f), (color.blue * mult * 1.05f).coerceAtMost(1f)),
                Color(color.red * mult * 0.92f, color.green * mult * 0.92f, color.blue * mult * 0.92f)
            )
        )
    } else {
        SolidColor(C.panel2)
    }

    Box(
        modifier.fillMaxWidth().heightIn(min = height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(btnBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                haptic(hapticFeedback, HapticFeedbackType.TextHandleMove)
                Sfx.tap()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Color.Black else C.dim, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, textAlign = TextAlign.Center) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.text, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).border(1.5.dp, if (enabled) C.line else C.panel2, RoundedCornerShape(16.dp))
            .background(C.panel).clickable(enabled = enabled, role = Role.Button) { Sfx.tap(); onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) color else C.dim, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color? = null, accent: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = if (color != null) {
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color).border(1.dp, C.line, RoundedCornerShape(18.dp))
    } else {
        modifier.fillMaxWidth().card(accent)
    }
    Column(
        base.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.dim) = Text(text, style = Type.label, color = color, modifier = modifier)

@Composable
fun Body(text: String, color: Color = C.muted, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    Text(text, style = Type.body, color = color, modifier = modifier, textAlign = align)

@Composable
fun Dot(color: Color, size: Dp = 12.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Chip(text: String, selected: Boolean = false, color: Color = C.amber, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(999.dp))
            .background(if (selected) color else C.panel2).border(1.dp, if (selected) color else C.line, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { Sfx.tap(); onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Color.Black else C.text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp) }
}

/** A segmented choice (e.g. 30″ / 60″ / 90″). */
@Composable
fun <T> Seg(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, color: Color = C.amber) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.panel2).padding(3.dp)) {
        options.forEach { (v, label) ->
            val on = v == value
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp)).background(if (on) color else Color.Transparent)
                    .clickable(role = Role.RadioButton) { Sfx.tap(); onChange(v) }.padding(6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Color.Black else C.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun Toggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = C.text, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, style = Type.small, color = C.muted)
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = C.amber))
    }
}

/** A player's (or a team's) name with its colour and avatar or initial. */
@Composable
fun SideName(night: Night, side: String, style: androidx.compose.ui.text.TextStyle = Type.body, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val player = night.players.firstOrNull { it.name == side }
        if (player != null) {
            MonsterAvatar(name = side, color = Color(night.colorOf(side)), variant = player.avatar, size = 22.dp)
        } else if (side.startsWith("#")) {
            val tm = side.drop(1).toIntOrNull() ?: 0
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(Color(night.colorOf(side))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${tm + 1}",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        } else {
            Dot(Color(night.colorOf(side)))
        }
        Spacer(Modifier.width(8.dp))
        Text(night.label(side), style = style, color = C.text, fontWeight = FontWeight.Bold)
    }
}

/**
 * «Δώσε το κινητό στον Νίκο»: a full screen between turns, so nobody sees what isn't theirs.
 * The next step starts only when that player presses the button.
 */
@Composable
fun PassTo(name: String, color: Color, note: String? = null, button: String = t("Είμαι εγώ, πάμε", "It's me, go"), onReady: () -> Unit) {
    gr.wsol.gamenight.chaos.CalmScreen()
    val player = gr.wsol.gamenight.core.Store.night.players.firstOrNull { it.name == name }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (player != null) {
            MonsterAvatar(name = name, color = color, variant = player.avatar, size = 84.dp)
        } else {
            Text("📱", fontSize = 56.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(t("Δώσε το κινητό σε", "Pass the phone to"), style = Type.body, color = C.muted)
        Text(name, style = Type.big, color = color, textAlign = TextAlign.Center)
        if (note != null) { Spacer(Modifier.height(10.dp)); Body(note, align = TextAlign.Center) }
        Spacer(Modifier.height(28.dp))
        BigButton(button, { gr.wsol.gamenight.chaos.Chaos.holder = name; onReady() }, color = color)
    }
}

/** A secret (role, word, number): blurred until the player holds a finger on it. */
@Composable
fun HoldToReveal(modifier: Modifier = Modifier, hint: String = t("Κράτα πατημένο για να δεις", "Hold to see"), onSeen: () -> Unit = {}, content: @Composable () -> Unit) {
    var held by remember { mutableStateOf(false) }
    val seen = rememberUpdatedState(onSeen)
    val a by animateFloatAsState(if (held) 1f else 0f, label = "reveal")
    Box(
        modifier.fillMaxWidth().heightIn(min = 220.dp).clip(RoundedCornerShape(24.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(24.dp))
            .semantics { contentDescription = hint }
            .pointerInput(Unit) { detectTapGestures(onPress = { held = true; Sfx.buzz(15); tryAwaitRelease(); held = false; seen.value() }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.alpha(a).blur(((1 - a) * 24).dp).padding(20.dp), contentAlignment = Alignment.Center) { content() }
        if (a < 0.5f) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👆", fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(hint, style = Type.body, color = C.muted, textAlign = TextAlign.Center)
        }
    }
}

/**
 * A countdown of [seconds] that runs while [running]; [onEnd] once at zero. Returns the
 * milliseconds left. [key] restarts it.
 */
@Composable
fun rememberCountdown(seconds: Int, running: Boolean, key: Any? = Unit, tickLast: Int = 5, onEnd: () -> Unit): Long {
    var left by remember(key) { mutableLongStateOf(seconds * 1000L) }
    val end = rememberUpdatedState(onEnd)
    gr.wsol.gamenight.chaos.TimedScreen(running && left > 0)
    LaunchedEffect(key, running) {
        if (!running) return@LaunchedEffect
        // the time of the frames (Compose's clock): real time on the phone, and the tests can move it on
        var last = withFrameNanos { it }
        var lastSec = (left / 1000).toInt()
        while (left > 0) {
            delay(50)
            val now = withFrameNanos { it }
            left = (left - (now - last) / 1_000_000).coerceAtLeast(0)
            last = now
            val s = ((left + 999) / 1000).toInt()
            if (s != lastSec) { lastSec = s; if (s in 1..tickLast) { Sfx.tick(); Sfx.buzz(10) } }
        }
        Sfx.end(); Sfx.buzz(400, strong = true)
        end.value()
    }
    return left
}

@Composable
fun TimerText(ms: Long, color: Color = C.amber, modifier: Modifier = Modifier) {
    val s = ((ms + 999) / 1000).toInt()
    val isUrgent = s in 1..10
    val isZero = s == 0
    val pulse by rememberInfiniteTransition(label = "timerPulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isUrgent) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse),
        label = "tp"
    )
    val flashAlpha by rememberInfiniteTransition(label = "timerFlash").animateFloat(
        initialValue = 1f,
        targetValue = if (isZero) 0.25f else 1f,
        animationSpec = infiniteRepeatable(tween(150), RepeatMode.Reverse),
        label = "tf"
    )

    Text(
        text = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "$s″",
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Black,
        style = Type.huge,
        color = if (isUrgent || isZero) C.red else color,
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isUrgent) pulse else 1f)
            .alpha(if (isZero) flashAlpha else 1f),
        textAlign = TextAlign.Center,
    )
}

/** Points just given: «+2 Νίκος». */
@Composable
fun PointsLine(night: Night, side: String, pts: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SideName(night, side, modifier = Modifier.weight(1f))
        Text((if (pts > 0) "+" else "") + pts, style = Type.h, color = if (pts >= 0) C.green else C.red)
    }
}

/** The current night, read from the store (recomposes when points change). */
val night: Night get() = Store.night

/** A text field in the app's colours, focused at once (the keyboard opens). */
@Composable
fun Input(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.material3.OutlinedTextField(
        value, { onChange(it.take(60)) }, modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text(placeholder, color = C.dim) }, singleLine = true, textStyle = Type.title.copy(color = C.text),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = C.amber, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.amber),
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Lays the content out turned 90° (for a phone held sideways): it gets the height as its width. */
fun Modifier.sideways(): Modifier = this.layout { m, c ->
    val p = m.measure(androidx.compose.ui.unit.Constraints(maxWidth = c.maxHeight, maxHeight = c.maxWidth))
    layout(p.height, p.width) { p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = 90f } }
}
