package com.monicoffee.pos.ui.auth

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette

class SplashScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        root.setBackgroundColor(Palette.dark); root.gravity = Gravity.CENTER
        val title = SpannableString("MoniCoffee").apply { setSpan(ForegroundColorSpan(Color.rgb(0, 150, 105)), 4, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        root.addView(text("", 34f, Color.WHITE).apply { text = title; gravity = Gravity.CENTER }); space(root, 14)
        root.addView(text("Moments of Healing for City Dwellers", 14f, Color.rgb(190, 190, 190)).apply { gravity = Gravity.CENTER })
    }
}
