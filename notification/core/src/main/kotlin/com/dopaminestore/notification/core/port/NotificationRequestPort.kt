package com.dopaminestore.notification.core.port

import com.dopaminestore.notification.core.domain.NotificationRequest

/**
 * NotificationRequest 출력 포트
 *
 * 생성된 NotificationRequest를 다음 처리 단계로 전달
 */
interface NotificationRequestPort {
    /**
     * 즉시 발송 알림 요청 전달
     */
    suspend fun sendImmediate(request: NotificationRequest)

    /**
     * 예약 발송 알림 요청 저장
     */
    suspend fun scheduleForLater(request: NotificationRequest)
}
