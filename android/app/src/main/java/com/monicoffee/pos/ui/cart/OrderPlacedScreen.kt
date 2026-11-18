package com.monicoffee.pos.ui.cart

import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Design 21: a back arrow, the celebrating coffee-break illustration and View Order. */
class OrderPlacedScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        header(""); val b = content(); space(b, 57)
        b.addView(ImageView(context).apply { setImageResource(R.drawable.order_success) }, LinearLayout.LayoutParams(dp(250), dp(250)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(b, 32)
        b.addView(text("Order placed successfully 🥳", 18f, Palette.ink, true).apply { gravity = Gravity.CENTER; letterSpacing = -0.02f }); space(b, 16)
        b.addView(text("Your coffee is being prepared, please wait...", 14f, Palette.muted).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f; leading(1.6f) }); space(b, 66)
        b.addView(button("View Order") {
            history.clear()
            if (orders.current == null) show(Route.ORDERS) else { history.addLast(Route.ORDERS); show(Route.TRACK_ORDER) } // Track Order
        }, LinearLayout.LayoutParams(-1, dp(56)))
    }
}
