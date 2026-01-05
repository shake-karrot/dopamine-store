package com.dopaminestore.product.core.domain.value

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for PaymentStatus value object.
 *
 * Tests cover:
 * - State transition validation (PENDING → SUCCESS, PENDING → FAILED)
 * - Invalid transitions
 * - Final state detection
 * - Helper methods (isPending, isSuccess, isFailed, isFinal)
 */
class PaymentStatusTest {

    @Test
    fun `should allow transition from PENDING to SUCCESS`() {
        val currentStatus = PaymentStatus.PENDING

        currentStatus.validateTransition(PaymentStatus.SUCCESS)

        // No exception thrown means validation passed
    }

    @Test
    fun `should allow transition from PENDING to FAILED`() {
        val currentStatus = PaymentStatus.PENDING

        currentStatus.validateTransition(PaymentStatus.FAILED)

        // No exception thrown means validation passed
    }

    @Test
    fun `should not allow transition from PENDING to PENDING`() {
        val currentStatus = PaymentStatus.PENDING

        val exception = assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(PaymentStatus.PENDING)
        }

        assertTrue(exception.message!!.contains("Invalid payment status transition"))
    }

    @Test
    fun `should not allow transition from SUCCESS to any state`() {
        val currentStatus = PaymentStatus.SUCCESS

        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(PaymentStatus.PENDING)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(PaymentStatus.FAILED)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(PaymentStatus.SUCCESS)
        }
    }

    @Test
    fun `should not allow transition from FAILED to any state`() {
        val currentStatus = PaymentStatus.FAILED

        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(PaymentStatus.PENDING)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(PaymentStatus.SUCCESS)
        }
        assertThrows<IllegalArgumentException> {
            currentStatus.validateTransition(PaymentStatus.FAILED)
        }
    }

    @Test
    fun `should identify final statuses correctly`() {
        assertTrue(PaymentStatus.SUCCESS.isFinal())
        assertTrue(PaymentStatus.FAILED.isFinal())
        assertFalse(PaymentStatus.PENDING.isFinal())
    }

    @Test
    fun `should check if payment is pending`() {
        assertTrue(PaymentStatus.PENDING.isPending())
        assertFalse(PaymentStatus.SUCCESS.isPending())
        assertFalse(PaymentStatus.FAILED.isPending())
    }

    @Test
    fun `should check if payment is successful`() {
        assertTrue(PaymentStatus.SUCCESS.isSuccess())
        assertFalse(PaymentStatus.PENDING.isSuccess())
        assertFalse(PaymentStatus.FAILED.isSuccess())
    }

    @Test
    fun `should check if payment is failed`() {
        assertTrue(PaymentStatus.FAILED.isFailed())
        assertFalse(PaymentStatus.PENDING.isFailed())
        assertFalse(PaymentStatus.SUCCESS.isFailed())
    }

    @Test
    fun `should have all three status types`() {
        val allStatuses = PaymentStatus.values()

        assertEquals(3, allStatuses.size)
        assertEquals(PaymentStatus.PENDING, allStatuses[0])
        assertEquals(PaymentStatus.SUCCESS, allStatuses[1])
        assertEquals(PaymentStatus.FAILED, allStatuses[2])
    }
}
