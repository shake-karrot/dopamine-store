package com.dopaminestore.notification.core.service

import com.dopaminestore.notification.core.domain.NotificationRequest
import com.dopaminestore.notification.core.domain.SendType
import com.dopaminestore.notification.core.logging.NotificationLogData
import com.dopaminestore.notification.core.port.IdempotencyChecker
import com.dopaminestore.notification.core.port.NotificationRequestPort
import com.dopaminestore.notification.core.usecase.ReceiveNotificationUseCase
import com.dopaminestore.notification.core.usecase.ReceiveResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 알림 요청 수신 서비스
 */
@Service
class ReceiveNotificationService(
    private val idempotencyChecker: IdempotencyChecker,
    private val notificationRequestPort: NotificationRequestPort
) : ReceiveNotificationUseCase {

    private val logger = LoggerFactory.getLogger(ReceiveNotificationService::class.java)

    override suspend fun receive(eventId: String, requests: List<NotificationRequest>): ReceiveResult {
        // 1. Idempotency 체크
        if (idempotencyChecker.isProcessed(eventId)) {
            logger.info(NotificationLogData.duplicate(eventId).toLogMessage())
            return ReceiveResult.Duplicate
        }

        // 2. 알림 요청 처리
        try {
            requests.forEach { request ->
                when (request.sendType) {
                    SendType.IMMEDIATE -> {
                        notificationRequestPort.sendImmediate(request)
                    }
                    SendType.SCHEDULED -> {
                        notificationRequestPort.scheduleForLater(request)
                    }
                }
                logger.info(NotificationLogData.created(request).toLogMessage())
            }

            // 3. Idempotency 키 저장
            idempotencyChecker.markAsProcessed(eventId)

            return ReceiveResult.Success(requests.size)
        } catch (e: Exception) {
            logger.error(NotificationLogData.failed(eventId, e.message ?: "Unknown error").toLogMessage())
            return ReceiveResult.Failed(e.message ?: "Unknown error")
        }
    }
}
