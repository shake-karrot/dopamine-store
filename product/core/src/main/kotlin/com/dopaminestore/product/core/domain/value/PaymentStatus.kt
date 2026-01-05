package com.dopaminestore.product.core.domain.value

/**
 * Payment transaction status.
 *
 * State transitions:
 * - PENDING → SUCCESS (webhook: payment.succeeded)
 * - PENDING → FAILED (webhook: payment.failed or timeout)
 * - SUCCESS is immutable (no further transitions)
 */
enum class PaymentStatus {
    /**
     * Payment initiated and waiting for gateway response.
     * Max duration: 5 minutes before timeout.
     */
    PENDING,

    /**
     * Payment completed successfully.
     * This is an immutable final state.
     */
    SUCCESS,

    /**
     * Payment failed or timed out.
     * User can retry with new idempotency key.
     */
    FAILED;

    /**
     * Check if payment is pending.
     */
    fun isPending(): Boolean = this == PENDING

    /**
     * Check if payment succeeded.
     */
    fun isSuccess(): Boolean = this == SUCCESS

    /**
     * Check if payment failed.
     */
    fun isFailed(): Boolean = this == FAILED

    /**
     * Check if payment is in a final state (no further transitions).
     */
    fun isFinal(): Boolean = this == SUCCESS || this == FAILED

    /**
     * Validate state transition.
     *
     * @param target Target status to transition to
     * @throws IllegalStateException if transition is invalid
     */
    fun validateTransition(target: PaymentStatus) {
        val validTransitions = when (this) {
            PENDING -> setOf(SUCCESS, FAILED)
            SUCCESS -> emptySet()  // Immutable final state
            FAILED -> emptySet()   // Terminal state (can create new payment for retry)
        }

        require(target in validTransitions) {
            "Invalid payment status transition: $this → $target. Valid transitions: $validTransitions"
        }
    }
}

/**
 * Payment method types.
 */
enum class PaymentMethod {
    /**
     * Credit or debit card payment.
     */
    CARD,

    /**
     * Bank transfer payment.
     */
    BANK_TRANSFER,

    /**
     * Digital wallet payment (future expansion).
     */
    WALLET;

    override fun toString(): String = name
}
