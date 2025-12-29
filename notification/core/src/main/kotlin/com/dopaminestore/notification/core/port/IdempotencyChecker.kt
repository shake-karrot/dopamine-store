package com.dopaminestore.notification.core.port

/**
 * Idempotency 체크 인터페이스
 *
 * Redis를 통해 이벤트 중복 처리 방지
 */
interface IdempotencyChecker {
    /**
     * 이벤트가 이미 처리되었는지 확인
     *
     * @param eventId 이벤트 ID
     * @return 이미 처리된 경우 true
     */
    suspend fun isProcessed(eventId: String): Boolean

    /**
     * 이벤트를 처리됨으로 표시
     *
     * @param eventId 이벤트 ID
     * @param ttlSeconds TTL (기본 24시간)
     */
    suspend fun markAsProcessed(eventId: String, ttlSeconds: Long = 86400)
}
