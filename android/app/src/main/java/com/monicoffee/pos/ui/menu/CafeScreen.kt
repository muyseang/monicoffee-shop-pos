package com.monicoffee.pos.ui.menu

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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

/**
 * Design 14: the shop's photo, name, hours, address, About and its location on a map.
 * (The design's rating and Reviews tab are left out: there are no reviews to show.)
 */
class CafeScreen(app: MainActivity) : Screen(app) {
    override fun draw() {
        header("Moni Cafe"); val b = content(); space(b, 4)
        b.addView(FrameLayout(context).apply {
            addView(localImage(R.drawable.onboard3, 200).apply { background = bg(Color.WHITE, 16) })
            addView(View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(128, 0, 0, 0))).apply { cornerRadius = dp(16).toFloat() }
            }, FrameLayout.LayoutParams(-1, dp(200)))
        }); space(b, 16)

        // Name with Book Now (which opens the menu to order), then the hours and the address.
        b.addView(row().apply {
            addView(text("Moni Coffee", 16f, Palette.ink, true).apply { letterSpacing = -0.02f }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(row().apply {
                background = bg(Palette.green, 8); setPadding(dp(12), dp(8), dp(12), dp(8))
                addView(text("Book Now", 10f, Color.WHITE, true))
                addView(vector(R.drawable.ic_double_chevron), LinearLayout.LayoutParams(dp(10), dp(10)).apply { leftMargin = dp(4) })
                setOnClickListener { history.clear(); show(Route.HOME) }
            })
        }); space(b, 8)
        b.addView(detail(R.drawable.ic_time, HOURS)); space(b, 4)
        b.addView(detail(R.drawable.ic_location, ADDRESS)); space(b, 24)

        // The design's tab bar, with only Location Details: there are no reviews to show.
        b.addView(text("Location Details", 14f, Palette.green).apply { gravity = Gravity.CENTER; letterSpacing = -0.01f },
            LinearLayout.LayoutParams(-1, dp(20))); space(b, 8)
        b.addView(View(context).apply { setBackgroundColor(Palette.green) }, LinearLayout.LayoutParams(-1, dp(2))); space(b, 32)
        b.addView(text("About", 16f, Palette.ink, true).apply { letterSpacing = -0.02f }); space(b, 8)
        b.addView(text("A cozy place to enjoy your favorite coffee, freshly prepared for dine-in or pickup.", 12f, Palette.muted).apply { letterSpacing = -0.01f; leading(1.6f) })
        space(b, 40)
        b.addView(text("Map Location", 16f, Palette.ink, true).apply { letterSpacing = -0.02f }); space(b, 24)
        b.addView(map(), LinearLayout.LayoutParams(-1, dp(260)))
    }

    /** A 16dp icon and a 12sp grey line, as the hours and address rows in the design. */
    private fun detail(icon: Int, value: String) = row().apply {
        addView(vector(icon), LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(4) })
        addView(text(value, 12f, Palette.muted).apply { letterSpacing = -0.01f; leading(1.6f) }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    /**
     * The streets around the shop (an OpenStreetMap picture bundled with the app, centred on it),
     * with the design's green circle and pin. Tapping it opens the shop in Google Maps.
     */
    private fun map() = FrameLayout(context).apply {
        background = bg(Color.rgb(238, 238, 238), 16); clipToOutline = true
        contentDescription = "Map: $ADDRESS. Opens in Maps."
        setOnClickListener { openMaps() }
        addView(ImageView(context).apply { setImageResource(R.drawable.cafe_map); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(26, 0, 118, 79)); setStroke(dp(1), Palette.green) }
        }, FrameLayout.LayoutParams(dp(164), dp(164), Gravity.CENTER))
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(102, 38, 156, 57)) }
        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        addView(vector(R.drawable.ic_expand).apply { contentDescription = "Open in Maps"; setOnClickListener { openMaps() } },
            FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(16), dp(16), 0) })
        // OpenStreetMap's licence asks for this credit wherever its map is shown.
        addView(text("© OpenStreetMap contributors", 9f, Color.rgb(90, 90, 90)).apply {
            setPadding(dp(6), dp(2), dp(6), dp(2)); background = bg(Color.argb(200, 255, 255, 255), 6)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(8), dp(8)) })
    }

    private fun openMaps() {
        try { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MAPS_LINK))) }
        catch (e: ActivityNotFoundException) { toast("No app can open maps on this phone.") }
    }

    private companion object {
        const val HOURS = "07:00-23:00"
        const val ADDRESS = "Street 302, Boeung Keng Kang 1, Phnom Penh"
        const val MAPS_LINK = "https://maps.app.goo.gl/Pyc6rSPuEdn2Xr3P7"
    }
}
