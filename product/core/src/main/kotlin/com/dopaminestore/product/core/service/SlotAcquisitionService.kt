package com.dopaminestore.product.core.usecase

import com.dopaminestore.product.core.domain.PurchaseSlot
import com.dopaminestore.product.core.port.EventPublisher
import com.dopaminestore.product.core.port.ProductRepository
import com.dopaminestore.product.core.port.PurchaseSlotRepository
import com.dopaminestore.product.core.port.RedisSlotCache
import com.dopaminestore.product.core.port.SlotAuditRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Instant

/**
 * Service implementing slot acquisition use case with fairness guarantees.
 *
 * **Architecture**: Hexagonal (Ports & Adapters)
 * - Depends on ports (interfaces), not adapters (implementations)
 * - All dependencies injected via constructor
 * - Pure business logic, no infrastructure concerns
 *
 * **Transactional Guarantees**:
 * - Redis operations are atomic (Lua script)
 * - Database operations use optimistic locking
 * - Event publishing is best-effort (at-least-once via Kafka)
 *
 * **Performance** (Constitution SC-002):
 * - Target: 100K RPS
 * - p99 latency: < 100ms
 * - All operations non-blocking (reactive)
 */
@Service
class SlotAcquisitionService(
    private val productRepository: ProductRepository,
    private val slotRepository: PurchaseSlotRepository,
    private val slotAuditRepository: SlotAuditRepository,
    private val redisSlotCache: RedisSlotCache,
    private val eventPublisher: EventPublisher
) : SlotAcquisitionUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun acquireSlot(command: SlotAcquisitionUseCase.AcquireSlotCommand): Mono<PurchaseSlot> {
        val startTime = System.currentTimeMillis()

        logger.info(
            "[SLOT_ACQUISITION_START] userId={}, productId={}, traceId={}",
            command.userId, command.productId, command.traceId
        )

        return validateProduct(command.productId, command.traceId)
            .flatMap { product ->
                checkDuplicateSlot(command.userId, command.productId, command.traceId)
                    .then(Mono.just(product))
            }
            .flatMap { product ->
                acquireSlotAtomically(
                    command.userId,
                    command.productId,
                    command.arrivalTimestamp,
                    command.traceId
                ).map { result -> product to result }
            }
            .flatMap { (product, cacheResult) ->
                persistSlot(command, product.name)
                    .map { slot -> slot to cacheResult }
            }
            .flatMap { (slot, cacheResult) ->
                logAcquisition(slot, cacheResult, command.traceId)
                    .then(Mono.just(slot))
            }
            .flatMap { slot ->
                publishAcquisitionEvent(slot, command.traceId)
                    .then(Mono.just(slot))
            }
            .doOnSuccess { slot ->
                val duration = System.currentTimeMillis() - startTime
                logger.info(
                    "[SLOT_ACQUISITION_SUCCESS] slotId={}, userId={}, productId={}, " +
                            "queuePosition={}, duration={}ms, traceId={}",
                    slot.id, command.userId, command.productId,
                    "N/A", duration, command.traceId
                )
            }
            .doOnError { error ->
                val duration = System.currentTimeMillis() - startTime
                logger.error(
                    "[SLOT_ACQUISITION_FAILED] userId={}, productId={}, " +
                            "error={}, duration={}ms, traceId={}",
                    command.userId, command.productId,
                    error.javaClass.simpleName, duration, command.traceId,
                    error
                )
            }
    }

    /**
     * Validate product exists and is available for purchase.
     *
     * @throws ProductNotFoundException if product not found
     * @throws ProductNotAvailableException if product is not ON_SALE
     */
    private fun validateProduct(productId: java.util.UUID, traceId: String): Mono<com.dopaminestore.product.core.domain.Product> {
        return productRepository.findById(productId)
            .switchIfEmpty(
                Mono.error(
                    ProductNotFoundException("Product not found: $productId", productId, traceId)
                )
            )
            .flatMap { product ->
                if (!product.isAvailableForPurchase()) {
                    val status = product.computeStatus()
                    Mono.error(
                        ProductNotAvailableException(
                            "Product is not available: status=$status",
                            productId,
                            status,
                            traceId
                        )
                    )
                } else {
                    Mono.just(product)
                }
            }
    }

    /**
     * Check if user already has an active slot for this product.
     *
     * @throws DuplicateSlotException if active slot exists
     */
    private fun checkDuplicateSlot(
        userId: java.util.UUID,
        productId: java.util.UUID,
        traceId: String
    ): Mono<Void> {
        return slotRepository.hasActiveSlot(userId, productId)
            .flatMap { hasSlot ->
                if (hasSlot) {
                    Mono.error(
                        DuplicateSlotException(
                            "User already has active slot for product",
                            userId,
                            productId,
                            traceId
                        )
                    )
                } else {
                    Mono.empty()
                }
            }
    }

    /**
     * Atomically acquire slot using Redis (fairness queue + stock decrement).
     *
     * This operation is atomic via Lua script execution in Redis.
     * Ensures:
     * 1. Stock > 0 (validation)
     * 2. Duplicate check (user not in queue)
     * 3. Stock decrement
     * 4. User added to fairness queue with arrival timestamp
     *
     * @throws OutOfStockException if no stock available
     * @throws DuplicateSlotException if user already in queue (race condition)
     */
    private fun acquireSlotAtomically(
        userId: java.util.UUID,
        productId: java.util.UUID,
        arrivalTimestamp: Long,
        traceId: String
    ): Mono<RedisSlotCache.AcquisitionResult> {
        return redisSlotCache.acquireSlot(userId, productId, arrivalTimestamp, traceId)
            .flatMap { result ->
                if (result.success) {
                    logger.debug(
                        "[REDIS_SLOT_ACQUIRED] userId={}, productId={}, queuePosition={}, " +
                                "remainingStock={}, traceId={}",
                        userId, productId, result.queuePosition, result.remainingStock, traceId
                    )
                    Mono.just(result)
                } else {
                    when (result.reason) {
                        "OUT_OF_STOCK" -> Mono.error(
                            OutOfStockException(
                                "Product is out of stock",
                                productId,
                                traceId
                            )
                        )
                        "DUPLICATE_REQUEST" -> Mono.error(
                            DuplicateSlotException(
                                "Duplicate slot acquisition request",
                                userId,
                                productId,
                                traceId
                            )
                        )
                        else -> Mono.error(
                            SlotAcquisitionException(
                                "Slot acquisition failed: ${result.reason}",
                                userId,
                                productId,
                                result.reason ?: "UNKNOWN",
                                traceId
                            )
                        )
                    }
                }
            }
    }

    /**
     * Persist slot to database.
     */
    private fun persistSlot(
        command: SlotAcquisitionUseCase.AcquireSlotCommand,
        productName: String
    ): Mono<PurchaseSlot> {
        val acquisitionTime = Instant.ofEpochMilli(command.arrivalTimestamp)
        val slot = PurchaseSlot.acquire(
            productId = command.productId,
            userId = command.userId,
            traceId = command.traceId,
            acquisitionTime = acquisitionTime
        )

        return slotRepository.save(slot)
            .doOnSuccess {
                logger.debug(
                    "[SLOT_PERSISTED] slotId={}, userId={}, productId={}, traceId={}",
                    it.id, command.userId, command.productId, command.traceId
                )
            }
    }

    /**
     * Log slot acquisition to audit trail.
     */
    private fun logAcquisition(
        slot: PurchaseSlot,
        cacheResult: RedisSlotCache.AcquisitionResult,
        traceId: String
    ): Mono<SlotAuditRepository.AuditLogEntry> {
        val metadata = mapOf(
            "queuePosition" to (cacheResult.queuePosition ?: 0),
            "remainingStock" to (cacheResult.remainingStock ?: 0)
        )

        return slotAuditRepository.logAcquisition(
            slotId = slot.id,
            userId = slot.userId,
            productId = slot.productId,
            acquisitionTimestamp = slot.acquisitionTimestamp,
            traceId = traceId,
            metadata = metadata
        )
    }

    /**
     * Publish slot acquisition event to Kafka.
     *
     * This is fire-and-forget with at-least-once delivery.
     * Failures are logged but do not fail the acquisition.
     */
    private fun publishAcquisitionEvent(slot: PurchaseSlot, traceId: String): Mono<Void> {
        // We need product name for the event - fetch from cache or database
        return productRepository.findById(slot.productId)
            .flatMap { product ->
                val event = EventPublisher.SlotAcquiredEvent(
                    slotId = slot.id,
                    userId = slot.userId,
                    productId = slot.productId,
                    productName = product.name,
                    expirationTimestamp = slot.expirationTimestamp,
                    acquisitionTimestamp = slot.acquisitionTimestamp,
                    traceId = traceId
                )

                eventPublisher.publishSlotAcquired(event)
            }
            .doOnSuccess {
                logger.debug(
                    "[EVENT_PUBLISHED] event=SLOT_ACQUIRED, slotId={}, traceId={}",
                    slot.id, traceId
                )
            }
            .doOnError { error ->
                logger.error(
                    "[EVENT_PUBLISH_FAILED] event=SLOT_ACQUIRED, slotId={}, traceId={}",
                    slot.id, traceId, error
                )
            }
            .onErrorResume { Mono.empty() } // Don't fail acquisition on event publish failure
    }
}

/**
 * Base exception for slot acquisition failures.
 */
open class SlotAcquisitionException(
    message: String,
    val userId: java.util.UUID,
    val productId: java.util.UUID,
    val reason: String,
    val traceId: String
) : RuntimeException(message)

/**
 * Product not found.
 */
class ProductNotFoundException(
    message: String,
    productId: java.util.UUID,
    traceId: String
) : SlotAcquisitionException(message, java.util.UUID.randomUUID(), productId, "PRODUCT_NOT_FOUND", traceId)

/**
 * Product is not available for purchase (UPCOMING or SOLD_OUT).
 */
class ProductNotAvailableException(
    message: String,
    productId: java.util.UUID,
    val status: com.dopaminestore.product.core.domain.value.ProductStatus,
    traceId: String
) : SlotAcquisitionException(message, java.util.UUID.randomUUID(), productId, "PRODUCT_NOT_AVAILABLE", traceId)

/**
 * User already has an active slot for this product.
 */
class DuplicateSlotException(
    message: String,
    userId: java.util.UUID,
    productId: java.util.UUID,
    traceId: String
) : SlotAcquisitionException(message, userId, productId, "DUPLICATE_SLOT", traceId)

/**
 * Product is out of stock.
 */
class OutOfStockException(
    message: String,
    productId: java.util.UUID,
    traceId: String
) : SlotAcquisitionException(message, java.util.UUID.randomUUID(), productId, "OUT_OF_STOCK", traceId)
