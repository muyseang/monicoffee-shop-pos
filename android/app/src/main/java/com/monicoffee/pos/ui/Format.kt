package com.monicoffee.pos.ui

import java.util.Locale

/** 2.5 -> "$2.50". */
fun money(n: Double): String = String.format(Locale.US, "$%.2f", n)

/** 1 -> "1 item", 3 -> "3 items". */
fun countLabel(n: Int) = if (n == 1) "1 item" else "$n items"

/** "14:05" -> "2:05 PM", for showing the chosen pickup time. */
fun clock12(time: String): String {
    val hour = time.take(2).toIntOrNull() ?: return time
    return "${if (hour % 12 == 0) 12 else hour % 12}:${time.takeLast(2)} ${if (hour < 12) "AM" else "PM"}"
}
