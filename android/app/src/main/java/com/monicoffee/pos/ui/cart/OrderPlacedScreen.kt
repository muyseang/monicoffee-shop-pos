package com.monicoffee.pos.ui.cart

import android.view.Gravity
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

class OrderPlacedScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val b = content(); space(b, 80); b.addView(picture("cupcheck", Palette.green, 230)); space(b, 36)
        b.addView(text("Order placed successfully 🥳", 20f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(b, 14)
        b.addView(text("Your coffee is being prepared, please wait...", 15f, Palette.muted).apply { gravity = Gravity.CENTER }); space(b, 40)
        b.addView(button("View Order") {
            history.clear()
            if (orders.current == null) show(Route.ORDERS) else { history.addLast(Route.ORDERS); show(Route.TRACK_ORDER) } // Track Order
        })
    }
}
