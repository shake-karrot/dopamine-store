# Data Model: Notification 모듈 프로젝트 설정

**Feature**: notification/001-project-setup
**Date**: 2025-12-23

## Overview

이 feature는 프로젝트 구조 설정에 관한 것으로, 데이터베이스 엔티티나 테이블이 없습니다. 대신 **모듈 구조와 패키지 구성**을 정의합니다.

## Module Structure

### 1. Core Module

순수 비즈니스 로직을 담당하며, Spring Framework 외 외부 의존성 없음.

```
core/
├── build.gradle.kts
└── src/main/kotlin/com/dopaminestore/notification/core/
    ├── domain/           # Domain entities (POJO)
    │   └── Notification.kt
    ├── usecase/          # UseCase interfaces
    │   └── SendNotificationUseCase.kt
    ├── service/          # Business logic implementations
    │   └── NotificationService.kt
    └── port/             # Port interfaces (for adapter)
        └── NotificationRepository.kt
```

**Key Classes**:
| Class | Type | Description |
|-------|------|-------------|
| `Notification` | Domain | 알림 도메인 엔티티 |
| `SendNotificationUseCase` | Interface | 알림 발송 유스케이스 인터페이스 |
| `NotificationService` | Class | 비즈니스 로직 구현체 |
| `NotificationRepository` | Interface | 저장소 포트 인터페이스 |

---

### 2. Adapter Module

외부 시스템 연동을 담당하며, core 모듈에 의존.

```
adapter/
├── build.gradle.kts
└── src/main/kotlin/com/dopaminestore/notification/adapter/
    ├── persistence/      # DB repositories
    │   └── NotificationRepositoryImpl.kt
    ├── external/         # External API clients
    │   └── EmailClient.kt
    └── config/           # External service configs
        └── PersistenceConfig.kt
```

**Key Classes**:
| Class | Type | Description |
|-------|------|-------------|
| `NotificationRepositoryImpl` | Class | Repository 포트 구현체 |
| `EmailClient` | Class | 외부 이메일 서비스 클라이언트 |
| `PersistenceConfig` | Config | DB 설정 클래스 |

---

### 3. App Module

REST API, gRPC 엔드포인트를 담당하며, core와 adapter에 의존.

```
app/
├── build.gradle.kts
└── src/main/kotlin/com/dopaminestore/notification/app/
    ├── NotificationAppApplication.kt
    ├── controller/       # REST controllers
    │   └── NotificationController.kt
    ├── grpc/             # gRPC services
    │   └── NotificationGrpcService.kt
    └── dto/              # Request/Response DTOs
        ├── SendNotificationRequest.kt
        └── NotificationResponse.kt
```

**Key Classes**:
| Class | Type | Description |
|-------|------|-------------|
| `NotificationAppApplication` | Main | Spring Boot 애플리케이션 진입점 |
| `NotificationController` | Controller | REST API 컨트롤러 |
| `NotificationGrpcService` | Service | gRPC 서비스 |

---

### 4. Worker Module

Kafka Consumer, Scheduled Job을 담당하며, core와 adapter에 의존.

```
worker/
├── build.gradle.kts
└── src/main/kotlin/com/dopaminestore/notification/worker/
    ├── NotificationWorkerApplication.kt
    ├── consumer/         # Kafka consumers
    │   └── NotificationEventConsumer.kt
    └── job/              # Scheduled jobs
        └── NotificationRetryJob.kt
```

**Key Classes**:
| Class | Type | Description |
|-------|------|-------------|
| `NotificationWorkerApplication` | Main | Spring Boot 애플리케이션 진입점 |
| `NotificationEventConsumer` | Consumer | Kafka 메시지 컨슈머 |
| `NotificationRetryJob` | Job | 재시도 스케줄 작업 |

---

## Dependency Graph

```
┌─────────────────────────────────────────────────────────┐
│                      app                                │
│  (REST API, gRPC - Spring Boot Application)             │
└─────────────────────┬───────────────────────────────────┘
                      │ depends on
                      ▼
┌─────────────────────────────────────────────────────────┐
│                    adapter                              │
│  (DB, External Services - Spring Boot Starter)          │
└─────────────────────┬───────────────────────────────────┘
                      │ depends on
                      ▼
┌─────────────────────────────────────────────────────────┐
│                     core                                │
│  (Business Logic - Spring Framework only)               │
└─────────────────────────────────────────────────────────┘
                      ▲
                      │ depends on
┌─────────────────────┴───────────────────────────────────┐
│                    worker                               │
│  (Kafka Consumer, Jobs - Spring Boot Application)       │
└─────────────────────────────────────────────────────────┘
```

## Mock Classes for Validation

이 feature에서 생성할 Mock 클래스 목록:

| Module | Mock Class | Purpose |
|--------|------------|---------|
| core | `MockNotification` | Domain entity 예시 |
| core | `MockUseCase` | UseCase interface 예시 |
| core | `MockService` | Service implementation 예시 |
| core | `MockPort` | Port interface 예시 |
| adapter | `MockRepositoryImpl` | Port 구현체 예시 |
| app | `MockController` | REST controller 예시 |
| worker | `MockConsumer` | Kafka consumer 예시 |

## Constraints

- core 모듈: Spring Framework 라이브러리만 의존 가능 (Spring Boot Starter 불가)
- adapter 모듈: core에만 의존, app/worker에 의존 불가
- app/worker 모듈: core와 adapter에만 의존, 서로 의존 불가
- 순환 의존성: 컴파일 타임에 Gradle이 검출하여 빌드 실패
