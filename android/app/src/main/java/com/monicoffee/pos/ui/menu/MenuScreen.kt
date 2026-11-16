package com.monicoffee.pos.ui.menu

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.data.model.MenuItem
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

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
                menu.items.isEmpty() && menu.loading -> skeleton(grid, "Loading menu…")
                screen == Route.FAVORITES -> empty(grid, vector(R.drawable.ic_heart_line), "No favorite coffee yet", "When there's coffee, it will appear here", "Add Now") { history.clear(); show(Route.EXPLORE) }
                else -> notice(grid, "No items found", "Try another category or search.")
            }
            return
        }
        list.chunked(2).forEach { pair ->
            val r = row().apply { gravity = Gravity.TOP }
            pair.forEachIndexed { index, item ->
                // Product card (Mainpage design): 8dp corners, a soft shadow, 128dp photo, 16sp name, 18sp price.
                val card = column().apply { setPadding(dp(8), dp(8), dp(8), dp(12)); background = bg(Color.WHITE, 8); elevation = dp(3).toFloat() }
                val picture = photo(item.imageUrl, 128).apply { background = bg(Color.rgb(249, 249, 249), 0); if (!item.inStock) alpha = 0.45f }
                if (screen == Route.FAVORITES) card.addView(FrameLayout(context).apply {
                    addView(picture)
                    addView(vector(R.drawable.ic_heart_fill).apply {
                        contentDescription = "Remove from favorites"
                        setOnClickListener { menu.favorites.remove(item.id); prefs.favorites = menu.favorites; show(Route.FAVORITES) }
                    }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
                }) else card.addView(picture)
                space(card, 8)
                card.addView(text(item.name, 16f, Color.rgb(36, 36, 36), true).apply { maxLines = 1; leading(1.5f) }); space(card, 4)
                card.addView(text(item.categoryName, 10f, Color.rgb(162, 162, 162))); space(card, 8)
                val price = row()
                price.addView(text(money(item.price), 18f, if (item.inStock) Color.rgb(5, 5, 5) else Palette.muted, true).apply { leading(1.5f) }, LinearLayout.LayoutParams(0, -2, 1f))
                if (item.inStock) price.addView(vector(R.drawable.ic_plus).apply {
                    contentDescription = "Add"; background = bg(Palette.green, 8); setOnClickListener { open(item) }
                }, LinearLayout.LayoutParams(dp(32), dp(32)))
                else price.addView(text("Out of stock", 11f, Palette.red, true))
                card.addView(price)
                card.setOnClickListener { open(item) }
                r.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) rightMargin = dp(15) })
            }
            if (pair.size == 1) r.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            // Room under the cards for their shadow; with it the rows sit 16dp apart (24dp on My Favorites, design 37).
            r.setPadding(0, 0, 0, dp(6)); grid.addView(r); space(grid, if (screen == Route.FAVORITES) 18 else 10)
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
