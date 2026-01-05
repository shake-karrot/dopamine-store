package com.dopaminestore.product.core.domain

import com.dopaminestore.product.core.domain.value.Money
import com.dopaminestore.product.core.domain.value.PaymentMethod
import com.dopaminestore.product.core.domain.value.PaymentStatus
import java.time.Instant
import java.util.UUID

/**
 * Purchase aggregate root.
 *
 * Represents a completed payment transaction for a purchase slot.
 * Each purchase is linked 1:1 with a purchase slot.
 *
 * Invariants:
 * - purchaseSlotId is unique (one purchase per slot)
 * - idempotencyKey is unique (prevent duplicate charges)
 * - amount must be positive
 * - confirmationTimestamp can only be set when paymentStatus = SUCCESS
 * - failureReason can only be set when paymentStatus = FAILED
 * - Once paymentStatus = SUCCESS, it cannot change (immutable confirmation)
 */
data class Purchase(
    val id: UUID,
    val purchaseSlotId: UUID,
    val userId: UUID,
    val productId: UUID,  // Denormalized for query efficiency
    val paymentId: String,
    val idempotencyKey: UUID,
    val amount: Money,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus,
    val confirmationTimestamp: Instant? = null,
    val failureReason: String? = null,
    val traceId: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(amount.isPositive()) { "Purchase amount must be positive: $amount" }
        require(traceId.isNotBlank()) { "Trace ID cannot be blank" }

        // Validate confirmationTimestamp consistency
        when (paymentStatus) {
            PaymentStatus.SUCCESS -> {
                requireNotNull(confirmationTimestamp) {
                    "Confirmation timestamp must be set when payment status is SUCCESS"
                }
                require(failureReason == null) {
                    "Failure reason cannot be set when payment status is SUCCESS"
                }
            }
            PaymentStatus.FAILED -> {
                requireNotNull(failureReason) {
                    "Failure reason must be set when payment status is FAILED"
                }
                require(failureReason.length <= 500) {
                    "Failure reason must be at most 500 characters, got: ${failureReason.length}"
                }
            }
            PaymentStatus.PENDING -> {
                require(confirmationTimestamp == null) {
                    "Confirmation timestamp cannot be set when payment status is PENDING"
                }
                require(failureReason == null) {
                    "Failure reason cannot be set when payment status is PENDING"
                }
            }
        }
    }

    /**
     * Check if payment is pending.
     */
    fun isPending(): Boolean = paymentStatus.isPending()

    /**
     * Check if payment succeeded.
     */
    fun isSuccess(): Boolean = paymentStatus.isSuccess()

    /**
     * Check if payment failed.
     */
    fun isFailed(): Boolean = paymentStatus.isFailed()

    /**
     * Mark payment as successful.
     *
     * @param confirmationTime Timestamp when payment was confirmed (defaults to now)
     * @return Updated purchase with SUCCESS status
     * @throws IllegalStateException if payment is not in PENDING status
     */
    fun markSuccess(confirmationTime: Instant = Instant.now()): Purchase {
        paymentStatus.validateTransition(PaymentStatus.SUCCESS)

        return copy(
            paymentStatus = PaymentStatus.SUCCESS,
            confirmationTimestamp = confirmationTime,
            failureReason = null,
            updatedAt = confirmationTime
        )
    }

    /**
     * Mark payment as failed.
     *
     * @param reason Failure reason (max 500 characters)
     * @param failureTime Timestamp when failure occurred (defaults to now)
     * @return Updated purchase with FAILED status
     * @throws IllegalStateException if payment is not in PENDING status
     */
    fun markFailed(reason: String, failureTime: Instant = Instant.now()): Purchase {
        paymentStatus.validateTransition(PaymentStatus.FAILED)
        require(reason.isNotBlank()) { "Failure reason cannot be blank" }
        require(reason.length <= 500) { "Failure reason must be at most 500 characters" }

        return copy(
            paymentStatus = PaymentStatus.FAILED,
            failureReason = reason,
            confirmationTimestamp = null,
            updatedAt = failureTime
        )
    }

    /**
     * Check if purchase can be retried.
     *
     * Only failed purchases can be retried (with new idempotency key).
     */
    fun canRetry(): Boolean = isFailed()

    /**
     * Get payment duration (time from creation to confirmation/failure).
     *
     * @return Duration in milliseconds, or null if payment is still pending
     */
    fun getPaymentDuration(): Long? {
        val endTime = confirmationTimestamp ?: updatedAt.takeIf { isFailed() } ?: return null
        return endTime.toEpochMilli() - createdAt.toEpochMilli()
    }

    companion object {
        /**
         * Initiate a new purchase (payment pending).
         *
         * @param purchaseSlotId Associated purchase slot
         * @param userId User making the purchase
         * @param productId Product being purchased (denormalized)
         * @param paymentId External payment gateway reference
         * @param idempotencyKey Unique key for duplicate prevention
         * @param amount Payment amount
         * @param paymentMethod Payment method (CARD, BANK_TRANSFER, etc.)
         * @param traceId Distributed trace ID
         * @param createdAt Creation timestamp (defaults to now)
         * @return New purchase with PENDING status
         */
        fun initiate(
            purchaseSlotId: UUID,
            userId: UUID,
            productId: UUID,
            paymentId: String,
            idempotencyKey: UUID,
            amount: Money,
            paymentMethod: PaymentMethod,
            traceId: String,
            createdAt: Instant = Instant.now()
        ): Purchase {
            require(paymentId.isNotBlank()) { "Payment ID cannot be blank" }

            return Purchase(
                id = UUID.randomUUID(),
                purchaseSlotId = purchaseSlotId,
                userId = userId,
                productId = productId,
                paymentId = paymentId,
                idempotencyKey = idempotencyKey,
                amount = amount,
                paymentMethod = paymentMethod,
                paymentStatus = PaymentStatus.PENDING,
                confirmationTimestamp = null,
                failureReason = null,
                traceId = traceId,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }

        /**
         * Maximum duration for payment processing before timeout (5 minutes).
         */
        val PAYMENT_TIMEOUT_MILLIS: Long = 5 * 60 * 1000  // 5 minutes
    }
}
