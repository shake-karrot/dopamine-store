package com.dopaminestore.notification.core.domain

import java.time.Instant

/**
 * Kafka로부터 수신되는 외부 도메인 이벤트의 공통 인터페이스
 */
interface DomainEvent {
    /** 이벤트 고유 ID */
    val eventId: String

    /** 이벤트 타입 문자열 */
    val eventType: String

    /** 이벤트 발생 시간 */
    val occurredAt: Instant
}
