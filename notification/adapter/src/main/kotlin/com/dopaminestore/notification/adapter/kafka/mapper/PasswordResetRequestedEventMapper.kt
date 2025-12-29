package com.dopaminestore.notification.adapter.kafka.mapper

import com.dopaminestore.notification.adapter.kafka.event.PasswordResetRequestedEvent
import com.dopaminestore.notification.core.domain.NotificationRequest
import com.dopaminestore.notification.core.domain.NotificationType
import org.springframework.stereotype.Component

/**
 * PasswordResetRequestedEvent -> NotificationRequest 매퍼
 */
@Component
class PasswordResetRequestedEventMapper {

    fun toNotificationRequest(event: PasswordResetRequestedEvent): NotificationRequest {
        val payload = mapOf(
            "resetToken" to event.resetToken,
            "resetLink" to generateResetLink(event.resetToken),
            "expiresAt" to event.expiresAt.toString()
        )

        return NotificationRequest.immediate(
            eventId = event.eventId,
            userId = event.userId,
            email = event.email,
            notificationType = NotificationType.PASSWORD_RESET_REQUESTED,
            payload = payload
        )
    }

    private fun generateResetLink(resetToken: String): String {
        // TODO: 실제 도메인 설정에서 가져오기
        return "https://dopamine-store.com/reset-password?token=$resetToken"
    }
}
