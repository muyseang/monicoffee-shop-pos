package com.monicoffee.pos.ui.orders

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Pay with KH QR: the shop's QR code; the proof of payment is sent from the next screen. */
class PayScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val o = orders.current ?: return show(Route.ORDERS)
        if (!quiet) {
            load({ api.qrPayment() }, failed = {}) { if (it != checkout.qr) { checkout.qr = it; redraw() } }
            load({ api.order(o.id) }, failed = {}) { if (it != orders.current) { orders.current = it; redraw() } }
        }
        every(AppConfig.ORDER_POLL_MS) { load({ api.order(o.id) }, failed = {}) { if (it != orders.current) { orders.current = it; redraw() } } }

        header("Pay with KH QR"); val b = content()
        amount(b, "Total to pay", o)
        val shop = checkout.qr
        when {
            shop == null -> b.addView(text("Loading the shop's QR code…", 14f, Palette.muted).apply { gravity = Gravity.CENTER })
            shop.qrImageUrl == null -> b.addView(text("The shop's QR code isn't available right now. Please pay at the counter.", 14f, Palette.muted).apply { gravity = Gravity.CENTER })
            else -> b.addView(FrameLayout(context).apply {
                background = bg(Color.WHITE, 16); elevation = dp(4).toFloat(); setPadding(dp(14), dp(14), dp(14), dp(14))
                addView(photo(shop.qrImageUrl, 220).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = null })
            }, LinearLayout.LayoutParams(dp(248), dp(248)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4); bottomMargin = dp(4) })
        }
        space(b, 16)
        centered(b, when {
            o.paymentStatus == "paid" -> pill("●  Payment confirmed", Palette.green, Color.rgb(219, 242, 230))
            !o.canUploadProof -> pill("●  No longer waiting for a payment", Color.GRAY, Palette.soft)
            o.paymentProofUrl != null -> pill("●  Screenshot sent · waiting for staff confirmation", Palette.amber, Palette.cream)
            else -> pill("●  Waiting for staff confirmation", Palette.amber, Palette.cream)
        })
        space(b, 14)
        b.addView(text(
            shop?.instructions?.ifBlank { null }
                ?: "Scan the KH QR code with your banking app to complete payment. Once paid, tap below to upload a screenshot as proof — our staff will verify it before confirming your order.",
            13f, Palette.muted,
        ).apply { gravity = Gravity.CENTER; setLineSpacing(dp(3).toFloat(), 1f) })
        // Design 40 shows only the QR, so the shop's account name and numbers aren't listed under it.
        if (o.canUploadProof) footer(if (o.paymentProofUrl == null) "I've Paid – Upload Screenshot" else "Replace Screenshot") {
            orders.proofBytes = null; orders.proofPreview = null; navigate(Route.UPLOAD_PROOF)
        } else footer("View My Order") { history.clear(); history.addLast(Route.ORDERS); show(Route.TRACK_ORDER) }
    }
}
