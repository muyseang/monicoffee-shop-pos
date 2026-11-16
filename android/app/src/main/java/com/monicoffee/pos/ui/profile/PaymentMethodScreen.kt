package com.monicoffee.pos.ui.profile

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Icon

/** How orders from the app are paid: QR only. Cash is taken at the counter, not in the app. */
class PaymentMethodScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        if (!quiet) load({ api.qrPayment() }, failed = {}) { if (it != checkout.qr) { checkout.qr = it; redraw() } }
        header("Payment method"); val b = content(); b.addView(text("Payment Method", 20f, Palette.ink, true)); space(b, 20)
        val qrOn = checkout.qr?.enabled == true
        b.addView(row().apply {
            if (!qrOn) alpha = 0.45f
            addView(text("KH", 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(Palette.green, 6) }, LinearLayout.LayoutParams(dp(56), dp(40)).apply { rightMargin = dp(16) })
            addView(text("KH QR Payment", 17f), LinearLayout.LayoutParams(0, -2, 1f))
            if (qrOn) addView(Icon(context, "check", Palette.green), LinearLayout.LayoutParams(dp(32), dp(32)))
        })
        // Not in design 35, which only shows the method switched on.
        if (!qrOn) { space(b, 6); b.addView(text("Not offered by the shop at the moment, so orders can't be placed in the app.", 13f, Palette.muted)) }
        divider(b)
    }
}
