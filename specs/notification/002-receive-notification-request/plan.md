# Implementation Plan: 알림 요청 수신 및 검증

**Branch**: `notification/002-receive-notification-request` | **Date**: 2025-12-30 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/notification/002-receive-notification-request/spec.md`

## Summary

Kafka Consumer를 통해 타 도메인(auth, purchase)에서 발행한 이벤트를 수신하고, NotificationRequest 도메인 객체로 변환하여 내부 처리 파이프라인으로 전달한다. 이벤트 검증, 중복 처리 방지(Idempotency), Dead Letter Queue 처리를 포함한다.

## Technical Context

**Language/Version**: Kotlin 1.9.25
**Primary Dependencies**: Spring Boot 3.5.8 (WebFlux), Spring Kafka, Kotlin Coroutines
**Storage**: Redis (Idempotency Key 저장)
**Testing**: JUnit 5, Kotest
**Target Platform**: Linux server (containerized)
**Project Type**: Multi-module (notification 도메인 - core, worker, adapter)
**Performance Goals**: p99 latency < 50ms (이벤트 수신~변환), 100,000 RPS capable
**Constraints**: Consumer는 최소 3 partitions 병렬 처리, 장애 시 5초 내 재시작
**Scale/Scope**: 10만 동시 사용자, auth/purchase 도메인 이벤트 3종 처리

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Concurrency-First** | ✅ PASS | Kafka Consumer 병렬 처리, Redis 기반 Idempotency |
| **II. Domain Ownership** | ✅ PASS | notification 도메인 내에서만 처리, Kafka로만 외부 통신 |
| **III. Prototype-Validate-Harden** | ✅ PASS | MVP로 USER_REGISTERED 먼저 구현 후 확장 |
| **IV. Observability by Default** | ✅ PASS | 구조화된 로그, Trace ID 전파 포함 |
| **V. Fairness Guarantees** | N/A | 알림은 선착순 처리 대상 아님 |
| **Module Dependencies** | ✅ PASS | core ← adapter ← worker 의존성 준수 |

## Project Structure

### Documentation (this feature)

```text
specs/notification/002-receive-notification-request/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Task list (already created)
```

### Source Code (repository root)

```text
notification/
├── build.gradle.kts
├── settings.gradle.kts
│
├── core/                           # 순수 비즈니스 로직
│   └── src/main/kotlin/
│       ├── domain/
│       │   ├── NotificationRequest.kt
│       │   ├── NotificationType.kt
│       │   ├── SendType.kt
│       │   └── DomainEvent.kt
│       ├── usecase/
│       │   └── ReceiveNotificationUseCase.kt
│       ├── service/
│       │   └── ReceiveNotificationService.kt
│       ├── port/
│       │   ├── EventValidator.kt
│       │   ├── NotificationRequestPort.kt
│       │   └── IdempotencyChecker.kt
│       ├── constant/
│       │   └── KafkaTopics.kt
│       └── logging/
│           └── NotificationLogger.kt
│
├── worker/                         # Kafka Consumer
│   └── src/main/kotlin/
│       ├── consumer/
│       │   ├── NewUserRegisteredConsumer.kt
│       │   ├── PurchaseSlotAcquiredConsumer.kt
│       │   ├── PasswordResetRequestedConsumer.kt
│       │   ├── event/
│       │   │   ├── NewUserRegisteredEvent.kt
│       │   │   ├── PurchaseSlotAcquiredEvent.kt
│       │   │   └── PasswordResetRequestedEvent.kt
│       │   ├── mapper/
│       │   │   ├── NewUserRegisteredEventMapper.kt
│       │   │   ├── PurchaseSlotAcquiredEventMapper.kt
│       │   │   └── PasswordResetRequestedEventMapper.kt
│       │   └── handler/
│       │       └── DlqErrorHandler.kt
│       └── config/
│           └── WorkerApplication.kt
│
└── adapter/                        # 외부 연동
    └── src/main/kotlin/
        ├── config/
        │   ├── KafkaConsumerConfig.kt
        │   └── DlqConfig.kt
        ├── validation/
        │   ├── EventValidatorImpl.kt
        │   ├── NewUserRegisteredValidator.kt
        │   ├── PurchaseSlotAcquiredValidator.kt
        │   └── PasswordResetRequestedValidator.kt
        └── idempotency/
            └── IdempotencyCheckerImpl.kt
```

**Structure Decision**: Constitution의 Internal Module Architecture를 따라 core/worker/adapter 3개 모듈로 구성. worker 모듈이 Kafka Consumer를 담당하며, core의 UseCase를 호출하고 adapter의 외부 연동(Redis, Kafka Config)을 사용한다.

## Complexity Tracking

> No violations detected. All design decisions align with Constitution principles.
