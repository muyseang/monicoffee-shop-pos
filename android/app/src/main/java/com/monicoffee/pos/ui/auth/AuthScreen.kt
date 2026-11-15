package com.monicoffee.pos.ui.auth

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.core.AppConfig
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Log in, or sign up when [register] is set: both ask for a phone number and send a code to it. */
class AuthScreen(app: MainActivity, private val register: Boolean) : Screen(app) {
    override fun draw() {
        val b = content(); space(b, 70)
        b.addView(text("Moni Coffee", 32f, Palette.ink, true).apply { gravity = Gravity.CENTER }); space(b, 12)
        b.addView(text("Enter your information below.", 14f, Palette.muted).apply { gravity = Gravity.CENTER }); space(b, 52)
        val name = field("Full name", auth.name); if (register) b.addView(name)
        val number = field("Phone number", auth.phone, numeric = true); b.addView(number)
        space(b, 40)
        if (register) { b.addView(text("By signing up, you agree to our Terms and Conditions and our Privacy Policy.", 14f, Palette.muted).apply { gravity = Gravity.CENTER }); space(b, 16) }
        val ready = { number.text.isNotBlank() && (!register || name.text.isNotBlank()) }
        val go = button(if (register) "Sign up" else "Log in") {
            if (ready()) session.requestCode(if (register) "register" else "login", number.text.toString().trim(), name.text.toString().trim()) { navigate(Route.VERIFICATION) }
        }
        // Grey until the form is filled in, as in the design.
        val paint = { go.background = bg(if (ready()) Palette.green else Color.rgb(228, 228, 228)) }
        paint(); number.onChange { paint() }; name.onChange { paint() }
        b.addView(go); space(b, 24)
        b.addView(text("Server: ${api.baseUrl}  (change)", 12f, Palette.muted).apply { gravity = Gravity.CENTER; setOnClickListener { chooseServer() } })

        val question = if (register) "Already have an account? " else "Don’t have an account? "
        val link = SpannableString(question + if (register) "Log in" else "Sign up").apply {
            setSpan(ForegroundColorSpan(Palette.green), question.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), question.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        root.addView(text("", 14f).apply {
            text = link; gravity = Gravity.CENTER; setPadding(dp(24), dp(16), dp(24), dp(28))
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
