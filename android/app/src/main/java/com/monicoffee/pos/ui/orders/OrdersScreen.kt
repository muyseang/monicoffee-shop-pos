package com.monicoffee.pos.ui.orders

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.countLabel
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** My Orders: the customer's orders in three tabs, refreshed every few seconds. */
class OrdersScreen(app: MainActivity) : Screen(app) {
    private val tabs = listOf("In Progress" to "active", "Completed" to "completed", "Cancelled" to "cancelled")

    override fun draw() {
        // Design 45: the title with the filter funnel on its right, which picks a tab like the bar below.
        val pickTab = { name: String -> orders.tab = tabs.first { it.first == name }.second; show(Route.ORDERS) }
        root.addView(row().apply {
            setPadding(dp(24), dp(24), dp(24), dp(10))
            addView(text("My Orders", 16f, Color.rgb(36, 36, 36), true).apply { leading(1.5f) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(vector(R.drawable.ic_filter_line).apply {
                contentDescription = "Filter"
                setOnClickListener { AlertDialog.Builder(context).setTitle("Show orders").setItems(tabs.map { it.first }.toTypedArray()) { _, i -> pickTab(tabs[i].first) }.show() }
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
        })
        val b = content()
        segmented(b, tabs.map { it.first }, false, { name -> tabs.first { it.first == name }.second == orders.tab }, pickTab)
        space(b, 16)
        val tab = orders.tab
        val reload = {
            orders.error = null
            load({ api.orders(tab) }, failed = { orders.error = it; redraw() }) {
                if (orders.byTab[tab] != it) { orders.byTab[tab] = it; redraw() }
            }
        }
        if (!quiet) reload()
        every(AppConfig.ORDER_POLL_MS) { if (orders.error == null) reload() }
        val list = orders.byTab[tab]
        when {
            list == null && orders.error != null -> problem(b, orders.error!!) { orders.error = null; show(Route.ORDERS) }
            list == null -> skeleton(b)
            list.isEmpty() -> empty(b, vector(R.drawable.ic_empty_orders), "No order history yet", "When there's activity, it will appear here", "Shop Now", top = 93) { history.clear(); show(Route.HOME) }
            else -> list.forEach { o ->
                // Design 45's order card: 1dp #ECECEC border, 16×14 padding, rows 10dp apart.
                val card = column().apply {
                    background = bg(Color.WHITE, 12, Color.rgb(236, 236, 236)); setPadding(dp(16), dp(14), dp(16), dp(14))
                    setOnClickListener { orders.current = o; navigate(Route.TRACK_ORDER) }
                }
                card.addView(row().apply {
                    addView(text("Order #${o.number}", 14f, Color.rgb(36, 36, 36), true), LinearLayout.LayoutParams(0, -2, 1f)); addView(statusPill(o))
                }); space(card, 10)
                card.addView(text(o.place, 12f, Color.rgb(102, 102, 102)))
                card.addView(View(context).apply { setBackgroundColor(Palette.mist) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(10) })
                card.addView(row().apply {
                    addView(text(countLabel(o.itemCount), 12f, Palette.muted), LinearLayout.LayoutParams(0, -2, 1f)); addView(text(money(o.total), 14f, Color.rgb(36, 36, 36), true))
                })
                b.addView(card); space(b, 12)
            }
        }
        nav(Route.ORDERS)
    }
}
