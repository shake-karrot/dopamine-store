# Feature Specification: 다중 채널 확장

**Feature Branch**: `notification/007-multi-channel-support`
**Created**: 2025-12-30
**Status**: Draft
**Priority**: P4

## Overview

이메일 외에 Slack, Discord 등 다양한 외부 채널을 통해 알림을 발송할 수 있도록
채널 추상화 계층을 제공한다. 이를 통해 새로운 채널 추가 시 핵심 로직 변경 없이 확장할 수 있다.

## User Scenarios & Testing

### User Story 1 - 채널 추상화 인터페이스 구현 (Priority: P1)

개발자로서, 새로운 알림 채널을 추가할 때 공통 인터페이스를 구현하기만 하면
기존 알림 파이프라인과 자동으로 연동되도록 한다.

**Why this priority**: 다중 채널 지원의 기반이 되는 아키텍처 설계이다.

**Independent Test**: NotificationSender 인터페이스를 구현한 Mock 채널을 추가하고, 기존 파이프라인에서 정상 동작하는지 확인한다.

**Acceptance Scenarios**:

1. **Given** NotificationSender 인터페이스가 정의되어 있을 때,
   **When** 새로운 채널 구현체를 추가하면,
   **Then** 별도의 파이프라인 수정 없이 해당 채널로 알림을 발송할 수 있다.

2. **Given** 여러 채널 구현체가 등록되어 있을 때,
   **When** 알림 요청의 channel 필드에 따라 라우팅하면,
   **Then** 지정된 채널의 구현체가 호출된다.

3. **Given** 등록되지 않은 채널로 발송을 시도했을 때,
   **When** 채널 조회에 실패하면,
   **Then** `UNSUPPORTED_CHANNEL` 에러를 반환하고 로그에 기록한다.

---

### User Story 2 - Slack 채널 연동 (Priority: P2)

사용자로서, 중요한 알림을 Slack 채널로도 받아볼 수 있어 업무 도구에서 바로 확인할 수 있다.

**Why this priority**: 실시간 협업 도구로 가장 널리 사용되는 채널이다.

**Independent Test**: Slack Webhook URL로 테스트 메시지를 발송하고 Slack 채널에 메시지가 표시되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** Slack Webhook URL이 설정되어 있을 때,
   **When** Slack 채널로 알림 발송을 요청하면,
   **Then** Webhook을 통해 지정된 Slack 채널에 메시지가 전송된다.

2. **Given** 알림 유형에 따른 Slack 메시지 템플릿이 존재할 때,
   **When** 알림을 발송하면,
   **Then** 알림 유형에 맞는 포맷(색상, 아이콘, 필드)으로 메시지가 구성된다.

3. **Given** Slack Webhook 호출이 실패했을 때,
   **When** 에러를 처리하면,
   **Then** 재시도 정책에 따라 처리되고 실패 사유가 기록된다.

---

### User Story 3 - Discord 채널 연동 (Priority: P3)

사용자로서, Discord 서버에서 알림을 받아볼 수 있어 커뮤니티 채널에서 바로 확인할 수 있다.

**Why this priority**: 게이밍/커뮤니티 분야에서 널리 사용되는 채널이다.

**Independent Test**: Discord Webhook URL로 테스트 메시지를 발송하고 Discord 채널에 메시지가 표시되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** Discord Webhook URL이 설정되어 있을 때,
   **When** Discord 채널로 알림 발송을 요청하면,
   **Then** Webhook을 통해 지정된 Discord 채널에 메시지가 전송된다.

2. **Given** 알림 유형에 따른 Discord Embed 템플릿이 존재할 때,
   **When** 알림을 발송하면,
   **Then** Embed 형식(제목, 설명, 색상, 필드)으로 메시지가 구성된다.

3. **Given** Discord Webhook 호출이 실패했을 때,
   **When** 에러를 처리하면,
   **Then** 재시도 정책에 따라 처리되고 실패 사유가 기록된다.

---

### User Story 4 - 알림 유형별 채널 매핑 설정 (Priority: P2)

시스템 관리자로서, 알림 유형별로 발송할 채널을 설정하여 적절한 채널로 알림이 전달되도록 한다.

**Why this priority**: 유연한 채널 라우팅을 위한 설정 기능이다.

**Independent Test**: 알림 유형별 채널 매핑을 설정하고, 해당 유형의 알림이 매핑된 채널로 발송되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 알림 유형별 채널 매핑이 설정되어 있을 때,
   **When** 알림 요청이 들어오면,
   **Then** 매핑된 모든 채널로 알림이 발송된다.

2. **Given** 하나의 알림 유형에 여러 채널이 매핑되어 있을 때,
   **When** 알림을 발송하면,
   **Then** 모든 매핑된 채널에 병렬로 발송된다.

3. **Given** 매핑된 채널이 없는 알림 유형인 경우,
   **When** 알림을 발송하려 하면,
   **Then** 기본 채널(EMAIL)로 발송한다.

---

### User Story 5 - 사용자별 채널 선호 설정 (Priority: P4)

사용자로서, 알림을 받고 싶은 채널을 직접 선택하여 원하는 방식으로 알림을 받을 수 있다.

**Why this priority**: 사용자 개인화 기능으로, 핵심 채널 연동 후 추가해도 무방하다.

**Independent Test**: 사용자 채널 선호도를 설정하고, 알림이 선호 채널로만 발송되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 사용자가 Slack 채널만 선호로 설정했을 때,
   **When** 해당 사용자에게 알림을 발송하면,
   **Then** Slack으로만 발송하고 이메일로는 발송하지 않는다.

2. **Given** 사용자가 채널 선호를 설정하지 않았을 때,
   **When** 알림을 발송하면,
   **Then** 시스템 기본 채널(EMAIL)로 발송한다.

---

### Edge Cases

- 여러 채널 중 일부만 발송 성공했을 때? → 성공한 채널은 SENT, 실패한 채널은 개별 재시도
- Webhook URL이 유효하지 않을 때? → 설정 검증 단계에서 사전 차단
- 채널 서비스(Slack/Discord)가 다운되었을 때? → 재시도 정책 적용, 최종 실패 시 DLQ
- 동일 알림을 여러 채널로 발송할 때 일부만 성공? → 채널별 독립적인 상태 관리
- 새로운 채널을 동적으로 추가할 때? → 애플리케이션 재시작 없이 설정 반영

## Requirements

### Functional Requirements

- **FR-001**: 시스템은 공통 인터페이스(NotificationSender)를 통해 다양한 채널을 지원해야 한다
- **FR-002**: 시스템은 Slack Webhook을 통해 알림을 발송할 수 있어야 한다
- **FR-003**: 시스템은 Discord Webhook을 통해 알림을 발송할 수 있어야 한다
- **FR-004**: 시스템은 알림 유형별로 발송할 채널을 매핑할 수 있어야 한다
- **FR-005**: 시스템은 하나의 알림을 여러 채널로 동시에 발송할 수 있어야 한다
- **FR-006**: 시스템은 채널별 메시지 템플릿을 관리해야 한다
- **FR-007**: 시스템은 사용자별 채널 선호도를 저장하고 반영해야 한다
- **FR-008**: 시스템은 채널별 발송 결과를 독립적으로 추적해야 한다

### Non-Functional Requirements

- **NFR-001**: 새로운 채널 추가 시 핵심 비즈니스 로직 수정 불필요
- **NFR-002**: 다중 채널 발송 시 병렬 처리로 총 지연 시간 최소화
- **NFR-003**: 채널별 Rate Limit 준수 (Slack: 1msg/sec, Discord: 30msg/min)

### Key Entities

- **NotificationSender**: 채널 공통 인터페이스
  - `send(request: ChannelRequest): SendResult`
  - `supports(channel: Channel): Boolean`

- **Channel**: 발송 채널 (Enum)
  - `EMAIL`: 이메일 (Gmail SMTP)
  - `SLACK`: Slack Webhook
  - `DISCORD`: Discord Webhook

- **ChannelConfig**: 채널별 설정
  - `channel`: 채널 유형
  - `enabled`: 활성화 여부
  - `webhookUrl`: Webhook URL (Slack/Discord)
  - `rateLimit`: 초당/분당 발송 제한
  - `templateMapping`: 알림 유형별 템플릿 ID 매핑

- **NotificationTypeChannelMapping**: 알림 유형별 채널 매핑
  - `notificationType`: 알림 유형
  - `channels`: 발송할 채널 목록
  - `priority`: 우선순위 (fallback 순서)

- **UserChannelPreference**: 사용자 채널 선호도
  - `userId`: 사용자 ID
  - `preferredChannels`: 선호 채널 목록
  - `disabledChannels`: 비활성화한 채널 목록

### Channel Message Templates

| Channel | Format | Features |
|---------|--------|----------|
| EMAIL | HTML | 리치 텍스트, 링크, 이미지 |
| SLACK | Block Kit | 버튼, 필드, 색상, 이모지 |
| DISCORD | Embed | 제목, 설명, 필드, 색상, 썸네일 |

## Success Criteria

### Measurable Outcomes

- **SC-001**: 새로운 채널 추가 시 기존 코드 수정 없이 구현체 추가만으로 연동
- **SC-002**: 다중 채널 동시 발송 성공률 > 99%
- **SC-003**: 채널별 Rate Limit 위반율 0%
- **SC-004**: 부분 성공 시 성공한 채널 기록률 100%
- **SC-005**: 사용자 채널 선호도 반영 정확도 100%

## Assumptions

- Slack과 Discord는 Incoming Webhook 방식으로 연동한다
- 채널별 Webhook URL은 환경 설정으로 관리한다
- 초기 버전에서는 사용자별 채널 선호도보다 시스템 기본 매핑을 우선한다
