package gr.wsol.gamenight

import android.app.Application
import gr.wsol.gamenight.core.Store
import gr.wsol.gamenight.extras.Crash

class GamenightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
        Store.init(this)
        gr.wsol.gamenight.core.Packs.init(this)
    }
}
