package com.monicoffee.pos.ui.orders

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.countLabel
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Track Order: where the order is in the shop's flow, refreshed every few seconds. */
class TrackOrderScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val o = orders.current ?: return show(Route.ORDERS)
        val refresh = { load({ api.order(o.id) }, failed = {}) { if (it != orders.current) { orders.current = it; redraw() } } }
        if (!quiet) refresh()
        every(AppConfig.ORDER_POLL_MS) { refresh() }

        header("Track Order"); val b = content()
        b.addView(row().apply {
            background = bg(Palette.mint, 12); setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(column().apply {
                addView(text("Order #${o.number}", 16f, Palette.ink, true)); space(this, 2); addView(text(o.place, 12f, Color.GRAY))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(statusPill(o))
        }); space(b, 22)
        b.addView(text("Order Status", 16f, Palette.ink, true)); space(b, 14)
        val dineIn = o.orderType == "dine_in"
        val time = o.createdAt.drop(11).take(5)
        val rejected = o.status == "rejected"
        if (rejected || o.status == "cancelled") {
            val stop = if (rejected) Color.rgb(222, 68, 60) else Color.GRAY
            step(b, "stepdone", Palette.green, "Pending", "Order received • $time", Palette.ink, Palette.muted, stop)
            step(b, "stepx", stop, o.statusLabel, if (rejected) "Declined by Moni Coffee" else "Cancelled by you", if (rejected) Palette.red else Palette.ink, Palette.muted, null)
            space(b, 16)
            b.addView(column().apply {
                setPadding(dp(14), dp(12), dp(14), dp(12)); background = if (rejected) bg(Palette.pink, 10, Color.rgb(240, 170, 165)) else bg(Palette.soft, 10, Palette.line)
                addView(text(if (rejected) "Reason from Moni Coffee" else "Cancelled by you", 12f, if (rejected) Palette.red else Color.GRAY, true)); space(this, 4)
                addView(text(
                    if (rejected) o.rejectionReason.ifBlank { "The shop couldn't take this order." }
                    else "You cancelled this order while it was still Pending. The items have been returned to stock.",
                    14f, Palette.ink,
                ))
            })
        } else {
            val steps = listOf(
                Triple("pending", "Pending", if (o.status == "pending") "Waiting for Moni Coffee to accept • $time" else "Order received • $time"),
                Triple("accepted", "Accepted", "Confirmed by Moni Coffee"),
                Triple("preparing", "Preparing", if (o.status == "preparing") "Our barista is making your drinks" else "Our barista makes your drinks"),
                Triple("ready", "Ready", if (dineIn) "We'll bring it to your table" else "We'll let you know when it's ready"),
                Triple("completed", "Completed", if (dineIn) "Enjoy your coffee at Table ${o.tableNumber}" else "Enjoy your coffee"),
            )
            val at = steps.indexOfFirst { it.first == o.status }
            steps.forEachIndexed { i, (_, title, detail) ->
                val done = i < at || o.status == "completed"
                val now = i == at && !done
                step(
                    b, if (done) "stepdone" else if (now) "stepnow" else "steptodo", if (done || now) Palette.green else Color.rgb(205, 205, 205),
                    title, detail, if (done || now) Palette.ink else Palette.muted, if (now) Palette.green else Palette.muted,
                    if (i == steps.lastIndex) null else if (i < at) Palette.green else Palette.line,
                )
            }
            if (o.status != "completed") { space(b, 6); b.addView(text("⟳  Updates automatically every ${AppConfig.ORDER_POLL_MS / 1000} seconds", 12f, Palette.muted).apply { setPadding(dp(34), 0, 0, 0) }) }
        }
        space(b, 18)
        b.addView(row().apply {
            background = bg(Color.WHITE, 12, Palette.line); setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { navigate(Route.ORDER_DETAILS) }
            addView(column().apply {
                addView(text("${countLabel(o.itemCount)}  •  ${o.paymentMethodLabel}", 14f, Palette.ink, true)); space(this, 2)
                addView(text("View order details", 12f, Palette.green, true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(money(o.total), 16f, Palette.ink, true))
        })

        val bottom = column().apply { setPadding(dp(24), dp(12), dp(24), dp(20)) }
        val active = o.status in setOf("pending", "accepted", "preparing", "ready")
        if (o.canUploadProof) {
            bottom.addView(button(if (o.paymentProofUrl == null) "Pay with KH QR" else "View QR / replace screenshot") { navigate(Route.PAY) }); space(bottom, 12)
        }
        if (active) {
            bottom.addView(text(if (o.canCancel) "You can cancel until Moni Coffee accepts your order." else "You can only cancel while the order is Pending.", 12f, Palette.muted).apply { gravity = Gravity.CENTER }); space(bottom, 10)
            if (o.canCancel) bottom.addView(outline("Cancel Order", Palette.red) {
                confirm("warning", "Cancel this order?", "Order #${o.number} will be cancelled. This can't be undone.", "Yes, Cancel Order", "Keep Order") {
                    submit({ api.cancelOrder(o.id) }) { orders.current = it; orders.byTab.clear(); menu.loadedAt = 0; redraw() }
                }
            }) else bottom.addView(text("Cancel Order", 16f, Palette.muted, true).apply { gravity = Gravity.CENTER; background = bg(Color.rgb(236, 236, 236)); minHeight = dp(52) })
        } else bottom.addView(button("Order Again") { history.clear(); show(Route.HOME) })
        root.addView(bottom)
    }
}
