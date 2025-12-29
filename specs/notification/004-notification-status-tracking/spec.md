# Feature Specification: 발송 상태 관리 및 로깅

**Feature Branch**: `notification/004-notification-status-tracking`
**Created**: 2025-12-30
**Status**: Draft
**Priority**: P2

## Overview

모든 알림의 발송 상태를 추적하고 저장하여 감사(Audit) 목적의 로그를 제공한다.
발송 성공/실패 여부를 기록하고, 실패한 알림의 재시도 대상 여부를 판단한다.

## User Scenarios & Testing

### User Story 1 - 알림 발송 상태 저장 (Priority: P1)

시스템 운영자로서, 모든 알림의 발송 시도와 결과를 추적하여 문제 발생 시 원인을 파악하고 감사 목적의 기록을 유지한다.

**Why this priority**: 발송 실패 원인 파악과 재시도 로직의 기반이 되는 핵심 기능이다.

**Independent Test**: 알림 발송 후 DB에서 해당 알림의 상태를 조회하여 정확히 기록되었는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 알림 발송 요청이 생성되었을 때,
   **When** 발송 프로세스가 시작되면,
   **Then** 알림 상태가 `PENDING`에서 `SENDING`으로 변경되고 DB에 저장된다.

2. **Given** 이메일 발송이 성공했을 때,
   **When** SendResult가 SUCCESS를 반환하면,
   **Then** 알림 상태가 `SENT`로 변경되고 messageId와 sentAt이 기록된다.

3. **Given** 이메일 발송이 실패했을 때,
   **When** SendResult가 FAILED를 반환하면,
   **Then** 알림 상태가 `FAILED`로 변경되고 failedReason과 failedAt이 기록된다.

---

### User Story 2 - 구조화된 로그 출력 (Priority: P1)

시스템 운영자로서, 알림 발송 이벤트를 구조화된 로그로 기록하여 로그 분석 시스템에서 쉽게 검색하고 분석할 수 있다.

**Why this priority**: Observability 원칙에 따라 모든 핵심 이벤트는 구조화된 로그로 기록되어야 한다.

**Independent Test**: 알림 발송 후 로그를 확인하여 필수 필드(traceId, notificationId, status, timestamp)가 포함되어 있는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 알림이 성공적으로 발송되었을 때,
   **When** 로그를 출력하면,
   **Then** `NOTIFICATION_SENT` 이벤트가 traceId, notificationId, recipientEmail(마스킹), channel, latencyMs와 함께 기록된다.

2. **Given** 알림 발송이 실패했을 때,
   **When** 로그를 출력하면,
   **Then** `NOTIFICATION_FAILED` 이벤트가 failedReason, attemptCount와 함께 기록된다.

3. **Given** 알림이 재시도되었을 때,
   **When** 로그를 출력하면,
   **Then** `NOTIFICATION_RETRIED` 이벤트가 retryCount, nextRetryAt과 함께 기록된다.

---

### User Story 3 - 알림 발송 이력 조회 (Priority: P2)

시스템 운영자로서, 특정 사용자나 기간의 알림 발송 이력을 조회하여 문제 해결이나 통계 분석에 활용한다.

**Why this priority**: 운영 편의성을 위한 기능으로, 핵심 발송 기능 이후 구현해도 무방하다.

**Independent Test**: 사용자 ID나 기간으로 알림 이력을 조회하여 정확한 결과가 반환되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 특정 사용자에게 여러 알림이 발송되었을 때,
   **When** 해당 사용자 ID로 발송 이력을 조회하면,
   **Then** 해당 사용자의 모든 알림 발송 이력이 최신순으로 반환된다.

2. **Given** 특정 기간 동안 알림이 발송되었을 때,
   **When** 시작일과 종료일로 발송 이력을 조회하면,
   **Then** 해당 기간의 모든 알림 발송 이력이 반환된다.

---

### Edge Cases

- 상태 업데이트 중 DB 장애 발생 시? → 재시도 후 최종 실패 시 로그에 경고 기록
- 동일 알림에 대한 동시 상태 업데이트 시? → Optimistic Locking으로 충돌 방지
- 대량의 알림 이력으로 인한 저장소 부담? → 90일 이후 이력은 아카이브 테이블로 이동

## Requirements

### Functional Requirements

- **FR-001**: 시스템은 모든 알림에 대해 발송 상태를 DB에 저장해야 한다
- **FR-002**: 시스템은 상태 변경 시마다 상태 이력(StatusHistory)을 기록해야 한다
- **FR-003**: 시스템은 Constitution에서 정의한 이벤트 형식으로 구조화된 로그를 출력해야 한다
- **FR-004**: 시스템은 사용자 ID, 기간, 상태 등 다양한 조건으로 발송 이력을 조회할 수 있어야 한다
- **FR-005**: 시스템은 이메일 주소 등 개인정보를 로그에 마스킹 처리해야 한다
- **FR-006**: 시스템은 90일 이후 이력을 아카이브 테이블로 이동해야 한다

### Non-Functional Requirements

- **NFR-001**: 상태 업데이트 p99 latency < 10ms
- **NFR-002**: 발송 이력 조회 p99 latency < 100ms (1000건 기준)
- **NFR-003**: 90일 이력 보관, 이후 아카이브

### Key Entities

- **NotificationStatus**: 알림 발송 상태 (Enum)
  - `PENDING`: 발송 대기 중
  - `SENDING`: 발송 진행 중
  - `SENT`: 발송 성공
  - `FAILED`: 발송 실패
  - `RETRY_SCHEDULED`: 재시도 예약됨

- **NotificationRecord**: 알림 발송 기록
  - `id`: UUID
  - `notificationRequestId`: 원본 요청 ID
  - `userId`: 수신자 사용자 ID
  - `recipientEmail`: 수신자 이메일 (마스킹 저장)
  - `notificationType`: 알림 유형
  - `channel`: 발송 채널 (EMAIL, SLACK, DISCORD)
  - `status`: 현재 상태
  - `messageId`: 외부 서비스 메시지 ID
  - `attemptCount`: 발송 시도 횟수
  - `failedReason`: 실패 사유
  - `createdAt`: 생성 시간
  - `sentAt`: 발송 성공 시간
  - `failedAt`: 최종 실패 시간
  - `version`: Optimistic Locking용 버전

- **StatusHistory**: 상태 변경 이력
  - `id`: UUID
  - `notificationRecordId`: 알림 기록 ID
  - `previousStatus`: 이전 상태
  - `newStatus`: 새 상태
  - `changedAt`: 변경 시간
  - `reason`: 변경 사유

### Log Event Format

```json
{
  "eventType": "NOTIFICATION_SENT",
  "traceId": "abc-123-def",
  "notificationId": "uuid-here",
  "userId": "user-123",
  "recipientEmail": "j***@example.com",
  "notificationType": "WELCOME",
  "channel": "EMAIL",
  "latencyMs": 1523,
  "timestamp": "2025-12-30T10:00:00Z"
}
```

## Success Criteria

### Measurable Outcomes

- **SC-001**: 모든 알림 발송 시도가 100% DB에 기록된다
- **SC-002**: 상태 업데이트 p99 latency < 10ms
- **SC-003**: 구조화된 로그에 필수 필드가 100% 포함된다
- **SC-004**: 개인정보(이메일) 마스킹 처리율 100%
- **SC-005**: 5분 이내 장애 원인 파악 가능한 로그 품질
