package com.dopaminestore.product.core.port

import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Port interface for publishing domain events to Kafka.
 *
 * This port defines the contract for event-driven communication between domains.
 * Critical for maintaining domain autonomy while enabling cross-domain workflows.
 *
 * **Constitution Compliance**:
 * - **Domain Ownership**: Events are the ONLY way to communicate across domains
 * - **No Direct DB Access**: Notification and Auth domains consume events, not database
 * - **Asynchronous by Default**: All events are fire-and-forget unless explicitly awaited
 *
 * **Event Design Principles**:
 * - **Avro Schemas**: All events use Avro for schema evolution and compatibility
 * - **Idempotency**: Events include unique IDs and timestamps for deduplication
 * - **Traceability**: Every event carries trace ID for distributed tracing
 * - **Self-Describing**: Events contain all data needed by consumers (no lookups required)
 *
 * **Topics and Consumers**:
 * - `product.slot.acquired` → Notification domain (send confirmation email/SMS)
 * - `product.slot.expired` → Notification domain (send expiration notice)
 * - `product.purchase.completed` → Auth domain (update user rewards), Notification domain (send receipt)
 * - `product.purchase.failed` → Notification domain (send failure notice)
 *
 * **Guarantees**:
 * - At-least-once delivery (Kafka default)
 * - Ordering within partition (keyed by userId or productId)
 * - Retention: 7 days (configurable)
 */
interface EventPublisher {

    /**
     * Publish slot acquisition event.
     *
     * Event: `product.slot.acquired`
     * Consumers: Notification domain
     * Purpose: Send slot confirmation to user (email/SMS/push)
     *
     * **Event Schema** (Avro):
     * - slotId: UUID of the acquired slot
     * - userId: User who acquired the slot
     * - productId: Product for which slot was acquired
     * - productName: Product name (for notification content)
     * - expirationTimestamp: When slot expires (ISO-8601)
     * - acquisitionTimestamp: When slot was acquired (ISO-8601)
     * - traceId: Distributed trace ID
     *
     * @param event Slot acquisition event details
     * @return Mono that completes when event is published
     */
    fun publishSlotAcquired(event: SlotAcquiredEvent): Mono<Void>

    /**
     * Slot acquisition event.
     */
    data class SlotAcquiredEvent(
        val slotId: UUID,
        val userId: UUID,
        val productId: UUID,
        val productName: String,
        val expirationTimestamp: Instant,
        val acquisitionTimestamp: Instant,
        val traceId: String
    )

    /**
     * Publish slot expiration event.
     *
     * Event: `product.slot.expired`
     * Consumers: Notification domain
     * Purpose: Notify user that their slot has expired
     *
     * **Event Schema** (Avro):
     * - slotId: UUID of the expired slot
     * - userId: User whose slot expired
     * - productId: Product for which slot expired
     * - productName: Product name
     * - expirationTimestamp: When slot expired (ISO-8601)
     * - reclaimReason: AUTO_EXPIRED or MANUAL_RECLAIMED
     * - traceId: Distributed trace ID
     *
     * @param event Slot expiration event details
     * @return Mono that completes when event is published
     */
    fun publishSlotExpired(event: SlotExpiredEvent): Mono<Void>

    /**
     * Slot expiration event.
     */
    data class SlotExpiredEvent(
        val slotId: UUID,
        val userId: UUID,
        val productId: UUID,
        val productName: String,
        val expirationTimestamp: Instant,
        val reclaimReason: String, // AUTO_EXPIRED or MANUAL_RECLAIMED
        val traceId: String
    )

    /**
     * Publish purchase completion event.
     *
     * Event: `product.purchase.completed`
     * Consumers: Auth domain (update rewards), Notification domain (send receipt)
     * Purpose: Notify downstream systems of successful purchase
     *
     * **Event Schema** (Avro):
     * - purchaseId: UUID of the purchase
     * - slotId: UUID of the associated slot
     * - userId: User who completed the purchase
     * - productId: Product that was purchased
     * - productName: Product name
     * - amount: Purchase amount (BigDecimal as string)
     * - currency: Currency code (KRW, USD, etc.)
     * - paymentId: External payment gateway reference
     * - completionTimestamp: When payment was completed (ISO-8601)
     * - traceId: Distributed trace ID
     *
     * @param event Purchase completion event details
     * @return Mono that completes when event is published
     */
    fun publishPurchaseCompleted(event: PurchaseCompletedEvent): Mono<Void>

    /**
     * Purchase completion event.
     */
    data class PurchaseCompletedEvent(
        val purchaseId: UUID,
        val slotId: UUID,
        val userId: UUID,
        val productId: UUID,
        val productName: String,
        val amount: String, // BigDecimal as string for Avro compatibility
        val currency: String,
        val paymentId: String,
        val completionTimestamp: Instant,
        val traceId: String
    )

    /**
     * Publish purchase failure event.
     *
     * Event: `product.purchase.failed`
     * Consumers: Notification domain
     * Purpose: Notify user of payment failure
     *
     * **Event Schema** (Avro):
     * - purchaseId: UUID of the failed purchase
     * - slotId: UUID of the associated slot
     * - userId: User whose purchase failed
     * - productId: Product purchase attempt
     * - productName: Product name
     * - failureReason: Reason for failure (PAYMENT_TIMEOUT, GATEWAY_ERROR, etc.)
     * - failureTimestamp: When payment failed (ISO-8601)
     * - traceId: Distributed trace ID
     *
     * @param event Purchase failure event details
     * @return Mono that completes when event is published
     */
    fun publishPurchaseFailed(event: PurchaseFailedEvent): Mono<Void>

    /**
     * Purchase failure event.
     */
    data class PurchaseFailedEvent(
        val purchaseId: UUID,
        val slotId: UUID,
        val userId: UUID,
        val productId: UUID,
        val productName: String,
        val failureReason: String,
        val failureTimestamp: Instant,
        val traceId: String
    )

    /**
     * Publish slot expiration warning event (5 minutes before expiration).
     *
     * Event: `product.slot.expiring-soon`
     * Consumers: Notification domain
     * Purpose: Send pre-expiration reminder to user
     *
     * **Event Schema** (Avro):
     * - slotId: UUID of the expiring slot
     * - userId: User whose slot is expiring
     * - productId: Product for which slot is expiring
     * - productName: Product name
     * - expirationTimestamp: When slot will expire (ISO-8601)
     * - minutesRemaining: Minutes until expiration (typically 5)
     * - traceId: Distributed trace ID
     *
     * @param event Slot expiration warning event details
     * @return Mono that completes when event is published
     */
    fun publishSlotExpiringSoon(event: SlotExpiringSoonEvent): Mono<Void>

    /**
     * Slot expiration warning event.
     */
    data class SlotExpiringSoonEvent(
        val slotId: UUID,
        val userId: UUID,
        val productId: UUID,
        val productName: String,
        val expirationTimestamp: Instant,
        val minutesRemaining: Long,
        val traceId: String
    )

    /**
     * Publish generic product event.
     *
     * Used for custom events not covered by specific methods.
     * Avoid using this method unless necessary - prefer specific event types.
     *
     * @param topic Kafka topic name
     * @param key Partition key (usually userId or productId)
     * @param event Event payload (must be Avro-serializable)
     * @param traceId Distributed trace ID
     * @return Mono that completes when event is published
     */
    fun publishEvent(
        topic: String,
        key: String,
        event: Any,
        traceId: String
    ): Mono<Void>

    /**
     * Publish multiple events in a batch.
     *
     * More efficient than publishing events one by one.
     * All events are published in a single Kafka transaction for atomicity.
     *
     * @param events List of events to publish
     * @return Mono that completes when all events are published
     */
    fun publishBatch(events: List<DomainEvent>): Mono<Void>

    /**
     * Generic domain event wrapper for batch publishing.
     */
    data class DomainEvent(
        val topic: String,
        val key: String,
        val payload: Any,
        val traceId: String,
        val timestamp: Instant = Instant.now()
    )

    companion object {
        /**
         * Standard Kafka topics for product domain events.
         */
        const val TOPIC_SLOT_ACQUIRED = "product.slot.acquired"
        const val TOPIC_SLOT_EXPIRED = "product.slot.expired"
        const val TOPIC_SLOT_EXPIRING_SOON = "product.slot.expiring-soon"
        const val TOPIC_PURCHASE_COMPLETED = "product.purchase.completed"
        const val TOPIC_PURCHASE_FAILED = "product.purchase.failed"

        /**
         * Event type constants for logging and monitoring.
         */
        const val EVENT_TYPE_SLOT_ACQUIRED = "SLOT_ACQUIRED"
        const val EVENT_TYPE_SLOT_EXPIRED = "SLOT_EXPIRED"
        const val EVENT_TYPE_SLOT_EXPIRING_SOON = "SLOT_EXPIRING_SOON"
        const val EVENT_TYPE_PURCHASE_COMPLETED = "PURCHASE_COMPLETED"
        const val EVENT_TYPE_PURCHASE_FAILED = "PURCHASE_FAILED"
    }
}
