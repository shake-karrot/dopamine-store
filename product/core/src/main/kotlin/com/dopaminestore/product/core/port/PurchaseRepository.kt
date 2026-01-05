package com.dopaminestore.product.core.port

import com.dopaminestore.product.core.domain.Purchase
import com.dopaminestore.product.core.domain.value.PaymentStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Repository port for Purchase aggregate persistence.
 *
 * This port defines the contract for purchase (payment transaction) persistence operations.
 * Critical for payment idempotency and transaction history.
 */
interface PurchaseRepository {

    /**
     * Save a new purchase or update an existing one.
     *
     * @param purchase Purchase to save
     * @return Saved purchase with updated timestamps
     */
    fun save(purchase: Purchase): Mono<Purchase>

    /**
     * Find a purchase by its ID.
     *
     * @param id Purchase ID
     * @return Purchase if found, empty Mono otherwise
     */
    fun findById(id: UUID): Mono<Purchase>

    /**
     * Find purchase by payment ID (external gateway reference).
     *
     * Used for webhook processing to locate purchase by gateway's payment ID.
     *
     * @param paymentId External payment gateway reference
     * @return Purchase if found, empty Mono otherwise
     */
    fun findByPaymentId(paymentId: String): Mono<Purchase>

    /**
     * Find purchase by purchase slot ID.
     *
     * Each slot can have at most one purchase (1:1 relationship).
     *
     * @param purchaseSlotId Purchase slot ID
     * @return Purchase if found, empty Mono otherwise
     */
    fun findByPurchaseSlotId(purchaseSlotId: UUID): Mono<Purchase>

    /**
     * Check if purchase exists for a purchase slot.
     *
     * Used to enforce 1:1 relationship between slot and purchase.
     *
     * @param purchaseSlotId Purchase slot ID
     * @return true if purchase exists for the slot, false otherwise
     */
    fun existsByPurchaseSlotId(purchaseSlotId: UUID): Mono<Boolean>

    /**
     * Find purchase by idempotency key.
     *
     * Critical for preventing duplicate charges on payment retry.
     * If purchase with same idempotency key exists, return existing purchase instead of creating new one.
     *
     * @param idempotencyKey Unique idempotency key
     * @return Purchase if found, empty Mono otherwise
     */
    fun findByIdempotencyKey(idempotencyKey: UUID): Mono<Purchase>

    /**
     * Check if idempotency key has been used.
     *
     * Used for quick duplicate detection before payment initiation.
     *
     * @param idempotencyKey Unique idempotency key
     * @return true if key has been used, false otherwise
     */
    fun existsByIdempotencyKey(idempotencyKey: UUID): Mono<Boolean>

    /**
     * Find all purchases for a user.
     *
     * Used for purchase history and "My Purchases" view.
     *
     * @param userId User ID
     * @return All purchases for the user, ordered by creation time descending
     */
    fun findByUserId(userId: UUID): Flux<Purchase>

    /**
     * Find purchases for a user filtered by payment status.
     *
     * @param userId User ID
     * @param status Payment status filter
     * @return Purchases matching the criteria
     */
    fun findByUserIdAndStatus(userId: UUID, status: PaymentStatus): Flux<Purchase>

    /**
     * Find all purchases for a product.
     *
     * Used for sales analytics and reporting.
     *
     * @param productId Product ID
     * @return All purchases for the product
     */
    fun findByProductId(productId: UUID): Flux<Purchase>

    /**
     * Find pending purchases that have timed out.
     *
     * Used by scheduled job to mark timed-out payments as FAILED.
     * Finds purchases where:
     * - status = PENDING
     * - createdAt + timeout < now
     *
     * @param timeout Payment timeout duration in milliseconds (default: 5 minutes)
     * @param now Current timestamp (defaults to Instant.now())
     * @return Timed-out pending purchases
     */
    fun findTimedOutPurchases(
        timeout: Long = Purchase.PAYMENT_TIMEOUT_MILLIS,
        now: Instant = Instant.now()
    ): Flux<Purchase>

    /**
     * Find purchases by status with pagination.
     *
     * @param status Payment status filter
     * @param offset Number of items to skip
     * @param limit Maximum number of items to return
     * @return Page of purchases
     */
    fun findByStatusWithPagination(
        status: PaymentStatus,
        offset: Long = 0,
        limit: Int = 20
    ): Flux<Purchase>

    /**
     * Find purchases in a date range.
     *
     * Used for reporting and analytics.
     *
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return Purchases in the date range
     */
    fun findByCreatedAtBetween(startDate: Instant, endDate: Instant): Flux<Purchase>

    /**
     * Count purchases by status.
     *
     * @param status Payment status
     * @return Number of purchases with the given status
     */
    fun countByStatus(status: PaymentStatus): Mono<Long>

    /**
     * Count successful purchases for a product.
     *
     * Used for sales reporting.
     *
     * @param productId Product ID
     * @return Number of successful purchases
     */
    fun countSuccessfulPurchasesByProduct(productId: UUID): Mono<Long>

    /**
     * Find purchases by trace ID.
     *
     * Used for debugging and troubleshooting specific requests.
     *
     * @param traceId Trace ID
     * @return Purchases with the given trace ID
     */
    fun findByTraceId(traceId: String): Flux<Purchase>

    /**
     * Calculate total revenue for a product.
     *
     * Sums amount of all successful purchases for the product.
     *
     * @param productId Product ID
     * @return Total revenue (sum of successful purchase amounts)
     */
    fun calculateRevenueByProduct(productId: UUID): Mono<java.math.BigDecimal>

    /**
     * Update payment status atomically.
     *
     * Low-level operation for status transitions.
     * Prefer using domain entity methods (markSuccess/markFailed) for business logic validation.
     *
     * @param id Purchase ID
     * @param newStatus New payment status
     * @param updatedAt Update timestamp
     * @return Updated purchase, or empty Mono if purchase not found
     */
    fun updateStatus(
        id: UUID,
        newStatus: PaymentStatus,
        updatedAt: Instant = Instant.now()
    ): Mono<Purchase>
}
