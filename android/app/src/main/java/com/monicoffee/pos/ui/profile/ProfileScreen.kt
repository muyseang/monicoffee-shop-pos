package com.monicoffee.pos.ui.profile

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette

/** Design 32: who is signed in, the settings list and Log Out. */
class ProfileScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        if (!quiet) load({ api.me() }, failed = {}) { if (it != state.profile) { session.save(it); redraw() } }
        val b = content(); space(b, 20)
        // The avatar (with its green dot) and the name, centred as a group.
        val identity = row().apply { gravity = Gravity.CENTER }
        identity.addView(FrameLayout(context).apply {
            addView(avatar(app.photos.get(state.profile?.id), state.initials, 64, 22f), FrameLayout.LayoutParams(dp(64), dp(64)))
            addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.green) } },
                FrameLayout.LayoutParams(dp(24), dp(24)).apply { setMargins(dp(44), dp(44), 0, 0) })
        }, LinearLayout.LayoutParams(dp(68), dp(68)).apply { rightMargin = dp(12) })
        identity.addView(column().apply {
            addView(text(state.displayName, 18f, Palette.ink, true).apply { letterSpacing = -0.02f; leading(1.3f) }, LinearLayout.LayoutParams(-2, -2)); space(this, 4)
            addView(text(state.profile?.phone ?: "", 12f, Palette.muted).apply { letterSpacing = -0.01f }, LinearLayout.LayoutParams(-2, -2))
        })
        b.addView(identity, LinearLayout.LayoutParams(-1, -2)); space(b, 32)
        b.addView(text("Basic Info", 16f, Palette.ink, true).apply { letterSpacing = -0.02f; leading(1.3f) }); space(b, 22)
        // My Favorites isn't in design 32, but the app has no other way to reach design 37.
        listOf("Order History" to Route.ORDERS, "Personal Info" to Route.PERSONAL_INFO, "Payment Method" to Route.PAYMENT_METHOD, "My Favorites" to Route.FAVORITES).forEach { (label, target) ->
            setting(b, label, vector(R.drawable.ic_chevron)) { navigate(target) }
        }
        setting(b, "App Version", text("1.0", 14f, Color.rgb(151, 151, 151)), null)
        // Log Out sits 32dp above the tab bar, without the usual footer shadow.
        root.addView(column().apply {
            setPadding(dp(24), dp(16), dp(24), dp(32))
            addView(button("Log Out", Color.rgb(189, 51, 37)) { confirmLogout() }, LinearLayout.LayoutParams(-1, dp(56)))
        })
        nav(Route.PROFILE)
    }

    /** One settings row: 14sp label, something on the right, a line 40dp below the top and 16dp to the next row. */
    private fun setting(b: LinearLayout, label: String, end: View, open: (() -> Unit)?) {
        b.addView(row().apply {
            gravity = Gravity.TOP; open?.let { o -> setOnClickListener { o() } }
            addView(text(label, 14f, Palette.ink).apply { letterSpacing = -0.01f; leading(1.6f) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(end, LinearLayout.LayoutParams(if (end is ImageView) dp(22) else -2, dp(22)))
        }, LinearLayout.LayoutParams(-1, dp(40)))
        b.addView(View(context).apply { setBackgroundColor(Color.rgb(229, 231, 235)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(16) })
    }

    private fun confirmLogout() = confirm("logout", "Log out?", "You'll need to log in again to place or track your orders.", "Yes, Log Out", "Stay Logged In") {
        // Sign out on this phone even if the server can't be reached to drop the token.
        submit({ try { api.logout() } catch (e: ApiException) { } }) { session.signOut() }
    }
}
