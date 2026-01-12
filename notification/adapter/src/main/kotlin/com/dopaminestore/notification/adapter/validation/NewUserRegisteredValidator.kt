package com.dopaminestore.notification.adapter.validation

import com.dopaminestore.notification.adapter.kafka.event.NewUserRegisteredEvent
import com.dopaminestore.notification.core.port.EventValidator
import com.dopaminestore.notification.core.port.ValidationResult
import org.springframework.stereotype.Component

/**
 * NewUserRegisteredEvent 검증기
 *
 * Required: eventId, userId, email, occurredAt
 */
@Component
class NewUserRegisteredValidator : EventValidator<NewUserRegisteredEvent> {

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    override fun validate(event: NewUserRegisteredEvent): ValidationResult {
        val errors = mutableListOf<String>()

        if (event.eventId.isBlank()) {
            errors.add("eventId is required")
        }

        if (event.userId.isBlank()) {
            errors.add("userId is required")
        }

        if (event.email.isBlank()) {
            errors.add("email is required")
        } else if (!emailRegex.matches(event.email)) {
            errors.add("email format is invalid")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
