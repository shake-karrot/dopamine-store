package com.dopaminestore.notification.adapter.validation

import com.dopaminestore.notification.core.domain.DomainEvent
import com.dopaminestore.notification.core.port.EventValidator
import com.dopaminestore.notification.core.port.ValidationResult
import org.springframework.stereotype.Component

/**
 * 공통 이벤트 검증 구현
 *
 * 모든 이벤트에 공통으로 적용되는 검증 로직
 */
@Component
class EventValidatorImpl : EventValidator<DomainEvent> {

    override fun validate(event: DomainEvent): ValidationResult {
        val errors = mutableListOf<String>()

        if (event.eventId.isBlank()) {
            errors.add("eventId is required")
        }

        if (event.eventType.isBlank()) {
            errors.add("eventType is required")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
