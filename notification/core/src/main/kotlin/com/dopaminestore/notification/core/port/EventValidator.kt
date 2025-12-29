package com.dopaminestore.notification.core.port

import com.dopaminestore.notification.core.domain.DomainEvent

/**
 * 이벤트 검증 인터페이스
 *
 * 이벤트 타입별로 필수 필드 및 비즈니스 규칙 검증
 */
interface EventValidator<T : DomainEvent> {
    /**
     * 이벤트 검증
     *
     * @param event 검증할 이벤트
     * @return 검증 결과
     */
    fun validate(event: T): ValidationResult
}

/**
 * 검증 결과
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()

    data class Invalid(
        val errors: List<String>
    ) : ValidationResult() {
        constructor(error: String) : this(listOf(error))
    }

    fun isValid(): Boolean = this is Valid

    fun getErrorsOrEmpty(): List<String> = when (this) {
        is Valid -> emptyList()
        is Invalid -> errors
    }
}
