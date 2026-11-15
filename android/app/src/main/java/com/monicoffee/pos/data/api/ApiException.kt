package com.monicoffee.pos.data.api

/** A request the server refused (status = HTTP code) or that never reached it (status = 0). */
class ApiException(val status: Int, message: String) : Exception(message)
