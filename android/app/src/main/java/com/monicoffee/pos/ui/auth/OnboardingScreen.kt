package com.monicoffee.pos.ui.auth

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route

/** The three introduction slides shown the first time the app is opened. */
class OnboardingScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val slides = listOf(
            Triple(R.drawable.onboard1, "Each morning,\nit's a cup of Americano that brings me to life.", "Next"),
            Triple(R.drawable.onboard2, "On a weekend afternoon,\nit's a leisurely cappuccino.", "Next"),
            Triple(R.drawable.cafe, "When working late into the night,\nit's the life-saving latte.", "Start"),
        )
        val (image, title, label) = slides[state.onboardingSlide.coerceIn(0, slides.lastIndex)]
        val frame = FrameLayout(context)
        frame.addView(ImageView(context).apply { setImageResource(image); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(150, 0, 0, 0), Color.argb(250, 0, 0, 0)))
        }, FrameLayout.LayoutParams(-1, -1))
        val b = column().apply { setPadding(dp(24), 0, dp(24), dp(40)) }
        b.addView(text(title, 24f, Color.WHITE, true).apply { setLineSpacing(dp(4).toFloat(), 1f) }); space(b, 14)
        b.addView(text("No need to wait, no need to hesitate — just a simple tap is all it takes.", 14f, Color.rgb(210, 210, 210))); space(b, 32)
        val bars = row()
        slides.indices.forEach { i ->
            bars.addView(View(context).apply { background = bg(if (i == state.onboardingSlide) Color.rgb(0, 150, 105) else Color.rgb(130, 130, 130), 4) },
                LinearLayout.LayoutParams(0, dp(4), if (i == state.onboardingSlide) 4f else 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        b.addView(bars); space(b, 28)
        b.addView(button(label) {
            if (state.onboardingSlide < slides.lastIndex) { state.onboardingSlide++; show(Route.ONBOARDING) }
            else { prefs.onboarded = true; show(Route.LOGIN) }
        })
        frame.addView(b, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
    }
}
