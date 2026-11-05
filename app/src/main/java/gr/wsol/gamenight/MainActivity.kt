package gr.wsol.gamenight

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import gr.wsol.gamenight.extras.Sfx
import gr.wsol.gamenight.ui.AppRoot
import gr.wsol.gamenight.ui.GamenightTheme

class MainActivity : ComponentActivity() {
    /** Shake to report a problem: only in the menus, never while a game is on screen (some games are played by moving the phone). */
    private val shake = gr.wsol.gamenight.extras.Shake {
        val top = gr.wsol.gamenight.ui.Nav.top
        if (gr.wsol.gamenight.core.Store.settings.shakeReport && top !is gr.wsol.gamenight.ui.Screen.Play && top != gr.wsol.gamenight.ui.Screen.Report)
            gr.wsol.gamenight.ui.Nav.go(gr.wsol.gamenight.ui.Screen.Report)
    }

    override fun onResume() { super.onResume(); shake.start(this); gr.wsol.gamenight.ui.UpdateState.resumes++ }
    override fun onPause() { super.onPause(); shake.stop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Sfx.init(this)
        // A game night: the screen stays on while the app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { GamenightTheme { AppRoot() } }
    }
}
