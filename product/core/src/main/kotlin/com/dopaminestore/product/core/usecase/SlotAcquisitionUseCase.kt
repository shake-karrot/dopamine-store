package com.dopaminestore.product.core.usecase

import com.dopaminestore.product.core.domain.PurchaseSlot
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Use case for acquiring purchase slots with first-come-first-served fairness.
 *
 * This use case orchestrates the slot acquisition flow:
 * 1. Validate product availability (status = ON_SALE, stock > 0)
 * 2. Check for duplicate slots (at most one active slot per user-product pair)
 * 3. Atomically allocate slot using Redis (fairness queue + stock decrement)
 * 4. Persist slot to database
 * 5. Publish slot acquisition event to Kafka
 *
 * **Fairness Guarantee**: Requests are processed in strict arrival-time order
 * using Redis sorted sets with arrival timestamp as score.
 *
 * **Concurrency**: Designed for 100K RPS with reactive non-blocking operations.
 */
interface SlotAcquisitionUseCase {

    /**
     * Acquire a purchase slot for a user and product.
     *
     * **Success Criteria**:
     * - Product is ON_SALE (sale date has passed, stock > 0)
     * - User does not have an active slot for this product
     * - Stock is available (atomic allocation via Redis)
     * - Arrival-time ordering is respected (fairness)
     *
     * **Failure Cases**:
     * - ProductNotFoundException: Product does not exist
     * - ProductNotAvailableException: Product is UPCOMING or SOLD_OUT
     * - DuplicateSlotException: User already has active slot for this product
     * - OutOfStockException: No stock available (race condition handled atomically)
     *
     * @param command Slot acquisition command with user, product, and trace context
     * @return Acquired purchase slot with 30-minute expiration
     */
    fun acquireSlot(command: AcquireSlotCommand): Mono<PurchaseSlot>

    /**
     * Command for slot acquisition.
     *
     * @param userId User requesting the slot
     * @param productId Product to acquire slot for
     * @param arrivalTimestamp Request arrival time (milliseconds since epoch) - used for fairness
     * @param traceId Distributed trace ID for observability
     */
    data class AcquireSlotCommand(
        val userId: UUID,
        val productId: UUID,
        val arrivalTimestamp: Long,
        val traceId: String
    )
}
