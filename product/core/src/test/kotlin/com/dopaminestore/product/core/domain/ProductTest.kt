package com.dopaminestore.product.core.domain

import com.dopaminestore.product.core.domain.value.ProductStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Product aggregate root.
 *
 * Tests cover:
 * - Product creation with validation
 * - Stock management (increase/decrease)
 * - Status computation
 * - Update operations
 * - Invariant enforcement
 */
class ProductTest {

    private val testProductId = UUID.randomUUID()
    private val testCreatedBy = UUID.randomUUID()

    @Test
    fun `should create product with valid properties`() {
        val product = Product(
            id = testProductId,
            name = "iPhone 15 Pro",
            description = "Latest iPhone with A17 Pro chip",
            stock = 100,
            initialStock = 100,
            saleDate = Instant.now().plus(1, ChronoUnit.HOURS),
            price = BigDecimal("1500000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertEquals(testProductId, product.id)
        assertEquals("iPhone 15 Pro", product.name)
        assertEquals(100, product.stock)
    }

    @Test
    fun `should compute status as UPCOMING when before sale date`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 50,
            initialStock = 50,
            saleDate = Instant.now().plus(1, ChronoUnit.HOURS),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertEquals(ProductStatus.UPCOMING, product.computeStatus())
    }

    @Test
    fun `should compute status as ON_SALE when after sale date with stock`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 50,
            initialStock = 50,
            saleDate = Instant.now().minus(1, ChronoUnit.HOURS),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertEquals(ProductStatus.ON_SALE, product.computeStatus())
    }

    @Test
    fun `should compute status as SOLD_OUT when stock is zero`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 0,
            initialStock = 50,
            saleDate = Instant.now().minus(1, ChronoUnit.HOURS),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertEquals(ProductStatus.SOLD_OUT, product.computeStatus())
    }

    @Test
    fun `should decrease stock correctly`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 50,
            initialStock = 50,
            saleDate = Instant.now(),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val updatedProduct = product.decreaseStock(10)

        assertEquals(40, updatedProduct.stock)
        assertEquals(50, product.stock) // Original unchanged
    }

    @Test
    fun `should throw exception when decreasing stock by zero or negative`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 50,
            initialStock = 50,
            saleDate = Instant.now(),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertThrows<IllegalArgumentException> {
            product.decreaseStock(0)
        }

        assertThrows<IllegalArgumentException> {
            product.decreaseStock(-5)
        }
    }

    @Test
    fun `should throw exception when decreasing stock below zero`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 5,
            initialStock = 50,
            saleDate = Instant.now(),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertThrows<IllegalArgumentException> {
            product.decreaseStock(10)
        }
    }

    @Test
    fun `should increase stock correctly`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 30,
            initialStock = 50,
            saleDate = Instant.now(),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val updatedProduct = product.increaseStock(10)

        assertEquals(40, updatedProduct.stock)
        assertEquals(30, product.stock) // Original unchanged
    }

    @Test
    fun `should throw exception when increasing stock above initial stock`() {
        val product = Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = 45,
            initialStock = 50,
            saleDate = Instant.now(),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertThrows<IllegalArgumentException> {
            product.increaseStock(10)
        }
    }

    @Test
    fun `should update product info`() {
        val product = Product(
            id = testProductId,
            name = "Old Name",
            description = "Old Description",
            stock = 50,
            initialStock = 50,
            saleDate = Instant.now(),
            price = BigDecimal("10000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val updated = product.updateInfo(
            name = "New Name",
            description = "New Description",
            price = BigDecimal("15000")
        )

        assertEquals("New Name", updated.name)
        assertEquals("New Description", updated.description)
        assertEquals(BigDecimal("15000"), updated.price)
    }

    @Test
    fun `should check if product is available for purchase`() {
        val onSaleProduct = Product(
            id = testProductId,
            name = "Test",
            description = "Test",
            stock = 10,
            initialStock = 10,
            saleDate = Instant.now().minus(1, ChronoUnit.HOURS),
            price = BigDecimal("1000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertTrue(onSaleProduct.isAvailableForPurchase())

        val upcomingProduct = onSaleProduct.copy(saleDate = Instant.now().plus(1, ChronoUnit.HOURS))
        assertFalse(upcomingProduct.isAvailableForPurchase())

        val soldOutProduct = onSaleProduct.copy(stock = 0)
        assertFalse(soldOutProduct.isAvailableForPurchase())
    }

    @Test
    fun `should check if product can be deleted`() {
        val product = Product(
            id = testProductId,
            name = "Test",
            description = "Test",
            stock = 10,
            initialStock = 10,
            saleDate = Instant.now(),
            price = BigDecimal("1000"),
            createdBy = testCreatedBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertTrue(product.canDelete(activeSlotCount = 0))
        assertFalse(product.canDelete(activeSlotCount = 1))
    }

    @Test
    fun `should create product using factory method`() {
        val product = Product.create(
            name = "New Product",
            description = "Product Description",
            stock = 100,
            saleDate = Instant.now().plus(2, ChronoUnit.HOURS),
            price = BigDecimal("50000"),
            createdBy = testCreatedBy
        )

        assertEquals("New Product", product.name)
        assertEquals(100, product.stock)
        assertEquals(100, product.initialStock)
    }

    @Test
    fun `should throw exception when creating product with past sale date`() {
        assertThrows<IllegalArgumentException> {
            Product.create(
                name = "Test",
                description = "Test",
                stock = 10,
                saleDate = Instant.now().minus(1, ChronoUnit.HOURS),
                price = BigDecimal("1000"),
                createdBy = testCreatedBy
            )
        }
    }
}
