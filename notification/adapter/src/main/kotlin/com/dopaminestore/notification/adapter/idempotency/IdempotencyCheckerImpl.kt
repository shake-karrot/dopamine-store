package com.dopaminestore.notification.adapter.idempotency

import com.dopaminestore.notification.core.port.IdempotencyChecker
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Redis 기반 Idempotency 체크 구현
 *
 * - Key: idempotency:{eventId}
 * - TTL: 24시간 (86400초)
 */
@Component
class IdempotencyCheckerImpl(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) : IdempotencyChecker {

    companion object {
        private const val KEY_PREFIX = "idempotency:"
    }

    override suspend fun isProcessed(eventId: String): Boolean {
        val key = "$KEY_PREFIX$eventId"
        return redisTemplate.hasKey(key).awaitSingle()
    }

    override suspend fun markAsProcessed(eventId: String, ttlSeconds: Long) {
        val key = "$KEY_PREFIX$eventId"
        redisTemplate.opsForValue()
            .set(key, "1", Duration.ofSeconds(ttlSeconds))
            .awaitSingleOrNull()
    }
}
