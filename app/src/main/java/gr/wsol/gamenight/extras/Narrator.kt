package gr.wsol.gamenight.extras

import android.content.Context
import android.speech.tts.TextToSpeech
import gr.wsol.gamenight.core.Lang
import gr.wsol.gamenight.core.Store
import java.util.Locale

/**
 * The narrator of «Το στοιχειωμένο σπίτι»: the phone's own text-to-speech (offline, no recordings).
 * If there is no Greek voice, the text is only shown on screen.
 */
object Narrator {
    private var tts: TextToSpeech? = null
    @Volatile var ready = false; private set

    fun init(c: Context) {
        if (tts != null) return
        tts = runCatching {
            TextToSpeech(c.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val loc = if (Lang.english) Locale.UK else Locale("el", "GR")
                    val r = tts?.setLanguage(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                    tts?.setSpeechRate(0.88f); tts?.setPitch(0.8f)
                }
            }
        }.getOrNull()
    }

    fun say(text: String) {
        if (!ready || !Store.settings.sounds) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "n" + text.hashCode()) }
    }

    fun stop() { runCatching { tts?.stop() } }
}
