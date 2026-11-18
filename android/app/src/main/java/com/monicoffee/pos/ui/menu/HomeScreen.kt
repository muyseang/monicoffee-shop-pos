package com.monicoffee.pos.ui.menu

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

class HomeScreen(app: MainActivity) : MenuScreen(app) {
    override fun draw() {
        if (!quiet) refreshMenu()
        val grid = column()
        // Mainpage design: a dark gradient block holding the location, cart and search bar.
        val h = column().apply {
            setPadding(dp(24), dp(16), dp(24), dp(13))
            background = GradientDrawable(GradientDrawable.Orientation.TR_BL, intArrayOf(Color.rgb(17, 17, 17), Color.rgb(49, 49, 49)))
        }
        val loc = row().apply { gravity = Gravity.BOTTOM }; val label = column()
        label.addView(text("Location", 12f, Color.rgb(162, 162, 162)).apply { letterSpacing = 0.01f }); space(label, 8)
        label.addView(row().apply {
            addView(text("Moni Coffee, Phnom Penh", 14f, Color.rgb(216, 216, 216), true).apply { leading(1.5f) })
            addView(vector(R.drawable.ic_arrow_down), LinearLayout.LayoutParams(dp(14), dp(14)).apply { leftMargin = dp(4) })
        }); label.setOnClickListener { navigate(Route.CAFE) }
        loc.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        loc.addView(cartButton(), LinearLayout.LayoutParams(dp(32), dp(30)))
        h.addView(loc); space(h, 12)
        val names = listOf("All") + menu.categories.map { it.name }
        val pick = { name: String -> menu.categoryId = menu.categories.firstOrNull { it.name == name }?.id ?: 0; show(Route.HOME) }
        val searchRow = row()
        val search = field("Search coffee", menu.query).apply {
            background = null; setTextColor(Color.WHITE); setHintTextColor(Color.rgb(162, 162, 162)); setPadding(dp(8), 0, dp(16), 0)
        }
        val searchBox = row().apply { background = bg(Color.rgb(42, 42, 42), 8); setPadding(dp(16), 0, 0, 0) }
        searchBox.addView(vector(R.drawable.ic_search), LinearLayout.LayoutParams(dp(20), dp(20)))
        searchBox.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, dp(48), 1f)); h.addView(searchRow); root.addView(h)
        val b = content(); space(b, 12)
        val current = menu.categories.firstOrNull { it.id == menu.categoryId }?.name ?: "All"
        chips(b, names, false, { it == current }, pick); space(b, 16)
        b.addView(grid)
        val visible = { menu.items.filter { (menu.categoryId == 0 || it.categoryId == menu.categoryId) && it.name.contains(menu.query, true) } }
        fillGrid(grid, visible())
        search.onChange { menu.query = it; fillGrid(grid, visible()) }
        nav(Route.HOME)
    }

    /** The cart icon, with a red dot counting the items once something is in the cart. */
    private fun cartButton() = FrameLayout(context).apply {
        val count = cart.sumOf { it.count }
        contentDescription = if (count == 0) "Cart" else "Cart, $count item${if (count == 1) "" else "s"}"
        setOnClickListener { navigate(Route.CART) }
        addView(vector(R.drawable.ic_cart), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.BOTTOM or Gravity.START))
        if (count > 0) addView(text(if (count > 9) "9+" else "$count", 9f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; includeFontPadding = false; minWidth = dp(16); setPadding(dp(3), 0, dp(3), 0)
            background = bg(Palette.red, 100).apply { setStroke(dp(1), Color.rgb(31, 31, 31)) }
        }, FrameLayout.LayoutParams(-2, dp(16), Gravity.TOP or Gravity.END))
    }
}
