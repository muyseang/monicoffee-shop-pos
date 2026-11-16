package com.monicoffee.pos.ui.cart

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.R
import com.monicoffee.pos.data.model.NewOrderLine
import com.monicoffee.pos.ui.clock12
import com.monicoffee.pos.ui.money
import com.monicoffee.pos.ui.navigation.Route
import com.monicoffee.pos.ui.theme.Palette
import com.monicoffee.pos.ui.widget.Icon
import java.util.Calendar
import java.util.Locale

/** Order Summary: dine in or pickup, the items, the payment method, and Place Order. */
class CheckoutScreen(app: MainActivity) : BasketScreen(app) {
    override fun draw() {
        if (cart.isEmpty()) return back()
        header("Order Summary")
        val b = content()
        // Fresh every visit: whether the shop's QR is set up decides if the order can be placed.
        if (!quiet) load({ api.qrPayment() }, failed = {}) { if (it != checkout.qr) { checkout.qr = it; redraw() } }
        segmented(b, listOf("Dine in", "Pickup"), true, { (it == "Pickup") == checkout.pickup }) { checkout.pickup = it == "Pickup"; checkout.error = false; show(Route.CHECKOUT) }
        space(b, 24)
        val problem = text(if (checkout.pickup) "ⓘ  Pickup time is required for pickup orders." else "ⓘ  Table number is required for dine-in orders.", 13f, Palette.red).apply {
            setPadding(0, dp(8), 0, 0); visibility = if (checkout.error) View.VISIBLE else View.GONE
        }
        if (checkout.pickup) {
            b.addView(row().apply {
                addView(Icon(context, "clock", Palette.ink), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) })
                addView(text("Time", 17f, Palette.ink, true))
            }); space(b, 12)
            val value = text(checkout.pickupTime?.let { "Today, ${clock12(it)}" } ?: "Choose a time", 15f, if (checkout.pickupTime == null) Palette.muted else Palette.ink, true).apply { setPadding(0, dp(4), 0, dp(2)) }
            b.addView(labelled("Expected Pickup Time", value, checkout.error).apply { setOnClickListener { choosePickupTime() } })
        } else {
            val table = EditText(context).apply {
                hint = "Enter your table number"; setText(checkout.tableNumber); textSize = 15f; setTypeface(null, Typeface.BOLD)
                setTextColor(Palette.ink); setHintTextColor(Palette.muted); setSingleLine(); background = null; setPadding(0, dp(4), 0, dp(2))
                filters = arrayOf(InputFilter.LengthFilter(20))
            }
            val box = labelled("Table Number", table, checkout.error)
            table.onChange {
                checkout.tableNumber = it
                if (checkout.error && it.isNotBlank()) {
                    // Clear the error in place: redrawing the screen would close the keyboard.
                    checkout.error = false; problem.visibility = View.GONE
                    box.background = bg(Color.rgb(243, 243, 243), 12, Palette.green); (box.getChildAt(0) as TextView).setTextColor(Palette.muted)
                }
            }
            b.addView(box)
        }
        b.addView(problem); divider(b)
        orderItems(b, bold = true)
        b.addView(text("Payment Summary", 17f, Palette.ink, true)); space(b, 16)
        pair(b, "Subtotal", money(total())); divider(b); pair(b, "Total", money(total()), labelBold = true)

        // App orders are paid by QR only (the backend refuses cash from the app); cash is taken at the counter.
        val paying = row().apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
            addView(column().apply {
                addView(text("KH QR Payment", 14f, Palette.ink, true)); space(this, 4)
                addView(text(money(total()), 12f, Palette.green, true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            // The only method, so the arrow opens its details rather than a list.
            addView(vector(R.drawable.ic_arrow_down, Color.rgb(42, 42, 42)).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Payment method"; setOnClickListener { navigate(Route.PAYMENT_METHOD) }
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
        }
        footer("Place Order", above = paying, raised = true) {
            when {
                if (checkout.pickup) checkout.pickupTime == null else checkout.tableNumber.isBlank() -> { checkout.error = true; show(Route.CHECKOUT) }
                checkout.qr?.enabled == false -> alert("Ordering in the app is unavailable right now: the shop hasn't set up QR payment yet.")
                else -> placeOrder()
            }
        }
    }

    private fun choosePickupTime() {
        val now = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
        TimePickerDialog(context, { _, hour, minute ->
            checkout.pickupTime = String.format(Locale.US, "%02d:%02d", hour, minute); checkout.error = false; show(Route.CHECKOUT)
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun placeOrder() {
        val lines = cart.map { NewOrderLine(it.item.id, it.count, it.picks) }
        val type = if (checkout.pickup) "pickup" else "dine_in"
        menu.loadedAt = 0 // stock changes either way: on success it drops, on failure ours was out of date
        submit({ api.placeOrder(type, checkout.tableNumber.trim(), checkout.pickupTime, "manual_qr", lines) }) { order ->
            cart.clear(); checkout.tableNumber = ""; checkout.pickupTime = null; checkout.error = false
            orders.current = order; orders.byTab.clear(); orders.tab = "active"
            history.clear(); history.addLast(Route.ORDERS); history.addLast(Route.TRACK_ORDER); show(Route.PAY)
        }
    }
}
