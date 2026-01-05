package com.dopaminestore.product.core.domain

import com.dopaminestore.product.core.domain.value.ReclaimStatus
import com.dopaminestore.product.core.domain.value.SlotStatus
import java.time.Instant
import java.time.Duration
import java.util.UUID

/**
 * PurchaseSlot aggregate root.
 *
 * Represents a temporary right to purchase a product, granted on first-come-first-served basis.
 * Slots expire after 30 minutes if payment is not completed.
 *
 * Invariants:
 * - expirationTimestamp = acquisitionTimestamp + 30 minutes (exactly)
 * - At most one ACTIVE slot per (userId, productId) pair
 * - Status transitions are one-way: ACTIVE → {EXPIRED, COMPLETED}
 * - reclaimStatus can only be set when status = EXPIRED
 */
data class PurchaseSlot(
    val id: UUID,
    val productId: UUID,
    val userId: UUID,
    val acquisitionTimestamp: Instant,
    val expirationTimestamp: Instant,
    val status: SlotStatus,
    val reclaimStatus: ReclaimStatus? = null,
    val traceId: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        // Validate 30-minute expiration
        val expectedExpiration = acquisitionTimestamp.plus(SLOT_LIFETIME)
        require(expirationTimestamp == expectedExpiration) {
            "Expiration timestamp must be exactly 30 minutes after acquisition. " +
                    "Expected: $expectedExpiration, Got: $expirationTimestamp"
        }

        // Validate reclaimStatus consistency
        when (status) {
            SlotStatus.EXPIRED -> {
                requireNotNull(reclaimStatus) {
                    "Reclaim status must be set when slot status is EXPIRED"
                }
            }
            else -> {
                require(reclaimStatus == null) {
                    "Reclaim status can only be set when slot status is EXPIRED, current status: $status"
                }
            }
        }

        require(traceId.isNotBlank()) { "Trace ID cannot be blank" }
    }

    /**
     * Check if slot is still active.
     */
    fun isActive(): Boolean = status == SlotStatus.ACTIVE

    /**
     * Check if slot has expired.
     */
    fun isExpired(): Boolean = status == SlotStatus.EXPIRED

    /**
     * Check if slot is completed (payment successful).
     */
    fun isCompleted(): Boolean = status == SlotStatus.COMPLETED

    /**
     * Check if slot has expired based on current time (lazy evaluation).
     *
     * This provides immediate expiration check without waiting for scheduled job.
     *
     * @param now Current timestamp (defaults to Instant.now())
     * @return true if current time > expirationTimestamp
     */
    fun isExpiredByTime(now: Instant = Instant.now()): Boolean {
        return now >= expirationTimestamp
    }

    /**
     * Get remaining time before expiration.
     *
     * @param now Current timestamp (defaults to Instant.now())
     * @return Remaining duration (negative if already expired)
     */
    fun remainingTime(now: Instant = Instant.now()): Duration {
        return Duration.between(now, expirationTimestamp)
    }

    /**
     * Get remaining time in seconds.
     *
     * @param now Current timestamp (defaults to Instant.now())
     * @return Remaining seconds (0 if already expired)
     */
    fun remainingSeconds(now: Instant = Instant.now()): Long {
        val remaining = remainingTime(now).seconds
        return if (remaining < 0) 0 else remaining
    }

    /**
     * Expire the slot (auto-expiration or manual reclamation).
     *
     * @param reason Reason for expiration
     * @param now Current timestamp (defaults to Instant.now())
     * @return Updated slot with EXPIRED status
     * @throws IllegalStateException if slot is not in ACTIVE status
     */
    fun expire(reason: ReclaimStatus, now: Instant = Instant.now()): PurchaseSlot {
        status.validateTransition(SlotStatus.EXPIRED)

        return copy(
            status = SlotStatus.EXPIRED,
            reclaimStatus = reason,
            updatedAt = now
        )
    }

    /**
     * Complete the slot (payment successful).
     *
     * @param now Current timestamp (defaults to Instant.now())
     * @return Updated slot with COMPLETED status
     * @throws IllegalStateException if slot is not in ACTIVE status or has expired
     */
    fun complete(now: Instant = Instant.now()): PurchaseSlot {
        status.validateTransition(SlotStatus.COMPLETED)

        // Validate slot hasn't expired (lazy evaluation)
        require(!isExpiredByTime(now)) {
            "Cannot complete expired slot. Expired at: $expirationTimestamp, Current time: $now"
        }

        return copy(
            status = SlotStatus.COMPLETED,
            updatedAt = now
        )
    }

    /**
     * Check if payment can be processed for this slot.
     *
     * @param now Current timestamp (defaults to Instant.now())
     * @return true if slot is active and not expired
     */
    fun canProcessPayment(now: Instant = Instant.now()): Boolean {
        return isActive() && !isExpiredByTime(now)
    }

    companion object {
        /**
         * Slot lifetime duration (30 minutes).
         */
        val SLOT_LIFETIME: Duration = Duration.ofMinutes(30)

        /**
         * Acquire a new purchase slot.
         *
         * @param productId Product being purchased
         * @param userId User acquiring the slot
         * @param traceId Distributed trace ID
         * @param acquisitionTime Timestamp when slot was acquired (defaults to now)
         * @return New purchase slot with ACTIVE status
         */
        fun acquire(
            productId: UUID,
            userId: UUID,
            traceId: String,
            acquisitionTime: Instant = Instant.now()
        ): PurchaseSlot {
            val expiration = acquisitionTime.plus(SLOT_LIFETIME)

            return PurchaseSlot(
                id = UUID.randomUUID(),
                productId = productId,
                userId = userId,
                acquisitionTimestamp = acquisitionTime,
                expirationTimestamp = expiration,
                status = SlotStatus.ACTIVE,
                reclaimStatus = null,
                traceId = traceId,
                createdAt = acquisitionTime,
                updatedAt = acquisitionTime
            )
        }
    }
}
