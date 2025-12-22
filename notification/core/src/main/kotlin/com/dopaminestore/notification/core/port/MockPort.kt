package com.dopaminestore.notification.core.port

import com.dopaminestore.notification.core.domain.MockNotification

/**
 * Mock port interface for validating module architecture.
 * Core module defines port interfaces, adapter module provides implementations.
 */
interface MockPort {
    fun save(notification: MockNotification): MockNotification
    fun findById(id: String): MockNotification?
}
