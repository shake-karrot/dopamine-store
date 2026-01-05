package com.dopaminestore.product.core.port

import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Port interface for Redis-based slot caching and fairness queue operations.
 *
 * This port defines the contract for distributed slot acquisition state management
 * using Redis as the backing store. Critical for fairness guarantees and duplicate prevention.
 *
 * **Fairness Mechanism**:
 * - Uses Redis sorted sets (ZSET) to maintain arrival-time ordering
 * - Lua scripts ensure atomic operations (duplicate check + allocation)
 * - Distributed locks prevent race conditions in concurrent scenarios
 *
 * **Data Structures**:
 * - `product:{productId}:stock` → Current available stock (STRING)
 * - `product:{productId}:queue` → Sorted set of (userId, timestamp) for fairness (ZSET)
 * - `user:{userId}:product:{productId}:slot` → Duplicate prevention flag (STRING with TTL)
 *
 * **TTL Settings**:
 * - Duplicate keys: 30 minutes (slot lifetime)
 * - Queue entries: Cleaned up on slot expiration
 * - Stock counters: No expiration (updated by background jobs)
 */
interface RedisSlotCache {

    /**
     * Attempt to acquire a slot for a user atomically.
     *
     * This operation executes a Lua script that performs:
     * 1. Duplicate check: Verify user doesn't have an active slot for this product
     * 2. Stock validation: Ensure stock > 0
     * 3. Atomic allocation: Decrement stock, add user to queue, set duplicate flag
     *
     * **Fairness Guarantee**: Uses arrival timestamp to maintain FIFO ordering.
     * The timestamp must be captured at the earliest possible point (HTTP request arrival).
     *
     * @param userId User requesting the slot
     * @param productId Product for which slot is being acquired
     * @param arrivalTimestamp Request arrival time (milliseconds since epoch)
     * @param traceId Distributed trace ID for observability
     * @return AcquisitionResult with success status, queue position, remaining stock
     */
    fun acquireSlot(
        userId: UUID,
        productId: UUID,
        arrivalTimestamp: Long,
        traceId: String
    ): Mono<AcquisitionResult>

    /**
     * Result of slot acquisition attempt.
     *
     * @param success Whether slot was successfully acquired
     * @param reason Failure reason if not successful (DUPLICATE_REQUEST, OUT_OF_STOCK, etc.)
     * @param queuePosition User's position in the fairness queue (1-indexed)
     * @param remainingStock Stock remaining after acquisition
     * @param timestamp When the acquisition occurred
     */
    data class AcquisitionResult(
        val success: Boolean,
        val reason: String? = null,
        val queuePosition: Long? = null,
        val remainingStock: Int? = null,
        val timestamp: Instant = Instant.now()
    )

    /**
     * Release a slot when it expires or is completed.
     *
     * Operations:
     * 1. Increment stock (if releasing due to expiration)
     * 2. Remove user from fairness queue
     * 3. Remove duplicate prevention flag
     *
     * @param userId User whose slot is being released
     * @param productId Product for which slot is being released
     * @param reclaimStock Whether to return stock to available pool (true for expiration, false for completion)
     * @param traceId Distributed trace ID
     * @return true if release was successful, false if no slot existed
     */
    fun releaseSlot(
        userId: UUID,
        productId: UUID,
        reclaimStock: Boolean,
        traceId: String
    ): Mono<Boolean>

    /**
     * Check if a user has an active slot for a product.
     *
     * Used for quick duplicate detection before executing expensive operations.
     *
     * @param userId User ID to check
     * @param productId Product ID to check
     * @return true if user has an active slot, false otherwise
     */
    fun hasActiveSlot(userId: UUID, productId: UUID): Mono<Boolean>

    /**
     * Get current stock from Redis cache.
     *
     * Returns the real-time available stock value from Redis.
     * Used for display and validation purposes.
     *
     * @param productId Product ID
     * @return Current stock value, or null if not cached
     */
    fun getStock(productId: UUID): Mono<Int?>

    /**
     * Set initial stock for a product in Redis.
     *
     * Called when:
     * - Product is created
     * - Product sale begins (pre-warm cache)
     * - Cache is invalidated and needs refresh
     *
     * @param productId Product ID
     * @param stock Initial stock value
     * @return true if operation succeeded
     */
    fun setStock(productId: UUID, stock: Int): Mono<Boolean>

    /**
     * Increment stock atomically.
     *
     * Used when slots are reclaimed due to expiration.
     *
     * @param productId Product ID
     * @param amount Amount to increment by (default: 1)
     * @return New stock value after increment
     */
    fun incrementStock(productId: UUID, amount: Int = 1): Mono<Long>

    /**
     * Decrement stock atomically (without slot acquisition).
     *
     * Used for manual stock adjustments by admins.
     *
     * @param productId Product ID
     * @param amount Amount to decrement by (default: 1)
     * @return New stock value after decrement, or null if stock would go negative
     */
    fun decrementStock(productId: UUID, amount: Int = 1): Mono<Long?>

    /**
     * Get user's position in the fairness queue.
     *
     * Returns 1-indexed position (1 = first in queue).
     *
     * @param userId User ID
     * @param productId Product ID
     * @return Queue position (1-indexed), or null if user not in queue
     */
    fun getQueuePosition(userId: UUID, productId: UUID): Mono<Long?>

    /**
     * Get total number of users in the fairness queue.
     *
     * @param productId Product ID
     * @return Number of users waiting
     */
    fun getQueueSize(productId: UUID): Mono<Long>

    /**
     * Clear all cache entries for a product.
     *
     * Used when:
     * - Product is deleted
     * - Cache needs invalidation
     * - Testing/debugging
     *
     * @param productId Product ID
     * @return true if operation succeeded
     */
    fun clearProduct(productId: UUID): Mono<Boolean>

    /**
     * Verify fairness ordering for a product.
     *
     * Returns all (userId, timestamp) pairs in the queue ordered by timestamp.
     * Used for compliance auditing and fairness verification.
     *
     * @param productId Product ID
     * @return List of queue entries with (userId, timestamp)
     */
    fun verifyQueueOrdering(productId: UUID): Mono<List<QueueEntry>>

    /**
     * Queue entry for fairness verification.
     *
     * @param userId User ID in the queue
     * @param arrivalTimestamp When user entered the queue (milliseconds since epoch)
     * @param position 1-indexed position in the queue
     */
    data class QueueEntry(
        val userId: UUID,
        val arrivalTimestamp: Long,
        val position: Long
    )

    companion object {
        /**
         * Redis key patterns for slot cache operations.
         */
        const val STOCK_KEY_PATTERN = "product:{productId}:stock"
        const val QUEUE_KEY_PATTERN = "product:{productId}:queue"
        const val DUPLICATE_KEY_PATTERN = "user:{userId}:product:{productId}:slot"

        /**
         * TTL for duplicate prevention keys (30 minutes = slot lifetime).
         */
        const val DUPLICATE_TTL_SECONDS = 1800L
    }
}
