package com.dopaminestore.notification.adapter.kafka.event

import com.dopaminestore.notification.core.domain.DomainEvent
import java.time.Instant

/**
 * 비밀번호 재설정 요청 이벤트 DTO
 *
 * Source: auth 도메인
 */
data class PasswordResetRequestedEvent(
    override val eventId: String,
    override val eventType: String = "PASSWORD_RESET_REQUESTED",
    override val occurredAt: Instant,
    val userId: String,
    val email: String,
    val resetToken: String,
    val expiresAt: Instant
) : DomainEvent
