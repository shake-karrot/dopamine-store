package com.dopaminestore.product.core.domain.value

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for SlotStatus value object.
 *
 * Tests cover:
 * - State transition validation (ACTIVE → EXPIRED, ACTIVE → COMPLETED)
 * - Invalid transitions
 * - Terminal state detection
 * - Helper methods (canPay, isExpired, isCompleted, isTerminal)
 */
class SlotStatusTest {

    @Test
    fun `should allow transition from ACTIVE to EXPIRED`() {
        val currentStatus = SlotStatus.ACTIVE

        currentStatus.validateTransition(SlotStatus.EXPIRED)

        // No exception thrown means validation passed
    }

    @Test
    fun `should allow transition from ACTIVE to COMPLETED`() {
        val currentStatus = SlotStatus.ACTIVE

        currentStatus.validateTransition(SlotStatus.COMPLETED)

        // No exception thrown means validation passed
    }

    @Test
    fun `should not allow transition from ACTIVE to ACTIVE`() {
        val currentStatus = SlotStatus.ACTIVE

        val exception = assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(SlotStatus.ACTIVE)
        }

        assertTrue(exception.message!!.contains("Invalid slot status transition"))
    }

    @Test
    fun `should not allow transition from EXPIRED to any state`() {
        val currentStatus = SlotStatus.EXPIRED

        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(SlotStatus.ACTIVE)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(SlotStatus.COMPLETED)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(SlotStatus.EXPIRED)
        }
    }

    @Test
    fun `should not allow transition from COMPLETED to any state`() {
        val currentStatus = SlotStatus.COMPLETED

        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(SlotStatus.ACTIVE)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(SlotStatus.EXPIRED)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(SlotStatus.COMPLETED)
        }
    }

    @Test
    fun `should identify terminal statuses correctly`() {
        assertTrue(SlotStatus.EXPIRED.isTerminal())
        assertTrue(SlotStatus.COMPLETED.isTerminal())
        assertFalse(SlotStatus.ACTIVE.isTerminal())
    }

    @Test
    fun `should check if slot can pay`() {
        assertTrue(SlotStatus.ACTIVE.canPay())
        assertFalse(SlotStatus.EXPIRED.canPay())
        assertFalse(SlotStatus.COMPLETED.canPay())
    }

    @Test
    fun `should check if slot is expired`() {
        assertTrue(SlotStatus.EXPIRED.isExpired())
        assertFalse(SlotStatus.ACTIVE.isExpired())
        assertFalse(SlotStatus.COMPLETED.isExpired())
    }

    @Test
    fun `should check if slot is completed`() {
        assertTrue(SlotStatus.COMPLETED.isCompleted())
        assertFalse(SlotStatus.ACTIVE.isCompleted())
        assertFalse(SlotStatus.EXPIRED.isCompleted())
    }

    @Test
    fun `should have all three status types`() {
        val allStatuses = SlotStatus.values()

        assertEquals(3, allStatuses.size)
        assertEquals(SlotStatus.ACTIVE, allStatuses[0])
        assertEquals(SlotStatus.EXPIRED, allStatuses[1])
        assertEquals(SlotStatus.COMPLETED, allStatuses[2])
    }
}
