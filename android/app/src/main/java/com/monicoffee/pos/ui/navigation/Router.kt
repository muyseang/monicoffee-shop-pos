package com.monicoffee.pos.ui.navigation

import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette
import java.util.ArrayDeque
import java.util.EnumMap

/** Decides which screen is on display and remembers the way back. */
class Router(private val app: MainActivity) {
    var current = Route.SPLASH
        private set

    /** The screens Back returns to, oldest first. */
    val history = ArrayDeque<Route>()

    /** Goes up on every redraw, so a reply meant for a screen that is gone can be dropped. */
    var generation = 0
        private set

    /** True while a screen is only being redrawn with data it just loaded (don't load again). */
    var quiet = false
        private set

    /** The layout the current screen draws into. */
    lateinit var root: LinearLayout
        private set

    /** The scrolling part of the current screen, if it has one. */
    var scroll: ScrollView? = null

    private val screens = EnumMap<Route, Screen>(Route::class.java)

    fun screen(route: Route): Screen = screens.getOrPut(route) { route.create(app) }

    /** Opens [to], keeping the current screen to come back to. */
    fun navigate(to: Route) {
        history.addLast(current)
        show(to)
    }

    /** Draws the current screen again from the state, without reloading its data. */
    fun redraw() {
        quiet = true
        show(current)
        quiet = false
    }

    fun show(to: Route) {
        // Redrawing the same screen (e.g. after tapping a chip) keeps the scroll position.
        val keepScroll = if (to == current) scroll?.scrollY ?: 0 else 0
        generation++
        app.tasks.stopTimer()
        current = to
        scroll = null
        paintSystemBars(to)
        root = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        app.setContentView(root)
        screen(to).draw()
        if (keepScroll > 0) scroll?.let { s -> s.post { s.scrollTo(0, keepScroll) } }
    }

    /** Handles the Back button; false means there is nowhere to go back to, so the app should close. */
    fun back(): Boolean {
        val state = app.state
        val signedIn = app.session.signedIn
        when {
            current == Route.ONBOARDING && state.onboardingSlide > 0 -> { state.onboardingSlide--; show(Route.ONBOARDING) }
            current == Route.SPLASH || current == Route.ONBOARDING -> return false
            current == Route.VERIFYING -> show(Route.VERIFICATION)
            history.isNotEmpty() -> show(history.removeLast())
            signedIn && current != Route.HOME -> show(Route.HOME)
            !signedIn && current != Route.LOGIN -> show(Route.LOGIN)
            else -> return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun paintSystemBars(to: Route) {
        val darkTop = to == Route.HOME || to == Route.SPLASH || to == Route.ONBOARDING
        val window = app.window
        window.statusBarColor = if (to == Route.ONBOARDING) Color.BLACK else if (darkTop) Palette.dark else Color.WHITE
        // Splash and onboarding are dark down to the home indicator (designs 01–04).
        val darkBottom = to == Route.SPLASH || to == Route.ONBOARDING
        window.navigationBarColor = if (darkBottom) Color.BLACK else if (Build.VERSION.SDK_INT >= 26) Color.WHITE else Palette.ink
        window.decorView.systemUiVisibility =
            (if (Build.VERSION.SDK_INT >= 26 && !darkBottom) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0) or
            (if (darkTop) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
    }
}
