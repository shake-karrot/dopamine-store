# Feature Specification: 예약 알림 스케줄링

**Feature Branch**: `notification/006-scheduled-notification`
**Created**: 2025-12-30
**Status**: Draft
**Priority**: P3

## Overview

지정된 시간에 알림을 발송하는 예약 스케줄링 기능을 제공한다.
주요 사용 사례는 구매 슬롯 만료 5분 전 알림처럼 특정 시점에 발송이 필요한 알림이다.

## User Scenarios & Testing

### User Story 1 - 예약 알림 생성 (Priority: P1)

시스템으로서, 즉시 발송이 아닌 특정 시간에 발송이 필요한 알림 요청을 수신하면
해당 시간에 발송되도록 예약 알림을 생성한다.

**Why this priority**: 예약 알림 기능의 기반이 되는 핵심 기능이다.

**Independent Test**: 슬롯 획득 이벤트 수신 후, 만료 5분 전 시간으로 예약 알림이 생성되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 슬롯 획득 이벤트가 수신되었을 때,
   **When** 만료 시간(expiresAt)이 포함된 경우,
   **Then** `expiresAt - 5분`으로 예약된 알림이 생성된다.

2. **Given** 예약 알림이 생성되었을 때,
   **When** 저장소에 저장하면,
   **Then** 상태가 `SCHEDULED`이고 `scheduledAt`에 발송 예정 시간이 기록된다.

3. **Given** 예약 시간이 현재 시간보다 이전인 경우,
   **When** 예약 알림을 생성하려 하면,
   **Then** 예약 대신 즉시 발송 대상으로 처리한다.

4. **Given** 예약 시간까지 남은 시간이 1분 미만인 경우,
   **When** 예약 알림을 생성하려 하면,
   **Then** 예약 대신 즉시 발송 대상으로 처리한다.

---

### User Story 2 - 예약 시간 도래 시 발송 (Priority: P1)

스케줄러로서, 예약된 시간이 도래한 알림을 주기적으로 조회하여 발송 프로세스로 전달한다.

**Why this priority**: 예약된 알림을 실제로 발송하는 핵심 실행 기능이다.

**Independent Test**: 예약 시간이 도래한 알림이 지연 없이 발송되고 상태가 업데이트되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 현재 시간이 예약 시간을 경과했을 때,
   **When** 스케줄러가 발송 대상을 조회하면,
   **Then** 해당 알림이 발송 대상 목록에 포함된다.

2. **Given** 발송 대상으로 조회된 알림에 대해,
   **When** 발송 프로세스로 전달하면,
   **Then** 상태가 `SCHEDULED`에서 `SENDING`으로 변경된다.

3. **Given** 예약 알림 발송이 성공했을 때,
   **When** 결과를 처리하면,
   **Then** 상태가 `SENT`로 변경되고 일반 알림과 동일하게 기록된다.

4. **Given** 예약 알림 발송이 실패했을 때,
   **When** 결과를 처리하면,
   **Then** 재시도 정책에 따라 처리된다.

---

### User Story 3 - 예약 알림 취소 (Priority: P2)

시스템으로서, 예약된 알림이 더 이상 필요하지 않을 때(예: 슬롯 결제 완료, 슬롯 취소)
발송 전에 예약을 취소할 수 있다.

**Why this priority**: 불필요한 알림 발송을 방지하여 사용자 경험을 보호한다.

**Independent Test**: 예약된 알림을 취소하고, 예약 시간이 지나도 발송되지 않는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 예약 알림이 `SCHEDULED` 상태일 때,
   **When** 취소 요청을 받으면,
   **Then** 상태가 `CANCELLED`로 변경되고 발송 대상에서 제외된다.

2. **Given** 슬롯 결제 완료 이벤트가 수신되었을 때,
   **When** 해당 슬롯의 만료 예정 알림이 예약되어 있으면,
   **Then** 자동으로 해당 예약 알림을 취소한다.

3. **Given** 슬롯 취소 이벤트가 수신되었을 때,
   **When** 해당 슬롯의 만료 예정 알림이 예약되어 있으면,
   **Then** 자동으로 해당 예약 알림을 취소한다.

4. **Given** 이미 발송된 알림에 대해 취소를 시도했을 때,
   **When** 상태가 `SENT`인 경우,
   **Then** 취소 실패를 반환하고 상태를 변경하지 않는다.

---

### User Story 4 - 예약 알림 조회 (Priority: P3)

운영자로서, 현재 예약된 알림 목록을 조회하여 시스템 상태를 모니터링할 수 있다.

**Why this priority**: 운영 편의 기능으로, 핵심 스케줄링 로직 구현 후 추가해도 무방하다.

**Independent Test**: 예약된 알림 목록을 조회하여 정확한 결과가 반환되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 여러 예약 알림이 존재할 때,
   **When** 예약 알림 목록을 조회하면,
   **Then** 예약 시간순으로 정렬된 목록이 반환된다.

2. **Given** 특정 사용자의 예약 알림을 조회할 때,
   **When** 사용자 ID로 필터링하면,
   **Then** 해당 사용자의 예약 알림만 반환된다.

---

### Edge Cases

- 스케줄러 다운 중 예약 시간이 지난 알림들? → 스케줄러 재시작 시 지연된 알림 일괄 발송
- 동일 시간에 대량의 예약 알림이 있을 때? → 배치 처리 및 순차 발송으로 부하 분산
- 예약 시간 직전에 시스템 시간이 변경되었을 때? → UTC 기준으로 시간 관리
- 슬롯 만료까지 5분 미만 남았을 때? → 예약 대신 즉시 발송
- 예약과 취소 요청이 동시에 들어올 때? → 취소 요청 우선 처리

## Requirements

### Functional Requirements

- **FR-001**: 시스템은 지정된 시간에 발송되는 예약 알림을 생성할 수 있어야 한다
- **FR-002**: 스케줄러는 매 초마다 발송 대상 알림을 조회해야 한다
- **FR-003**: 예약 시간이 현재보다 이전이거나 1분 미만인 경우 즉시 발송해야 한다
- **FR-004**: 시스템은 예약 알림을 발송 전에 취소할 수 있어야 한다
- **FR-005**: 관련 이벤트(결제 완료, 슬롯 취소 등) 수신 시 예약 알림을 자동 취소해야 한다
- **FR-006**: 모든 시간은 UTC 기준으로 저장하고 처리해야 한다
- **FR-007**: 스케줄러 재시작 시 지연된 예약 알림을 복구해야 한다
- **FR-008**: 예약 알림과 원본 이벤트를 연결하는 correlationId를 관리해야 한다

### Non-Functional Requirements

- **NFR-001**: 예약 시간 대비 발송 지연 < 5초
- **NFR-002**: 스케줄러는 분당 10,000건의 예약 알림을 처리할 수 있어야 한다
- **NFR-003**: 스케줄러 가용률 > 99.9%

### Key Entities

- **ScheduledNotification**: 예약된 알림
  - `id`: UUID
  - `notificationRequestId`: 원본 요청 ID
  - `correlationId`: 연관 이벤트 ID (취소 시 참조용, e.g., slotId)
  - `scheduledAt`: 예약된 발송 시간 (UTC)
  - `status`: 예약 상태
  - `createdAt`: 생성 시간
  - `processedAt`: 처리 완료 시간
  - `cancelledAt`: 취소 시간 (취소된 경우)
  - `cancelReason`: 취소 사유 (취소된 경우)

- **ScheduledStatus**: 예약 알림 상태 (Enum)
  - `SCHEDULED`: 예약됨, 발송 대기 중
  - `PROCESSING`: 발송 프로세스로 전달됨
  - `COMPLETED`: 발송 완료
  - `CANCELLED`: 취소됨
  - `EXPIRED`: 만료됨 (발송 없이 유효 기간 초과)

### Auto-Cancel Triggers

| Trigger Event | Target Notification | Cancel Reason |
|---------------|---------------------|---------------|
| SLOT_PAYMENT_COMPLETED | SLOT_EXPIRING (같은 slotId) | 결제 완료로 인한 취소 |
| SLOT_EXPIRED | SLOT_EXPIRING (같은 slotId) | 슬롯 만료로 인한 취소 |
| SLOT_CANCELLED | SLOT_EXPIRING (같은 slotId) | 슬롯 취소로 인한 취소 |

## Success Criteria

### Measurable Outcomes

- **SC-001**: 예약 시간 대비 발송 지연 p99 < 5초
- **SC-002**: 예약 알림 발송 성공률 > 99%
- **SC-003**: 자동 취소 트리거 정확도 100%
- **SC-004**: 스케줄러 재시작 후 지연 알림 복구율 100%
- **SC-005**: 스케줄러 가용률 > 99.9%
