package com.monicoffee.pos.ui.orders

import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
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
        root.addView(text("My Orders", 17f, Palette.ink, true).apply { setPadding(dp(24), dp(22), dp(24), dp(10)) })
        val b = content()
        segmented(b, tabs.map { it.first }, false, { name -> tabs.first { it.first == name }.second == orders.tab }) { name ->
            orders.tab = tabs.first { it.first == name }.second; show(Route.ORDERS)
        }
        space(b, 20)
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
            list.isEmpty() -> empty(b, "orders", "No order history yet", "When there's activity, it will appear here", "Shop Now") { history.clear(); show(Route.HOME) }
            else -> list.forEach { o ->
                val card = column().apply {
                    background = bg(Color.WHITE, 12, Palette.line); setPadding(dp(16), dp(16), dp(16), dp(16))
                    setOnClickListener { orders.current = o; navigate(Route.TRACK_ORDER) }
                }
                card.addView(row().apply {
                    addView(text("Order #${o.number}", 15f, Palette.ink, true), LinearLayout.LayoutParams(0, -2, 1f)); addView(statusPill(o))
                }); space(card, 8)
                card.addView(text(o.place, 13f, Color.GRAY))
                card.addView(View(context).apply { setBackgroundColor(Palette.line) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
                card.addView(row().apply {
                    addView(text(countLabel(o.itemCount), 13f, Palette.muted), LinearLayout.LayoutParams(0, -2, 1f)); addView(text(money(o.total), 16f, Palette.ink, true))
                })
                b.addView(card); space(b, 12)
            }
        }
        nav(Route.ORDERS)
    }
}
