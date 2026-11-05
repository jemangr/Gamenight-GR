package gr.wsol.gamenight.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import gr.wsol.gamenight.core.t
import java.io.File

/** One line drawn with a finger; points are 0–1 of the pad, so it redraws at any size. */
data class Line(val color: Int, val points: List<Offset>, val width: Float = 0.012f)

/**
 * A white pad to draw on. [enabled] lets the finger draw; [onLine] gets each finished line,
 * [onStart] the first touch of a line (to start a 3″ clock, say).
 */
@Composable
fun DrawPad(lines: List<Line>, color: Int, enabled: Boolean, modifier: Modifier = Modifier, onStart: () -> Unit = {}, onLine: (Line) -> Unit = {}) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val cb = rememberUpdatedState(onLine)
    val start = rememberUpdatedState(onStart)
    val col = rememberUpdatedState(color)
    val on = rememberUpdatedState(enabled)
    Canvas(
        modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFBF8EF)).border(2.dp, C.line, RoundedCornerShape(18.dp))
            .semantics { contentDescription = t("Πίνακας ζωγραφικής", "Drawing pad") }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> if (on.value) { current = listOf(Offset(o.x / size.width, o.y / size.height)); start.value() } },
                    onDragEnd = { if (current.size > 1) cb.value(Line(col.value, current)); current = emptyList() },
                    onDragCancel = { current = emptyList() },
                ) { ch, _ -> if (on.value && current.isNotEmpty()) current = current + Offset((ch.position.x / size.width).coerceIn(0f, 1f), (ch.position.y / size.height).coerceIn(0f, 1f)) }
            },
    ) {
        (lines + if (current.size > 1) listOf(Line(color, current)) else emptyList()).forEach { l ->
            val p = Path()
            l.points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x * size.width, pt.y * size.height) else p.lineTo(pt.x * size.width, pt.y * size.height) }
            drawPath(p, Color(l.color), style = Stroke(width = l.width * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}


/** Shares a drawing as a PNG (it's made only when asked, and stays in the app's cache). */
object Drawing {
    fun bitmap(lines: List<Line>, size: Int = 1080): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(0xFFFBF8EF.toInt())
        lines.forEach { l ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = l.color; style = Paint.Style.STROKE; strokeWidth = l.width * size; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            val p = android.graphics.Path()
            l.points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x * size, pt.y * size) else p.lineTo(pt.x * size, pt.y * size) }
            c.drawPath(p, paint)
        }
        return b
    }

    fun share(context: Context, lines: List<Line>, title: String) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "gamenight.png")
            f.outputStream().use { bitmap(lines).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Ink colours that read well on the pad (one per player). */
fun inkOf(playerColor: Int): Int = when (playerColor) {
    0xFFFFB020.toInt() -> 0xFFC77700.toInt()
    0xFF39FF88.toInt() -> 0xFF0E9F4F.toInt()
    0xFF22D3EE.toInt() -> 0xFF0B8FB0.toInt()
    0xFFC6F432.toInt() -> 0xFF6E8F00.toInt()
    else -> playerColor
}
