package com.monicoffee.pos.ui.profile

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette

/** Design 33: the avatar and the outlined name, address and phone fields, then Save. */
class PersonalInfoScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        header("Personal Info"); val b = content(); space(b, 24)
        b.addView(text(state.initials, 40f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(Palette.forest, 100) },
            LinearLayout.LayoutParams(dp(120), dp(120)).apply { gravity = Gravity.CENTER_HORIZONTAL }); space(b, 84)
        val name = outlinedField("Full name", state.profile?.fullName ?: "", green = true); b.addView(name.first); space(b, 24)
        val address = outlinedField("Address", state.profile?.address ?: "", green = true); b.addView(address.first); space(b, 24)
        // The phone number is the login, so the server doesn't let it change here.
        val phone = outlinedField("Phone number", state.profile?.phone ?: "", numeric = true, green = true); b.addView(phone.first)
        phone.second.apply { isEnabled = false; setTextColor(Palette.ink) }
        footer("Save") {
            if (name.second.text.isBlank()) name.second.error = "Enter your name"
            else submit({ api.updateMe(name.second.text.toString().trim(), address.second.text.toString().trim()) }) {
                session.save(it); toast("Saved"); back()
            }
        }
    }
}
