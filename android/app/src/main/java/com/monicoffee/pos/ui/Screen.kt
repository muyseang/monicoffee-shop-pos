package com.monicoffee.pos.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
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
        // The designs' title bar: 16dp under the status bar, a 36dp back button, the title centred across the page.
        val bar = row().apply { setPadding(dp(24), dp(16), dp(24), dp(16)) }
        bar.addView(vector(R.drawable.ic_back).apply { contentDescription = "Back"; setOnClickListener { back() } }, LinearLayout.LayoutParams(dp(36), dp(36)))
        bar.addView(text(title, 16f, Palette.heading, true).apply { gravity = Gravity.CENTER; letterSpacing = -0.02f }, LinearLayout.LayoutParams(0, dp(36), 1f))
        bar.addView(right ?: View(context), LinearLayout.LayoutParams(dp(36), dp(36)))
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
    protected fun footer(label: String, color: Int = Palette.green, above: View? = null, raised: Boolean = false, action: () -> Unit) {
        root.addView(column().apply {
            // [raised]: the soft shadow above the checkout's payment bar (design 18/19).
            setPadding(dp(24), dp(16), dp(24), dp(20)); setBackgroundColor(Color.WHITE); if (raised) elevation = dp(10).toFloat()
            above?.let { addView(it) }
            addView(button(label, color, action))
        })
    }

    /** The bottom navigation bar, with [active] highlighted. */
    protected fun nav(active: Route) {
        // Design: 56dp tall, 24dp sides and 38dp between tabs, 24dp icons over 10sp labels.
        val bar = row().apply { setPadding(dp(24), 0, dp(24), 0); elevation = dp(5).toFloat(); setBackgroundColor(Color.WHITE) }
        TABS.forEachIndexed { i, tab ->
            val on = tab.route == active
            val color = if (on) Palette.green else Palette.muted
            bar.addView(column().apply {
                gravity = Gravity.CENTER
                addView(vector(if (on) tab.onIcon else tab.offIcon, color), LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(text(tab.name, 10f, color, on).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
                setOnClickListener { history.clear(); show(tab.route) }
            }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) leftMargin = dp(38) })
        }
        root.addView(bar)
    }

    /** A bottom tab; the designs fill its icon in while it is the open one. */
    private class Tab(val name: String, val route: Route, val onIcon: Int, val offIcon: Int)

    private companion object {
        val TABS = listOf(
            Tab("Home", Route.HOME, R.drawable.ic_nav_home, R.drawable.ic_nav_home_line),
            Tab("Explore", Route.EXPLORE, R.drawable.ic_nav_explore, R.drawable.ic_nav_explore),
            Tab("Orders", Route.ORDERS, R.drawable.ic_nav_orders_fill, R.drawable.ic_nav_orders),
            Tab("Profile", Route.PROFILE, R.drawable.ic_nav_profile_fill, R.drawable.ic_nav_profile),
        )
    }
}
