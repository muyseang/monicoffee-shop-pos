package com.monicoffee.pos.data.api

import java.net.HttpURLConnection
import java.net.URL

/** The Android emulator's name for the development computer's own 127.0.0.1. */
internal const val EMULATOR_HOST = "192.168.18.100"
    //"10.0.2.2"


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
