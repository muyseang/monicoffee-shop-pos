package com.monicoffee.pos.ui.auth

import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import java.util.Locale

/** The code is typed into an invisible field and shown in the four boxes of the design. */
class VerificationScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val t = auth.ticket ?: return show(Route.LOGIN)
        header("OTP Verification"); val b = content(); space(b, 12)
        b.addView(text("Enter the ${AppConfig.CODE_LENGTH}-digit code sent to ${auth.phone}.", 14f, Palette.muted).apply { setLineSpacing(dp(3).toFloat(), 1f) }); space(b, 20)
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(AppConfig.CODE_LENGTH))
            alpha = 0f; isCursorVisible = false; background = null
        }
        val boxes = row()
        val digits = (0 until AppConfig.CODE_LENGTH).map { i ->
            text("", 22f, Palette.green, true).apply { gravity = Gravity.CENTER; background = bg(Color.WHITE, 12, Palette.green) }.also {
                boxes.addView(it, LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) leftMargin = dp(20) })
            }
        }
        boxes.setOnClickListener { keyboard(input) }
        b.addView(boxes); b.addView(input, LinearLayout.LayoutParams(dp(1), dp(1))); space(b, 14)
        val timer = text("", 13f, Color.GRAY); b.addView(timer); space(b, 12)
        // The backend only includes the code in its reply while it runs in DEBUG mode (no SMS gateway yet).
        t.debugCode?.let { b.addView(text("Demo server: your code is $it", 13f, Palette.green, true)); space(b, 12) }
        b.addView(text("Resend code", 14f, Palette.green, true).apply {
            setOnClickListener { session.requestCode(auth.purpose, auth.phone, auth.name) { toast("A new code was sent."); show(Route.VERIFICATION) } }
        })
        space(b, 40)
        val go = button("Verify") { if (input.text.length == AppConfig.CODE_LENGTH) { auth.code = input.text.toString(); show(Route.VERIFYING) } }
        go.background = bg(Color.rgb(228, 228, 228)); b.addView(go)
        input.onChange { typed ->
            digits.forEachIndexed { i, box -> box.text = typed.getOrNull(i)?.toString() ?: "" }
            go.background = bg(if (typed.length == AppConfig.CODE_LENGTH) Palette.green else Color.rgb(228, 228, 228))
        }
        keyboard(input)

        var ticks = 0
        val tick = {
            val left = t.expiresIn - ((System.currentTimeMillis() - auth.ticketAt) / 1000).toInt()
            timer.text = if (left > 0) String.format(Locale.US, "Expires in %02d:%02d", left / 60, left % 60) else "This code has expired. Tap Resend code."
            // The shop's SMS device can confirm the code for us; pick that up without typing.
            if (++ticks % (AppConfig.OTP_POLL_MS / 1000).toInt() == 0 && left > 0) load({ api.otpStatus(auth.purpose, t.requestId) }, failed = {}) { it?.let(session::signIn) }
        }
        tick(); every(1000) { tick() }
    }
}
