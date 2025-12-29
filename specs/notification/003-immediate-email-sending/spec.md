# Feature Specification: 즉시 이메일 발송

**Feature Branch**: `notification/003-immediate-email-sending`
**Created**: 2025-12-30
**Status**: Draft
**Priority**: P1 (Critical Path)

## Overview

수신된 알림 요청을 Gmail SMTP를 통해 즉시 이메일로 발송한다.
이 기능은 알림 서비스의 가장 기본적인 핵심 기능으로, 외부 이메일 서비스와의 연동을 담당한다.

## User Scenarios & Testing

### User Story 1 - 회원가입 환영 이메일 발송 (Priority: P1)

신규 회원에게 환영 이메일을 발송하여 회원가입 완료를 알리고 서비스 이용 안내를 제공한다.

**Why this priority**: 첫 번째 사용자 접점으로, 서비스에 대한 첫인상을 결정한다.

**Independent Test**: NotificationRequest를 생성하고 EmailSender를 호출하여 실제 이메일이 발송되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** WELCOME 타입의 `NotificationRequest`가 생성되었을 때,
   **When** EmailSender가 해당 요청을 처리하면,
   **Then** Gmail SMTP를 통해 수신자에게 환영 이메일이 발송된다.

2. **Given** 이메일 발송이 성공했을 때,
   **When** 발송 결과를 반환하면,
   **Then** `SendResult.SUCCESS`와 함께 messageId가 반환된다.

3. **Given** 유효하지 않은 이메일 주소로 발송을 시도했을 때,
   **When** SMTP 서버가 거부하면,
   **Then** `SendResult.FAILED`와 함께 실패 사유가 반환된다.

---

### User Story 2 - 비밀번호 재설정 이메일 발송 (Priority: P1)

사용자가 비밀번호 재설정을 요청하면 재설정 링크가 포함된 이메일을 발송한다.

**Why this priority**: 보안 관련 기능으로, 사용자가 계정에 접근하기 위해 반드시 필요하다.

**Independent Test**: PASSWORD_RESET 타입의 NotificationRequest로 이메일 발송을 시도하고, 재설정 링크가 포함되어 있는지 확인한다.

**Acceptance Scenarios**:

1. **Given** PASSWORD_RESET 타입의 `NotificationRequest`가 생성되었을 때,
   **When** EmailSender가 해당 요청을 처리하면,
   **Then** payload에 포함된 resetToken으로 생성된 재설정 링크가 이메일 본문에 포함된다.

2. **Given** 재설정 이메일 발송이 성공했을 때,
   **When** 로그를 확인하면,
   **Then** 보안상 resetToken은 마스킹되어 로그에 기록된다.

---

### User Story 3 - 슬롯 획득 축하 이메일 발송 (Priority: P1)

사용자가 구매 슬롯을 획득하면 축하 이메일과 함께 결제 안내를 발송한다.

**Why this priority**: 핵심 비즈니스 이벤트로, 사용자가 다음 단계(결제)로 진행하도록 유도한다.

**Independent Test**: SLOT_ACQUIRED 타입의 NotificationRequest로 이메일 발송을 시도하고, 슬롯 정보와 결제 마감 시간이 포함되어 있는지 확인한다.

**Acceptance Scenarios**:

1. **Given** SLOT_ACQUIRED 타입의 `NotificationRequest`가 생성되었을 때,
   **When** EmailSender가 해당 요청을 처리하면,
   **Then** 슬롯 정보(상품명, 만료 시간)와 결제 링크가 이메일 본문에 포함된다.

2. **Given** 만료 시간이 payload에 포함되어 있을 때,
   **When** 이메일을 생성하면,
   **Then** 만료 시간이 사용자 timezone 기준으로 포맷팅되어 표시된다.

---

### Edge Cases

- Gmail SMTP 서버 연결 실패 시? → Connection Timeout 후 실패 처리, 재시도 대상으로 마킹
- Gmail 일일 발송 한도 초과 시? → Rate Limit 에러 감지 후 백오프
- 이메일 본문이 너무 길 때? → 최대 길이 제한 적용 (100KB)
- 첨부 파일 포함 요청 시? → 현재 버전에서는 미지원, 에러 반환

## Requirements

### Functional Requirements

- **FR-001**: 시스템은 Gmail SMTP 서버(smtp.gmail.com:587)를 통해 이메일을 발송해야 한다
- **FR-002**: 시스템은 TLS를 사용하여 SMTP 연결을 암호화해야 한다
- **FR-003**: 시스템은 NotificationRequest의 notificationType에 따라 적절한 이메일 템플릿을 선택해야 한다
- **FR-004**: 시스템은 발송 성공/실패 결과를 SendResult 객체로 반환해야 한다
- **FR-005**: 시스템은 발송 시도 시 Trace ID를 이메일 헤더(X-Trace-ID)에 포함해야 한다
- **FR-006**: 시스템은 민감 정보(토큰, 비밀번호)를 로그에 마스킹 처리해야 한다
- **FR-007**: 시스템은 이메일 발송 결과를 구조화된 로그로 기록해야 한다

### Non-Functional Requirements

- **NFR-001**: 단일 이메일 발송 p99 latency < 3초
- **NFR-002**: SMTP 연결 timeout: 5초, 읽기 timeout: 10초
- **NFR-003**: Gmail 일일 발송 한도(500통/일) 고려한 Rate Limiting

### Key Entities

- **EmailSender**: 이메일 발송을 담당하는 Port 인터페이스
  - `send(request: EmailRequest): SendResult`

- **EmailRequest**: 이메일 발송에 필요한 정보
  - `to`: 수신자 이메일 주소
  - `subject`: 이메일 제목
  - `body`: 이메일 본문 (HTML)
  - `traceId`: 추적용 ID

- **SendResult**: 발송 결과
  - `status`: SUCCESS, FAILED, RATE_LIMITED
  - `messageId`: SMTP 서버가 반환한 메시지 ID (성공 시)
  - `errorMessage`: 실패 사유 (실패 시)
  - `timestamp`: 발송 시도 시간

- **EmailTemplate**: 알림 유형별 이메일 템플릿
  - `notificationType`: 알림 유형
  - `subject`: 제목 템플릿
  - `bodyTemplate`: 본문 HTML 템플릿
  - `variables`: 치환 가능한 변수 목록

### Email Templates

| Notification Type | Subject | Variables |
|-------------------|---------|-----------|
| WELCOME | [Dopamine Store] 회원가입을 환영합니다! | userName |
| PASSWORD_RESET | [Dopamine Store] 비밀번호 재설정 안내 | userName, resetLink, expiresIn |
| SLOT_ACQUIRED | [Dopamine Store] 구매 권한을 획득하셨습니다! | userName, productName, expiresAt, paymentLink |
| SLOT_EXPIRING | [Dopamine Store] 구매 권한이 곧 만료됩니다 | userName, productName, expiresAt, remainingMinutes |

## Success Criteria

### Measurable Outcomes

- **SC-001**: 유효한 이메일 주소로의 발송 성공률 > 99%
- **SC-002**: 단일 이메일 발송 p99 latency < 3초
- **SC-003**: 발송 실패 시 100% 로그에 기록
- **SC-004**: Gmail Rate Limit 도달 전 선제적 throttling 동작
- **SC-005**: 모든 발송 이메일에 X-Trace-ID 헤더 포함
