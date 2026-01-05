package com.dopaminestore.product.core.port

import com.dopaminestore.product.core.domain.PurchaseSlot
import com.dopaminestore.product.core.domain.value.SlotStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Repository port for PurchaseSlot aggregate persistence.
 *
 * This port defines the contract for purchase slot persistence operations.
 * Critical for enforcing fairness guarantees and managing slot lifecycle.
 */
interface PurchaseSlotRepository {

    /**
     * Save a new purchase slot or update an existing one.
     *
     * @param slot Purchase slot to save
     * @return Saved slot with updated timestamps
     */
    fun save(slot: PurchaseSlot): Mono<PurchaseSlot>

    /**
     * Find a purchase slot by its ID.
     *
     * @param id Slot ID
     * @return Purchase slot if found, empty Mono otherwise
     */
    fun findById(id: UUID): Mono<PurchaseSlot>

    /**
     * Find active slot for a user and product.
     *
     * Critical for duplicate prevention: at most one ACTIVE slot per (userId, productId).
     *
     * @param userId User ID
     * @param productId Product ID
     * @return Active slot if exists, empty Mono otherwise
     */
    fun findActiveSlotByUserAndProduct(userId: UUID, productId: UUID): Mono<PurchaseSlot>

    /**
     * Check if an active slot exists for a user and product.
     *
     * Used for quick duplicate detection before slot acquisition.
     *
     * @param userId User ID
     * @param productId Product ID
     * @return true if active slot exists, false otherwise
     */
    fun hasActiveSlot(userId: UUID, productId: UUID): Mono<Boolean>

    /**
     * Find all slots for a user.
     *
     * Used for "My Slots" view with all statuses (ACTIVE, EXPIRED, COMPLETED).
     *
     * @param userId User ID
     * @return All slots for the user, ordered by acquisition time descending
     */
    fun findByUserId(userId: UUID): Flux<PurchaseSlot>

    /**
     * Find slots for a user filtered by status.
     *
     * @param userId User ID
     * @param status Slot status filter
     * @return Slots matching the criteria
     */
    fun findByUserIdAndStatus(userId: UUID, status: SlotStatus): Flux<PurchaseSlot>

    /**
     * Find all active slots for a product.
     *
     * Used for counting active slots to validate stock constraints.
     *
     * @param productId Product ID
     * @return Active slots for the product
     */
    fun findActiveSlotsByProduct(productId: UUID): Flux<PurchaseSlot>

    /**
     * Count active slots for a product.
     *
     * Critical for validating product deletion and stock management.
     * Invariant: activeSlotCount <= product.stock
     *
     * @param productId Product ID
     * @return Number of active slots
     */
    fun countActiveSlotsByProduct(productId: UUID): Mono<Long>

    /**
     * Find expired slots that need reclamation.
     *
     * Used by scheduled expiration job to find slots where:
     * - status = ACTIVE
     * - expirationTimestamp <= now
     *
     * @param now Current timestamp (defaults to Instant.now())
     * @param limit Maximum number of slots to return (for batch processing)
     * @return Expired slots to be reclaimed
     */
    fun findExpiredSlots(now: Instant = Instant.now(), limit: Int = 100): Flux<PurchaseSlot>

    /**
     * Find slots expiring soon (for pre-expiration notifications).
     *
     * Used by scheduled job to send 5-minute warning notifications.
     * Finds slots where expirationTimestamp is within [now + minMinutes, now + maxMinutes].
     *
     * @param minMinutes Minimum minutes until expiration (e.g., 4)
     * @param maxMinutes Maximum minutes until expiration (e.g., 6)
     * @param now Current timestamp (defaults to Instant.now())
     * @return Slots expiring in the time window
     */
    fun findSlotsExpiringSoon(
        minMinutes: Long = 4,
        maxMinutes: Long = 6,
        now: Instant = Instant.now()
    ): Flux<PurchaseSlot>

    /**
     * Find slots by status with pagination.
     *
     * @param status Slot status filter
     * @param offset Number of items to skip
     * @param limit Maximum number of items to return
     * @return Page of slots
     */
    fun findByStatusWithPagination(
        status: SlotStatus,
        offset: Long = 0,
        limit: Int = 20
    ): Flux<PurchaseSlot>

    /**
     * Count slots by status.
     *
     * @param status Slot status
     * @return Number of slots with the given status
     */
    fun countByStatus(status: SlotStatus): Mono<Long>

    /**
     * Find slots by trace ID.
     *
     * Used for debugging and troubleshooting specific requests.
     *
     * @param traceId Trace ID
     * @return Slots with the given trace ID
     */
    fun findByTraceId(traceId: String): Flux<PurchaseSlot>

    /**
     * Delete a purchase slot.
     *
     * Note: Should only be used for cleanup or admin operations.
     * Normal slot lifecycle should transition through status changes, not deletion.
     *
     * @param id Slot ID to delete
     * @return Mono that completes when deletion is done
     */
    fun deleteById(id: UUID): Mono<Void>

    /**
     * Update slot status atomically.
     *
     * Low-level operation for status transitions.
     * Prefer using domain entity methods (expire/complete) for business logic validation.
     *
     * @param id Slot ID
     * @param newStatus New status
     * @param updatedAt Update timestamp
     * @return Updated slot, or empty Mono if slot not found
     */
    fun updateStatus(id: UUID, newStatus: SlotStatus, updatedAt: Instant = Instant.now()): Mono<PurchaseSlot>
}
