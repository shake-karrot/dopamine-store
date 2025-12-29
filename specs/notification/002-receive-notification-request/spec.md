# Feature Specification: 알림 요청 수신 및 검증

**Feature Branch**: `notification/002-receive-notification-request`
**Created**: 2025-12-30
**Status**: Draft
**Priority**: P1 (Critical Path)

## Overview

Kafka Consumer를 통해 타 도메인(auth, purchase)에서 발행한 이벤트를 수신하고,
처리 가능한 알림 요청으로 변환하여 내부 처리 파이프라인으로 전달한다.

## User Scenarios & Testing

### User Story 1 - 회원가입 완료 알림 요청 수신 (Priority: P1)

auth 도메인에서 사용자가 회원가입을 완료하면, 알림 서비스가 해당 이벤트를 수신하여
환영 이메일 발송을 위한 알림 요청으로 변환한다.

**Why this priority**: 회원가입은 사용자 여정의 시작점이며, 환영 이메일은 서비스 신뢰도를 높이는 핵심 기능이다.

**Independent Test**: Kafka에 `NEW_USER_REGISTERED` 이벤트를 발행하고, 알림 서비스가 정상적으로 수신하여 로그에 기록되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** auth 도메인에서 `NEW_USER_REGISTERED` 이벤트가 발행되었을 때,
   **When** 알림 서비스의 Kafka Consumer가 해당 이벤트를 수신하면,
   **Then** 이벤트를 `NotificationRequest` 도메인 객체로 변환하고 즉시 발송 큐에 전달한다.

2. **Given** 이벤트에 필수 필드(userId, email, eventType)가 모두 포함되어 있을 때,
   **When** 이벤트 검증을 수행하면,
   **Then** 검증을 통과하고 다음 처리 단계로 진행한다.

3. **Given** 이벤트에 필수 필드가 누락되었을 때,
   **When** 이벤트 검증을 수행하면,
   **Then** 검증 실패 로그를 기록하고 Dead Letter Queue로 이동시킨다.

---

### User Story 2 - 구매 권한 획득 알림 요청 수신 (Priority: P1)

purchase 도메인에서 사용자가 슬롯을 획득하면, 알림 서비스가 해당 이벤트를 수신하여
즉시 알림과 예약 알림(만료 5분 전) 두 가지 알림 요청을 생성한다.

**Why this priority**: 슬롯 획득은 핵심 비즈니스 이벤트이며, 사용자에게 즉각적인 피드백이 필요하다.

**Independent Test**: Kafka에 `PURCHASE_SLOT_ACQUIRED` 이벤트를 발행하고, 알림 서비스가 두 개의 알림 요청(즉시, 예약)을 생성하는지 확인한다.

**Acceptance Scenarios**:

1. **Given** purchase 도메인에서 `PURCHASE_SLOT_ACQUIRED` 이벤트가 발행되었을 때,
   **When** 알림 서비스의 Kafka Consumer가 해당 이벤트를 수신하면,
   **Then** 즉시 발송용 `NotificationRequest`와 예약 발송용 `ScheduledNotificationRequest`를 각각 생성한다.

2. **Given** `PURCHASE_SLOT_ACQUIRED` 이벤트에 `expiresAt` 필드가 포함되어 있을 때,
   **When** 예약 알림 요청을 생성하면,
   **Then** 발송 예정 시간을 `expiresAt - 5분`으로 설정한다.

---

### User Story 3 - 비밀번호 재설정 알림 요청 수신 (Priority: P2)

auth 도메인에서 비밀번호 재설정이 요청되면, 알림 서비스가 해당 이벤트를 수신하여
재설정 링크가 포함된 이메일 발송 요청을 생성한다.

**Why this priority**: 보안 관련 알림이지만 회원가입/구매보다 빈도가 낮다.

**Independent Test**: Kafka에 `PASSWORD_RESET_REQUESTED` 이벤트를 발행하고, 알림 요청이 생성되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** auth 도메인에서 `PASSWORD_RESET_REQUESTED` 이벤트가 발행되었을 때,
   **When** 알림 서비스의 Kafka Consumer가 해당 이벤트를 수신하면,
   **Then** 즉시 발송용 `NotificationRequest`를 생성하고 `resetToken` 정보를 포함시킨다.

---

### Edge Cases

- 동일한 이벤트가 중복 수신되었을 때? → Idempotency Key로 중복 처리 방지
- Kafka Consumer가 다운된 상태에서 이벤트가 발행되었을 때? → Consumer Group의 offset 관리로 재시작 시 미처리 이벤트 수신
- 이벤트 형식이 예상과 다를 때? → 검증 실패 후 DLQ 이동 및 알림

## Requirements

### Functional Requirements

- **FR-001**: 시스템은 Kafka 토픽 `notification.requests`를 구독하여 이벤트를 수신해야 한다
- **FR-002**: 시스템은 수신된 이벤트를 `NotificationRequest` 도메인 객체로 변환해야 한다
- **FR-003**: 시스템은 이벤트의 필수 필드(userId, email, eventType)를 검증해야 한다
- **FR-004**: 시스템은 검증 실패한 이벤트를 Dead Letter Queue로 이동시켜야 한다
- **FR-005**: 시스템은 이벤트 타입에 따라 즉시/예약 발송을 결정해야 한다
- **FR-006**: 시스템은 Idempotency Key를 통해 중복 이벤트를 필터링해야 한다
- **FR-007**: 시스템은 모든 수신 이벤트에 대해 구조화된 로그를 기록해야 한다

### Non-Functional Requirements

- **NFR-001**: 이벤트 수신부터 변환 완료까지 p99 latency < 50ms
- **NFR-002**: Consumer는 최소 3개의 partition을 병렬 처리할 수 있어야 한다
- **NFR-003**: Consumer 장애 시 5초 이내 재시작되어야 한다

### Key Entities

- **NotificationRequest**: 알림 발송을 위한 내부 도메인 객체
  - `id`: UUID (Idempotency Key)
  - `userId`: 수신자 사용자 ID
  - `email`: 수신자 이메일 주소
  - `notificationType`: 알림 유형 (NEW_USER_REGISTERED, PASSWORD_RESET, SLOT_ACQUIRED, SLOT_EXPIRING)
  - `payload`: 알림 내용 (템플릿 변수들)
  - `sendType`: 발송 유형 (IMMEDIATE, SCHEDULED)
  - `scheduledAt`: 예약 발송 시간 (SCHEDULED인 경우)
  - `createdAt`: 요청 생성 시간

- **DomainEvent**: Kafka로 수신되는 외부 도메인 이벤트
  - `eventId`: 이벤트 고유 ID
  - `eventType`: 이벤트 타입 (NEW_USER_REGISTERED, PURCHASE_SLOT_ACQUIRED 등)
  - `occurredAt`: 이벤트 발생 시간
  - `payload`: 이벤트 페이로드

### Supported Event Types

| Event Type                 | Source Domain | Notification Type        | Send Type |
|----------------------------|---------------|--------------------------|-----------|
| `NEW_USER_REGISTERED`      | auth | NEW_USER_REGISTERED      | IMMEDIATE |
| `PASSWORD_RESET_REQUESTED` | auth | PASSWORD_RESET_REQUESTED | IMMEDIATE |
| `PURCHASE_SLOT_ACQUIRED`   | purchase | PURCHASE_SLOT_ACQUIRED   | IMMEDIATE |
| `PURCHASE_SLOT_ACQUIRED`   | purchase | PURCHASE_SLOT_EXPIRING   | SCHEDULED (expiresAt - 5min) |

## Success Criteria

### Measurable Outcomes

- **SC-001**: 모든 유효한 이벤트가 100% 수신되어 NotificationRequest로 변환된다
- **SC-002**: 이벤트 수신부터 변환까지 p99 latency < 50ms
- **SC-003**: 중복 이벤트 필터링 정확도 100%
- **SC-004**: 검증 실패 이벤트의 DLQ 이동률 100%
- **SC-005**: Consumer 재시작 시 미처리 이벤트 손실률 0%
