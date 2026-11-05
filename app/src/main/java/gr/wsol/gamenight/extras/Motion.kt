package gr.wsol.gamenight.extras

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

/** Calls [onShake] when the phone is shaken (accelerometer, no permission needed). */
@Composable
fun OnShake(enabled: Boolean = true, onShake: () -> Unit) {
    val ctx = LocalContext.current
    val cb = rememberUpdatedState(onShake)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val gravity = acc?.type == Sensor.TYPE_ACCELEROMETER
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                val g = sqrt(x * x + y * y + z * z) - if (gravity) 9.81f else 0f
                val now = System.currentTimeMillis()
                if (g > 13f && now - last > 900) { last = now; cb.value() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        acc?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(l) }
    }
}

/**
 * For «Στο μέτωπο»: the phone held on the forehead, screen towards the group. Tilting the screen
 * towards the floor calls [onDown] (correct), towards the ceiling [onUp] (pass). It must come back
 * upright before the next tilt counts.
 */
@Composable
fun OnTilt(enabled: Boolean = true, onDown: () -> Unit, onUp: () -> Unit) {
    val ctx = LocalContext.current
    val down = rememberUpdatedState(onDown)
    val up = rememberUpdatedState(onUp)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var armed = true
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val z = e.values[2] // + : screen to the ceiling, − : screen to the floor
                when {
                    armed && z < -6.5f -> { armed = false; down.value() }
                    armed && z > 6.5f -> { armed = false; up.value() }
                    !armed && kotlin.math.abs(z) < 3.5f -> armed = true
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        s?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(l) }
    }
}

/** The phone's tilt as gravity along x and y (m/s²), updated while on screen. Zero without a sensor. */
@Composable
fun rememberTilt(): androidx.compose.runtime.State<androidx.compose.ui.geometry.Offset> {
    val ctx = LocalContext.current
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { state.value = androidx.compose.ui.geometry.Offset(-e.values[0], e.values[1]) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        s?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(l) }
    }
    return state
}
