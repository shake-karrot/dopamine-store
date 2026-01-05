package com.dopaminestore.product.adapter.redis

import com.dopaminestore.product.core.port.RedisSlotCache
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.time.Instant
import java.util.UUID
import jakarta.annotation.PostConstruct

/**
 * Redis-based implementation of slot cache with fairness guarantees.
 *
 * **Architecture**:
 * - Uses Lettuce reactive client for non-blocking operations
 * - Lua scripts ensure atomicity for slot acquisition
 * - Sorted sets maintain arrival-time ordering for fairness
 *
 * **Data Structures**:
 * - `product:{productId}:stock` → Current stock (STRING)
 * - `product:{productId}:queue` → Fairness queue (ZSET with timestamp scores)
 * - `user:{userId}:product:{productId}` → Duplicate prevention flag (STRING with TTL)
 *
 * **Performance**:
 * - Target: 100K RPS
 * - p99 latency: < 10ms (Redis operations)
 * - Connection pool: 50 max connections
 */
@Component
class RedisSlotCacheImpl(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) : RedisSlotCache {

    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var acquisitionScript: RedisScript<Map<*, *>>

    @PostConstruct
    fun init() {
        // Load Lua script for atomic slot acquisition
        try {
            val resource = ClassPathResource("redis/slot-acquisition.lua")
            val scriptText = resource.inputStream.bufferedReader().use { it.readText() }
            acquisitionScript = RedisScript.of(scriptText, Map::class.java)
            logger.info("[REDIS_INIT] Slot acquisition Lua script loaded successfully")
        } catch (e: Exception) {
            logger.error("[REDIS_INIT_FAILED] Failed to load Lua script", e)
            throw IllegalStateException("Failed to initialize Redis slot cache", e)
        }
    }

    override fun acquireSlot(
        userId: UUID,
        productId: UUID,
        arrivalTimestamp: Long,
        traceId: String
    ): Mono<RedisSlotCache.AcquisitionResult> {
        val stockKey = RedisKeyHelper.productStockKey(productId.toString())
        val queueKey = RedisKeyHelper.productQueueKey(productId.toString())
        val duplicateKey = RedisKeyHelper.duplicatePreventionKey(userId.toString(), productId.toString())

        val keys = listOf(stockKey, queueKey, duplicateKey)
        val args = listOf(userId.toString(), arrivalTimestamp.toString())

        return redisTemplate.execute(acquisitionScript, keys, args)
            .next()
            .flatMap { result ->
                when {
                    result["err"] != null -> {
                        val reason = result["err"].toString()
                        logger.debug(
                            "[REDIS_SLOT_ACQUISITION_FAILED] userId={}, productId={}, reason={}, traceId={}",
                            userId, productId, reason, traceId
                        )
                        RedisSlotCache.AcquisitionResult(
                            success = false,
                            reason = reason
                        ).toMono()
                    }
                    result["ok"] != null -> {
                        // Get queue position and remaining stock
                        getQueuePosition(userId, productId)
                            .zipWith(getStock(productId).defaultIfEmpty(0))
                            .map { tuple ->
                                val position = tuple.t1
                                val stock = tuple.t2
                                logger.debug(
                                    "[REDIS_SLOT_ACQUISITION_SUCCESS] userId={}, productId={}, " +
                                            "queuePosition={}, remainingStock={}, traceId={}",
                                    userId, productId, position, stock, traceId
                                )
                                RedisSlotCache.AcquisitionResult(
                                    success = true,
                                    queuePosition = position,
                                    remainingStock = stock,
                                    timestamp = Instant.ofEpochMilli(arrivalTimestamp)
                                )
                            }
                    }
                    else -> {
                        logger.error(
                            "[REDIS_SLOT_ACQUISITION_UNKNOWN] userId={}, productId={}, " +
                                    "result={}, traceId={}",
                            userId, productId, result, traceId
                        )
                        RedisSlotCache.AcquisitionResult(
                            success = false,
                            reason = "UNKNOWN_ERROR"
                        ).toMono()
                    }
                }
            }
            .onErrorResume { error ->
                logger.error(
                    "[REDIS_SLOT_ACQUISITION_ERROR] userId={}, productId={}, traceId={}",
                    userId, productId, traceId, error
                )
                RedisSlotCache.AcquisitionResult(
                    success = false,
                    reason = "REDIS_ERROR"
                ).toMono()
            }
    }

    override fun releaseSlot(
        userId: UUID,
        productId: UUID,
        reclaimStock: Boolean,
        traceId: String
    ): Mono<Boolean> {
        val queueKey = RedisKeyHelper.productQueueKey(productId.toString())
        val duplicateKey = RedisKeyHelper.duplicatePreventionKey(userId.toString(), productId.toString())

        val operations = redisTemplate.opsForZSet().remove(queueKey, userId.toString())
            .zipWith(redisTemplate.delete(duplicateKey))
            .flatMap { tuple ->
                val removed = tuple.t1
                val deleted = tuple.t2
                if (reclaimStock && removed > 0) {
                    // Increment stock back if slot is being reclaimed
                    incrementStock(productId)
                        .map { deleted > 0 }
                } else {
                    Mono.just(deleted > 0)
                }
            }

        return operations
            .doOnSuccess { success ->
                logger.debug(
                    "[REDIS_SLOT_RELEASED] userId={}, productId={}, reclaimStock={}, " +
                            "success={}, traceId={}",
                    userId, productId, reclaimStock, success, traceId
                )
            }
            .onErrorResume { error ->
                logger.error(
                    "[REDIS_SLOT_RELEASE_ERROR] userId={}, productId={}, traceId={}",
                    userId, productId, traceId, error
                )
                Mono.just(false)
            }
    }

    override fun hasActiveSlot(userId: UUID, productId: UUID): Mono<Boolean> {
        val duplicateKey = RedisKeyHelper.duplicatePreventionKey(userId.toString(), productId.toString())
        return redisTemplate.hasKey(duplicateKey)
            .onErrorResume { error ->
                logger.error(
                    "[REDIS_HAS_SLOT_ERROR] userId={}, productId={}",
                    userId, productId, error
                )
                Mono.just(false)
            }
    }

    override fun getStock(productId: UUID): Mono<Int?> {
        val stockKey = RedisKeyHelper.productStockKey(productId.toString())
        return redisTemplate.opsForValue().get(stockKey)
            .map { it.toIntOrNull() }
            .onErrorResume { error ->
                logger.error("[REDIS_GET_STOCK_ERROR] productId={}", productId, error)
                Mono.empty()
            }
    }

    override fun setStock(productId: UUID, stock: Int): Mono<Boolean> {
        require(stock >= 0) { "Stock cannot be negative" }

        val stockKey = RedisKeyHelper.productStockKey(productId.toString())
        return redisTemplate.opsForValue().set(stockKey, stock.toString())
            .doOnSuccess {
                logger.debug("[REDIS_STOCK_SET] productId={}, stock={}", productId, stock)
            }
            .onErrorResume { error ->
                logger.error("[REDIS_SET_STOCK_ERROR] productId={}, stock={}", productId, stock, error)
                Mono.just(false)
            }
    }

    override fun incrementStock(productId: UUID, amount: Int): Mono<Long> {
        require(amount > 0) { "Increment amount must be positive" }

        val stockKey = RedisKeyHelper.productStockKey(productId.toString())
        return redisTemplate.opsForValue().increment(stockKey, amount.toLong())
            .doOnSuccess { newStock ->
                logger.debug(
                    "[REDIS_STOCK_INCREMENTED] productId={}, amount={}, newStock={}",
                    productId, amount, newStock
                )
            }
            .onErrorResume { error ->
                logger.error(
                    "[REDIS_INCREMENT_STOCK_ERROR] productId={}, amount={}",
                    productId, amount, error
                )
                Mono.just(0L)
            }
    }

    override fun decrementStock(productId: UUID, amount: Int): Mono<Long?> {
        require(amount > 0) { "Decrement amount must be positive" }

        val stockKey = RedisKeyHelper.productStockKey(productId.toString())

        // Check current stock first to prevent negative values
        return getStock(productId)
            .flatMap { currentStock ->
                if (currentStock == null || currentStock < amount) {
                    logger.warn(
                        "[REDIS_DECREMENT_BLOCKED] productId={}, currentStock={}, requestedAmount={}",
                        productId, currentStock, amount
                    )
                    Mono.empty()
                } else {
                    redisTemplate.opsForValue().decrement(stockKey, amount.toLong())
                        .doOnSuccess { newStock ->
                            logger.debug(
                                "[REDIS_STOCK_DECREMENTED] productId={}, amount={}, newStock={}",
                                productId, amount, newStock
                            )
                        }
                }
            }
            .onErrorResume { error ->
                logger.error(
                    "[REDIS_DECREMENT_STOCK_ERROR] productId={}, amount={}",
                    productId, amount, error
                )
                Mono.empty()
            }
    }

    override fun getQueuePosition(userId: UUID, productId: UUID): Mono<Long?> {
        val queueKey = RedisKeyHelper.productQueueKey(productId.toString())
        return redisTemplate.opsForZSet().rank(queueKey, userId.toString())
            .map { rank -> rank + 1 } // Convert 0-indexed rank to 1-indexed position
            .onErrorResume { error ->
                logger.error(
                    "[REDIS_GET_QUEUE_POSITION_ERROR] userId={}, productId={}",
                    userId, productId, error
                )
                Mono.empty()
            }
    }

    override fun getQueueSize(productId: UUID): Mono<Long> {
        val queueKey = RedisKeyHelper.productQueueKey(productId.toString())
        return redisTemplate.opsForZSet().size(queueKey)
            .onErrorResume { error ->
                logger.error("[REDIS_GET_QUEUE_SIZE_ERROR] productId={}", productId, error)
                Mono.just(0L)
            }
    }

    override fun clearProduct(productId: UUID): Mono<Boolean> {
        val stockKey = RedisKeyHelper.productStockKey(productId.toString())
        val queueKey = RedisKeyHelper.productQueueKey(productId.toString())

        return redisTemplate.delete(stockKey, queueKey)
            .map { count -> count > 0 }
            .doOnSuccess { success ->
                logger.info("[REDIS_PRODUCT_CLEARED] productId={}, success={}", productId, success)
            }
            .onErrorResume { error ->
                logger.error("[REDIS_CLEAR_PRODUCT_ERROR] productId={}", productId, error)
                Mono.just(false)
            }
    }

    override fun verifyQueueOrdering(productId: UUID): Mono<List<RedisSlotCache.QueueEntry>> {
        val queueKey = RedisKeyHelper.productQueueKey(productId.toString())

        return redisTemplate.opsForZSet()
            .rangeWithScores(queueKey, org.springframework.data.domain.Range.unbounded())
            .collectList()
            .map { entries ->
                entries.mapIndexed { index, typedTuple ->
                    RedisSlotCache.QueueEntry(
                        userId = UUID.fromString(typedTuple.value),
                        arrivalTimestamp = typedTuple.score!!.toLong(),
                        position = index + 1L
                    )
                }
            }
            .doOnSuccess { entries ->
                logger.debug(
                    "[REDIS_QUEUE_ORDERING_VERIFIED] productId={}, queueSize={}",
                    productId, entries.size
                )
            }
            .onErrorResume { error ->
                logger.error("[REDIS_VERIFY_QUEUE_ERROR] productId={}", productId, error)
                Mono.just(emptyList())
            }
    }
}
