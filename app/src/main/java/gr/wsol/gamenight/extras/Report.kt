package gr.wsol.gamenight.extras

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import gr.wsol.gamenight.BuildConfig
import gr.wsol.gamenight.core.Lang
import gr.wsol.gamenight.core.Store
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The bug report: what the user writes, plus the version, the phone, the game being played, a few
 * settings and the last errors. Never the players' names. It stays on the phone until the user sends it.
 */
object Report {
    const val EMAIL = "info@3wsol.gr"
    const val SUBJECT = "Bug report Gamenight GR"

    /** The game on screen right now (its id), for the report and the crash file. */
    @Volatile var game: String? = null

    private val errors = ArrayDeque<String>()

    fun note(where: String, what: String) = synchronized(errors) {
        errors.addLast("${SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())} $where: ${what.take(200)}")
        while (errors.size > 20) errors.removeFirst()
    }

    fun recent(): List<String> = synchronized(errors) { errors.toList() }

    fun text(context: Context, what: String): String = buildString {
        appendLine("Gamenight GR · bug report")
        appendLine()
        appendLine(what.trim().ifEmpty { "-" })
        appendLine()
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        val s = Store.settings
        val n = Store.night
        appendLine("Game: ${game ?: Store.lastGame ?: "-"} · players: ${n.players.size} · teams: ${n.teams} · language: ${if (Lang.english) "en" else "el"}")
        appendLine("18+: ${s.adult} · sounds: ${s.sounds} · vibration: ${s.haptics} · update check: ${s.updateCheck}")
        val r = recent()
        if (r.isNotEmpty()) { appendLine(); appendLine("Last errors:"); r.forEach { appendLine(it) } }
        Crash.last(context)?.let { appendLine(); appendLine("Last crash:"); append(it.take(4000)) }
    }.trim()

    /** Opens the phone's email app with the address, the subject and the report filled in. */
    fun email(context: Context, text: String): Boolean {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, SUBJECT)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }

    fun share(context: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, SUBJECT).putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun issueUrl(text: String): String =
        "https://github.com/jemangr/Gamenight-GR/issues/new?title=" + URLEncoder.encode("Bug: ", "UTF-8") +
            "&body=" + URLEncoder.encode(text.take(5000), "UTF-8")
}
