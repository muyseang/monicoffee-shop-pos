package com.monicoffee.pos.ui.menu

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.data.model.CartItem
import com.monicoffee.pos.data.model.ItemOption
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Icon

/** One menu item: its options, the quantity, and Add to Cart. */
class DetailsScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val item = menu.selected ?: return show(Route.HOME)
        val saved = item.id in menu.favorites
        header("Details", icon(if (saved) "heartfill" else "heart", if (saved) Color.rgb(240, 80, 80) else Palette.muted) {
            if (!menu.favorites.add(item.id)) menu.favorites.remove(item.id)
            prefs.favorites = menu.favorites; show(Route.DETAILS)
        })
        root.setBackgroundColor(Color.rgb(252, 252, 254)) // design 39's page colour
        val b = content(); space(b, 4)
        val unavailable = Color.rgb(214, 69, 69)
        b.addView(FrameLayout(context).apply {
            addView(photo(item.imageUrl, 200).apply { background = bg(Color.rgb(238, 238, 238), 16) })
            if (!item.inStock) {
                addView(View(context).apply { background = bg(Color.argb(115, 0, 0, 0), 16) }, FrameLayout.LayoutParams(-1, dp(200)))
                addView(text("Out of Stock", 14f, Color.WHITE, true).apply { setPadding(dp(16), dp(8), dp(16), dp(8)); background = bg(unavailable, 20) },
                    FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            }
        }); space(b, 20)
        // The name over its category. (Design 39 also shows ingredient icons, which the menu has no data for.)
        b.addView(text(item.name, 16f, Color.rgb(36, 36, 36), true).apply { leading(1.5f) }); space(b, 4)
        b.addView(text(item.categoryName, 12f, Color.rgb(162, 162, 162)))
        if (!item.inStock) {
            space(b, 11)
            b.addView(text("This item is currently unavailable. Please check back later.", 12f, unavailable).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6)); background = bg(Color.rgb(253, 241, 241), 8)
            }, LinearLayout.LayoutParams(-2, -2))
        }
        if (item.description.isNotBlank()) {
            space(b, 24)
            b.addView(text("Description", 14f, Palette.ink, true).apply { letterSpacing = -0.01f }); space(b, 15)
            b.addView(text(item.description, 12f, Color.rgb(98, 98, 98)).apply { letterSpacing = -0.01f; leading(1.6f) })
        }
        space(b, 24)

        // Everything the customer chooses; greyed out and untappable when the item is out of stock.
        val controls = column()
        val choices = item.options.filter { it.choices.isNotEmpty() }
        // A short free choice (Size: Small / Medium / Large) is drawn as round letters next to Quantity.
        val size = choices.firstOrNull { o -> o.extraPrice == 0.0 && o.choices.size <= 4 && o.choices.map { it.first().uppercaseChar() }.distinct().size == o.choices.size }
        val addOns = item.options.filter { it.choices.isEmpty() }
        if (addOns.isNotEmpty()) {
            val label = { o: ItemOption -> if (o.extraPrice > 0) "${o.name} +${money(o.extraPrice)}" else o.name }
            controls.addView(text("Add-ons", 14f, SECTION, true)); space(controls, 15)
            chips(controls, addOns.map(label), true, { name -> addOns.first { label(it) == name }.id in menu.picks }) { name ->
                val id = addOns.first { label(it) == name }.id
                if (menu.picks.remove(id) == null) menu.picks[id] = ""
                show(Route.DETAILS)
            }
            space(controls, 24)
        }
        choices.filter { it != size }.forEach { option ->
            val extra = if (option.extraPrice > 0) "  +${money(option.extraPrice)}" else ""
            controls.addView(text(option.name + extra, 14f, SECTION, true)); space(controls, 15)
            chips(controls, option.choices, true, { menu.picks[option.id] == it }) { choice ->
                // A free choice always has a value; a paid one can be tapped off again.
                if (menu.picks[option.id] == choice && option.extraPrice > 0) menu.picks.remove(option.id) else menu.picks[option.id] = choice
                show(Route.DETAILS)
            }
            space(controls, 24)
        }
        val bottomRow = row().apply { gravity = Gravity.TOP }
        if (size != null) {
            val left = column(); left.addView(text(size.name, 14f, SECTION, true)); space(left, 15)
            val letters = row()
            size.choices.forEach { choice ->
                val on = menu.picks[size.id] == choice
                letters.addView(text(choice.take(1).uppercase(), 16f, if (on) Color.WHITE else Color.rgb(119, 119, 119), on).apply {
                    gravity = Gravity.CENTER; background = bg(if (on) Palette.green else Color.rgb(245, 245, 245), 100); contentDescription = choice
                    setOnClickListener { menu.picks[size.id] = choice; show(Route.DETAILS) }
                }, LinearLayout.LayoutParams(dp(35), dp(35)).apply { rightMargin = dp(20) })
            }
            left.addView(letters); bottomRow.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val right = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        right.addView(text("Quantity", 14f, SECTION, true), LinearLayout.LayoutParams(-2, -2)); space(right, 15)
        val q = row()
        q.addView(stepper("−", 35, true) { if (menu.quantity > 1) menu.quantity--; show(Route.DETAILS) })
        q.addView(text("${menu.quantity}", 14f, Color.rgb(47, 53, 72), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), -2))
        q.addView(stepper("+", 35, true) { if (menu.quantity < AppConfig.MAX_QUANTITY) menu.quantity++; show(Route.DETAILS) })
        // Fixed width: a wrap-content column of full-width children would collapse to nothing.
        right.addView(q, LinearLayout.LayoutParams(-2, -2)); bottomRow.addView(right, LinearLayout.LayoutParams(dp(124), -2))
        controls.addView(bottomRow)
        if (item.inStock) b.addView(controls) else b.addView(FrameLayout(context).apply {
            addView(controls.apply { alpha = 0.4f })
            addView(View(context).apply { isClickable = true }, FrameLayout.LayoutParams(-1, -1))
        })

        val entry = CartItem(item, menu.picks.toMap(), menu.quantity)
        // Design 39's bottom bar: white with a soft shadow above, the 24sp total and a 16dp-cornered button.
        val bottom = row().apply { setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(10).toFloat(); setBackgroundColor(Color.WHITE) }
        val price = column()
        price.addView(text(money(entry.unitPrice * menu.quantity), 24f, Palette.ink, true).apply { letterSpacing = -0.02f })
        price.addView(text("Total Price", 14f, Palette.muted).apply { letterSpacing = -0.01f })
        bottom.addView(price, LinearLayout.LayoutParams(0, -2, 1f))
        val add = row().apply { gravity = Gravity.CENTER; background = bg(if (item.inStock) Palette.green else Color.rgb(230, 230, 230), 16) }
        if (item.inStock) {
            add.addView(Icon(context, "orders", Color.WHITE), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) })
            add.addView(text("Add to Cart", 16f, Color.WHITE, true))
            add.setOnClickListener {
                val existing = cart.find { it.item.id == item.id && it.picks == entry.picks }
                if (existing != null) existing.count = minOf(AppConfig.MAX_QUANTITY, existing.count + menu.quantity) else cart.add(entry)
                navigate(Route.CART)
            }
        } else add.addView(text("Out of Stock", 16f, Palette.muted, true))
        bottom.addView(add, LinearLayout.LayoutParams(dp(166), dp(56))); root.addView(bottom)
    }

    private companion object {
        val SECTION = Color.rgb(68, 68, 68) // the option headings' #444
    }
}
