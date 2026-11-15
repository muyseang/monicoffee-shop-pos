package com.monicoffee.pos.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Widgets
import com.monicoffee.pos.ui.widget.alert
import com.monicoffee.pos.ui.widget.toast

/**
 * Base class of every screen. A screen draws itself into [root] from the app state each time
 * [draw] is called; this class gives it the page frame (header, scrolling content, footer,
 * bottom navigation) and short names for navigation, background work and the shared state.
 */
abstract class Screen(protected val app: MainActivity) : Widgets(app) {

    /** Builds the screen's views into [root]. */
    abstract fun draw()

    // --- shared state and services ---

    protected val api get() = app.api
    protected val prefs get() = app.prefs
    protected val session get() = app.session
    protected val state get() = app.state
    protected val auth get() = state.auth
    protected val menu get() = state.menu
    protected val cart get() = state.cart
    protected val checkout get() = state.checkout
    protected val orders get() = state.orders

    // --- navigation ---

    protected val root get() = app.router.root
    protected val screen get() = app.router.current
    protected val history get() = app.router.history
    protected val quiet get() = app.router.quiet

    protected fun show(to: Route) = app.router.show(to)
    protected fun navigate(to: Route) = app.router.navigate(to)
    protected fun redraw() = app.router.redraw()

    @Suppress("DEPRECATION")
    protected fun back() = app.onBackPressed()

    // --- background work and messages ---

    protected fun <T> load(
        work: () -> T,
        sticky: Boolean = false,
        failed: (ApiException) -> Unit = { toast(it.message) },
        done: (T) -> Unit,
    ) = app.tasks.load(work, sticky, failed, done)

    protected fun <T> submit(work: () -> T, done: (T) -> Unit) = app.tasks.submit(work, done)
    protected fun every(millis: Long, tick: () -> Unit) = app.tasks.every(millis, tick)
    protected fun toast(message: String?) = app.toast(message)
    protected fun alert(message: String?) = app.alert(message)

    // --- page frame ---

    /** Title bar with a back arrow and an optional button on the right. */
    protected fun header(title: String, right: View? = null) {
        val bar = row().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        bar.addView(icon("back", Palette.ink) { back() })
        bar.addView(text(title, 17f, Palette.ink, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(40), 1f))
        bar.addView(right ?: View(context), LinearLayout.LayoutParams(dp(40), dp(40)))
        root.addView(bar)
    }

    /** The scrolling middle of the page; returns the column to add views to. */
    protected fun content(): LinearLayout {
        val scroll = ScrollView(context).apply { isFillViewport = true; clipToPadding = false }
        val body = column().apply { setPadding(dp(24), dp(12), dp(24), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        app.router.scroll = scroll
        return body
    }

    /** The main button pinned to the bottom of the page, with an optional view above it. */
    protected fun footer(label: String, color: Int = Palette.green, above: View? = null, action: () -> Unit) {
        root.addView(column().apply {
            setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE)
            above?.let { addView(it) }
            addView(button(label, color, action))
        })
    }

    /** The bottom navigation bar, with [active] highlighted. */
    protected fun nav(active: Route) {
        val bar = row().apply { setPadding(dp(8), dp(7), dp(8), dp(8)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE) }
        TABS.forEach { (name, tab) ->
            val color = if (tab == active) Palette.green else Palette.muted
            val open = { history.clear(); show(tab) }
            bar.addView(column().apply {
                gravity = Gravity.CENTER
                addView(icon(name.lowercase(), color, open))
                addView(text(name, 11f, color, tab == active).apply { gravity = Gravity.CENTER })
                setOnClickListener { open() }
            }, LinearLayout.LayoutParams(0, dp(62), 1f))
        }
        root.addView(bar)
    }

    private companion object {
        val TABS = listOf("Home" to Route.HOME, "Explore" to Route.EXPLORE, "Orders" to Route.ORDERS, "Profile" to Route.PROFILE)
    }
}
