package com.dopaminestore.notification.adapter.output

import com.dopaminestore.notification.core.domain.NotificationRequest
import com.dopaminestore.notification.core.port.NotificationRequestPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * NotificationRequestPort 구현
 *
 * 현재 단계에서는 로깅만 수행 (다음 feature에서 실제 발송 구현)
 */
@Component
class NotificationRequestPortImpl : NotificationRequestPort {

    private val logger = LoggerFactory.getLogger(NotificationRequestPortImpl::class.java)

    override suspend fun sendImmediate(request: NotificationRequest) {
        logger.info(
            "IMMEDIATE notification queued: id={}, type={}, userId={}, email={}",
            request.id,
            request.notificationType,
            request.userId,
            request.email
        )
        // TODO: 003-immediate-email-sending에서 실제 발송 구현
    }

    override suspend fun scheduleForLater(request: NotificationRequest) {
        logger.info(
            "SCHEDULED notification stored: id={}, type={}, userId={}, scheduledAt={}",
            request.id,
            request.notificationType,
            request.userId,
            request.scheduledAt
        )
        // TODO: 006-scheduled-notification에서 실제 저장 구현
    }
}
