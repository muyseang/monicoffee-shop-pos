package com.monicoffee.pos.data.model

data class Profile(val id: Int, val fullName: String, val phone: String, val address: String)

/** A sent code; [smsNumber] is the shop phone to text it to, as the Owner set it in Settings. */
data class OtpTicket(val requestId: String, val debugCode: String?, val expiresIn: Int, val smsNumber: String)

data class Session(val token: String, val profile: Profile)
