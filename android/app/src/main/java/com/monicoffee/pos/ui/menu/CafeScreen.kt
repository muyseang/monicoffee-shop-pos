package com.monicoffee.pos.ui.menu

import android.graphics.Color
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

class CafeScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        header("Moni Cafe"); val b = content(); b.addView(localImage(R.drawable.cafe, 200)); space(b, 24)
        b.addView(text("Moni Coffee, Phnom Penh", 21f, Palette.ink, true)); space(b, 12); b.addView(text("◷  07:00–23:00", 14f, Palette.muted)); divider(b)
        b.addView(text("Location Details", 16f, Palette.green, true)); space(b, 24); b.addView(text("About", 18f, Palette.ink, true)); space(b, 12)
        b.addView(text("A cozy place to enjoy your favorite coffee, freshly prepared for dine-in or pickup.", 15f, Color.GRAY))
        footer("Browse Menu") { history.clear(); show(Route.HOME) }
    }
}
