package com.dopaminestore.notification.adapter.persistence

import com.dopaminestore.notification.core.domain.MockNotification
import com.dopaminestore.notification.core.port.MockPort
import org.springframework.stereotype.Repository

/**
 * Mock repository implementation for validating module architecture.
 * Adapter module implements core's port interfaces.
 */
@Repository
class MockRepositoryImpl : MockPort {

    private val storage = mutableMapOf<String, MockNotification>()

    override fun save(notification: MockNotification): MockNotification {
        storage[notification.id] = notification
        return notification
    }

    override fun findById(id: String): MockNotification? {
        return storage[id]
    }
}
