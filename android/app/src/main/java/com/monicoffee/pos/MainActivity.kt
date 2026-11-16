package com.monicoffee.pos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.core.AppState
import com.monicoffee.pos.core.SessionManager
import com.monicoffee.pos.core.TaskRunner
import com.monicoffee.pos.data.api.ApiClient
import com.monicoffee.pos.data.local.Preferences
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.navigation.Router
import com.monicoffee.pos.ui.orders.UploadProofScreen

/**
 * The Moni Coffee customer app. This single activity only wires the parts together:
 * the screens live in `ui`, the backend client and saved settings in `data`, and the
 * app state, session and background work in `core`.
 */
class MainActivity : Activity() {
    lateinit var prefs: Preferences
        private set
    lateinit var api: ApiClient
        private set

    val state = AppState()
    val tasks = TaskRunner(this)
    val router = Router(this)
    val session = SessionManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(this)
        api = ApiClient(prefs.server, prefs.token)
        session.restore()
        state.menu.favorites.addAll(prefs.favorites)
        router.show(Route.SPLASH)
        tasks.later(AppConfig.SPLASH_MS) {
            if (!isDestroyed && router.current == Route.SPLASH) router.show(session.startRoute)
        }
    }

    override fun onStop() {
        super.onStop()
        tasks.stopTimer()
    }

    override fun onRestart() {
        super.onRestart()
        // Restart polling and refresh what's on screen — except forms, which would lose what was typed.
        if (router.current !in FORMS) router.show(router.current)
    }

    override fun onDestroy() {
        super.onDestroy()
        tasks.shutdown()
    }

    @Deprecated("Native back navigation")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (!router.back()) super.onBackPressed()
    }

    @Deprecated("Result of the picture chooser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val picture = data?.data ?: return
        if (requestCode != PICK_PROOF || resultCode != RESULT_OK) return
        (router.screen(Route.UPLOAD_PROOF) as UploadProofScreen).onPicked(picture)
    }

    companion object {
        /** Request code of the payment-screenshot chooser. */
        const val PICK_PROOF = 41

        private val FORMS = setOf(
            // Not VERIFICATION / VERIFYING: they only poll, and must poll again after the SMS app.
            Route.LOGIN, Route.REGISTER, Route.PERSONAL_INFO, Route.SPLASH,
        )
    }
}
