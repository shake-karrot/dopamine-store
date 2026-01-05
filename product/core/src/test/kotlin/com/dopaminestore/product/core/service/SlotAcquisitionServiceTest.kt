package com.dopaminestore.product.core.service

import com.dopaminestore.product.core.domain.Product
import com.dopaminestore.product.core.domain.PurchaseSlot
import com.dopaminestore.product.core.domain.value.ProductStatus
import com.dopaminestore.product.core.domain.value.SlotStatus
import com.dopaminestore.product.core.port.*
import com.dopaminestore.product.core.usecase.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for SlotAcquisitionService.
 *
 * Tests cover:
 * - Happy path: successful slot acquisition
 * - Product validation: not found, not available (UPCOMING, SOLD_OUT)
 * - Duplicate slot prevention
 * - Out of stock handling
 * - Event publishing
 * - Error propagation
 */
class SlotAcquisitionServiceTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var slotRepository: PurchaseSlotRepository
    private lateinit var slotAuditRepository: SlotAuditRepository
    private lateinit var redisSlotCache: RedisSlotCache
    private lateinit var eventPublisher: EventPublisher
    private lateinit var service: SlotAcquisitionService

    private val testUserId = UUID.randomUUID()
    private val testProductId = UUID.randomUUID()
    private val testTraceId = "test-trace-123"
    private val testArrivalTimestamp = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        productRepository = mock()
        slotRepository = mock()
        slotAuditRepository = mock()
        redisSlotCache = mock()
        eventPublisher = mock()

        service = SlotAcquisitionService(
            productRepository = productRepository,
            slotRepository = slotRepository,
            slotAuditRepository = slotAuditRepository,
            redisSlotCache = redisSlotCache,
            eventPublisher = eventPublisher
        )
    }

    @Test
    fun `should successfully acquire slot when all conditions are met`() {
        // Given: Valid product, no duplicate, stock available
        val product = createTestProduct(stock = 100)
        val acquisitionResult = RedisSlotCache.AcquisitionResult(
            success = true,
            queuePosition = 1L,
            remainingStock = 99,
            timestamp = Instant.ofEpochMilli(testArrivalTimestamp)
        )
        val slot = PurchaseSlot.acquire(
            productId = testProductId,
            userId = testUserId,
            traceId = testTraceId,
            acquisitionTime = Instant.ofEpochMilli(testArrivalTimestamp)
        )
        val auditEntry = SlotAuditRepository.AuditLogEntry(
            id = UUID.randomUUID(),
            slotId = slot.id,
            eventType = "SLOT_ACQUIRED",
            oldStatus = null,
            newStatus = SlotStatus.ACTIVE,
            timestamp = Instant.now(),
            traceId = testTraceId,
            metadata = emptyMap()
        )

        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(product))
        whenever(slotRepository.hasActiveSlot(testUserId, testProductId)).thenReturn(Mono.just(false))
        whenever(redisSlotCache.acquireSlot(testUserId, testProductId, testArrivalTimestamp, testTraceId))
            .thenReturn(Mono.just(acquisitionResult))
        whenever(slotRepository.save(any())).thenAnswer { Mono.just(it.arguments[0] as PurchaseSlot) }
        whenever(slotAuditRepository.logAcquisition(any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(auditEntry))
        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(product))
        whenever(eventPublisher.publishSlotAcquired(any())).thenReturn(Mono.empty())

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then
        StepVerifier.create(service.acquireSlot(command))
            .assertNext { acquiredSlot ->
                assertEquals(testUserId, acquiredSlot.userId)
                assertEquals(testProductId, acquiredSlot.productId)
                assertEquals(SlotStatus.ACTIVE, acquiredSlot.status)
                assertNotNull(acquiredSlot.id)
            }
            .verifyComplete()

        // Verify all interactions
        verify(productRepository, times(2)).findById(testProductId)
        verify(slotRepository).hasActiveSlot(testUserId, testProductId)
        verify(redisSlotCache).acquireSlot(testUserId, testProductId, testArrivalTimestamp, testTraceId)
        verify(slotRepository).save(any())
        verify(slotAuditRepository).logAcquisition(any(), any(), any(), any(), any(), any())
        verify(eventPublisher).publishSlotAcquired(any())
    }

    @Test
    fun `should fail when product does not exist`() {
        // Given: Product not found
        whenever(productRepository.findById(testProductId)).thenReturn(Mono.empty())

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then
        StepVerifier.create(service.acquireSlot(command))
            .expectErrorMatches { error ->
                error is ProductNotFoundException &&
                        error.productId == testProductId &&
                        error.traceId == testTraceId
            }
            .verify()

        verify(productRepository).findById(testProductId)
        verifyNoInteractions(slotRepository, redisSlotCache, eventPublisher)
    }

    @Test
    fun `should fail when product is UPCOMING`() {
        // Given: Product sale date is in the future
        val futureProduct = createTestProduct(
            saleDate = Instant.now().plus(1, ChronoUnit.HOURS),
            stock = 100
        )

        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(futureProduct))

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then
        StepVerifier.create(service.acquireSlot(command))
            .expectErrorMatches { error ->
                error is ProductNotAvailableException &&
                        error.productId == testProductId &&
                        error.status == ProductStatus.UPCOMING &&
                        error.traceId == testTraceId
            }
            .verify()

        verify(productRepository).findById(testProductId)
        verifyNoInteractions(slotRepository, redisSlotCache, eventPublisher)
    }

    @Test
    fun `should fail when product is SOLD_OUT`() {
        // Given: Product stock is zero
        val soldOutProduct = createTestProduct(stock = 0)

        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(soldOutProduct))

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then
        StepVerifier.create(service.acquireSlot(command))
            .expectErrorMatches { error ->
                error is ProductNotAvailableException &&
                        error.productId == testProductId &&
                        error.status == ProductStatus.SOLD_OUT &&
                        error.traceId == testTraceId
            }
            .verify()

        verify(productRepository).findById(testProductId)
        verifyNoInteractions(slotRepository, redisSlotCache, eventPublisher)
    }

    @Test
    fun `should fail when user already has active slot`() {
        // Given: User has active slot
        val product = createTestProduct(stock = 100)

        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(product))
        whenever(slotRepository.hasActiveSlot(testUserId, testProductId)).thenReturn(Mono.just(true))

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then
        StepVerifier.create(service.acquireSlot(command))
            .expectErrorMatches { error ->
                error is DuplicateSlotException &&
                        error.userId == testUserId &&
                        error.productId == testProductId &&
                        error.traceId == testTraceId
            }
            .verify()

        verify(productRepository).findById(testProductId)
        verify(slotRepository).hasActiveSlot(testUserId, testProductId)
        verifyNoInteractions(redisSlotCache, eventPublisher)
    }

    @Test
    fun `should fail when Redis reports out of stock`() {
        // Given: Redis atomic operation fails due to out of stock
        val product = createTestProduct(stock = 100)
        val failureResult = RedisSlotCache.AcquisitionResult(
            success = false,
            reason = "OUT_OF_STOCK"
        )

        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(product))
        whenever(slotRepository.hasActiveSlot(testUserId, testProductId)).thenReturn(Mono.just(false))
        whenever(redisSlotCache.acquireSlot(testUserId, testProductId, testArrivalTimestamp, testTraceId))
            .thenReturn(Mono.just(failureResult))

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then
        StepVerifier.create(service.acquireSlot(command))
            .expectErrorMatches { error ->
                error is OutOfStockException &&
                        error.productId == testProductId &&
                        error.traceId == testTraceId
            }
            .verify()

        verify(productRepository).findById(testProductId)
        verify(slotRepository).hasActiveSlot(testUserId, testProductId)
        verify(redisSlotCache).acquireSlot(testUserId, testProductId, testArrivalTimestamp, testTraceId)
        verify(slotRepository, never()).save(any())
        verifyNoInteractions(eventPublisher)
    }

    @Test
    fun `should fail when Redis reports duplicate request`() {
        // Given: Redis detects duplicate (race condition)
        val product = createTestProduct(stock = 100)
        val failureResult = RedisSlotCache.AcquisitionResult(
            success = false,
            reason = "DUPLICATE_REQUEST"
        )

        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(product))
        whenever(slotRepository.hasActiveSlot(testUserId, testProductId)).thenReturn(Mono.just(false))
        whenever(redisSlotCache.acquireSlot(testUserId, testProductId, testArrivalTimestamp, testTraceId))
            .thenReturn(Mono.just(failureResult))

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then
        StepVerifier.create(service.acquireSlot(command))
            .expectErrorMatches { error ->
                error is DuplicateSlotException &&
                        error.userId == testUserId &&
                        error.productId == testProductId &&
                        error.traceId == testTraceId
            }
            .verify()
    }

    @Test
    fun `should not fail acquisition when event publishing fails`() {
        // Given: Event publishing fails but acquisition should succeed
        val product = createTestProduct(stock = 100)
        val acquisitionResult = RedisSlotCache.AcquisitionResult(
            success = true,
            queuePosition = 1L,
            remainingStock = 99
        )
        val auditEntry = SlotAuditRepository.AuditLogEntry(
            id = UUID.randomUUID(),
            slotId = UUID.randomUUID(),
            eventType = "SLOT_ACQUIRED",
            oldStatus = null,
            newStatus = SlotStatus.ACTIVE,
            timestamp = Instant.now(),
            traceId = testTraceId,
            metadata = emptyMap()
        )

        whenever(productRepository.findById(testProductId)).thenReturn(Mono.just(product))
        whenever(slotRepository.hasActiveSlot(testUserId, testProductId)).thenReturn(Mono.just(false))
        whenever(redisSlotCache.acquireSlot(testUserId, testProductId, testArrivalTimestamp, testTraceId))
            .thenReturn(Mono.just(acquisitionResult))
        whenever(slotRepository.save(any())).thenAnswer { Mono.just(it.arguments[0] as PurchaseSlot) }
        whenever(slotAuditRepository.logAcquisition(any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(auditEntry))
        whenever(eventPublisher.publishSlotAcquired(any()))
            .thenReturn(Mono.error(RuntimeException("Kafka unavailable")))

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = testUserId,
            productId = testProductId,
            arrivalTimestamp = testArrivalTimestamp,
            traceId = testTraceId
        )

        // When & Then: Should succeed despite event publishing failure
        StepVerifier.create(service.acquireSlot(command))
            .assertNext { slot ->
                assertEquals(testUserId, slot.userId)
                assertEquals(testProductId, slot.productId)
                assertEquals(SlotStatus.ACTIVE, slot.status)
            }
            .verifyComplete()
    }

    // Helper method to create test product
    private fun createTestProduct(
        stock: Int = 100,
        saleDate: Instant = Instant.now().minus(1, ChronoUnit.HOURS)
    ): Product {
        return Product(
            id = testProductId,
            name = "Test Product",
            description = "Test Description",
            stock = stock,
            initialStock = stock,
            saleDate = saleDate,
            price = BigDecimal("100000"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = UUID.randomUUID()
        )
    }
}
