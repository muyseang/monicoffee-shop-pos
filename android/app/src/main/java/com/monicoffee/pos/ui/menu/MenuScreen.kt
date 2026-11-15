package com.monicoffee.pos.ui.menu

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.data.model.MenuItem
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Icon

/** What Home, Explore and Favorites share: loading the menu and drawing it as a grid of cards. */
abstract class MenuScreen(app: MainActivity) : Screen(app) {
    /** Fetches categories and items; redraws the menu screens when something changed. */
    protected fun refreshMenu(force: Boolean = false) {
        val stale = System.currentTimeMillis() - menu.loadedAt > AppConfig.MENU_MAX_AGE_MS
        if (menu.loading || !(force || stale) || (menu.error != null && !force)) return
        menu.loading = true; menu.error = null
        val onMenu = { screen in MENU_ROUTES }
        load({ api.categories() to api.menuItems() }, sticky = true, failed = {
            menu.loading = false
            menu.loadedAt = System.currentTimeMillis()
            if (menu.items.isEmpty()) { menu.error = it; if (onMenu()) redraw() }
        }) { (newCategories, newItems) ->
            menu.loading = false
            menu.loadedAt = System.currentTimeMillis()
            val changed = newCategories != menu.categories || newItems != menu.items
            menu.categories = newCategories; menu.items = newItems
            if (menu.categories.none { it.id == menu.categoryId }) menu.categoryId = 0
            if (changed && onMenu()) redraw()
        }
    }

    protected fun fillGrid(grid: LinearLayout, list: List<MenuItem>) {
        grid.removeAllViews()
        if (list.isEmpty()) {
            when {
                menu.error != null -> problem(grid, menu.error!!) { refreshMenu(force = true); redraw() }
                menu.items.isEmpty() && menu.loading -> skeleton(grid)
                screen == Route.FAVORITES -> empty(grid, "heart", "No favorite coffee yet", "When there's coffee, it will appear here", "Add Now") { history.clear(); show(Route.EXPLORE) }
                else -> notice(grid, "No items found", "Try another category or search.")
            }
            return
        }
        list.chunked(2).forEach { pair ->
            val r = row().apply { gravity = Gravity.TOP }
            pair.forEachIndexed { index, item ->
                val card = column().apply { setPadding(dp(8), dp(8), dp(8), dp(12)); background = bg(); elevation = dp(3).toFloat() }
                val picture = photo(item.imageUrl, 128).apply { if (!item.inStock) alpha = 0.45f }
                if (screen == Route.FAVORITES) card.addView(FrameLayout(context).apply {
                    addView(picture)
                    addView(Icon(context, "heartfill", Color.rgb(240, 80, 80)).apply {
                        contentDescription = "Remove from favorites"
                        setOnClickListener { menu.favorites.remove(item.id); prefs.favorites = menu.favorites; show(Route.FAVORITES) }
                    }, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
                }) else card.addView(picture)
                space(card, 8)
                card.addView(text(item.name, 16f, Palette.ink, true).apply { maxLines = 1 }); space(card, 4)
                card.addView(text(item.categoryName, 11f, Palette.muted)); space(card, 6)
                val price = row()
                price.addView(text(money(item.price), 19f, if (item.inStock) Color.BLACK else Palette.muted, true), LinearLayout.LayoutParams(0, -2, 1f))
                if (item.inStock) price.addView(button("+") { open(item) }.apply { minHeight = dp(32) }, LinearLayout.LayoutParams(dp(32), dp(32)))
                else price.addView(text("Out of stock", 11f, Palette.red, true))
                card.addView(price)
                card.setOnClickListener { open(item) }
                r.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) rightMargin = dp(16) })
            }
            if (pair.size == 1) r.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(r); space(grid, 16)
        }
    }

    private fun open(item: MenuItem) {
        menu.selected = item; menu.quantity = 1; menu.picks.clear()
        // Free choices such as Size start on their first value; paid extras start off.
        item.options.filter { it.choices.isNotEmpty() && it.extraPrice == 0.0 }.forEach { menu.picks[it.id] = it.choices.first() }
        navigate(Route.DETAILS)
    }

    private companion object {
        val MENU_ROUTES = setOf(Route.HOME, Route.EXPLORE, Route.FAVORITES)
    }
}
