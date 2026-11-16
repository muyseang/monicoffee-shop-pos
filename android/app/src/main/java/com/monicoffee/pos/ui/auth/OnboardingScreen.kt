package com.monicoffee.pos.ui.auth

import android.graphics.Color
import android.graphics.Typeface
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
import com.monicoffee.pos.ui.theme.Palette

/** Designs 02–04: the three introduction slides shown the first time the app is opened. */
class OnboardingScreen(app: MainActivity) : Screen(app) {
    private class Slide(val image: Int, val title: String, val align: Int, val label: String)

    override fun draw() {
        // Each slide's text sits where its design puts it: left, centred, then right.
        val slides = listOf(
            Slide(R.drawable.onboard1, "Each morning,\nIt's a cup of Americano that brings me to life.", Gravity.START, "Next"),
            Slide(R.drawable.onboard2, "On a weekend afternoon,\nIt's a leisurely cappuccino", Gravity.CENTER_HORIZONTAL, "Next"),
            Slide(R.drawable.onboard3, "When working late into the night,\nIt's the life-saving latte.", Gravity.END, "Start"),
        )
        val current = state.onboardingSlide.coerceIn(0, slides.lastIndex)
        val slide = slides[current]
        val frame = FrameLayout(context)
        frame.addView(ImageView(context).apply { setImageResource(slide.image); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
        // Clear at the top, half black a quarter of the way down, black at the bottom.
        frame.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(128, 0, 0, 0), Color.BLACK)).apply { setGradientCenter(0.5f, 0.26f) }
        }, FrameLayout.LayoutParams(-1, -1))
        val b = column().apply { setPadding(dp(24), 0, dp(24), dp(56)); gravity = slide.align }
        b.addView(text(slide.title, 25f, Color.WHITE).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.01f; leading(1.3f); gravity = slide.align
        }); space(b, 20)
        b.addView(text("No need to wait, no need to hesitate—just a simple tap is all it takes.", 14f, Color.WHITE).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); leading(1.6f); gravity = slide.align
        }, LinearLayout.LayoutParams(if (slide.align == Gravity.START) -1 else dp(280), -2)); space(b, 72)
        // The current slide's bar stretches; slides already seen stay green.
        val bars = row()
        slides.indices.forEach { i ->
            val on = i == current
            bars.addView(View(context).apply { background = bg(if (i <= current) Palette.green else Color.argb(191, 120, 120, 120), 16) },
                LinearLayout.LayoutParams(if (on) 0 else dp(48), dp(8), if (on) 1f else 0f).apply { if (i > 0) leftMargin = dp(12) })
        }
        b.addView(bars, LinearLayout.LayoutParams(-1, -2)); space(b, 40)
        b.addView(button(slide.label) {
            if (current < slides.lastIndex) { state.onboardingSlide++; show(Route.ONBOARDING) }
            else { prefs.onboarded = true; show(Route.LOGIN) }
        }.semibold(), LinearLayout.LayoutParams(-1, dp(56)))
        frame.addView(b, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
    }
}
