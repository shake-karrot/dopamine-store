package com.dopaminestore.product.core.domain

import com.dopaminestore.product.core.domain.value.ReclaimStatus
import com.dopaminestore.product.core.domain.value.SlotStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PurchaseSlot aggregate root.
 *
 * Tests cover:
 * - Slot creation and validation
 * - Expiration detection
 * - State transitions
 * - Payment eligibility checks
 * - Remaining time calculations
 */
class PurchaseSlotTest {

    private val testProductId = UUID.randomUUID()
    private val testUserId = UUID.randomUUID()
    private val testTraceId = "trace-123"

    @Test
    fun `should acquire slot with 30-minute expiration`() {
        val acquisitionTime = Instant.now()
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        assertEquals(SlotStatus.ACTIVE, slot.status)
        assertEquals(acquisitionTime.plus(Duration.ofMinutes(30)), slot.expirationTimestamp)
        assertNull(slot.reclaimStatus)
    }

    @Test
    fun `should detect slot as not expired when before expiration time`() {
        val acquisitionTime = Instant.now()
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        val now = acquisitionTime.plus(20, ChronoUnit.MINUTES)
        assertFalse(slot.isExpiredByTime(now))
    }

    @Test
    fun `should detect slot as expired at expiration time`() {
        val acquisitionTime = Instant.now()
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        val expirationTime = slot.expirationTimestamp
        assertTrue(slot.isExpiredByTime(expirationTime))
    }

    @Test
    fun `should detect slot as expired after expiration time`() {
        val acquisitionTime = Instant.now().minus(31, ChronoUnit.MINUTES)
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        assertTrue(slot.isExpiredByTime())
    }

    @Test
    fun `should expire slot with AUTO_EXPIRED reason`() {
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId
        )

        val expiredSlot = slot.expire(ReclaimStatus.AUTO_EXPIRED)

        assertEquals(SlotStatus.EXPIRED, expiredSlot.status)
        assertEquals(ReclaimStatus.AUTO_EXPIRED, expiredSlot.reclaimStatus)
        assertEquals(SlotStatus.ACTIVE, slot.status) // Original unchanged
    }

    @Test
    fun `should expire slot with MANUAL_RECLAIMED reason`() {
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId
        )

        val expiredSlot = slot.expire(ReclaimStatus.MANUAL_RECLAIMED)

        assertEquals(SlotStatus.EXPIRED, expiredSlot.status)
        assertEquals(ReclaimStatus.MANUAL_RECLAIMED, expiredSlot.reclaimStatus)
    }

    @Test
    fun `should throw exception when expiring already expired slot`() {
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId
        )

        val expired = slot.expire(ReclaimStatus.AUTO_EXPIRED)

        assertThrows<IllegalArgumentException> {
            expired.expire(ReclaimStatus.AUTO_EXPIRED)
        }
    }

    @Test
    fun `should complete slot successfully`() {
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId
        )

        val completedSlot = slot.complete()

        assertEquals(SlotStatus.COMPLETED, completedSlot.status)
        assertNull(completedSlot.reclaimStatus)
        assertEquals(SlotStatus.ACTIVE, slot.status) // Original unchanged
    }

    @Test
    fun `should throw exception when completing expired slot by time`() {
        val acquisitionTime = Instant.now().minus(31, ChronoUnit.MINUTES)
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        assertThrows<IllegalArgumentException> {
            slot.complete()
        }
    }

    @Test
    fun `should throw exception when completing already completed slot`() {
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId
        )

        val completed = slot.complete()

        assertThrows<IllegalArgumentException> {
            completed.complete()
        }
    }

    @Test
    fun `should calculate remaining time correctly`() {
        val acquisitionTime = Instant.now()
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        val now = acquisitionTime.plus(10, ChronoUnit.MINUTES)
        val remaining = slot.remainingTime(now)

        assertEquals(Duration.ofMinutes(20), remaining)
    }

    @Test
    fun `should calculate remaining seconds correctly`() {
        val acquisitionTime = Instant.now()
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        val now = acquisitionTime.plus(10, ChronoUnit.MINUTES)
        val remainingSeconds = slot.remainingSeconds(now)

        assertEquals(1200L, remainingSeconds) // 20 minutes = 1200 seconds
    }

    @Test
    fun `should return zero remaining seconds when expired`() {
        val acquisitionTime = Instant.now().minus(31, ChronoUnit.MINUTES)
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = acquisitionTime
        )

        val remainingSeconds = slot.remainingSeconds()

        assertEquals(0L, remainingSeconds)
    }

    @Test
    fun `should check if payment can be processed`() {
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId
        )

        assertTrue(slot.canProcessPayment())

        val expired = slot.expire(ReclaimStatus.AUTO_EXPIRED)
        assertFalse(expired.canProcessPayment())

        val completed = slot.complete()
        assertFalse(completed.canProcessPayment())
    }

    @Test
    fun `should check slot status helpers`() {
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId
        )

        assertTrue(slot.isActive())
        assertFalse(slot.isExpired())
        assertFalse(slot.isCompleted())

        val expired = slot.expire(ReclaimStatus.AUTO_EXPIRED)
        assertFalse(expired.isActive())
        assertTrue(expired.isExpired())
        assertFalse(expired.isCompleted())

        val completed = slot.complete()
        assertFalse(completed.isActive())
        assertFalse(completed.isExpired())
        assertTrue(completed.isCompleted())
    }

    @Test
    fun `should enforce exactly 30-minute expiration`() {
        val acquisitionTime = Instant.now()
        val wrongExpiration = acquisitionTime.plus(29, ChronoUnit.MINUTES)

        assertThrows<IllegalArgumentException> {
            PurchaseSlot(
                id = UUID.randomUUID(),
                productId = testProductId,
                userId = testUserId,
                acquisitionTimestamp = acquisitionTime,
                expirationTimestamp = wrongExpiration,
                status = SlotStatus.ACTIVE,
                reclaimStatus = null,
                traceId = testTraceId
            )
        }
    }

    @Test
    fun `should enforce reclaim status consistency`() {
        val acquisitionTime = Instant.now()

        // EXPIRED slot requires reclaimStatus
        assertThrows<IllegalArgumentException> {
            PurchaseSlot(
                id = UUID.randomUUID(),
                productId = testProductId,
                userId = testUserId,
                acquisitionTimestamp = acquisitionTime,
                expirationTimestamp = acquisitionTime.plus(Duration.ofMinutes(30)),
                status = SlotStatus.EXPIRED,
                reclaimStatus = null,
                traceId = testTraceId
            )
        }

        // ACTIVE slot cannot have reclaimStatus
        assertThrows<IllegalArgumentException> {
            PurchaseSlot(
                id = UUID.randomUUID(),
                productId = testProductId,
                userId = testUserId,
                acquisitionTimestamp = acquisitionTime,
                expirationTimestamp = acquisitionTime.plus(Duration.ofMinutes(30)),
                status = SlotStatus.ACTIVE,
                reclaimStatus = ReclaimStatus.AUTO_EXPIRED,
                traceId = testTraceId
            )
        }
    }
}
