package com.monicoffee.pos.ui.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.QrCode
import com.monicoffee.pos.ui.widget.QrView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Design 08.5 / 10.5: shows the code and sends the customer to their SMS app to text it to the
 * shop phone, whose Moni SMS Forwarder confirms it. The status polling then signs this app in.
 * Design 10.7 once the code has expired: empty red boxes and a button for a new code.
 */
class VerificationScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        val t = auth.ticket ?: return show(Route.LOGIN)
        val expired = auth.secondsLeft() == 0
        header("OTP Verification"); val b = content(); space(b, 12)
        b.addView(text("To verify, send an SMS with the code below to ${AppConfig.SHOP_SMS_NUMBER_LABEL}. It must be sent from your registered number ${auth.phone}.", 14f, Palette.muted).apply { letterSpacing = -0.01f; leading(20f / 14f) },
            LinearLayout.LayoutParams(dp(272), -2)); space(b, 20)
        // The backend only includes the code in its reply while it runs in DEBUG mode (no SMS gateway yet).
        val code = if (expired) "" else t.debugCode ?: ""
        val boxes = row().apply { gravity = Gravity.CENTER }
        for (i in 0 until AppConfig.CODE_LENGTH) {
            boxes.addView(text(code.getOrNull(i)?.toString() ?: "", 20f, Palette.forest, true).apply {
                gravity = Gravity.CENTER; background = bg(Color.WHITE, 12).apply { setStroke(px(1.5f), if (expired) Palette.red else Palette.forest) }
            }, LinearLayout.LayoutParams(dp(64), dp(56)).apply { if (i > 0) leftMargin = dp(20) })
        }
        b.addView(boxes); space(b, 28)
        if (expired) return drawExpired(b)

        val timer = text("", 13f, Palette.grey); b.addView(timer)
        // The registered number may be on another phone: its camera reads this standard "SMSTO:" QR
        // and opens a message to the shop with the code filled in. The polling below still signs in here.
        if (code.isNotEmpty()) {
            space(b, 16)
            b.addView(orDivider(), LinearLayout.LayoutParams(-1, dp(18))); space(b, 6)
            b.addView(text("Sending the SMS from a different phone? Scan this code there to continue.", 12f, Palette.grey).apply { gravity = Gravity.CENTER }); space(b, 14)
            b.addView(qrCard(QrCode.encode("SMSTO:${AppConfig.SHOP_SMS_NUMBER}:$code")), LinearLayout.LayoutParams(dp(184), dp(184)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            space(b, 34)
        } else space(b, 88)
        b.addView(button("Open SMS App") {
            val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${AppConfig.SHOP_SMS_NUMBER}")).putExtra("sms_body", code)
            try { app.startActivity(sms); navigate(Route.VERIFYING) } catch (e: ActivityNotFoundException) { toast("No SMS app found. Send $code to ${AppConfig.SHOP_SMS_NUMBER_LABEL}.") }
        }, LinearLayout.LayoutParams(-1, dp(56)))

        var ticks = 0
        val tick = {
            val left = auth.secondsLeft()
            timer.text = String.format(Locale.US, "Expires in %02d:%02d · Standard SMS rates may apply.", left / 60, left % 60)
            // Also signs in if the customer texted the code without using the button.
            if (++ticks % (AppConfig.OTP_POLL_MS / 1000).toInt() == 0 && left > 0) load({ api.otpStatus(auth.purpose, t.requestId) }, failed = {}) { it?.let(session::signIn) }
            if (left == 0) show(Route.VERIFICATION) // redraw as design 10.7
        }
        tick(); every(1000) { tick() }
    }

    /** Design 10.7: the expired notice, then Resend Code with its caption. */
    private fun drawExpired(b: LinearLayout) {
        b.addView(row().apply {
            addView(text("!", 10f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; includeFontPadding = false; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.red) }
            }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(6) })
            addView(text("Your verification code has expired.", 13f, Palette.red))
        }); space(b, 117)
        b.addView(button("Resend Code") {
            session.requestCode(auth.purpose, auth.phone, auth.name) { toast("A new code was sent."); show(Route.VERIFICATION) }
        }, LinearLayout.LayoutParams(-1, dp(56))); space(b, 16)
        b.addView(text("A new code will be sent to your registered number.", 12f, Palette.grey).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun px(v: Float) = (v * context.resources.displayMetrics.density).roundToInt()

    /** A hairline with an "OR" badge on its centre. */
    private fun orDivider() = FrameLayout(context).apply {
        addView(View(context).apply { setBackgroundColor(Palette.line) }, FrameLayout.LayoutParams(-1, dp(1), Gravity.CENTER_VERTICAL))
        addView(text("OR", 10f, Palette.grey, true).apply { gravity = Gravity.CENTER; background = bg(Color.WHITE, 9, Palette.line) }, FrameLayout.LayoutParams(dp(34), dp(18), Gravity.CENTER))
    }

    /**
     * The QR on a grey tile inside a green-bordered, shadowed card, with scanner-style brackets on the
     * card's corners and the brand mark on the centre (the level-H code still reads with it covered).
     */
    private fun qrCard(modules: Array<BooleanArray>) = FrameLayout(context).apply {
        val edge = dp(2)
        addView(FrameLayout(context).apply {
            background = bg(Color.WHITE, 20).apply { setStroke(edge, Palette.green) }; elevation = dp(6).toFloat()
            addView(QrView(context, modules, quiet = 2, darkColor = Color.rgb(38, 38, 38)).apply { background = bg(Color.rgb(245, 245, 245), 14); clipToOutline = true },
                FrameLayout.LayoutParams(dp(152), dp(152), Gravity.CENTER))
            addView(text("M", 20f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.green); setStroke(dp(6), Color.WHITE) }
            }, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        }, FrameLayout.LayoutParams(dp(180), dp(180), Gravity.CENTER))
        // Each bracket arm lines up with the card's corner and pokes 2dp outside its edge.
        val bar = { lp: FrameLayout.LayoutParams -> addView(View(context).apply { background = bg(Palette.green, 2); elevation = dp(7).toFloat() }, lp) }
        for (corner in listOf(Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END, Gravity.BOTTOM or Gravity.START, Gravity.BOTTOM or Gravity.END)) {
            bar(FrameLayout.LayoutParams(dp(24), dp(4), corner).apply { leftMargin = edge; rightMargin = edge })
            bar(FrameLayout.LayoutParams(dp(4), dp(24), corner).apply { topMargin = edge; bottomMargin = edge })
        }
    }
}
