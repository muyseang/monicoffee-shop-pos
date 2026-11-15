package com.monicoffee.pos.ui.orders

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.monicoffee.pos.data.model.Order
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Widgets

/** Text and background colour of an order's status badge, as in the design. */
internal fun Widgets.statusPill(order: Order): TextView = when (order.status) {
    "pending" -> pill(order.statusLabel, Color.rgb(146, 100, 10), Color.rgb(255, 243, 205))
    "accepted" -> pill(order.statusLabel, Color.rgb(37, 84, 199), Color.rgb(224, 235, 255))
    "preparing" -> pill(order.statusLabel, Color.WHITE, Palette.green)
    "cancelled", "rejected" -> pill(order.statusLabel, Palette.red, Palette.pink)
    else -> pill(order.statusLabel, Palette.green, Color.rgb(219, 242, 230)) // ready, completed
}

/** "Total to pay / $24.00 / Order #MC-0142" at the top of the two payment screens. */
internal fun Widgets.amount(parent: LinearLayout, label: String, o: Order) {
    parent.addView(text(label, 13f, Palette.muted).apply { gravity = Gravity.CENTER })
    parent.addView(text(money(o.total), 30f, Palette.ink, true).apply { gravity = Gravity.CENTER })
    parent.addView(text("Order #${o.number}", 13f, Palette.ink).apply { gravity = Gravity.CENTER }); space(parent, 18)
}
