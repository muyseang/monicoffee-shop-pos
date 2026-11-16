package com.monicoffee.pos.ui.auth

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette

/** Design 01: the brand name on a dark gradient while the app starts. */
class SplashScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        root.background = GradientDrawable(GradientDrawable.Orientation.TR_BL, intArrayOf(Color.rgb(17, 17, 17), Color.rgb(49, 49, 49)))
        root.gravity = Gravity.CENTER
        val title = SpannableString("MoniCoffee").apply { setSpan(ForegroundColorSpan(Palette.green), 4, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        root.addView(text("", 32f, Color.WHITE).apply { text = title; gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.01f }); space(root, 8)
        root.addView(text("Moments of Healing for City Dwellers", 14f, Palette.muted).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f })
    }
}
