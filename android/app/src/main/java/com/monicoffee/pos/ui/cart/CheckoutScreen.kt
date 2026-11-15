package com.monicoffee.pos.ui.cart

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.monicoffee.pos.MainActivity
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
        if (checkout.qr == null) load({ api.qrPayment() }, failed = {}) { checkout.qr = it; if (it.enabled) redraw() }
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

        val qrOn = checkout.qr?.enabled == true
        if (!qrOn) checkout.payment = "cash"
        val methods = if (qrOn) listOf("Cash" to "cash", "QR Payment" to "manual_qr") else listOf("Cash" to "cash")
        // The payment bar above the button: tap it to switch method when the shop offers more than cash.
        val paying = row().apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            addView(column().apply {
                addView(text(methods.first { it.second == checkout.payment }.first, 15f, Palette.ink, true)); space(this, 2)
                addView(text(money(total()), 13f, Palette.green, true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Icon(context, "down", Palette.ink), LinearLayout.LayoutParams(dp(30), dp(30)))
            setOnClickListener {
                AlertDialog.Builder(context).setTitle("Payment method")
                    .setItems(methods.map { it.first }.toTypedArray()) { _, i -> checkout.payment = methods[i].second; show(Route.CHECKOUT) }.show()
            }
        }
        footer("Place Order", above = paying) {
            if (if (checkout.pickup) checkout.pickupTime == null else checkout.tableNumber.isBlank()) { checkout.error = true; show(Route.CHECKOUT) }
            else placeOrder()
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
        submit({ api.placeOrder(type, checkout.tableNumber.trim(), checkout.pickupTime, checkout.payment, lines) }) { order ->
            cart.clear(); checkout.tableNumber = ""; checkout.pickupTime = null; checkout.error = false
            orders.current = order; orders.byTab.clear(); orders.tab = "active"
            history.clear()
            if (order.paymentMethod == "manual_qr") { history.addLast(Route.ORDERS); history.addLast(Route.TRACK_ORDER); show(Route.PAY) } else show(Route.ORDER_PLACED)
        }
    }
}
