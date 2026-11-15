package com.monicoffee.pos.data.model

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
