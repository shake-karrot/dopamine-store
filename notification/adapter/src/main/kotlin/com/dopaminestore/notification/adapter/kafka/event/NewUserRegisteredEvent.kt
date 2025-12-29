package com.dopaminestore.notification.adapter.kafka.event

import com.dopaminestore.notification.core.domain.DomainEvent
import java.time.Instant

/**
 * 회원가입 완료 이벤트 DTO
 *
 * Source: auth 도메인
 */
data class NewUserRegisteredEvent(
    override val eventId: String,
    override val eventType: String = "NEW_USER_REGISTERED",
    override val occurredAt: Instant,
    val userId: String,
    val email: String,
    val userName: String? = null
) : DomainEvent
