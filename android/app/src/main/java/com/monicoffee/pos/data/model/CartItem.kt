package com.monicoffee.pos.data.model

/** A menu item in the cart with the options chosen for it (option id -> chosen value). */
data class CartItem(val item: MenuItem, val picks: Map<Int, String>, var count: Int) {
    val unitPrice get() = item.price + item.options.filter { it.id in picks }.sumOf { it.extraPrice }

    /** The chosen options in words, e.g. "Size: Large · Extra shot". */
    val label
        get() = item.options.filter { it.id in picks }
            .joinToString(" · ") { if (it.choices.isEmpty()) it.name else "${it.name}: ${picks[it.id]}" }
}
