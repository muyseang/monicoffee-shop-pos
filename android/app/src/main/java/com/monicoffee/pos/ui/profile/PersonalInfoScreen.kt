package com.monicoffee.pos.ui.profile

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.theme.Palette

class PersonalInfoScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        header("Personal Info"); val b = content(); space(b, 24)
        b.addView(text(state.initials, 40f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(Palette.green, 100); layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply { gravity = Gravity.CENTER_HORIZONTAL } }); space(b, 48)
        b.addView(text("Full name", 13f, Palette.muted)); val name = field("Full name", state.profile?.fullName ?: ""); b.addView(name)
        b.addView(text("Address", 13f, Palette.muted)); val address = field("Address", state.profile?.address ?: ""); b.addView(address)
        b.addView(text("Phone number", 13f, Palette.muted))
        b.addView(field("Phone number", state.profile?.phone ?: "", true).apply { isEnabled = false; setTextColor(Palette.muted); background = bg(Palette.soft, 12, Palette.line) })
        b.addView(text("Your phone number is your login, so it can't be changed here.", 12f, Palette.muted))
        footer("Save") {
            if (name.text.isBlank()) name.error = "Enter your name"
            else submit({ api.updateMe(name.text.toString().trim(), address.text.toString().trim()) }) {
                session.save(it); toast("Saved"); back()
            }
        }
    }
}
