package com.dopaminestore.product.core.domain.value

import java.time.Instant

/**
 * Product availability status (computed field).
 *
 * Status is derived from sale date and stock:
 * - UPCOMING: Sale date is in the future
 * - ON_SALE: Sale date has passed and stock > 0
 * - SOLD_OUT: Stock is 0
 */
enum class ProductStatus {
    /**
     * Product sale has not started yet.
     * Sale date is in the future.
     */
    UPCOMING,

    /**
     * Product is currently available for purchase.
     * Sale date has passed and stock > 0.
     */
    ON_SALE,

    /**
     * Product is sold out.
     * Stock is 0.
     */
    SOLD_OUT;

    companion object {
        /**
         * Compute product status from sale date and stock.
         *
         * @param saleDate When the product becomes available
         * @param stock Current available stock
         * @param now Current timestamp (defaults to Instant.now())
         * @return Computed product status
         */
        fun compute(saleDate: Instant, stock: Int, now: Instant = Instant.now()): ProductStatus {
            return when {
                stock <= 0 -> SOLD_OUT
                now < saleDate -> UPCOMING
                else -> ON_SALE
            }
        }
    }

    /**
     * Check if product is available for purchase.
     */
    fun isAvailableForPurchase(): Boolean = this == ON_SALE

    /**
     * Check if product is sold out.
     */
    fun isSoldOut(): Boolean = this == SOLD_OUT

    /**
     * Check if product sale hasn't started yet.
     */
    fun isUpcoming(): Boolean = this == UPCOMING
}
