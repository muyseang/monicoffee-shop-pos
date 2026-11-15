package com.monicoffee.pos.ui.orders

import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Order Details: what was ordered and how it is paid. */
class OrderDetailsScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val o = orders.current ?: return show(Route.ORDERS)
        val refresh = { load({ api.order(o.id) }, failed = {}) { if (it != orders.current) { orders.current = it; redraw() } } }
        if (!quiet) refresh()
        every(AppConfig.ORDER_POLL_MS) { refresh() }

        header("Order Details"); val b = content()
        b.addView(row().apply {
            addView(text("Order Summary", 17f, Palette.ink, true), LinearLayout.LayoutParams(0, -2, 1f)); addView(statusPill(o))
        }); space(b, 14)
        pair(b, "Order ID", "#${o.number}"); space(b, 10)
        pair(b, "Order Date", o.createdAt.replace("T", " ").take(16)); space(b, 10)
        pair(b, "Order Type", o.place); space(b, 10)
        pair(b, "Payment Method", o.paymentMethodLabel); space(b, 10)
        pair(b, "Payment Status", o.paymentStatusLabel)
        divider(b); b.addView(text("Order List", 17f, Palette.ink, true)); space(b, 16)
        o.items.forEach { item ->
            val r = row()
            r.addView(photo(item.imageUrl, 48), LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })
            val label = column(); label.addView(text(item.name, 15f, Palette.ink, true)); space(label, 4)
            label.addView(text(listOf(item.options, money(item.unitPrice)).filter { it.isNotBlank() }.joinToString(" • "), 12f, Palette.muted))
            r.addView(label, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(text("× ${item.quantity}", 14f, Palette.ink, true))
            b.addView(r); space(b, 16)
        }
        divider(b); b.addView(text("Payment Summary", 17f, Palette.ink, true)); space(b, 16)
        pair(b, "Subtotal", money(o.total)); divider(b); pair(b, "Total Payment", money(o.total), labelBold = true)
    }
}
