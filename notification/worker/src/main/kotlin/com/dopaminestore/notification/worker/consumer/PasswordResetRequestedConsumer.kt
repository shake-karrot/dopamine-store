package com.dopaminestore.notification.worker.consumer

import com.dopaminestore.notification.adapter.kafka.event.PasswordResetRequestedEvent
import com.dopaminestore.notification.adapter.kafka.mapper.PasswordResetRequestedEventMapper
import com.dopaminestore.notification.adapter.validation.PasswordResetRequestedValidator
import com.dopaminestore.notification.core.constant.KafkaTopics
import com.dopaminestore.notification.core.domain.NotificationType
import com.dopaminestore.notification.core.logging.NotificationLogData
import com.dopaminestore.notification.core.port.ValidationResult
import com.dopaminestore.notification.core.usecase.ReceiveNotificationUseCase
import com.dopaminestore.notification.worker.consumer.handler.ConsumerErrorHandler
import com.dopaminestore.notification.worker.consumer.handler.ValidationException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * PASSWORD_RESET_REQUESTED 이벤트 Consumer
 */
@Component
class PasswordResetRequestedConsumer(
    private val objectMapper: ObjectMapper,
    private val validator: PasswordResetRequestedValidator,
    private val mapper: PasswordResetRequestedEventMapper,
    private val receiveNotificationUseCase: ReceiveNotificationUseCase
) {
    private val logger = LoggerFactory.getLogger(PasswordResetRequestedConsumer::class.java)

    @KafkaListener(
        topics = [KafkaTopics.NOTIFICATION_REQUESTS],
        groupId = KafkaTopics.CONSUMER_GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        val rawMessage = record.value()

        // 1. eventType 확인 (다른 타입이면 무시)
        val eventType = extractEventType(rawMessage)
        if (eventType != "PASSWORD_RESET_REQUESTED") {
            ack.acknowledge()
            return
        }

        // 2. 파싱
        val event = try {
            objectMapper.readValue<PasswordResetRequestedEvent>(rawMessage)
        } catch (e: Exception) {
            ConsumerErrorHandler.logParseFailure(rawMessage, e.message ?: "Unknown parse error")
            throw RuntimeException("Failed to parse PASSWORD_RESET_REQUESTED event", e)
        }

        logger.info(NotificationLogData.received(event.eventId, NotificationType.PASSWORD_RESET_REQUESTED).toLogMessage())

        // 3. 검증
        when (val result = validator.validate(event)) {
            is ValidationResult.Valid -> {
                logger.info(NotificationLogData.validated(event.eventId).toLogMessage())
            }
            is ValidationResult.Invalid -> {
                ConsumerErrorHandler.logValidationFailure(event.eventId, result.errors)
                throw ValidationException(event.eventId, result.errors)
            }
        }

        // 4. 매핑 및 처리
        val notificationRequest = mapper.toNotificationRequest(event)

        runBlocking {
            receiveNotificationUseCase.receive(event.eventId, listOf(notificationRequest))
        }

        // 5. Acknowledge
        ack.acknowledge()
    }

    private fun extractEventType(rawMessage: String): String? {
        return try {
            val node = objectMapper.readTree(rawMessage)
            node.get("eventType")?.asText()
        } catch (e: Exception) {
            null
        }
    }
}
