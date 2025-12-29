# Quickstart: 알림 요청 수신 및 검증

**Feature**: notification/002-receive-notification-request
**Date**: 2025-12-30

이 문서는 기능 검증을 위한 빠른 테스트 시나리오를 제공합니다.

---

## Prerequisites

### 1. Infrastructure Setup

```bash
# Docker Compose로 Kafka, Redis 실행
docker-compose -f docker/docker-compose.dev.yml up -d kafka redis

# Kafka 토픽 생성
docker exec -it kafka kafka-topics.sh --create \
  --topic notification.requests \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092

# DLQ 토픽 생성
docker exec -it kafka kafka-topics.sh --create \
  --topic notification.requests.DLQ \
  --partitions 1 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092
```

### 2. Application Start

```bash
# notification worker 모듈 실행
cd notification
./gradlew :worker:bootRun
```

---

## Test Scenarios

### Scenario 1: 회원가입 완료 알림 (US1 - MVP)

**Given**: Kafka가 실행 중이고 Consumer가 구독 중
**When**: NEW_USER_REGISTERED 이벤트 발행
**Then**: NotificationRequest가 생성되고 로그에 기록됨

```bash
# Kafka 메시지 발행
docker exec -it kafka kafka-console-producer.sh \
  --topic notification.requests \
  --bootstrap-server localhost:9092

# 아래 JSON 입력 후 Enter
{"eventId":"evt-001","eventType":"NEW_USER_REGISTERED","occurredAt":"2025-12-30T10:00:00Z","userId":"user-123","email":"test@example.com","userName":"홍길동"}
```

**Expected Log Output**:
```json
{
  "level": "INFO",
  "eventType": "NOTIFICATION_REQUEST_RECEIVED",
  "eventId": "evt-001",
  "notificationType": "NEW_USER_REGISTERED"
}
{
  "level": "INFO",
  "eventType": "NOTIFICATION_REQUEST_CREATED",
  "notificationId": "...",
  "sendType": "IMMEDIATE"
}
```

---

### Scenario 2: 구매 슬롯 획득 알림 (US2)

**Given**: Kafka가 실행 중이고 Consumer가 구독 중
**When**: PURCHASE_SLOT_ACQUIRED 이벤트 발행
**Then**: 2개의 NotificationRequest가 생성됨 (IMMEDIATE + SCHEDULED)

```bash
# 현재 시간 + 30분 후를 expiresAt으로 설정
docker exec -it kafka kafka-console-producer.sh \
  --topic notification.requests \
  --bootstrap-server localhost:9092

# 아래 JSON 입력
{"eventId":"evt-002","eventType":"PURCHASE_SLOT_ACQUIRED","occurredAt":"2025-12-30T10:00:00Z","userId":"user-456","email":"buyer@example.com","slotId":"slot-789","productId":"prod-001","productName":"한정판 스니커즈","expiresAt":"2025-12-30T10:30:00Z"}
```

**Expected Log Output**:
```json
{
  "level": "INFO",
  "eventType": "NOTIFICATION_REQUEST_CREATED",
  "notificationType": "PURCHASE_SLOT_ACQUIRED",
  "sendType": "IMMEDIATE"
}
{
  "level": "INFO",
  "eventType": "NOTIFICATION_REQUEST_CREATED",
  "notificationType": "PURCHASE_SLOT_EXPIRING",
  "sendType": "SCHEDULED",
  "scheduledAt": "2025-12-30T10:25:00Z"
}
```

---

### Scenario 3: 비밀번호 재설정 알림 (US3)

```bash
docker exec -it kafka kafka-console-producer.sh \
  --topic notification.requests \
  --bootstrap-server localhost:9092

# 아래 JSON 입력
{"eventId":"evt-003","eventType":"PASSWORD_RESET_REQUESTED","occurredAt":"2025-12-30T10:00:00Z","userId":"user-789","email":"forgot@example.com","resetToken":"reset-token-abc123","expiresAt":"2025-12-30T11:00:00Z"}
```

**Expected Log Output**:
```json
{
  "level": "INFO",
  "eventType": "NOTIFICATION_REQUEST_CREATED",
  "notificationType": "PASSWORD_RESET_REQUESTED",
  "sendType": "IMMEDIATE"
}
```

---

### Scenario 4: 중복 이벤트 필터링 (Idempotency)

**Given**: 동일한 eventId로 이벤트가 이미 처리됨
**When**: 같은 eventId로 다시 이벤트 발행
**Then**: 중복으로 감지되어 무시됨

```bash
# 첫 번째 발행 (정상 처리)
{"eventId":"evt-duplicate","eventType":"NEW_USER_REGISTERED","occurredAt":"2025-12-30T10:00:00Z","userId":"user-111","email":"dup@example.com","userName":"중복테스트"}

# 동일한 eventId로 두 번째 발행
{"eventId":"evt-duplicate","eventType":"NEW_USER_REGISTERED","occurredAt":"2025-12-30T10:00:00Z","userId":"user-111","email":"dup@example.com","userName":"중복테스트"}
```

**Expected Log Output (두 번째 발행 시)**:
```json
{
  "level": "INFO",
  "eventType": "NOTIFICATION_REQUEST_DUPLICATE",
  "eventId": "evt-duplicate",
  "message": "Duplicate event ignored"
}
```

**Redis 확인**:
```bash
docker exec -it redis redis-cli GET "idempotency:evt-duplicate"
# 결과: "1"
```

---

### Scenario 5: 검증 실패 → DLQ 이동

**Given**: 필수 필드가 누락된 이벤트
**When**: 이벤트 발행
**Then**: 검증 실패 후 DLQ로 이동

```bash
# 필수 필드(email) 누락
{"eventId":"evt-invalid","eventType":"NEW_USER_REGISTERED","occurredAt":"2025-12-30T10:00:00Z","userId":"user-999","userName":"이메일없음"}
```

**Expected Log Output**:
```json
{
  "level": "ERROR",
  "eventType": "NOTIFICATION_REQUEST_FAILED",
  "eventId": "evt-invalid",
  "reason": "Validation failed: email is required"
}
```

**DLQ 확인**:
```bash
docker exec -it kafka kafka-console-consumer.sh \
  --topic notification.requests.DLQ \
  --from-beginning \
  --bootstrap-server localhost:9092
# 실패한 메시지가 DLQ에 존재해야 함
```

---

## Verification Checklist

| Scenario | Expected | Verification Method |
|----------|----------|---------------------|
| US1: 회원가입 | NotificationRequest 1개 (IMMEDIATE) | 로그 확인 |
| US2: 슬롯 획득 | NotificationRequest 2개 (IMMEDIATE + SCHEDULED) | 로그 확인 |
| US3: 비밀번호 재설정 | NotificationRequest 1개 (IMMEDIATE) | 로그 확인 |
| Idempotency | 중복 이벤트 무시 | Redis key 확인, 로그 확인 |
| Validation Failure | DLQ로 이동 | DLQ 토픽 consume |

---

## Cleanup

```bash
# Docker 컨테이너 정리
docker-compose -f docker/docker-compose.dev.yml down -v
```
