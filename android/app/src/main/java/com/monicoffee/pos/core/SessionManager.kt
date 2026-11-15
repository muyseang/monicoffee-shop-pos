package com.monicoffee.pos.core

import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.data.model.Profile
import com.monicoffee.pos.data.model.Session
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.widget.toast

/** Signing in and out: keeps the API token, the saved profile and the screens in step. */
class SessionManager(private val app: MainActivity) {
    val signedIn get() = app.api.token != null

    /** Where the app goes after the splash screen. */
    val startRoute
        get() = when {
            signedIn -> Route.HOME
            app.prefs.onboarded -> Route.LOGIN
            else -> Route.ONBOARDING
        }

    /** Brings back the profile saved by the last run, if the customer is still signed in. */
    fun restore() {
        if (signedIn) app.state.profile = app.prefs.profile
    }

    /** Asks the server to send a login or registration code to [phone]. */
    fun requestCode(purpose: String, phone: String, fullName: String, then: () -> Unit) {
        app.tasks.submit({ app.api.requestOtp(purpose, phone, fullName.takeIf { purpose == "register" }) }) {
            val auth = app.state.auth
            auth.purpose = purpose; auth.phone = phone; auth.name = fullName
            auth.ticket = it; auth.ticketAt = System.currentTimeMillis()
            then()
        }
    }

    fun signIn(session: Session) {
        app.api.token = session.token
        app.prefs.token = session.token
        save(session.profile)
        app.router.history.clear()
        app.state.menu.loadedAt = 0
        if (app.state.auth.purpose == "register") {
            app.router.show(Route.REGISTERED)
        } else {
            app.router.show(Route.HOME)
            app.toast(if (session.profile.fullName.isBlank()) "Welcome!" else "Welcome, ${session.profile.fullName}!")
        }
    }

    fun save(profile: Profile) {
        app.state.profile = profile
        app.prefs.profile = profile
    }

    fun signOut() {
        app.tasks.dismissWait()
        app.api.token = null
        app.prefs.clearSession()
        app.state.apply {
            profile = null
            cart.clear()
            orders.byTab.clear()
            orders.current = null
        }
        app.router.history.clear()
        app.router.show(Route.LOGIN)
    }

    /** The server no longer accepts our token (e.g. logged out from another phone). */
    fun expire() {
        signOut()
        app.toast("Please log in again.")
    }
}
