package com.dopaminestore.product.core.domain.value

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ProductStatus value object.
 *
 * Tests cover:
 * - Status computation based on sale date and stock
 * - Time-based transitions (UPCOMING → ON_SALE → SOLD_OUT)
 * - Helper methods (isAvailableForPurchase, isSoldOut, isUpcoming)
 */
class ProductStatusTest {

    @Test
    fun `should compute UPCOMING when current time is before sale date`() {
        val saleDate = Instant.now().plus(1, ChronoUnit.HOURS)
        val stock = 10
        val now = Instant.now()

        val status = ProductStatus.compute(saleDate, stock, now)

        assertEquals(ProductStatus.UPCOMING, status)
    }

    @Test
    fun `should compute ON_SALE when current time is at sale date and stock available`() {
        val saleDate = Instant.now()
        val stock = 10
        val now = saleDate

        val status = ProductStatus.compute(saleDate, stock, now)

        assertEquals(ProductStatus.ON_SALE, status)
    }

    @Test
    fun `should compute ON_SALE when current time is after sale date and stock available`() {
        val saleDate = Instant.now().minus(1, ChronoUnit.HOURS)
        val stock = 10
        val now = Instant.now()

        val status = ProductStatus.compute(saleDate, stock, now)

        assertEquals(ProductStatus.ON_SALE, status)
    }

    @Test
    fun `should compute SOLD_OUT when stock is zero regardless of sale date`() {
        val saleDate = Instant.now().minus(1, ChronoUnit.HOURS)
        val stock = 0
        val now = Instant.now()

        val status = ProductStatus.compute(saleDate, stock, now)

        assertEquals(ProductStatus.SOLD_OUT, status)
    }

    @Test
    fun `should compute SOLD_OUT when stock is negative`() {
        val saleDate = Instant.now().minus(1, ChronoUnit.HOURS)
        val stock = -1
        val now = Instant.now()

        val status = ProductStatus.compute(saleDate, stock, now)

        assertEquals(ProductStatus.SOLD_OUT, status)
    }

    @Test
    fun `should prioritize SOLD_OUT over UPCOMING when stock is zero before sale`() {
        val saleDate = Instant.now().plus(1, ChronoUnit.HOURS)
        val stock = 0
        val now = Instant.now()

        val status = ProductStatus.compute(saleDate, stock, now)

        assertEquals(ProductStatus.SOLD_OUT, status)
    }

    @Test
    fun `should check if status is available for purchase`() {
        assertTrue(ProductStatus.ON_SALE.isAvailableForPurchase())
        assertFalse(ProductStatus.UPCOMING.isAvailableForPurchase())
        assertFalse(ProductStatus.SOLD_OUT.isAvailableForPurchase())
    }

    @Test
    fun `should check if status is sold out`() {
        assertTrue(ProductStatus.SOLD_OUT.isSoldOut())
        assertFalse(ProductStatus.UPCOMING.isSoldOut())
        assertFalse(ProductStatus.ON_SALE.isSoldOut())
    }

    @Test
    fun `should check if status is upcoming`() {
        assertTrue(ProductStatus.UPCOMING.isUpcoming())
        assertFalse(ProductStatus.ON_SALE.isUpcoming())
        assertFalse(ProductStatus.SOLD_OUT.isUpcoming())
    }

    @Test
    fun `should have all three status types`() {
        val allStatuses = ProductStatus.values()

        assertEquals(3, allStatuses.size)
        assertEquals(ProductStatus.UPCOMING, allStatuses[0])
        assertEquals(ProductStatus.ON_SALE, allStatuses[1])
        assertEquals(ProductStatus.SOLD_OUT, allStatuses[2])
    }
}
