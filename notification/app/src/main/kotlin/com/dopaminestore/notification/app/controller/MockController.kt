package com.dopaminestore.notification.app.controller

import com.dopaminestore.notification.core.domain.MockNotification
import com.dopaminestore.notification.core.usecase.MockUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Mock controller for validating module architecture.
 * App module injects core's use case interfaces.
 */
@RestController
@RequestMapping("/api/mock")
class MockController(
    private val mockUseCase: MockUseCase
) {
    @GetMapping("/health")
    fun health(): Map<String, String> {
        return mapOf("status" to "ok")
    }
}
