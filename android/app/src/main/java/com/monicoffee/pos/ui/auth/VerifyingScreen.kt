package com.monicoffee.pos.ui.auth

import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

class VerifyingScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        header("Verifying"); val b = content(); space(b, 140)
        b.addView(ProgressBar(context).apply { indeterminateTintList = ColorStateList.valueOf(Palette.green) }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(b, 24)
        b.addView(text("Verifying your number...", 18f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(b, 10)
        b.addView(text("Please wait while we confirm your code.\nThis may take a few seconds.", 14f, Palette.muted).apply { gravity = Gravity.CENTER })
        root.addView(text("Cancel", 15f, Palette.muted, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(24), dp(16), dp(24), dp(40)); setOnClickListener { show(Route.VERIFICATION) }
        })
        // sticky: a code the server accepted signs the app in even if Cancel was tapped meanwhile.
        if (!quiet) load({ api.verifyOtp(auth.purpose, auth.phone, auth.code) }, sticky = true, failed = {
            if (screen == Route.VERIFYING) show(Route.VERIFICATION); alert(it.message)
        }) { session.signIn(it) }
    }
}
