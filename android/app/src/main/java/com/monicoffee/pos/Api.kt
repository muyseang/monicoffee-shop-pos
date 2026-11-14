package com.monicoffee.pos

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The Android emulator's name for the development computer's own 127.0.0.1. */
private const val EMULATOR_HOST = "10.0.2.2"

/**
 * Opens a connection to the backend. From the emulator the computer is reached as 10.0.2.2,
 * but Django only answers to the names in its ALLOWED_HOSTS (127.0.0.1 and localhost by
 * default). 10.0.2.2 *is* that computer's 127.0.0.1, so we send the name the server knows
 * itself by — the same one a browser on that computer sends — and the backend needs no change.
 */
fun connect(url: String): HttpURLConnection {
    val target = URL(url)
    val connection = target.openConnection() as HttpURLConnection
    if (target.host == EMULATOR_HOST) {
        connection.setRequestProperty("Host", "127.0.0.1" + if (target.port == -1) "" else ":${target.port}")
    }
    return connection
}

/** A request the server refused (status = HTTP code) or that never reached it (status = 0). */
class ApiException(val status: Int, message: String) : Exception(message)

data class Category(val id: Int, val name: String)

data class ItemOption(val id: Int, val name: String, val choices: List<String>, val extraPrice: Double)

data class MenuItem(
    val id: Int,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String?,
    val categoryId: Int,
    val categoryName: String,
    val inStock: Boolean,
    val options: List<ItemOption>,
)

data class OrderLine(
    val name: String,
    val options: String,
    val unitPrice: Double,
    val quantity: Int,
    val lineTotal: Double,
    val imageUrl: String?,
)

data class Order(
    val id: Int,
    val number: String,
    val status: String,
    val statusLabel: String,
    val orderType: String,
    val orderTypeLabel: String,
    val tableNumber: String,
    val pickupTime: String?,
    val paymentMethod: String,
    val paymentMethodLabel: String,
    val paymentStatus: String,
    val paymentStatusLabel: String,
    val paymentProofUrl: String?,
    val total: Double,
    val rejectionReason: String,
    val items: List<OrderLine>,
    val canCancel: Boolean,
    val canUploadProof: Boolean,
    val createdAt: String,
) {
    val itemCount get() = items.sumOf { it.quantity }

    /** "Dine-in • Table 5" or "Pickup • 11:00". */
    val place
        get() = if (orderType == "dine_in") "$orderTypeLabel • Table $tableNumber"
        else "$orderTypeLabel • ${pickupTime?.take(5) ?: ""}"
}

data class QrPayment(
    val enabled: Boolean,
    val qrImageUrl: String?,
    val accountName: String,
    val khrAccount: String,
    val usdAccount: String,
    val instructions: String,
)

data class Profile(val id: Int, val fullName: String, val phone: String, val address: String)

data class OtpTicket(val requestId: String, val debugCode: String?, val expiresIn: Int)

data class Session(val token: String, val profile: Profile)

/** One line of a new order: the item, how many, and option id -> chosen value ("" for a plain add-on). */
data class NewOrderLine(val menuItemId: Int, val quantity: Int, val picks: Map<Int, String>)

/**
 * Blocking client for the Moni Coffee Django API. Every method does network I/O,
 * so call it from a background thread. Uses only the Android platform classes.
 */
class ApiClient(var baseUrl: String, var token: String?) {

    // --- auth -------------------------------------------------------------------

    fun requestOtp(purpose: String, phone: String, fullName: String?): OtpTicket {
        val body = JSONObject().put("phone", phone)
        if (fullName != null) body.put("full_name", fullName)
        val o = JSONObject(send("POST", "/api/auth/$purpose/request-otp/", body, auth = false))
        return OtpTicket(o.getString("request_id"), o.str("debug_code"), o.optInt("expires_in", 300))
    }

    fun verifyOtp(purpose: String, phone: String, code: String): Session {
        val body = JSONObject().put("phone", phone).put("code", code)
        return session(JSONObject(send("POST", "/api/auth/$purpose/verify-otp/", body, auth = false)))
    }

    /** Null while the code hasn't been verified yet (e.g. by the shop's SMS device). */
    fun otpStatus(purpose: String, requestId: String): Session? {
        val id = URLEncoder.encode(requestId, "UTF-8")
        val o = JSONObject(send("GET", "/api/auth/$purpose/status/?request_id=$id", auth = false))
        return if (o.optBoolean("verified")) session(o) else null
    }

    fun logout() {
        send("POST", "/api/auth/logout/")
    }

    fun me(): Profile = profile(JSONObject(send("GET", "/api/auth/me/")))

    fun updateMe(fullName: String, address: String): Profile {
        val body = JSONObject().put("full_name", fullName).put("address", address)
        return profile(JSONObject(send("PATCH", "/api/auth/me/", body)))
    }

    // --- menu -------------------------------------------------------------------

    fun categories(): List<Category> =
        JSONArray(send("GET", "/api/menu/categories/")).objects().map { Category(it.getInt("id"), it.getString("name")) }

    fun menuItems(): List<MenuItem> = JSONArray(send("GET", "/api/menu/items/")).objects().map { o ->
        val category = o.getJSONObject("category")
        MenuItem(
            id = o.getInt("id"),
            name = o.getString("name"),
            description = o.str("description") ?: "",
            price = o.money("price"),
            imageUrl = o.media("image_url"),
            categoryId = category.getInt("id"),
            categoryName = category.getString("name"),
            inStock = o.optBoolean("in_stock", true),
            options = o.getJSONArray("options").objects().map { opt ->
                val choices = opt.optJSONArray("choices") ?: JSONArray()
                ItemOption(
                    opt.getInt("id"),
                    opt.getString("name"),
                    (0 until choices.length()).map { choices.getString(it) },
                    opt.money("extra_price"),
                )
            },
        )
    }

    // --- payments ---------------------------------------------------------------

    fun qrPayment(): QrPayment {
        val o = JSONObject(send("GET", "/api/payments/methods/")).getJSONObject("manual_qr")
        return QrPayment(
            enabled = o.optBoolean("enabled"),
            qrImageUrl = o.media("qr_image_url"),
            accountName = o.str("account_name") ?: "",
            khrAccount = o.str("khr_account_number") ?: "",
            usdAccount = o.str("usd_account_number") ?: "",
            instructions = o.str("instructions") ?: "",
        )
    }

    // --- orders -----------------------------------------------------------------

    /** group: "active", "completed" or "cancelled" (the My Orders tabs). */
    fun orders(group: String): List<Order> =
        JSONArray(send("GET", "/api/orders/?status=$group")).objects().map(::order)

    fun order(id: Int): Order = order(JSONObject(send("GET", "/api/orders/$id/")))

    fun placeOrder(
        orderType: String,
        tableNumber: String,
        pickupTime: String?,
        paymentMethod: String,
        lines: List<NewOrderLine>,
    ): Order {
        val items = JSONArray()
        for (line in lines) {
            val options = JSONArray()
            for ((optionId, choice) in line.picks) {
                options.put(JSONObject().put("option", optionId).put("choice", choice))
            }
            items.put(JSONObject().put("menu_item", line.menuItemId).put("quantity", line.quantity).put("options", options))
        }
        val body = JSONObject().put("order_type", orderType).put("payment_method", paymentMethod).put("items", items)
        if (orderType == "dine_in") body.put("table_number", tableNumber) else body.put("pickup_time", pickupTime)
        return order(JSONObject(send("POST", "/api/orders/", body)))
    }

    fun cancelOrder(id: Int): Order = order(JSONObject(send("POST", "/api/orders/$id/cancel/")))

    fun uploadPaymentProof(id: Int, jpeg: ByteArray): Order {
        val boundary = "----MoniCoffee${System.nanoTime()}"
        val head = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"image\"; filename=\"payment.jpg\"\r\n" +
            "Content-Type: image/jpeg\r\n\r\n"
        val payload = head.toByteArray() + jpeg + "\r\n--$boundary--\r\n".toByteArray()
        val text = send("POST", "/api/orders/$id/payment-proof/", payload, "multipart/form-data; boundary=$boundary")
        return order(JSONObject(text))
    }

    // --- plumbing ---------------------------------------------------------------

    private fun send(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): String =
        send(method, path, body?.toString()?.toByteArray(), "application/json", auth)

    private fun send(method: String, path: String, body: ByteArray?, contentType: String, auth: Boolean = true): String {
        val status: Int
        val text: String
        try {
            val connection = connect(baseUrl.trimEnd('/') + path)
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8000
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                if (auth) token?.let { connection.setRequestProperty("Authorization", "Token $it") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", contentType)
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            throw ApiException(0, "Can't reach the server at $baseUrl. Check that it is running and try again.")
        } catch (e: IllegalArgumentException) {
            throw ApiException(0, "The server address \"$baseUrl\" is not valid.")
        }
        if (status !in 200..299) throw ApiException(status, errorMessage(status, text))
        return text
    }

    /** DRF errors are {"detail": "..."} or {"field": ["...", ...]} (possibly nested). */
    private fun errorMessage(status: Int, text: String): String {
        val messages = mutableListOf<String>()
        fun collect(value: Any?) {
            when (value) {
                is String -> if (value.isNotBlank()) messages.add(value)
                is JSONArray -> for (i in 0 until value.length()) collect(value.opt(i))
                is JSONObject -> value.keys().forEach { collect(value.opt(it)) }
            }
        }
        try {
            collect(JSONObject(text))
        } catch (e: Exception) {
            // Not JSON (e.g. an HTML error page) — fall through to the generic message.
        }
        return messages.distinct().joinToString("\n").ifEmpty { "The server returned an error ($status)." }
    }

    private fun session(o: JSONObject) = Session(o.getString("token"), profile(o.getJSONObject("user")))

    private fun profile(o: JSONObject) =
        Profile(o.getInt("id"), o.str("full_name") ?: "", o.str("phone") ?: "", o.str("address") ?: "")

    private fun order(o: JSONObject) = Order(
        id = o.getInt("id"),
        number = o.getString("number"),
        status = o.getString("status"),
        statusLabel = o.getString("status_label"),
        orderType = o.getString("order_type"),
        orderTypeLabel = o.getString("order_type_label"),
        tableNumber = o.str("table_number") ?: "",
        pickupTime = o.str("pickup_time"),
        paymentMethod = o.getString("payment_method"),
        paymentMethodLabel = o.getString("payment_method_label"),
        paymentStatus = o.getString("payment_status"),
        paymentStatusLabel = o.getString("payment_status_label"),
        paymentProofUrl = o.media("payment_proof_url"),
        total = o.money("total"),
        rejectionReason = o.str("rejection_reason") ?: "",
        items = o.getJSONArray("items").objects().map {
            OrderLine(
                name = it.getString("name"),
                options = it.str("options") ?: "",
                unitPrice = it.money("unit_price"),
                quantity = it.getInt("quantity"),
                lineTotal = it.money("line_total"),
                imageUrl = it.media("image_url"),
            )
        },
        canCancel = o.optBoolean("can_cancel"),
        canUploadProof = o.optBoolean("can_upload_proof"),
        createdAt = o.str("created_at") ?: "",
    )

    /**
     * The server builds picture links from the name it was called by, so on the emulator they
     * come back as http://127.0.0.1:8000/media/… — which, on the emulator, is the emulator
     * itself. Point them back at the computer (see [connect]).
     */
    private fun JSONObject.media(key: String): String? {
        val link = str(key) ?: return null
        if (URL(baseUrl).host != EMULATOR_HOST) return link
        val url = URL(link)
        return if (url.host == "127.0.0.1") URL(url.protocol, EMULATOR_HOST, url.port, url.file).toString() else link
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    /** DRF sends decimals as strings ("2.50"). */
    private fun JSONObject.money(key: String): Double = optString(key, "0").toDoubleOrNull() ?: 0.0

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
