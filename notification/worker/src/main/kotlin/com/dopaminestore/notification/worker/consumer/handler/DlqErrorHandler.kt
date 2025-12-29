package com.dopaminestore.notification.worker.consumer.handler

import org.slf4j.LoggerFactory

/**
 * Consumer에서 발생하는 예외 처리를 위한 핸들러
 *
 * 검증 실패 시 DLQ로 전송될 예외를 생성
 */
class ValidationException(
    val eventId: String,
    val errors: List<String>
) : RuntimeException("Validation failed for eventId=$eventId: ${errors.joinToString(", ")}")

object ConsumerErrorHandler {
    private val logger = LoggerFactory.getLogger(ConsumerErrorHandler::class.java)

    fun logValidationFailure(eventId: String, errors: List<String>) {
        logger.error(
            "eventType=NOTIFICATION_REQUEST_FAILED, eventId={}, reason=Validation failed: {}",
            eventId,
            errors.joinToString(", ")
        )
    }

    fun logParseFailure(rawMessage: String, error: String) {
        logger.error(
            "eventType=NOTIFICATION_REQUEST_FAILED, reason=Parse failed: {}, message={}",
            error,
            rawMessage.take(200)
        )
    }
}
