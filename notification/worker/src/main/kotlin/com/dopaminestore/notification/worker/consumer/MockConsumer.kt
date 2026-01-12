package com.dopaminestore.notification.worker.consumer

import com.dopaminestore.notification.core.domain.MockNotification
import com.dopaminestore.notification.core.service.MockService
import org.springframework.stereotype.Component

/**
 * Mock consumer for validating module architecture.
 * Worker module injects core's services.
 */
@Component
class MockConsumer(
    private val mockService: MockService
) {
    fun consume(notification: MockNotification) {
        mockService.process(notification)
    }
}
