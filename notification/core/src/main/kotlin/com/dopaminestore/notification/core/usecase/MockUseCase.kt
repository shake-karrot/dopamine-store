package com.dopaminestore.notification.core.usecase

import com.dopaminestore.notification.core.domain.MockNotification

/**
 * Mock use case interface for validating module architecture.
 * Core module defines use case interfaces that app/worker modules implement.
 */
interface MockUseCase {
    fun execute(notification: MockNotification): Boolean
}
