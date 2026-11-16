package com.monicoffee.pos.ui.menu

import com.monicoffee.pos.MainActivity

class FavoritesScreen(app: MainActivity) : MenuScreen(app) {
    override fun draw() {
        if (!quiet) refreshMenu()
        val grid = column()
        val saved = menu.items.filter { it.id in menu.favorites }
        header("My Favorites"); val b = content(); if (saved.isNotEmpty()) space(b, 20); b.addView(grid)
        fillGrid(grid, saved)
    }
}
