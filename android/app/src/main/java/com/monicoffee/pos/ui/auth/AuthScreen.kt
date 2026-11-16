package com.monicoffee.pos.ui.auth

import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Log in, or sign up when [register] is set: both ask for a phone number and send a code to it. */
class AuthScreen(app: MainActivity, private val register: Boolean) : Screen(app) {
    override fun draw() {
        // Designs 05–08 spacing, measured from under the status bar.
        val b = content(); space(b, 68)
        // Long-press: the server address (developer setting, not part of the design).
        b.addView(text("Moni Coffee", 32f, Palette.ink).semibold().apply {
            gravity = Gravity.CENTER; letterSpacing = -0.01f; setOnLongClickListener { chooseServer(); true }
        }); space(b, 8)
        b.addView(text("Enter your information below.", 14f, Palette.muted).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f }); space(b, 52)
        val name = outlinedField("Full name", auth.name); if (register) { b.addView(name.first); space(b, 16) }
        val number = outlinedField("Phone number", auth.phone, numeric = true); b.addView(number.first)
        space(b, if (register) 92 else 100)
        if (register) { b.addView(text("By signing up, you agree to our Terms and Conditions and our Privacy Policy.", 14f, Palette.muted).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f; leading(20f / 14f) }); space(b, 16) }
        val ready = { number.second.text.isNotBlank() && (!register || name.second.text.isNotBlank()) }
        val go = button(if (register) "Sign up" else "Log in") {
            if (ready()) session.requestCode(if (register) "register" else "login", number.second.text.toString().trim(), name.second.text.toString().trim()) { navigate(Route.VERIFICATION) }
            else toast(if (register) "Enter your full name and phone number." else "Enter your phone number.")
        }
        // As in the design: Log in is grey until a number is entered (05/06); Sign up is always green (07).
        val paint = { go.background = bg(if (register || ready()) Palette.green else Color.rgb(228, 228, 228)) }
        paint(); number.second.onChange { paint() }; name.second.onChange { paint() }
        b.addView(go.semibold(), LinearLayout.LayoutParams(-1, dp(56)))

        // A 12sp question and a 14sp green link, 12dp above the home indicator. (The register design
        // repeats "Sign up"; that screen links back to Log in instead.)
        val question = if (register) "Already have an account? " else "Don’t have an account? "
        val link = SpannableString(question + if (register) "Log in" else "Sign up").apply {
            setSpan(AbsoluteSizeSpan(12, true), 0, question.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Palette.green), question.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan("sans-serif-medium"), question.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        root.addView(text("", 14f).apply {
            text = link; gravity = Gravity.CENTER; letterSpacing = -0.01f; setPadding(dp(24), dp(16), dp(24), dp(12))
            setOnClickListener { history.clear(); show(if (register) Route.LOGIN else Route.REGISTER) }
        })
    }

    /** Where the Django backend runs: 10.0.2.2 is "this computer" from the emulator; a real phone needs the PC's Wi-Fi address. */
    private fun chooseServer() {
        val input = field("http://192.168.1.10:8000", api.baseUrl).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val box = column().apply { setPadding(dp(24), dp(16), dp(24), 0); addView(input) }
        AlertDialog.Builder(context).setTitle("Server address").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val typed = input.text.toString().trim().trimEnd('/')
                val url = when {
                    typed.isEmpty() -> AppConfig.DEFAULT_SERVER
                    typed.startsWith("http://") || typed.startsWith("https://") -> typed
                    else -> "http://$typed"
                }
                api.baseUrl = url; prefs.saveServer(url)
                menu.loadedAt = 0; menu.error = null; show(screen)
            }.setNeutralButton("Reset") { _, _ ->
                api.baseUrl = AppConfig.DEFAULT_SERVER; prefs.saveServer(null); show(screen)
            }.setNegativeButton("Cancel", null).show()
    }
}
