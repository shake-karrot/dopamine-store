package com.dopaminestore.product.core.domain

import com.dopaminestore.product.core.domain.value.ProductStatus
import java.time.Instant
import java.util.UUID

/**
 * Product aggregate root.
 *
 * Represents a purchasable item in the Dopamine Store catalog with stock management
 * and sale date scheduling.
 *
 * Invariants:
 * - stock >= 0 (cannot be negative)
 * - stock <= initialStock (stock can only decrease or stay same)
 * - initialStock is immutable after creation
 * - saleDate cannot be changed if any purchase slots exist
 */
data class Product(
    val id: UUID,
    val name: String,
    val description: String,
    val stock: Int,
    val initialStock: Int,
    val saleDate: Instant,
    val price: java.math.BigDecimal,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val createdBy: UUID
) {
    init {
        require(name.isNotBlank()) { "Product name cannot be blank" }
        require(name.length in 1..200) { "Product name must be 1-200 characters, got: ${name.length}" }
        require(description.isNotBlank()) { "Product description cannot be blank" }
        require(description.length in 1..2000) { "Product description must be 1-2000 characters, got: ${description.length}" }
        require(stock >= 0) { "Stock cannot be negative: $stock" }
        require(initialStock >= 0) { "Initial stock cannot be negative: $initialStock" }
        require(stock <= initialStock) { "Stock ($stock) cannot exceed initial stock ($initialStock)" }
        require(price >= java.math.BigDecimal.ZERO) { "Price cannot be negative: $price" }
    }

    /**
     * Compute current product status based on sale date and stock.
     *
     * - UPCOMING: current time < saleDate
     * - ON_SALE: current time >= saleDate AND stock > 0
     * - SOLD_OUT: stock == 0
     */
    fun computeStatus(now: Instant = Instant.now()): ProductStatus {
        return ProductStatus.compute(saleDate, stock, now)
    }

    /**
     * Check if product is available for purchase.
     */
    fun isAvailableForPurchase(now: Instant = Instant.now()): Boolean {
        return computeStatus(now).isAvailableForPurchase()
    }

    /**
     * Decrease stock by the given amount.
     *
     * @param amount Amount to decrease (must be positive)
     * @return Updated product with decreased stock
     * @throws IllegalArgumentException if amount is invalid or stock insufficient
     */
    fun decreaseStock(amount: Int): Product {
        require(amount > 0) { "Decrease amount must be positive: $amount" }
        require(stock >= amount) { "Insufficient stock: requested=$amount, available=$stock" }
        return copy(
            stock = stock - amount,
            updatedAt = Instant.now()
        )
    }

    /**
     * Increase stock by the given amount.
     *
     * Used for:
     * - Admin adding more stock
     * - Reclaiming stock from expired slots
     *
     * @param amount Amount to increase (must be positive)
     * @return Updated product with increased stock
     * @throws IllegalArgumentException if amount is invalid or exceeds initial stock
     */
    fun increaseStock(amount: Int): Product {
        require(amount > 0) { "Increase amount must be positive: $amount" }
        val newStock = stock + amount
        require(newStock <= initialStock) {
            "New stock ($newStock) would exceed initial stock ($initialStock)"
        }
        return copy(
            stock = newStock,
            updatedAt = Instant.now()
        )
    }

    /**
     * Update product information (name, description, price).
     *
     * Stock and sale date have separate update methods with validation.
     *
     * @param name New name (optional)
     * @param description New description (optional)
     * @param price New price (optional)
     * @return Updated product
     */
    fun updateInfo(
        name: String? = null,
        description: String? = null,
        price: java.math.BigDecimal? = null
    ): Product {
        return copy(
            name = name ?: this.name,
            description = description ?: this.description,
            price = price ?: this.price,
            updatedAt = Instant.now()
        )
    }

    /**
     * Check if product can be deleted.
     *
     * Product can only be deleted if no active purchase slots exist.
     * This should be validated at the service layer by checking repository.
     *
     * @param activeSlotCount Number of active slots for this product
     * @return true if product can be deleted
     */
    fun canDelete(activeSlotCount: Int): Boolean {
        return activeSlotCount == 0
    }

    companion object {
        /**
         * Create a new product.
         *
         * @param name Product name (1-200 characters)
         * @param description Product description (1-2000 characters)
         * @param stock Initial stock quantity (must be > 0)
         * @param saleDate When product becomes available (must be in future)
         * @param price Product price (must be non-negative)
         * @param createdBy Admin user ID who created the product
         * @return New product with generated UUID
         */
        fun create(
            name: String,
            description: String,
            stock: Int,
            saleDate: Instant,
            price: java.math.BigDecimal,
            createdBy: UUID
        ): Product {
            require(stock > 0) { "Initial stock must be positive: $stock" }
            val now = Instant.now()
            require(saleDate > now.plusSeconds(3600)) {
                "Sale date must be at least 1 hour in the future: $saleDate"
            }

            return Product(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                stock = stock,
                initialStock = stock,
                saleDate = saleDate,
                price = price,
                createdAt = now,
                updatedAt = now,
                createdBy = createdBy
            )
        }
    }
}
