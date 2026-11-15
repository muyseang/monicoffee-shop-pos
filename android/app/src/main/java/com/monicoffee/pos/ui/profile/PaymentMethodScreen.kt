package com.monicoffee.pos.ui.profile

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Icon

/** The customer's preferred payment method, pre-selected at checkout. */
class PaymentMethodScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        if (!quiet) load({ api.qrPayment() }, failed = {}) { if (it != checkout.qr) { checkout.qr = it; redraw() } }
        header("Payment method"); val b = content(); b.addView(text("Payment Method", 20f, Palette.ink, true)); space(b, 20)
        val qrOn = checkout.qr?.enabled == true
        listOf("cash" to "Cash", "manual_qr" to "Manual QR Payment").forEach { (value, label) ->
            val usable = value == "cash" || qrOn
            val chosen = value == checkout.payment && usable
            val tile = if (value == "cash") FrameLayout(context).apply {
                background = bg(Color.WHITE, 6, Palette.line)
                addView(Icon(context, "cash", Palette.ink), FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            } else text("QR", 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(Palette.green, 6) }
            b.addView(row().apply {
                if (!usable) alpha = 0.45f
                addView(tile, LinearLayout.LayoutParams(dp(56), dp(40)).apply { rightMargin = dp(16) })
                addView(text(label, 17f), LinearLayout.LayoutParams(0, -2, 1f))
                if (chosen) addView(Icon(context, "check", Palette.green), LinearLayout.LayoutParams(dp(32), dp(32)))
                if (usable) setOnClickListener { checkout.payment = value; prefs.payment = value; show(Route.PAYMENT_METHOD) }
            })
            if (!usable) { space(b, 6); b.addView(text("Not offered by the shop at the moment.", 13f, Palette.muted)) }
            divider(b)
        }
    }
}
