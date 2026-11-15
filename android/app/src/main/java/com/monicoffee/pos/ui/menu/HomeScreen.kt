package com.monicoffee.pos.ui.menu

import android.app.AlertDialog
import android.graphics.Color
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Icon

class HomeScreen(app: MainActivity) : MenuScreen(app) {
    override fun draw() {
        if (!quiet) refreshMenu()
        val grid = column()
        val h = column().apply { setPadding(dp(24), dp(20), dp(24), dp(14)); setBackgroundColor(Color.rgb(31, 31, 31)) }
        val loc = row(); val label = column(); label.addView(text("Location", 13f, Palette.muted)); space(label, 6)
        label.addView(text("Moni Coffee, Phnom Penh⌄", 15f, Color.WHITE, true)); label.setOnClickListener { navigate(Route.CAFE) }
        loc.addView(label, LinearLayout.LayoutParams(0, -2, 1f)); loc.addView(icon("cart", Color.WHITE) { navigate(Route.CART) }); h.addView(loc); space(h, 14)
        val names = listOf("All") + menu.categories.map { it.name }
        val pick = { name: String -> menu.categoryId = menu.categories.firstOrNull { it.name == name }?.id ?: 0; show(Route.HOME) }
        val searchRow = row()
        val search = field("Search coffee", menu.query).apply { background = null; setTextColor(Color.WHITE); setPadding(dp(4), 0, dp(12), 0) }
        val searchBox = row().apply { background = bg(Color.rgb(43, 43, 43)); setPadding(dp(8), 0, 0, 0) }
        searchBox.addView(Icon(context, "explore", Color.rgb(200, 200, 200)), LinearLayout.LayoutParams(dp(32), dp(32)))
        searchBox.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(16) })
        searchRow.addView(icon("filter", Color.WHITE) {
            AlertDialog.Builder(context).setTitle("Category").setItems(names.toTypedArray()) { _, i -> pick(names[i]) }.show()
        }.apply { background = bg(Palette.green); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)) }); h.addView(searchRow); root.addView(h)
        val b = content()
        val current = menu.categories.firstOrNull { it.id == menu.categoryId }?.name ?: "All"
        chips(b, names, false, { it == current }, pick); space(b, 16)
        b.addView(grid)
        val visible = { menu.items.filter { (menu.categoryId == 0 || it.categoryId == menu.categoryId) && it.name.contains(menu.query, true) } }
        fillGrid(grid, visible())
        search.onChange { menu.query = it; fillGrid(grid, visible()) }
        nav(Route.HOME)
    }
}
