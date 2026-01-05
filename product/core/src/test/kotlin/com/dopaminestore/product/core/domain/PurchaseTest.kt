package com.dopaminestore.product.core.domain

import com.dopaminestore.product.core.domain.value.Money
import com.dopaminestore.product.core.domain.value.PaymentMethod
import com.dopaminestore.product.core.domain.value.PaymentStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Purchase aggregate root.
 *
 * Tests cover:
 * - Purchase creation and validation
 * - Payment status transitions
 * - Success/failure marking
 * - Payment duration calculations
 * - Idempotency key handling
 */
class PurchaseTest {

    private val testPurchaseSlotId = UUID.randomUUID()
    private val testUserId = UUID.randomUUID()
    private val testProductId = UUID.randomUUID()
    private val testIdempotencyKey = UUID.randomUUID()
    private val testPaymentId = "pay_test_123"
    private val testTraceId = "trace-456"

    @Test
    fun `should initiate purchase with PENDING status`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        assertEquals(PaymentStatus.PENDING, purchase.paymentStatus)
        assertNull(purchase.confirmationTimestamp)
        assertNull(purchase.failureReason)
    }

    @Test
    fun `should mark payment as successful`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        val confirmationTime = Instant.now()
        val successfulPurchase = purchase.markSuccess(confirmationTime)

        assertEquals(PaymentStatus.SUCCESS, successfulPurchase.paymentStatus)
        assertEquals(confirmationTime, successfulPurchase.confirmationTimestamp)
        assertNull(successfulPurchase.failureReason)
        assertEquals(PaymentStatus.PENDING, purchase.paymentStatus) // Original unchanged
    }

    @Test
    fun `should mark payment as failed with reason`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        val failedPurchase = purchase.markFailed("Card declined")

        assertEquals(PaymentStatus.FAILED, failedPurchase.paymentStatus)
        assertEquals("Card declined", failedPurchase.failureReason)
        assertNull(failedPurchase.confirmationTimestamp)
    }

    @Test
    fun `should throw exception when marking SUCCESS as FAILED`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        val successful = purchase.markSuccess()

        assertThrows<IllegalArgumentException> {
            successful.markFailed("Cannot fail")
        }
    }

    @Test
    fun `should throw exception when marking FAILED as SUCCESS`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        val failed = purchase.markFailed("Card declined")

        assertThrows<IllegalArgumentException> {
            failed.markSuccess()
        }
    }

    @Test
    fun `should check payment status helpers`() {
        val pending = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        assertTrue(pending.isPending())
        assertFalse(pending.isSuccess())
        assertFalse(pending.isFailed())

        val successful = pending.markSuccess()
        assertFalse(successful.isPending())
        assertTrue(successful.isSuccess())
        assertFalse(successful.isFailed())

        val failed = pending.markFailed("Test failure")
        assertFalse(failed.isPending())
        assertFalse(failed.isSuccess())
        assertTrue(failed.isFailed())
    }

    @Test
    fun `should check if purchase can be retried`() {
        val pending = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        assertFalse(pending.canRetry())

        val successful = pending.markSuccess()
        assertFalse(successful.canRetry())

        val failed = pending.markFailed("Card declined")
        assertTrue(failed.canRetry())
    }

    @Test
    fun `should calculate payment duration for successful payment`() {
        val createdAt = Instant.now().minus(2, ChronoUnit.MINUTES)
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId,
            createdAt = createdAt
        )

        val confirmationTime = createdAt.plus(2, ChronoUnit.MINUTES)
        val successful = purchase.markSuccess(confirmationTime)

        val duration = successful.getPaymentDuration()
        assertNotNull(duration)
        assertEquals(120000L, duration) // 2 minutes in milliseconds
    }

    @Test
    fun `should return null payment duration for pending purchase`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        assertNull(purchase.getPaymentDuration())
    }

    @Test
    fun `should enforce positive amount`() {
        assertThrows<IllegalArgumentException> {
            Purchase.initiate(
                purchaseSlotId = testPurchaseSlotId,
                userId = testUserId,
                productId = testProductId,
                paymentId = testPaymentId,
                idempotencyKey = testIdempotencyKey,
                amount = Money.zero(),
                paymentMethod = PaymentMethod.CARD,
                traceId = testTraceId
            )
        }
    }

    @Test
    fun `should enforce failure reason when marking failed`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        assertThrows<IllegalArgumentException> {
            purchase.markFailed("")
        }
    }

    @Test
    fun `should enforce failure reason max length`() {
        val purchase = Purchase.initiate(
            purchaseSlotId = testPurchaseSlotId,
            userId = testUserId,
            productId = testProductId,
            paymentId = testPaymentId,
            idempotencyKey = testIdempotencyKey,
            amount = Money(BigDecimal("100000")),
            paymentMethod = PaymentMethod.CARD,
            traceId = testTraceId
        )

        val longReason = "x".repeat(501)

        assertThrows<IllegalArgumentException> {
            purchase.markFailed(longReason)
        }
    }

    @Test
    fun `should have payment timeout constant`() {
        assertEquals(300000L, Purchase.PAYMENT_TIMEOUT_MILLIS) // 5 minutes
    }

    @Test
    fun `should support all payment methods`() {
        val methods = listOf(PaymentMethod.CARD, PaymentMethod.BANK_TRANSFER, PaymentMethod.WALLET)

        methods.forEach { method ->
            val purchase = Purchase.initiate(
                purchaseSlotId = testPurchaseSlotId,
                userId = testUserId,
                productId = testProductId,
                paymentId = testPaymentId,
                idempotencyKey = UUID.randomUUID(),
                amount = Money(BigDecimal("100000")),
                paymentMethod = method,
                traceId = testTraceId
            )

            assertEquals(method, purchase.paymentMethod)
        }
    }
}
