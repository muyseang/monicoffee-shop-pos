package com.monicoffee.pos.ui.auth

import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Design 09: the coffee-break illustration, the welcome text and Get started. */
class RegisteredScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val b = content(); space(b, 125)
        b.addView(ImageView(context).apply { setImageResource(R.drawable.coffee_break) }, LinearLayout.LayoutParams(dp(250), dp(250)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(b, 32)
        b.addView(text("Sign-up successful🥳", 18f, Palette.ink).semibold().apply { gravity = Gravity.CENTER; letterSpacing = -0.02f }); space(b, 16)
        b.addView(text("Congratulations! Your account has been activated. You can now explore and buy the best coffee in the world.", 14f, Palette.muted).apply {
            gravity = Gravity.CENTER; letterSpacing = -0.01f; leading(1.6f)
        }); space(b, 26)
        b.addView(button("Get started") { history.clear(); show(Route.HOME) }.semibold(), LinearLayout.LayoutParams(-1, dp(56)))
    }
}
