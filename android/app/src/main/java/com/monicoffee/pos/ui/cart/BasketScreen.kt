package com.monicoffee.pos.ui.cart

import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** What the cart and the checkout share: the list of chosen items and their total. */
abstract class BasketScreen(app: MainActivity) : Screen(app) {
    protected fun total() = cart.sumOf { it.unitPrice * it.count }

    /** The "Order Items" list, with a − / + stepper for each item. */
    protected fun orderItems(b: LinearLayout, bold: Boolean) {
        val heading = row(); heading.addView(text("Order Items", 17f, Palette.ink, bold), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text("+ Add More", 14f, Palette.green, true).apply { setOnClickListener { history.clear(); show(Route.HOME) } }); b.addView(heading); space(b, 20)
        cart.toList().forEach { item ->
            val r = row(); r.addView(photo(item.item.imageUrl, 54), LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) })
            val label = column(); label.addView(text(item.item.name, 15f, Palette.ink, true)); space(label, 4)
            label.addView(text(listOf(item.label, money(item.unitPrice)).filter { it.isNotEmpty() }.joinToString(" • "), 12f, Palette.muted))
            r.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(stepper("−", 28, false) { item.count--; if (item.count == 0) cart.remove(item); show(screen) })
            r.addView(text("${item.count}", 14f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(30), -2))
            r.addView(stepper("+", 28, false) { if (item.count < AppConfig.MAX_QUANTITY) item.count++; show(screen) })
            b.addView(r); space(b, 20)
        }
    }
}
