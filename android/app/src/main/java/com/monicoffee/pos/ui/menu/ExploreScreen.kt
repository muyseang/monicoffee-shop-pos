package com.monicoffee.pos.ui.menu

import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.navigation.Route

class ExploreScreen(app: MainActivity) : MenuScreen(app) {
    override fun draw() {
        if (!quiet) refreshMenu()
        val grid = column()
        header("Explore", icon("heart") { navigate(Route.FAVORITES) })
        val b = content(); val search = field("Search coffee"); b.addView(search)
        b.addView(grid)
        fillGrid(grid, menu.items)
        search.onChange { typed -> fillGrid(grid, menu.items.filter { it.name.contains(typed, true) }) }
        nav(Route.EXPLORE)
    }
}
