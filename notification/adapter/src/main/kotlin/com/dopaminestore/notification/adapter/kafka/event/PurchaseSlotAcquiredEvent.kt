package com.dopaminestore.notification.adapter.kafka.event

import com.dopaminestore.notification.core.domain.DomainEvent
import java.time.Instant

/**
 * 구매 슬롯 획득 이벤트 DTO
 *
 * Source: purchase 도메인
 */
data class PurchaseSlotAcquiredEvent(
    override val eventId: String,
    override val eventType: String = "PURCHASE_SLOT_ACQUIRED",
    override val occurredAt: Instant,
    val userId: String,
    val email: String,
    val slotId: String,
    val productId: String,
    val productName: String,
    val expiresAt: Instant
) : DomainEvent
