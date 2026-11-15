package com.monicoffee.pos.data.model

data class QrPayment(
    val enabled: Boolean,
    val qrImageUrl: String?,
    val accountName: String,
    val khrAccount: String,
    val usdAccount: String,
    val instructions: String,
)
