package com.monicoffee.pos.ui.cart

import android.app.AlertDialog
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.countLabel
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

class CartScreen(app: MainActivity) : BasketScreen(app) {
    override fun draw() {
        if (cart.isEmpty()) {
            header("My Cart")
            empty(content(), vector(R.drawable.ic_empty_cart), "Your cart is empty", "Browse the menu and add your favorite drinks to get started.", "Browse Menu") { history.clear(); show(Route.HOME) }
            return
        }
        header("My Cart", icon("trash", Palette.red) {
            AlertDialog.Builder(context).setTitle("Clear your cart?").setPositiveButton("Clear") { _, _ -> cart.clear(); show(Route.CART) }.setNegativeButton("Cancel", null).show()
        })
        val b = content()
        b.addView(text("You have ${countLabel(cart.sumOf { it.count })} in your cart", 15f, Palette.ink, true)); space(b, 24)
        orderItems(b, bold = false)
        divider(b); summary(b, "Total", money(total()))
        footer("Checkout Now") { checkout.qr = null; checkout.error = false; navigate(Route.CHECKOUT) }
    }
}
