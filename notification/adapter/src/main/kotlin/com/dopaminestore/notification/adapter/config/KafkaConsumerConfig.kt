package com.dopaminestore.notification.adapter.config

import com.dopaminestore.notification.core.constant.KafkaTopics
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

/**
 * Kafka Consumer 설정
 *
 * - Manual Acknowledgment 모드 사용
 * - 최소 3개 파티션 병렬 처리
 */
@Configuration
class KafkaConsumerConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String
) {

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to KafkaTopics.CONSUMER_GROUP_ID,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        dlqErrorHandler: DlqErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            this.consumerFactory = consumerFactory
            this.setConcurrency(1) // 단건 Consumer
            this.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            this.setCommonErrorHandler(dlqErrorHandler.errorHandler())
        }
    }
}
