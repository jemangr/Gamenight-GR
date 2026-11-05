package gr.wsol.gamenight.extras

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import gr.wsol.gamenight.core.Store
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sounds made in code (no recordings, nothing to license) and short vibrations. Both follow the
 * switches in the settings.
 */
object Sfx {
    private const val RATE = 22050
    private val cache = HashMap<String, ShortArray>()

    fun correct() = play("ok") { join(tone(0.09, sq(880.0), dec(18.0)), tone(0.16, sq(1320.0), dec(12.0))) }
    fun wrong() = play("no") { tone(0.32, { x -> 0.7 * sin(2 * PI * 150 * x) + 0.3 * sin(2 * PI * 155 * x) }, dec(5.0)) }
    fun pass() = play("pass") { tone(0.14, { x -> sin(2 * PI * (900 - 2500 * x) * x) }, dec(14.0)) }
    fun tick() = play("tick") { tone(0.03, sq(2000.0), dec(80.0)) }
    fun tap() = play("tap") { tone(0.05, sq(660.0), dec(50.0)) }
    fun heartbeat() = play("heart") { join(tone(0.09, { x -> sin(2 * PI * 60 * x) }, dec(30.0)), silence(0.07), tone(0.09, { x -> sin(2 * PI * 52 * x) }, dec(30.0))) }
    fun end() = play("end") { join(tone(0.18, sq(523.0), { 1.0 }), tone(0.18, sq(659.0), { 1.0 }), tone(0.45, sq(784.0), dec(4.0))) }
    fun win() = play("win") { join(tone(0.12, sq(523.0), { 1.0 }), tone(0.12, sq(659.0), { 1.0 }), tone(0.12, sq(784.0), { 1.0 }), tone(0.5, sq(1046.0), dec(3.0))) }
    fun boom() = play("boom") { mix(noise(1.2, 1.0, 3.0), tone(0.8, { x -> sin(2 * PI * (90 - 50 * x) * x) }, dec(4.0))) }

    // «Το στοιχειωμένο σπίτι»
    fun wind() = play("wind") { noise(2.2, 0.55, 0.4) }
    fun creak() = play("creak") { tone(0.7, { x -> 0.6 * sin(2 * PI * (180 + 90 * sin(2 * PI * 7 * x)) * x) }, { x -> sin(PI * x / 0.7) * 0.8 }) }
    fun bell() = play("bell") { tone(2.0, { x -> sin(2 * PI * 196 * x) + 0.5 * sin(2 * PI * 392 * x) + 0.3 * sin(2 * PI * 587 * x) }, dec(1.6)) }

    private fun play(key: String, make: () -> ShortArray) {
        if (!Store.settings.sounds) return
        val pcm = synchronized(cache) { cache.getOrPut(key, make) }
        Thread {
            runCatching {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                t.write(pcm, 0, pcm.size); t.setVolume(0.7f); t.play()
                Thread.sleep(pcm.size * 1000L / RATE + 80)
                t.release()
            }.onFailure { Report.note("sound", it.toString()) }
        }.start()
    }

    // ---- vibration ----
    private var vibrator: Vibrator? = null

    fun init(c: Context) {
        vibrator = runCatching { (c.getSystemService(VibratorManager::class.java))?.defaultVibrator }.getOrNull()
    }

    fun buzz(ms: Long = 30, strong: Boolean = false) {
        if (!Store.settings.haptics) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, if (strong) 255 else 120)) }
    }

    fun pattern(vararg ms: Long) {
        if (!Store.settings.haptics) return
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, *ms), -1)) }
    }

    // ---- synthesis ----
    private fun samples(sec: Double) = (sec * RATE).toInt()
    private fun tone(sec: Double, f: (Double) -> Double, env: (Double) -> Double) =
        ShortArray(samples(sec)) { i -> val x = i.toDouble() / RATE; (f(x) * env(x) * 11000).toInt().coerceIn(-32767, 32767).toShort() }
    private fun sq(freq: Double): (Double) -> Double = { x -> if (sin(2 * PI * freq * x) >= 0) 0.5 else -0.5 }
    private fun dec(k: Double): (Double) -> Double = { x -> exp(-k * x) }
    private fun silence(sec: Double) = ShortArray(samples(sec))
    private fun join(vararg p: ShortArray) = p.fold(ShortArray(0)) { a, b -> a + b }
    private fun mix(vararg p: ShortArray): ShortArray {
        val n = p.maxOf { it.size }
        return ShortArray(n) { i -> p.sumOf { s -> if (i < s.size) s[i].toInt() else 0 }.coerceIn(-32767, 32767).toShort() }
    }
    private fun noise(sec: Double, level: Double, decay: Double): ShortArray {
        var last = 0.0
        return ShortArray(samples(sec)) { i ->
            last += (Random.nextDouble(-1.0, 1.0) - last) * 0.3
            (last * level * exp(-decay * i.toDouble() / RATE) * 26000).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
