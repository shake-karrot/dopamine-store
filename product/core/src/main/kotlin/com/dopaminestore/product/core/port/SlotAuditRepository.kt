package com.dopaminestore.product.core.port

import com.dopaminestore.product.core.domain.value.SlotStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Repository port for slot audit log persistence.
 *
 * This port defines the contract for audit trail operations.
 * Critical for fairness verification and compliance requirements.
 *
 * All slot state transitions must be logged for:
 * - Fairness verification (arrival-time ordering)
 * - Regulatory compliance
 * - Debugging and troubleshooting
 * - Business analytics
 */
interface SlotAuditRepository {

    /**
     * Audit log entry for slot state transitions.
     */
    data class AuditLogEntry(
        val id: UUID,
        val slotId: UUID,
        val eventType: String,
        val oldStatus: SlotStatus?,
        val newStatus: SlotStatus,
        val timestamp: Instant,
        val traceId: String,
        val metadata: Map<String, Any>? = null
    )

    /**
     * Log a slot state transition.
     *
     * @param entry Audit log entry
     * @return Saved entry with generated ID
     */
    fun log(entry: AuditLogEntry): Mono<AuditLogEntry>

    /**
     * Log slot acquisition event.
     *
     * Event type: SLOT_ACQUIRED
     * Records: request arrival timestamp, user ID, product ID, trace ID
     *
     * @param slotId Slot ID
     * @param userId User ID
     * @param productId Product ID
     * @param acquisitionTimestamp When slot was acquired (arrival time)
     * @param traceId Trace ID
     * @param metadata Additional metadata (e.g., queue position, stock remaining)
     * @return Saved audit entry
     */
    fun logAcquisition(
        slotId: UUID,
        userId: UUID,
        productId: UUID,
        acquisitionTimestamp: Instant,
        traceId: String,
        metadata: Map<String, Any>? = null
    ): Mono<AuditLogEntry>

    /**
     * Log slot expiration event.
     *
     * Event type: SLOT_EXPIRED
     * Records: expiration timestamp, reclaim status, trace ID
     *
     * @param slotId Slot ID
     * @param expirationTimestamp When slot expired
     * @param reclaimStatus Reason for expiration (AUTO_EXPIRED or MANUAL_RECLAIMED)
     * @param traceId Trace ID
     * @param metadata Additional metadata (e.g., admin user for manual reclaim)
     * @return Saved audit entry
     */
    fun logExpiration(
        slotId: UUID,
        expirationTimestamp: Instant,
        reclaimStatus: String,
        traceId: String,
        metadata: Map<String, Any>? = null
    ): Mono<AuditLogEntry>

    /**
     * Log slot completion event (payment successful).
     *
     * Event type: SLOT_COMPLETED
     * Records: completion timestamp, payment ID, trace ID
     *
     * @param slotId Slot ID
     * @param completionTimestamp When payment was completed
     * @param paymentId Payment transaction ID
     * @param traceId Trace ID
     * @param metadata Additional metadata (e.g., payment method, amount)
     * @return Saved audit entry
     */
    fun logCompletion(
        slotId: UUID,
        completionTimestamp: Instant,
        paymentId: String,
        traceId: String,
        metadata: Map<String, Any>? = null
    ): Mono<AuditLogEntry>

    /**
     * Log generic slot event.
     *
     * Used for custom events not covered by specific methods.
     *
     * @param slotId Slot ID
     * @param eventType Event type (e.g., SLOT_REQUESTED, SLOT_VIEWED)
     * @param oldStatus Previous slot status (nullable for non-transition events)
     * @param newStatus Current slot status
     * @param traceId Trace ID
     * @param metadata Additional metadata
     * @return Saved audit entry
     */
    fun logEvent(
        slotId: UUID,
        eventType: String,
        oldStatus: SlotStatus?,
        newStatus: SlotStatus,
        traceId: String,
        metadata: Map<String, Any>? = null
    ): Mono<AuditLogEntry>

    /**
     * Find all audit logs for a slot.
     *
     * Returns complete audit trail ordered by timestamp ascending.
     * Used for debugging and fairness verification.
     *
     * @param slotId Slot ID
     * @return Audit logs for the slot
     */
    fun findBySlotId(slotId: UUID): Flux<AuditLogEntry>

    /**
     * Find audit logs by event type.
     *
     * @param eventType Event type (e.g., SLOT_ACQUIRED, SLOT_EXPIRED)
     * @return Audit logs matching the event type
     */
    fun findByEventType(eventType: String): Flux<AuditLogEntry>

    /**
     * Find audit logs in a time range.
     *
     * Used for reporting and analytics.
     *
     * @param startTime Start of time range (inclusive)
     * @param endTime End of time range (inclusive)
     * @return Audit logs in the time range
     */
    fun findByTimestampBetween(startTime: Instant, endTime: Instant): Flux<AuditLogEntry>

    /**
     * Find audit logs by trace ID.
     *
     * Returns all audit events associated with a specific request trace.
     * Critical for distributed tracing and debugging.
     *
     * @param traceId Trace ID
     * @return Audit logs with the given trace ID
     */
    fun findByTraceId(traceId: String): Flux<AuditLogEntry>

    /**
     * Verify arrival-time ordering for fairness.
     *
     * For a given product, verify that slot acquisitions respect strict arrival-time order.
     * Used for compliance verification and fairness audits.
     *
     * Returns violations where later arrivals got slots before earlier arrivals.
     *
     * @param productId Product ID
     * @param startTime Start of time range to check
     * @param endTime End of time range to check
     * @return Pairs of (earlierArrival, laterArrival) where ordering was violated
     */
    fun verifyArrivalTimeOrdering(
        productId: UUID,
        startTime: Instant,
        endTime: Instant
    ): Flux<Pair<AuditLogEntry, AuditLogEntry>>

    /**
     * Count audit logs by event type in a time range.
     *
     * Used for analytics and reporting.
     *
     * @param eventType Event type
     * @param startTime Start of time range
     * @param endTime End of time range
     * @return Number of events
     */
    fun countByEventTypeAndTimestampBetween(
        eventType: String,
        startTime: Instant,
        endTime: Instant
    ): Mono<Long>

    /**
     * Delete old audit logs (data retention policy).
     *
     * Used for cleanup of logs older than retention period.
     * Consider archiving before deletion for long-term compliance.
     *
     * @param olderThan Delete logs older than this timestamp
     * @return Number of deleted entries
     */
    fun deleteOlderThan(olderThan: Instant): Mono<Long>

    companion object {
        /**
         * Standard event types for slot lifecycle.
         */
        const val EVENT_SLOT_REQUESTED = "SLOT_REQUESTED"
        const val EVENT_SLOT_ACQUIRED = "SLOT_ACQUIRED"
        const val EVENT_SLOT_EXPIRED = "SLOT_EXPIRED"
        const val EVENT_SLOT_COMPLETED = "SLOT_COMPLETED"
        const val EVENT_SLOT_VIEWED = "SLOT_VIEWED"
        const val EVENT_PAYMENT_INITIATED = "PAYMENT_INITIATED"
        const val EVENT_PAYMENT_FAILED = "PAYMENT_FAILED"
    }
}
