package com.dopaminestore.notification.core.usecase

import com.dopaminestore.notification.core.domain.NotificationRequest

/**
 * 알림 요청 수신 UseCase
 */
interface ReceiveNotificationUseCase {
    /**
     * 알림 요청 처리
     *
     * @param eventId 이벤트 ID (idempotency key)
     * @param requests 생성된 알림 요청들 (1개 또는 2개)
     * @return 처리 결과
     */
    suspend fun receive(eventId: String, requests: List<NotificationRequest>): ReceiveResult
}

sealed class ReceiveResult {
    data class Success(val processedCount: Int) : ReceiveResult()
    data object Duplicate : ReceiveResult()
    data class Failed(val reason: String) : ReceiveResult()
}
