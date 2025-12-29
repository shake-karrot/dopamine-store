# Feature Specification: 실패 알림 재시도

**Feature Branch**: `notification/005-retry-failed-notification`
**Created**: 2025-12-30
**Status**: Draft
**Priority**: P2

## Overview

발송 실패한 알림에 대해 지수 백오프(Exponential Backoff) 전략으로 자동 재시도를 수행한다.
최대 재시도 횟수를 초과하면 Dead Letter Queue로 이동시켜 운영자가 수동 처리할 수 있도록 한다.

## User Scenarios & Testing

### User Story 1 - 일시적 실패에 대한 자동 재시도 (Priority: P1)

시스템으로서, 네트워크 오류나 외부 서비스 장애 등 일시적 원인으로 발송 실패한 알림을
자동으로 재시도하여 최종 발송 성공률을 높인다.

**Why this priority**: 일시적 장애로 인한 알림 누락을 방지하는 핵심 복원력(Resilience) 기능이다.

**Independent Test**: 의도적으로 발송 실패를 유발한 후, 재시도가 예약되고 지정된 시간 후 다시 시도되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 알림 발송이 일시적 오류(연결 실패, 서비스 일시 중단 등)로 실패했을 때,
   **When** 재시도 정책을 적용하면,
   **Then** 알림 상태가 `RETRY_SCHEDULED`로 변경되고 다음 재시도 시간이 설정된다.

2. **Given** 첫 번째 재시도가 예약되었을 때,
   **When** 1분 후 재시도를 수행하면,
   **Then** 다시 발송을 시도하고 결과에 따라 상태를 업데이트한다.

3. **Given** 두 번째 재시도도 실패했을 때,
   **When** 지수 백오프를 적용하면,
   **Then** 5분 후로 다음 재시도가 예약된다.

4. **Given** 세 번째 재시도도 실패했을 때,
   **When** 지수 백오프를 적용하면,
   **Then** 15분 후로 다음 재시도가 예약된다.

---

### User Story 2 - 최대 재시도 횟수 초과 시 DLQ 이동 (Priority: P1)

시스템으로서, 최대 재시도 횟수(3회)를 모두 소진한 알림은 Dead Letter Queue로 이동시켜
운영자가 수동으로 확인하고 처리할 수 있도록 한다.

**Why this priority**: 무한 재시도를 방지하고, 수동 개입이 필요한 케이스를 명확히 식별한다.

**Independent Test**: 3회 재시도 실패 후 알림이 DLQ로 이동되고 운영 알람이 발생하는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 알림이 3회 재시도 모두 실패했을 때,
   **When** 최대 재시도 횟수를 초과하면,
   **Then** 알림 상태가 `FAILED`로 확정되고 Dead Letter Queue로 이동한다.

2. **Given** 알림이 DLQ로 이동했을 때,
   **When** 이벤트가 발생하면,
   **Then** `NOTIFICATION_MOVED_TO_DLQ` 로그가 기록되고 운영 알람이 트리거된다.

3. **Given** DLQ에 알림이 존재할 때,
   **When** 운영자가 해당 알림을 확인하면,
   **Then** 전체 재시도 이력과 실패 사유를 조회할 수 있다.

---

### User Story 3 - 영구적 실패의 즉시 처리 (Priority: P2)

시스템으로서, 재시도해도 성공할 수 없는 영구적 실패(잘못된 이메일 형식, 수신 거부 등)는
재시도 없이 즉시 실패 처리하여 불필요한 리소스 낭비를 방지한다.

**Why this priority**: 의미 없는 재시도를 방지하여 시스템 효율성을 높인다.

**Independent Test**: 잘못된 이메일 형식으로 발송 시도 후, 재시도 없이 즉시 FAILED 처리되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 이메일 주소가 유효하지 않아 영구적 실패가 발생했을 때,
   **When** 실패 유형을 분석하면,
   **Then** 재시도 없이 즉시 `PERMANENTLY_FAILED` 상태로 변경한다.

2. **Given** 수신자가 수신 거부 목록에 있는 경우,
   **When** 발송을 시도하면,
   **Then** 발송하지 않고 즉시 `PERMANENTLY_FAILED` 상태로 처리한다.

---

### User Story 4 - DLQ 항목 수동 재처리 (Priority: P3)

운영자로서, DLQ에 있는 알림을 검토한 후 문제가 해결되면 수동으로 재시도를 요청할 수 있다.

**Why this priority**: 운영 편의 기능으로, 핵심 재시도 로직 구현 후 추가해도 무방하다.

**Independent Test**: DLQ 항목을 수동 재처리 요청하고, 정상적으로 재시도 큐에 투입되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** DLQ에 알림이 존재할 때,
   **When** 운영자가 수동 재처리를 요청하면,
   **Then** 재시도 횟수가 초기화되고 즉시 발송 큐에 투입된다.

2. **Given** 수동 재처리가 요청되었을 때,
   **When** 처리 결과를 기록하면,
   **Then** 처리한 운영자 ID와 처리 시간이 함께 기록된다.

---

### Edge Cases

- 재시도 중 동일 알림이 다시 요청되었을 때? → Idempotency Key로 중복 재시도 방지
- 스케줄러 장애로 재시도가 누락되었을 때? → 스케줄러 재시작 시 미처리 재시도 대상 일괄 조회
- DLQ가 가득 찼을 때? → 알람 발생 및 오래된 항목 아카이브 처리
- 재시도 중 시스템 재시작 시? → 상태 기반 복구로 진행 중이던 재시도 재개

## Requirements

### Functional Requirements

- **FR-001**: 시스템은 일시적 실패에 대해 지수 백오프 전략으로 재시도해야 한다
- **FR-002**: 재시도 간격은 1분 → 5분 → 15분으로 증가해야 한다
- **FR-003**: 최대 재시도 횟수는 3회로 제한해야 한다
- **FR-004**: 최대 재시도 초과 시 Dead Letter Queue로 이동해야 한다
- **FR-005**: 영구적 실패(잘못된 이메일, 수신 거부)는 재시도 없이 즉시 실패 처리해야 한다
- **FR-006**: 재시도 시마다 attemptCount를 증가시키고 로그를 기록해야 한다
- **FR-007**: DLQ 이동 시 운영 알람을 트리거해야 한다
- **FR-008**: 운영자는 DLQ 항목을 수동으로 재처리할 수 있어야 한다

### Non-Functional Requirements

- **NFR-001**: 재시도 스케줄러는 1초 주기로 대상을 조회해야 한다
- **NFR-002**: 스케줄러 장애 시 5초 이내 재시작되어야 한다
- **NFR-003**: 동시에 최대 100건의 재시도를 병렬 처리할 수 있어야 한다

### Key Entities

- **RetryPolicy**: 재시도 정책
  - `maxRetryCount`: 최대 재시도 횟수 (기본값: 3)
  - `backoffIntervals`: 재시도 간격 목록 [1분, 5분, 15분]
  - `retryableErrorTypes`: 재시도 가능한 에러 유형 목록

- **FailureType**: 실패 유형 (Enum)
  - `TRANSIENT`: 일시적 실패 - 재시도 가능
    - CONNECTION_TIMEOUT, RATE_LIMITED, SERVICE_UNAVAILABLE
  - `PERMANENT`: 영구적 실패 - 재시도 불가
    - INVALID_EMAIL, RECIPIENT_REJECTED, UNSUBSCRIBED

- **DeadLetterEntry**: DLQ 항목
  - `id`: UUID
  - `notificationRecordId`: 원본 알림 기록 ID
  - `failureType`: 실패 유형
  - `finalFailureReason`: 최종 실패 사유
  - `attemptHistory`: 모든 시도 이력
  - `movedAt`: DLQ 이동 시간
  - `processedAt`: 수동 처리 시간 (nullable)
  - `processedBy`: 처리한 운영자 ID (nullable)

### Retry Schedule

| Attempt | Interval | Cumulative Time |
|---------|----------|-----------------|
| 1차 재시도 | 1분 후 | 1분 |
| 2차 재시도 | 5분 후 | 6분 |
| 3차 재시도 | 15분 후 | 21분 |
| DLQ 이동 | - | 21분 이후 |

## Success Criteria

### Measurable Outcomes

- **SC-001**: 일시적 실패의 재시도 후 최종 성공률 > 95%
- **SC-002**: 영구적 실패의 즉시 처리율 100%
- **SC-003**: DLQ 이동 시 운영 알람 전달율 100%
- **SC-004**: 재시도 스케줄러 가용률 > 99.9%
- **SC-005**: 재시도 간격 준수율 > 99% (±10초 오차 허용)
