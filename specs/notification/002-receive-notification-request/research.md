# Research: 알림 요청 수신 및 검증

**Feature**: notification/002-receive-notification-request
**Date**: 2025-12-30

## Overview

이 문서는 Kafka Consumer 기반 알림 요청 수신 기능 구현을 위한 기술 조사 결과를 정리한다.

---

## 1. Spring Kafka Consumer 구현 패턴

### Decision: `@KafkaListener` with Manual Acknowledgment

### Rationale
- Spring Kafka의 `@KafkaListener` 어노테이션이 가장 간결하고 Spring Boot와 자연스럽게 통합됨
- Manual Acknowledgment를 사용하여 처리 실패 시 재시도 가능
- Consumer Group 기반으로 동일 토픽의 여러 파티션을 병렬 처리 가능

### Alternatives Considered
| Alternative | Rejected Because |
|-------------|------------------|
| Reactive Kafka (reactor-kafka) | WebFlux와 통합 가능하나 복잡도 증가, 팀 학습 곡선 |
| Spring Cloud Stream | 추상화 레벨이 높아 세부 제어 어려움 |
| Plain Kafka Consumer API | 보일러플레이트 코드 과다 |

### Implementation Pattern
```kotlin
@KafkaListener(
    topics = [KafkaTopics.NOTIFICATION_REQUESTS],
    groupId = "notification-consumer-group",
    containerFactory = "kafkaListenerContainerFactory"
)
fun consume(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
    // 1. Deserialize event
    // 2. Validate event
    // 3. Check idempotency
    // 4. Transform to NotificationRequest
    // 5. Forward to next stage
    // 6. Acknowledge
    ack.acknowledge()
}
```

---

## 2. Dead Letter Queue (DLQ) 전략

### Decision: Spring Kafka의 `DeadLetterPublishingRecoverer` 사용

### Rationale
- Spring Kafka에서 기본 제공하는 DLQ 메커니즘
- 원본 메시지의 헤더(offset, partition, topic)를 보존하여 추적 용이
- 별도의 DLQ 토픽 설정으로 실패 메시지 분리 관리

### Configuration
```kotlin
@Bean
fun errorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
    val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
        TopicPartition("${record.topic()}.DLQ", record.partition())
    }
    return DefaultErrorHandler(recoverer, FixedBackOff(1000L, 2L))
}
```

### DLQ Topic Naming
- 원본 토픽: `notification.requests`
- DLQ 토픽: `notification.requests.DLQ`

---

## 3. Idempotency 구현 방식

### Decision: Redis 기반 Idempotency Key 저장

### Rationale
- 분산 환경에서 여러 Consumer 인스턴스가 동일한 idempotency 상태 공유 가능
- TTL 설정으로 자동 만료 (메모리 효율)
- Constitution의 "공유 상태는 Redis를 통해 관리" 원칙 준수

### Alternatives Considered
| Alternative | Rejected Because |
|-------------|------------------|
| In-memory Map | 분산 환경에서 공유 불가 |
| Database unique constraint | 쓰기 부하, p99 latency 증가 |
| Kafka exactly-once | 복잡도 높음, 모든 downstream도 exactly-once 필요 |

### Implementation
```kotlin
interface IdempotencyChecker {
    suspend fun isProcessed(eventId: String): Boolean
    suspend fun markAsProcessed(eventId: String, ttlSeconds: Long = 86400)
}

// Redis implementation
class RedisIdempotencyChecker(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) : IdempotencyChecker {
    override suspend fun isProcessed(eventId: String): Boolean {
        return redisTemplate.hasKey("idempotency:$eventId").awaitSingle()
    }

    override suspend fun markAsProcessed(eventId: String, ttlSeconds: Long) {
        redisTemplate.opsForValue()
            .set("idempotency:$eventId", "1", Duration.ofSeconds(ttlSeconds))
            .awaitSingle()
    }
}
```

### TTL Strategy
- 기본 TTL: 24시간 (86400초)
- 이유: 대부분의 재전송은 수분 내 발생, 24시간이면 충분한 중복 방지 윈도우

---

## 4. 이벤트 검증 전략

### Decision: 2단계 검증 (Schema + Business Rule)

### Rationale
- Schema 검증: 필수 필드 존재 여부, 타입 체크 (빠른 실패)
- Business Rule 검증: 도메인 규칙 검증 (이메일 형식, 유효한 eventType 등)

### Validation Flow
```
Event Received
    ↓
[Schema Validation] ─ Fail → DLQ (INVALID_SCHEMA)
    ↓ Pass
[Business Validation] ─ Fail → DLQ (INVALID_BUSINESS_RULE)
    ↓ Pass
[Idempotency Check] ─ Duplicate → Ignore (log only)
    ↓ New
Process Event
```

### Required Fields by Event Type

| Event Type | Required Fields |
|------------|-----------------|
| NEW_USER_REGISTERED | eventId, userId, email, occurredAt |
| PASSWORD_RESET_REQUESTED | eventId, userId, email, resetToken, occurredAt |
| PURCHASE_SLOT_ACQUIRED | eventId, userId, email, slotId, expiresAt, occurredAt |

---

## 5. Structured Logging 패턴

### Decision: Kotlin Logger with MDC (Mapped Diagnostic Context)

### Rationale
- Constitution의 "구조화된 로그" 요구사항 충족
- Trace ID 전파를 위한 MDC 활용
- JSON 형식 로그로 로그 분석 시스템 연동 용이

### Log Event Types (Constitution 준수)
- `NOTIFICATION_REQUEST_RECEIVED`: 이벤트 수신 시
- `NOTIFICATION_REQUEST_VALIDATED`: 검증 통과 시
- `NOTIFICATION_REQUEST_CREATED`: NotificationRequest 생성 시
- `NOTIFICATION_REQUEST_FAILED`: 처리 실패 시
- `NOTIFICATION_REQUEST_DUPLICATE`: 중복 이벤트 감지 시

### Log Format
```json
{
  "timestamp": "2025-12-30T10:00:00.000Z",
  "level": "INFO",
  "logger": "NotificationConsumer",
  "traceId": "abc-123-def",
  "eventType": "NOTIFICATION_REQUEST_RECEIVED",
  "eventId": "evt-456",
  "userId": "user-789",
  "notificationType": "NEW_USER_REGISTERED",
  "latencyMs": 12
}
```

---

~~## 6. Consumer 병렬 처리 설정~~

### Decision: ~~ConcurrentKafkaListenerContainerFactory with 3+ threads~~ Single Consumer

### Rationale
~~- NFR-002 요구사항: "Consumer는 최소 3개의 partition을 병렬 처리"~~
~~- Spring Kafka의 concurrency 설정으로 간단히 구현 가능~~

### Configuration
```kotlin
@Bean
fun kafkaListenerContainerFactory(
    consumerFactory: ConsumerFactory<String, String>
): ConcurrentKafkaListenerContainerFactory<String, String> {
    return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
        this.consumerFactory = consumerFactory
        this.concurrency = 3  // Minimum 3 parallel consumers
        this.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
    }
}
```

---

## 7. Scheduled Notification 처리

### Decision: 즉시 알림과 예약 알림을 별도 처리 경로로 분리

### Rationale
- PURCHASE_SLOT_ACQUIRED 이벤트는 두 개의 NotificationRequest 생성
  1. 즉시 발송 (IMMEDIATE): 슬롯 획득 축하
  2. 예약 발송 (SCHEDULED): 만료 5분 전 알림
- 예약 알림은 별도 저장소에 저장 후 스케줄러가 처리 (다음 feature에서 구현)

### Current Scope (002)
- 예약 알림 요청은 생성만 하고 다음 단계(003-immediate-email-sending, 006-scheduled-notification)에서 처리
- 현재는 로그 기록 및 저장만 수행

---

## Summary

| Topic | Decision                                                |
|-------|---------------------------------------------------------|
| Consumer Pattern | @KafkaListener with Manual Ack                          |
| DLQ Strategy | DeadLetterPublishingRecoverer                           |
| Idempotency | Redis with 24h TTL                                      |
| Validation | 2-stage (Schema + Business)                             |
| Logging | Structured JSON with MDC                                |
| Parallelism | ~~ConcurrentKafkaListenerContainerFactory (3+)~~ Single |
| Scheduled Notification | Create only, defer processing                           |
