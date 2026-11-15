package com.monicoffee.pos.data.model

data class Profile(val id: Int, val fullName: String, val phone: String, val address: String)

data class OtpTicket(val requestId: String, val debugCode: String?, val expiresIn: Int)

data class Session(val token: String, val profile: Profile)
