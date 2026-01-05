package com.dopaminestore.product.core.domain.value

/**
 * Purchase slot lifecycle status.
 *
 * State transitions:
 * - ACTIVE → EXPIRED (30-minute timeout or admin action)
 * - ACTIVE → COMPLETED (payment success)
 * - No reverting allowed (one-way transitions)
 */
enum class SlotStatus {
    /**
     * Slot is active and can be used for payment.
     * User has 30 minutes from acquisition to complete payment.
     */
    ACTIVE,

    /**
     * Slot has expired without payment.
     * Stock has been reclaimed and returned to pool.
     */
    EXPIRED,

    /**
     * Payment completed successfully.
     * Slot is finalized and cannot expire.
     */
    COMPLETED;

    /**
     * Check if slot can be used for payment.
     */
    fun canPay(): Boolean = this == ACTIVE

    /**
     * Check if slot has expired.
     */
    fun isExpired(): Boolean = this == EXPIRED

    /**
     * Check if slot is completed.
     */
    fun isCompleted(): Boolean = this == COMPLETED

    /**
     * Validate state transition.
     *
     * @param target Target status to transition to
     * @throws IllegalStateException if transition is invalid
     */
    fun validateTransition(target: SlotStatus) {
        val validTransitions = when (this) {
            ACTIVE -> setOf(EXPIRED, COMPLETED)
            EXPIRED -> emptySet()  // Terminal state
            COMPLETED -> emptySet()  // Terminal state
        }

        require(target in validTransitions) {
            "Invalid slot status transition: $this → $target. Valid transitions: $validTransitions"
        }
    }

    /**
     * Check if this is a terminal state (no further transitions allowed).
     */
    fun isTerminal(): Boolean = this == EXPIRED || this == COMPLETED
}

/**
 * Reason for slot expiration (only applies when status = EXPIRED).
 */
enum class ReclaimStatus {
    /**
     * Slot expired automatically after 30-minute timeout.
     */
    AUTO_EXPIRED,

    /**
     * Slot was manually reclaimed by admin action.
     */
    MANUAL_RECLAIMED;

    override fun toString(): String = name
}
