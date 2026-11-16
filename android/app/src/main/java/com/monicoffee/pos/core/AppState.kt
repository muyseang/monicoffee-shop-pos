package com.monicoffee.pos.core

import android.graphics.Bitmap
import com.monicoffee.pos.data.api.ApiException
import com.monicoffee.pos.data.model.CartItem
import com.monicoffee.pos.data.model.Category
import com.monicoffee.pos.data.model.MenuItem
import com.monicoffee.pos.data.model.Order
import com.monicoffee.pos.data.model.OtpTicket
import com.monicoffee.pos.data.model.Profile
import com.monicoffee.pos.data.model.QrPayment

/**
 * Everything the app remembers while it is open. Screens keep no data of their own:
 * they are re-drawn from this state whenever something changes.
 */
class AppState {
    var profile: Profile? = null
    var onboardingSlide = 0

    val auth = AuthState()
    val menu = MenuState()
    val cart = mutableListOf<CartItem>()
    val checkout = CheckoutState()
    val orders = OrdersState()

    val displayName get() = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Moni Coffee Guest"
    val initials get() = displayName.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
}

/** The login or registration in progress. */
class AuthState {
    var purpose = "login"
    var phone = ""
    var name = ""
    var ticket: OtpTicket? = null
    /** When [ticket] was issued, to count down its expiry. */
    var ticketAt = 0L

    /** Seconds until the current code expires; 0 once it has. */
    fun secondsLeft(): Int {
        val t = ticket ?: return 0
        return maxOf(0, t.expiresIn - ((System.currentTimeMillis() - ticketAt) / 1000).toInt())
    }
}

/** The menu as last loaded, how the customer is browsing it, and the item being configured. */
class MenuState {
    var categories = listOf<Category>()
    var items = listOf<MenuItem>()
    var loadedAt = 0L
    var loading = false
    var error: ApiException? = null
    var categoryId = 0 // 0 = all categories
    var query = ""
    val favorites = mutableSetOf<Int>()

    var selected: MenuItem? = null
    var quantity = 1
    /** Option id -> chosen value ("" for an add-on that has no choices). */
    val picks = mutableMapOf<Int, String>()
}

/** What the customer has filled in on the checkout screen. */
class CheckoutState {
    var pickup = false
    var tableNumber = ""
    var pickupTime: String? = null
    var error = false
    var qr: QrPayment? = null
}

class OrdersState {
    var tab = "active"
    val byTab = mutableMapOf<String, List<Order>>()
    var error: ApiException? = null
    var current: Order? = null
    /** The payment screenshot the customer picked, shrunk to a JPEG, and its preview. */
    var proofBytes: ByteArray? = null
    var proofPreview: Bitmap? = null
}
