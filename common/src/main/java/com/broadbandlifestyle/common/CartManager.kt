package com.broadbandlifestyle.common

object CartManager {
    private val productDetails = mutableMapOf<Int, Product>()
    private val items = mutableMapOf<Int, Int>()

    fun addItem(product: Product) {
        productDetails[product.id] = product
        val currentQty = items[product.id] ?: 0
        items[product.id] = currentQty + 1
    }

    fun removeItem(productId: Int) {
        val currentQty = items[productId] ?: 0
        if (currentQty > 1) {
            items[productId] = currentQty - 1
        } else {
            items.remove(productId)
            productDetails.remove(productId)
        }
    }

    fun deleteItem(productId: Int) {
        items.remove(productId)
        productDetails.remove(productId)
    }

    // Accessors for Cart & Checkout activities
    fun getProductsInCart(): List<Product> = productDetails.values.toList()
    fun getCartProducts(): List<Product> = productDetails.values.toList()
    fun getCartItems(): Map<Int, Int> = items.toMap()
    fun getQuantity(productId: Int): Int = items[productId] ?: 0
    fun getCartCount(): Int = items.values.sum()

    fun clearCart() {
        items.clear()
        productDetails.clear()
    }
}