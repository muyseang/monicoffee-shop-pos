package com.monicoffee.pos.data.model

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

/** One line of a new order: the item, how many, and option id -> chosen value ("" for a plain add-on). */
data class NewOrderLine(val menuItemId: Int, val quantity: Int, val picks: Map<Int, String>)
