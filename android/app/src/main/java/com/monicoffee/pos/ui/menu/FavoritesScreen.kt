package com.monicoffee.pos.ui.menu

import com.monicoffee.pos.MainActivity

class FavoritesScreen(app: MainActivity) : MenuScreen(app) {
    override fun draw() {
        if (!quiet) refreshMenu()
        val grid = column()
        header("My Favorites"); val b = content(); b.addView(grid)
        fillGrid(grid, menu.items.filter { it.id in menu.favorites })
    }
}
