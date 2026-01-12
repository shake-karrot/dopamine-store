package com.dopaminestore.notification.core.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 알림 발송을 위한 내부 도메인 객체
 *
 * Kafka 이벤트로부터 변환되어 생성된다.
 */
data class NotificationRequest(
    /** Idempotency Key (from eventId) */
    val id: UUID,

    /** 수신자 사용자 ID */
    val userId: String,

    /** 수신자 이메일 주소 */
    val email: String,

    /** 알림 유형 */
    val notificationType: NotificationType,

    /** 템플릿 변수들 */
    val payload: Map<String, Any>,

    /** IMMEDIATE or SCHEDULED */
    val sendType: SendType,

    /** 예약 발송 시간 (SCHEDULED인 경우) */
    val scheduledAt: Instant?,

    /** 생성 시간 */
    val createdAt: Instant
) {
    companion object {
        /**
         * 즉시 발송 알림 요청 생성
         */
        fun immediate(
            eventId: String,
            userId: String,
            email: String,
            notificationType: NotificationType,
            payload: Map<String, Any>
        ): NotificationRequest = NotificationRequest(
            id = UUID.nameUUIDFromBytes(eventId.toByteArray()),
            userId = userId,
            email = email,
            notificationType = notificationType,
            payload = payload,
            sendType = SendType.IMMEDIATE,
            scheduledAt = null,
            createdAt = Instant.now()
        )

        /**
         * 예약 발송 알림 요청 생성
         *
         * @param expiresAt 만료 시간
         * @param beforeMinutes 만료 몇 분 전에 발송할지 (기본 5분)
         */
        fun scheduled(
            eventId: String,
            userId: String,
            email: String,
            notificationType: NotificationType,
            payload: Map<String, Any>,
            expiresAt: Instant,
            beforeMinutes: Long = 5
        ): NotificationRequest {
            val scheduledAt = expiresAt.minus(Duration.ofMinutes(beforeMinutes))
            val adjustedEventId = "${eventId}-scheduled"

            return NotificationRequest(
                id = UUID.nameUUIDFromBytes(adjustedEventId.toByteArray()),
                userId = userId,
                email = email,
                notificationType = notificationType,
                payload = payload + ("remainingMinutes" to beforeMinutes),
                sendType = SendType.SCHEDULED,
                scheduledAt = scheduledAt,
                createdAt = Instant.now()
            )
        }
    }
}
