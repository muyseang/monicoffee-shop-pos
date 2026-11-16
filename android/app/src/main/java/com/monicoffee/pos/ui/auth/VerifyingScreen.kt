package com.monicoffee.pos.ui.auth

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Design 08.6 / 10.6: shown after the SMS app, until the shop's SMS Forwarder confirms the texted code. */
class VerifyingScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val t = auth.ticket ?: return show(Route.LOGIN)
        header("Verifying"); val b = content(); space(b, 136)
        b.addView(Spinner(context, dp(6).toFloat()), LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(b, 24)
        b.addView(text("Verifying your number...", 16f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(b, 9)
        b.addView(text("Please wait while we confirm your SMS. This may take a few seconds.", 13f, Palette.muted).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(272), -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(text("Cancel", 14f, Palette.muted, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(24), dp(16), dp(24), dp(60)); setOnClickListener { show(Route.VERIFICATION) }
        })
        val check = {
            // Expired: back to the code screen, which shows design 10.7.
            if (auth.secondsLeft() == 0) show(Route.VERIFICATION)
            else load({ api.otpStatus(auth.purpose, t.requestId) }, failed = {}) { it?.let(session::signIn) }
        }
        check(); every(AppConfig.OTP_POLL_MS) { check() }
    }

    /** The design's spinner: a three-quarter green arc turning over a light grey track. */
    private class Spinner(context: Context, private val stroke: Float) : View(context) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Color.rgb(233, 233, 233) }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = Palette.green }

        private val turn = ObjectAnimator.ofFloat(this, "rotation", 0f, 360f).apply { duration = 1000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator() }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); turn.start() }

        override fun onDetachedFromWindow() { turn.cancel(); super.onDetachedFromWindow() }

        override fun onDraw(c: Canvas) {
            val inset = stroke / 2
            c.drawOval(inset, inset, width - inset, height - inset, track)
            c.drawArc(inset, inset, width - inset, height - inset, -90f, 270f, false, arc)
        }
    }
}
