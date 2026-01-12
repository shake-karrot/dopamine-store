package com.dopaminestore.notification.core.logging

import com.dopaminestore.notification.core.domain.NotificationRequest
import com.dopaminestore.notification.core.domain.NotificationType
import com.dopaminestore.notification.core.domain.SendType

/**
 * 구조화된 로깅을 위한 이벤트 타입
 */
enum class LogEventType {
    NOTIFICATION_REQUEST_RECEIVED,
    NOTIFICATION_REQUEST_VALIDATED,
    NOTIFICATION_REQUEST_CREATED,
    NOTIFICATION_REQUEST_FAILED,
    NOTIFICATION_REQUEST_DUPLICATE
}

/**
 * 구조화된 로그 데이터
 */
data class NotificationLogData(
    val eventType: LogEventType,
    val eventId: String? = null,
    val notificationId: String? = null,
    val notificationType: NotificationType? = null,
    val sendType: SendType? = null,
    val userId: String? = null,
    val reason: String? = null,
    val latencyMs: Long? = null
) {
    fun toLogMessage(): String {
        val parts = mutableListOf<String>()
        parts.add("eventType=$eventType")
        eventId?.let { parts.add("eventId=$it") }
        notificationId?.let { parts.add("notificationId=$it") }
        notificationType?.let { parts.add("notificationType=$it") }
        sendType?.let { parts.add("sendType=$it") }
        userId?.let { parts.add("userId=$it") }
        reason?.let { parts.add("reason=$it") }
        latencyMs?.let { parts.add("latencyMs=$it") }
        return parts.joinToString(", ")
    }

    companion object {
        fun received(eventId: String, notificationType: NotificationType) = NotificationLogData(
            eventType = LogEventType.NOTIFICATION_REQUEST_RECEIVED,
            eventId = eventId,
            notificationType = notificationType
        )

        fun validated(eventId: String) = NotificationLogData(
            eventType = LogEventType.NOTIFICATION_REQUEST_VALIDATED,
            eventId = eventId
        )

        fun created(request: NotificationRequest) = NotificationLogData(
            eventType = LogEventType.NOTIFICATION_REQUEST_CREATED,
            notificationId = request.id.toString(),
            notificationType = request.notificationType,
            sendType = request.sendType,
            userId = request.userId
        )

        fun failed(eventId: String, reason: String) = NotificationLogData(
            eventType = LogEventType.NOTIFICATION_REQUEST_FAILED,
            eventId = eventId,
            reason = reason
        )

        fun duplicate(eventId: String) = NotificationLogData(
            eventType = LogEventType.NOTIFICATION_REQUEST_DUPLICATE,
            eventId = eventId,
            reason = "Duplicate event ignored"
        )
    }
}
