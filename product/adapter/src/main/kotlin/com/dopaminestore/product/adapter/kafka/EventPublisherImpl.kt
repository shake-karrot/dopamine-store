package com.dopaminestore.product.adapter.kafka

import com.dopaminestore.product.core.port.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Instant

/**
 * Kafka-based implementation of EventPublisher.
 *
 * Publishes domain events to Kafka topics using Avro serialization.
 * All events are fire-and-forget with error logging only (no business logic failures).
 *
 * **Architecture**:
 * - Uses Spring Kafka KafkaTemplate for reliable message delivery
 * - Avro serialization with Confluent Schema Registry
 * - Events are partitioned by userId or productId for ordering
 *
 * **Performance**:
 * - Async publishing (non-blocking)
 * - Batch mode with 10ms linger time (configured in KafkaProducerConfig)
 * - Compression enabled (lz4)
 * - Idempotent producer (no duplicate events)
 *
 * **Error Handling**:
 * - Failures are logged but do not fail business operations
 * - Kafka retries: 3 attempts with exponential backoff
 * - Circuit breaker: N/A (fail-fast, log, and continue)
 */
@Component
class EventPublisherImpl(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : EventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publishSlotAcquired(event: EventPublisher.SlotAcquiredEvent): Mono<Void> {
        val topic = EventPublisher.TOPIC_SLOT_ACQUIRED
        val key = event.userId.toString() // Partition by userId for ordering

        val avroEvent = mapOf(
            "slotId" to event.slotId.toString(),
            "userId" to event.userId.toString(),
            "productId" to event.productId.toString(),
            "productName" to event.productName,
            "expirationTimestamp" to event.expirationTimestamp.toString(),
            "acquisitionTimestamp" to event.acquisitionTimestamp.toString(),
            "traceId" to event.traceId,
            "timestamp" to Instant.now().toString()
        )

        return publishEvent(topic, key, avroEvent, event.traceId)
            .doOnSuccess {
                logger.info(
                    "[EVENT_PUBLISHED] type=SLOT_ACQUIRED, topic={}, slotId={}, userId={}, traceId={}",
                    topic, event.slotId, event.userId, event.traceId
                )
            }
    }

    override fun publishSlotExpired(event: EventPublisher.SlotExpiredEvent): Mono<Void> {
        val topic = EventPublisher.TOPIC_SLOT_EXPIRED
        val key = event.userId.toString()

        val avroEvent = mapOf(
            "slotId" to event.slotId.toString(),
            "userId" to event.userId.toString(),
            "productId" to event.productId.toString(),
            "productName" to event.productName,
            "expirationTimestamp" to event.expirationTimestamp.toString(),
            "reclaimReason" to event.reclaimReason,
            "traceId" to event.traceId,
            "timestamp" to Instant.now().toString()
        )

        return publishEvent(topic, key, avroEvent, event.traceId)
            .doOnSuccess {
                logger.info(
                    "[EVENT_PUBLISHED] type=SLOT_EXPIRED, topic={}, slotId={}, userId={}, traceId={}",
                    topic, event.slotId, event.userId, event.traceId
                )
            }
    }

    override fun publishPurchaseCompleted(event: EventPublisher.PurchaseCompletedEvent): Mono<Void> {
        val topic = EventPublisher.TOPIC_PURCHASE_COMPLETED
        val key = event.userId.toString()

        val avroEvent = mapOf(
            "purchaseId" to event.purchaseId.toString(),
            "slotId" to event.slotId.toString(),
            "userId" to event.userId.toString(),
            "productId" to event.productId.toString(),
            "productName" to event.productName,
            "amount" to event.amount,
            "currency" to event.currency,
            "paymentId" to event.paymentId,
            "completionTimestamp" to event.completionTimestamp.toString(),
            "traceId" to event.traceId,
            "timestamp" to Instant.now().toString()
        )

        return publishEvent(topic, key, avroEvent, event.traceId)
            .doOnSuccess {
                logger.info(
                    "[EVENT_PUBLISHED] type=PURCHASE_COMPLETED, topic={}, purchaseId={}, userId={}, traceId={}",
                    topic, event.purchaseId, event.userId, event.traceId
                )
            }
    }

    override fun publishPurchaseFailed(event: EventPublisher.PurchaseFailedEvent): Mono<Void> {
        val topic = EventPublisher.TOPIC_PURCHASE_FAILED
        val key = event.userId.toString()

        val avroEvent = mapOf(
            "purchaseId" to event.purchaseId.toString(),
            "slotId" to event.slotId.toString(),
            "userId" to event.userId.toString(),
            "productId" to event.productId.toString(),
            "productName" to event.productName,
            "failureReason" to event.failureReason,
            "failureTimestamp" to event.failureTimestamp.toString(),
            "traceId" to event.traceId,
            "timestamp" to Instant.now().toString()
        )

        return publishEvent(topic, key, avroEvent, event.traceId)
            .doOnSuccess {
                logger.info(
                    "[EVENT_PUBLISHED] type=PURCHASE_FAILED, topic={}, purchaseId={}, userId={}, traceId={}",
                    topic, event.purchaseId, event.userId, event.traceId
                )
            }
    }

    override fun publishSlotExpiringSoon(event: EventPublisher.SlotExpiringSoonEvent): Mono<Void> {
        val topic = EventPublisher.TOPIC_SLOT_EXPIRING_SOON
        val key = event.userId.toString()

        val avroEvent = mapOf(
            "slotId" to event.slotId.toString(),
            "userId" to event.userId.toString(),
            "productId" to event.productId.toString(),
            "productName" to event.productName,
            "expirationTimestamp" to event.expirationTimestamp.toString(),
            "minutesRemaining" to event.minutesRemaining,
            "traceId" to event.traceId,
            "timestamp" to Instant.now().toString()
        )

        return publishEvent(topic, key, avroEvent, event.traceId)
            .doOnSuccess {
                logger.info(
                    "[EVENT_PUBLISHED] type=SLOT_EXPIRING_SOON, topic={}, slotId={}, userId={}, traceId={}",
                    topic, event.slotId, event.userId, event.traceId
                )
            }
    }

    override fun publishEvent(
        topic: String,
        key: String,
        event: Any,
        traceId: String
    ): Mono<Void> {
        return Mono.fromFuture {
            kafkaTemplate.send(topic, key, event)
        }
            .flatMap { result ->
                val metadata = result.recordMetadata
                logger.debug(
                    "[KAFKA_SEND_SUCCESS] topic={}, partition={}, offset={}, key={}, traceId={}",
                    metadata.topic(), metadata.partition(), metadata.offset(), key, traceId
                )
                Mono.empty<Void>()
            }
            .onErrorResume { error ->
                logger.error(
                    "[KAFKA_SEND_FAILED] topic={}, key={}, traceId={}, error={}",
                    topic, key, traceId, error.message, error
                )
                // Don't fail business operation on event publish failure
                Mono.empty()
            }
    }

    override fun publishBatch(events: List<EventPublisher.DomainEvent>): Mono<Void> {
        if (events.isEmpty()) {
            return Mono.empty()
        }

        val startTime = System.currentTimeMillis()
        logger.debug(
            "[KAFKA_BATCH_START] eventCount={}, traceId={}",
            events.size, events.firstOrNull()?.traceId
        )

        // Convert each event to a Mono and merge them
        val publishMonos = events.map { domainEvent ->
            publishEvent(
                topic = domainEvent.topic,
                key = domainEvent.key,
                event = domainEvent.payload,
                traceId = domainEvent.traceId
            )
        }

        return Mono.`when`(publishMonos)
            .doOnSuccess {
                val duration = System.currentTimeMillis() - startTime
                logger.info(
                    "[KAFKA_BATCH_SUCCESS] eventCount={}, duration={}ms, traceId={}",
                    events.size, duration, events.firstOrNull()?.traceId
                )
            }
            .onErrorResume { error ->
                val duration = System.currentTimeMillis() - startTime
                logger.error(
                    "[KAFKA_BATCH_FAILED] eventCount={}, duration={}ms, traceId={}, error={}",
                    events.size, duration, events.firstOrNull()?.traceId, error.message, error
                )
                // Don't fail business operation on batch publish failure
                Mono.empty()
            }
    }
}
