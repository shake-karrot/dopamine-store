package com.dopaminestore.notification.core.domain

/**
 * Mock domain entity for validating module architecture.
 * Core module contains pure domain entities with no external dependencies.
 */
data class MockNotification(
    val id: String,
    val message: String,
    val recipient: String
)
