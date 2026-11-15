package com.monicoffee.pos.ui.profile

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Icon

class ProfileScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        if (!quiet) load({ api.me() }, failed = {}) { if (it != state.profile) { session.save(it); redraw() } }
        val b = content(); space(b, 42); val identity = row()
        identity.addView(text(state.initials, 32f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = bg(Color.rgb(22, 100, 74), 100) }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { rightMargin = dp(20) })
        identity.addView(
            column().apply { addView(text(state.displayName, 22f, Palette.ink, true)); space(this, 6); addView(text(state.profile?.phone ?: "", 15f, Palette.muted)) },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        b.addView(identity); space(b, 40); b.addView(text("Basic Info", 20f, Palette.ink, true)); space(b, 12)
        listOf("Order History" to Route.ORDERS, "Personal Info" to Route.PERSONAL_INFO, "Payment Method" to Route.PAYMENT_METHOD, "My Favorites" to Route.FAVORITES).forEach { (label, target) ->
            b.addView(row().apply {
                setPadding(0, dp(4), 0, dp(4)); setOnClickListener { navigate(target) }
                addView(text(label, 17f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(Icon(context, "chevron", Palette.muted), LinearLayout.LayoutParams(dp(32), dp(32)))
            }); divider(b)
        }
        b.addView(row().apply {
            addView(text("App Version", 17f), LinearLayout.LayoutParams(0, -2, 1f)); addView(text("1.0", 17f, Palette.muted))
        }); divider(b)
        footer("Log Out", Palette.red) { confirmLogout() }; nav(Route.PROFILE)
    }

    private fun confirmLogout() = confirm("logout", "Log out?", "You'll need to log in again to place or track your orders.", "Yes, Log Out", "Stay Logged In") {
        // Sign out on this phone even if the server can't be reached to drop the token.
        submit({ try { api.logout() } catch (e: ApiException) { } }) { session.signOut() }
    }
}
