package com.dopaminestore.notification.adapter.kafka.mapper

import com.dopaminestore.notification.adapter.kafka.event.PurchaseSlotAcquiredEvent
import com.dopaminestore.notification.core.domain.NotificationRequest
import com.dopaminestore.notification.core.domain.NotificationType
import org.springframework.stereotype.Component

/**
 * PurchaseSlotAcquiredEvent -> NotificationRequest 매퍼
 *
 * 하나의 이벤트로부터 2개의 NotificationRequest 생성:
 * 1. 즉시 발송 (IMMEDIATE): 슬롯 획득 축하
 * 2. 예약 발송 (SCHEDULED): 만료 5분 전 알림
 */
@Component
class PurchaseSlotAcquiredEventMapper {

    /**
     * 즉시 발송 알림 요청 생성
     */
    fun toImmediateNotificationRequest(event: PurchaseSlotAcquiredEvent): NotificationRequest {
        val payload = mapOf(
            "productName" to event.productName,
            "slotId" to event.slotId,
            "expiresAt" to event.expiresAt.toString(),
            "paymentLink" to generatePaymentLink(event.slotId)
        )

        return NotificationRequest.immediate(
            eventId = event.eventId,
            userId = event.userId,
            email = event.email,
            notificationType = NotificationType.PURCHASE_SLOT_ACQUIRED,
            payload = payload
        )
    }

    /**
     * 예약 발송 알림 요청 생성 (만료 5분 전)
     */
    fun toScheduledNotificationRequest(event: PurchaseSlotAcquiredEvent): NotificationRequest {
        val payload = mapOf(
            "productName" to event.productName,
            "slotId" to event.slotId,
            "expiresAt" to event.expiresAt.toString()
        )

        return NotificationRequest.scheduled(
            eventId = event.eventId,
            userId = event.userId,
            email = event.email,
            notificationType = NotificationType.PURCHASE_SLOT_EXPIRING,
            payload = payload,
            expiresAt = event.expiresAt,
            beforeMinutes = 5
        )
    }

    /**
     * 이벤트에서 모든 알림 요청 생성
     */
    fun toNotificationRequests(event: PurchaseSlotAcquiredEvent): List<NotificationRequest> {
        return listOf(
            toImmediateNotificationRequest(event),
            toScheduledNotificationRequest(event)
        )
    }

    private fun generatePaymentLink(slotId: String): String {
        // TODO: 실제 결제 링크 생성 로직 구현
        return "https://dopamine-store.com/payment?slotId=$slotId"
    }
}
