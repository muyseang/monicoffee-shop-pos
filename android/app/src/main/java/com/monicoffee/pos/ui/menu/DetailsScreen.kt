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
        val b = content()
        b.addView(FrameLayout(context).apply {
            addView(photo(item.imageUrl, 200).apply { if (!item.inStock) alpha = 0.55f })
            if (!item.inStock) addView(
                text("Out of Stock", 14f, Color.WHITE, true).apply { setPadding(dp(16), dp(7), dp(16), dp(8)); background = bg(Color.rgb(222, 68, 60), 20) },
                FrameLayout.LayoutParams(-2, -2, Gravity.CENTER),
            )
        }); space(b, 24)
        val title = row(); title.addView(text(item.name, 19f, Palette.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(text("♧  ${item.categoryName}", 13f, Palette.green)); b.addView(title)
        if (!item.inStock) {
            space(b, 10)
            b.addView(text("This item is currently unavailable. Please check back later.", 13f, Palette.red).apply {
                gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), dp(6)); background = bg(Palette.pink, 8)
            })
        }
        if (item.description.isNotBlank()) {
            if (item.inStock) divider(b) else space(b, 16)
            b.addView(text("Description", 15f, Palette.ink, true)); space(b, 14)
            b.addView(text(item.description, 14f, Color.GRAY).apply { setLineSpacing(dp(4).toFloat(), 1f) })
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
            controls.addView(text("Add-ons", 15f, Palette.ink, true)); space(controls, 12)
            chips(controls, addOns.map(label), true, { name -> addOns.first { label(it) == name }.id in menu.picks }) { name ->
                val id = addOns.first { label(it) == name }.id
                if (menu.picks.remove(id) == null) menu.picks[id] = ""
                show(Route.DETAILS)
            }
            space(controls, 24)
        }
        choices.filter { it != size }.forEach { option ->
            val extra = if (option.extraPrice > 0) "  +${money(option.extraPrice)}" else ""
            controls.addView(text(option.name + extra, 15f, Palette.ink, true)); space(controls, 12)
            chips(controls, option.choices, true, { menu.picks[option.id] == it }) { choice ->
                // A free choice always has a value; a paid one can be tapped off again.
                if (menu.picks[option.id] == choice && option.extraPrice > 0) menu.picks.remove(option.id) else menu.picks[option.id] = choice
                show(Route.DETAILS)
            }
            space(controls, 24)
        }
        val bottomRow = row().apply { gravity = Gravity.TOP }
        if (size != null) {
            val left = column(); left.addView(text(size.name, 15f, Palette.ink, true)); space(left, 12)
            val letters = row()
            size.choices.forEach { choice ->
                val on = menu.picks[size.id] == choice
                letters.addView(text(choice.take(1).uppercase(), 15f, if (on) Color.WHITE else Palette.muted, on).apply {
                    gravity = Gravity.CENTER; background = bg(if (on) Palette.green else Color.rgb(236, 236, 236), 100); contentDescription = choice
                    setOnClickListener { menu.picks[size.id] = choice; show(Route.DETAILS) }
                }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
            }
            left.addView(letters); bottomRow.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val right = column(); right.addView(text("Quantity", 15f, Palette.ink, true)); space(right, 12)
        val q = row()
        q.addView(stepper("−", 36, true) { if (menu.quantity > 1) menu.quantity--; show(Route.DETAILS) })
        q.addView(text("${menu.quantity}", 16f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), -2))
        q.addView(stepper("+", 36, true) { if (menu.quantity < AppConfig.MAX_QUANTITY) menu.quantity++; show(Route.DETAILS) })
        // Fixed width: a wrap-content column of full-width children would collapse to nothing.
        right.addView(q); bottomRow.addView(right, LinearLayout.LayoutParams(dp(116), -2))
        controls.addView(bottomRow)
        if (item.inStock) b.addView(controls) else b.addView(FrameLayout(context).apply {
            addView(controls.apply { alpha = 0.4f })
            addView(View(context).apply { isClickable = true }, FrameLayout.LayoutParams(-1, -1))
        })

        val entry = CartItem(item, menu.picks.toMap(), menu.quantity)
        val bottom = row().apply { setPadding(dp(24), dp(16), dp(24), dp(20)); elevation = dp(8).toFloat(); setBackgroundColor(Color.WHITE) }
        val price = column(); price.addView(text(money(entry.unitPrice * menu.quantity), 25f, Palette.ink, true)); price.addView(text("Total Price", 13f, Palette.muted))
        bottom.addView(price, LinearLayout.LayoutParams(0, -2, 1f))
        val add = row().apply { gravity = Gravity.CENTER; background = bg(if (item.inStock) Palette.green else Color.rgb(228, 228, 228)) }
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
}
