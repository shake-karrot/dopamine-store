package com.dopaminestore.notification.adapter.kafka.mapper

import com.dopaminestore.notification.adapter.kafka.event.NewUserRegisteredEvent
import com.dopaminestore.notification.core.domain.NotificationRequest
import com.dopaminestore.notification.core.domain.NotificationType
import org.springframework.stereotype.Component

/**
 * NewUserRegisteredEvent -> NotificationRequest 매퍼
 */
@Component
class NewUserRegisteredEventMapper {

    fun toNotificationRequest(event: NewUserRegisteredEvent): NotificationRequest {
        val payload = buildMap<String, Any> {
            event.userName?.let { put("userName", it) }
        }

        return NotificationRequest.immediate(
            eventId = event.eventId,
            userId = event.userId,
            email = event.email,
            notificationType = NotificationType.NEW_USER_REGISTERED,
            payload = payload
        )
    }
}
