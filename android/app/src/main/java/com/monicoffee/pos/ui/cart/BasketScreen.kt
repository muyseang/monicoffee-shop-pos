package com.monicoffee.pos.ui.cart

import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** What the cart and the checkout share: the list of chosen items and their total. */
abstract class BasketScreen(app: MainActivity) : Screen(app) {
    protected fun total() = cart.sumOf { it.unitPrice * it.count }

    /** The "Order Items" list, with a − / + stepper and a remove button for each item. */
    protected fun orderItems(b: LinearLayout, bold: Boolean) {
        val heading = row(); heading.addView(text("Order Items", 17f, Palette.ink, bold), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text("+ Add More", 14f, Palette.green, true).apply { setOnClickListener { history.clear(); show(Route.HOME) } }); b.addView(heading); space(b, 20)
        cart.toList().forEach { item ->
            val r = row(); r.addView(photo(item.item.imageUrl, 54), LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) })
            val label = column(); label.addView(text(item.item.name, 15f, Palette.ink, true)); space(label, 4)
            label.addView(text(listOf(item.label, money(item.unitPrice)).filter { it.isNotEmpty() }.joinToString(" · "), 12f, Palette.muted))
            r.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(stepper("−", 28, false) { item.count--; if (item.count == 0) cart.remove(item); show(screen) })
            r.addView(text("${item.count}", 14f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(30), -2))
            r.addView(stepper("+", 28, false) { if (item.count < AppConfig.MAX_QUANTITY) item.count++; show(screen) })
            // Takes the whole line out at once, whatever its quantity.
            r.addView(vector(R.drawable.ic_close).apply {
                contentDescription = "Remove ${item.item.name}"
                setOnClickListener { cart.remove(item); toast("${item.item.name} removed from your cart."); show(screen) }
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { leftMargin = dp(6) })
            b.addView(r); space(b, 20)
        }
    }
}
