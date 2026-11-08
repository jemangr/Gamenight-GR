package gr.wsol.gamenight

import android.app.Application
import gr.wsol.gamenight.core.Cinema
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.extras.Crash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GamenightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
        Store.init(this)
        gr.wsol.gamenight.core.Packs.init(this)
        // 1.4: with the «Σινεμά» look, its photos (~1.4 MB) download in the background if they are not on the phone yet.
        // Until then (or without internet) the screens show the drawn graphics; the next start tries again.
        if (Store.settings.look == 3 && !Cinema.isInstalled(this) && android.os.Build.FINGERPRINT != "robolectric") {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { Cinema.download(this@GamenightApp) { _, _ -> } }
        }
    }
}
