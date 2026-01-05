package com.dopaminestore.product.adapter.redis

/**
 * Redis key helper utilities for consistent key naming across the Product domain.
 *
 * Key patterns:
 * - Product stock: product:{productId}:stock
 * - Product fairness queue: product:{productId}:queue
 * - Duplicate prevention: user:{userId}:product:{productId}
 * - Payment idempotency: payment:idempotency:{idempotencyKey}
 */
object RedisKeyHelper {

    /**
     * Generate Redis key for product stock counter.
     *
     * @param productId The product UUID
     * @return Key in format: product:{productId}:stock
     */
    fun productStockKey(productId: String): String {
        return "product:${productId}:stock"
    }

    /**
     * Generate Redis key for product fairness queue (Sorted Set).
     *
     * @param productId The product UUID
     * @return Key in format: product:{productId}:queue
     */
    fun productQueueKey(productId: String): String {
        return "product:${productId}:queue"
    }

    /**
     * Generate Redis key for duplicate request prevention.
     *
     * @param userId The user UUID
     * @param productId The product UUID
     * @return Key in format: user:{userId}:product:{productId}
     */
    fun duplicatePreventionKey(userId: String, productId: String): String {
        return "user:${userId}:product:${productId}"
    }

    /**
     * Generate Redis key for payment idempotency tracking.
     *
     * @param idempotencyKey The unique idempotency key
     * @return Key in format: payment:idempotency:{idempotencyKey}
     */
    fun paymentIdempotencyKey(idempotencyKey: String): String {
        return "payment:idempotency:${idempotencyKey}"
    }

    /**
     * Extract product ID from a product stock key.
     *
     * @param key The Redis key
     * @return Product ID or null if key format is invalid
     */
    fun extractProductId(key: String): String? {
        val pattern = Regex("product:([^:]+):.*")
        return pattern.find(key)?.groupValues?.getOrNull(1)
    }

    /**
     * Extract user ID from a duplicate prevention key.
     *
     * @param key The Redis key
     * @return User ID or null if key format is invalid
     */
    fun extractUserId(key: String): String? {
        val pattern = Regex("user:([^:]+):product:.*")
        return pattern.find(key)?.groupValues?.getOrNull(1)
    }
}
