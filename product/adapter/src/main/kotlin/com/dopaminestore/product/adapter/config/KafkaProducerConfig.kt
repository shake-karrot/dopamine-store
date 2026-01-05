package com.dopaminestore.product.adapter.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig

/**
 * Kafka producer configuration for publishing domain events.
 *
 * Producer settings optimized for high-throughput event publishing:
 * - Compression: lz4 for fast compression/decompression
 * - Batching: 16KB batch size with 10ms linger time
 * - Acks: all (ensures durability)
 * - Retries: 3 attempts with exponential backoff
 * - Idempotence: enabled to prevent duplicate events
 *
 * Avro serialization with Confluent Schema Registry for type safety and schema evolution.
 */
@Configuration
class KafkaProducerConfig {

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to (System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,

            // Schema Registry configuration for Avro serialization
            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to (System.getenv("SCHEMA_REGISTRY_URL") ?: "http://localhost:8081"),

            // Performance and reliability settings
            ProducerConfig.COMPRESSION_TYPE_CONFIG to "lz4",
            ProducerConfig.BATCH_SIZE_CONFIG to 16384,  // 16KB batches
            ProducerConfig.LINGER_MS_CONFIG to 10,      // Wait up to 10ms to batch messages
            ProducerConfig.ACKS_CONFIG to "all",        // Wait for all replicas
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,  // Prevent duplicate messages

            // Timeout settings
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 30000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 120000,

            // Buffer settings
            ProducerConfig.BUFFER_MEMORY_CONFIG to 33554432,  // 32MB buffer
            ProducerConfig.MAX_BLOCK_MS_CONFIG to 60000       // Max wait time for buffer space
        )

        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(configProps as Map<String, Any>)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }
}
