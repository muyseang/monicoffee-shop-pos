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
internal fun Widgets.statusPill(order: Order): TextView {
    val (color, fill) = when (order.status) {
        "pending" -> Color.rgb(138, 90, 0) to Color.rgb(255, 241, 204)
        "accepted" -> Color.rgb(26, 86, 219) to Color.rgb(231, 239, 255)
        "preparing" -> Color.WHITE to Palette.green
        "cancelled" -> Color.WHITE to Color.rgb(107, 114, 128)
        "rejected" -> Palette.red to Palette.pink
        else -> Palette.green to Color.rgb(221, 243, 231) // ready, completed
    }
    // Design 45's badge: 11sp bold, 10×4 padding, 8dp corners.
    return text(order.statusLabel, 11f, color, true).apply { setPadding(dp(10), dp(4), dp(10), dp(4)); background = bg(fill, 8) }
}

/** "Total to pay / $24.00 / Order #MC-0142" at the top of the two payment screens. */
internal fun Widgets.amount(parent: LinearLayout, label: String, o: Order) {
    parent.addView(text(label, 13f, Palette.muted).apply { gravity = Gravity.CENTER })
    parent.addView(text(money(o.total), 30f, Palette.ink, true).apply { gravity = Gravity.CENTER })
    parent.addView(text("Order #${o.number}", 13f, Palette.ink).apply { gravity = Gravity.CENTER }); space(parent, 18)
}
