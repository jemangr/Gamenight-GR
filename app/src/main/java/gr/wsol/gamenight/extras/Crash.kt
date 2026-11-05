package gr.wsol.gamenight.extras

import android.content.Context
import android.os.Build
import gr.wsol.gamenight.BuildConfig
import java.io.File

/**
 * If the app closes on an error, the technical details (never the players' names) stay on the
 * phone until the next start, where the user can email or copy them.
 */
object Crash {
    private fun file(c: Context) = File(c.filesDir, "crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file(app).writeText(report(thread.name, e)) }
            previous?.uncaughtException(thread, e)
        }
    }

    fun report(thread: String, e: Throwable): String = buildString {
        appendLine("Gamenight GR ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Game: ${Report.game ?: "-"}")
        appendLine("Thread: $thread")
        appendLine()
        append(e.stackTraceToString().take(6000))
    }

    fun last(c: Context): String? = file(c).takeIf { it.exists() }?.readText()?.ifBlank { null }
    fun clear(c: Context) { file(c).delete() }
}
