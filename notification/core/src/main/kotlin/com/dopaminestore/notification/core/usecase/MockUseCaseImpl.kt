package com.dopaminestore.notification.core.usecase

import com.dopaminestore.notification.core.domain.MockNotification
import org.springframework.stereotype.Service

/**
 * Mock use case implementation for validating module architecture.
 */
@Service
class MockUseCaseImpl : MockUseCase {
    override fun execute(notification: MockNotification): Boolean {
        return true
    }
}
