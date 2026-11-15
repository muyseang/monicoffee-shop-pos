package com.monicoffee.pos.ui.auth

import android.view.Gravity
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

class RegisteredScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val b = content(); space(b, 80); b.addView(picture("cup", Palette.green, 230)); space(b, 36)
        b.addView(text("Sign-up successful 🥳", 20f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(b, 14)
        b.addView(text("Congratulations! Your account has been activated. You can now explore and order from our menu.", 15f, Palette.muted).apply { gravity = Gravity.CENTER; setLineSpacing(dp(4).toFloat(), 1f) }); space(b, 32)
        b.addView(button("Get started") { history.clear(); show(Route.HOME) })
    }
}
