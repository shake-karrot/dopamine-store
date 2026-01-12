package com.dopaminestore.notification.core.service

import com.dopaminestore.notification.core.domain.MockNotification
import com.dopaminestore.notification.core.port.MockPort
import org.springframework.stereotype.Service

/**
 * Mock service for validating module architecture.
 * Core module contains business logic that depends only on ports (interfaces).
 */
@Service
class MockService(
    private val mockPort: MockPort
) {
    fun process(notification: MockNotification): MockNotification {
        return mockPort.save(notification)
    }

    fun findById(id: String): MockNotification? {
        return mockPort.findById(id)
    }
}
