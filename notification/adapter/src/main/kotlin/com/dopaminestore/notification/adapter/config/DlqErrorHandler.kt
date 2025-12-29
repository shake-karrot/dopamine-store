package com.dopaminestore.notification.adapter.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

/**
 * Dead Letter Queue 설정
 *
 * - 검증 실패 또는 처리 실패 시 DLQ로 메시지 이동
 * - 원본 토픽명.DLQ 형식으로 DLQ 토픽 결정
 * - 2회 재시도 후 DLQ로 이동
 */
@Configuration
class DlqConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String
) {
    private val logger = LoggerFactory.getLogger(DlqConfig::class.java)

    @Bean
    fun dlqProducerFactory(): ProducerFactory<String, String> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun dlqKafkaTemplate(dlqProducerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        return KafkaTemplate(dlqProducerFactory)
    }
}

/**
 * DLQ Error Handler Bean
 */
@Configuration
class DlqErrorHandler(
    private val dlqKafkaTemplate: KafkaTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(DlqErrorHandler::class.java)

    @Bean
    fun errorHandler(): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(dlqKafkaTemplate) { record, exception ->
            logger.error(
                "Sending message to DLQ. topic={}, partition={}, offset={}, error={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception.message
            )
            TopicPartition("${record.topic()}.DLQ", record.partition())
        }

        // 1초 간격으로 2회 재시도 후 DLQ로 이동
        return DefaultErrorHandler(recoverer, FixedBackOff(1000L, 2L))
    }
}
