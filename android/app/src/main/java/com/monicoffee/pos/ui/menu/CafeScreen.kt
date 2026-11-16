package com.monicoffee.pos.ui.menu

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette

/**
 * Design 14: the shop's photo, name, opening hours and About. (The design's rating, Book Now,
 * address, map and Reviews tab are template content the backend has no data for.)
 */
class CafeScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        header("Moni Cafe"); val b = content(); space(b, 4)
        b.addView(FrameLayout(context).apply {
            addView(localImage(R.drawable.onboard3, 200).apply { background = bg(Color.WHITE, 16) })
            addView(View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(128, 0, 0, 0))).apply { cornerRadius = dp(16).toFloat() }
            }, FrameLayout.LayoutParams(-1, dp(200)))
        }); space(b, 20)
        b.addView(text("Moni Coffee, Phnom Penh", 16f, Palette.ink, true).apply { letterSpacing = -0.02f }); space(b, 12)
        b.addView(row().apply {
            addView(vector(R.drawable.ic_time), LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(4) })
            addView(text("07:00-23:00", 12f, Palette.muted).apply { letterSpacing = -0.01f })
        }); space(b, 24)
        // The design's tab bar, with only Location Details: there are no reviews to show.
        b.addView(text("Location Details", 14f, Palette.green).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f },
            LinearLayout.LayoutParams(-1, dp(20))); space(b, 8)
        b.addView(View(context).apply { setBackgroundColor(Palette.green) }, LinearLayout.LayoutParams(-1, dp(2))); space(b, 32)
        b.addView(text("About", 16f, Palette.ink, true).apply { letterSpacing = -0.02f }); space(b, 8)
        b.addView(text("A cozy place to enjoy your favorite coffee, freshly prepared for dine-in or pickup.", 12f, Palette.muted).apply { letterSpacing = -0.01f; leading(1.6f) })
    }
}
