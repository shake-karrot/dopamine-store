# Notification Module

알림 서비스 모듈. Kafka를 통해 타 도메인의 이벤트를 수신하고 알림 요청을 처리합니다.

## 모듈 구조

```
notification/
├── core/     # 순수 비즈니스 로직 (도메인, 포트, 서비스)
├── adapter/  # 외부 연동 (Kafka, Redis, 검증기)
├── worker/   # Kafka Consumer 애플리케이션
└── app/      # API 애플리케이션 (미구현)
```

## Kafka 토픽 설정

### 토픽 목록

| 토픽 | 설명 |
|------|------|
| `notification.requests` | 알림 요청 수신 토픽 |
| `notification.requests.DLQ` | Dead Letter Queue |

### Consumer 설정

| 설정 | 값 |
|------|-----|
| Consumer Group | `notification-consumer-group` |
| Ack Mode | `MANUAL` |
| Concurrency | `1` (단건 처리) |
| Error Handler | DLQ (2회 재시도 후 DLQ 전송) |

## 지원 이벤트 타입

### 1. NEW_USER_REGISTERED

회원가입 완료 알림

```json
{
  "eventId": "evt-001",
  "eventType": "NEW_USER_REGISTERED",
  "occurredAt": "2025-12-30T10:00:00Z",
  "userId": "user-123",
  "email": "test@example.com",
  "userName": "홍길동"
}
```

### 2. PURCHASE_SLOT_ACQUIRED

구매 슬롯 획득 알림 (즉시 + 만료 5분 전 예약)

```json
{
  "eventId": "evt-002",
  "eventType": "PURCHASE_SLOT_ACQUIRED",
  "occurredAt": "2025-12-30T10:00:00Z",
  "userId": "user-456",
  "email": "buyer@example.com",
  "slotId": "slot-789",
  "productId": "prod-001",
  "productName": "한정판 스니커즈",
  "expiresAt": "2025-12-30T10:30:00Z"
}
```

### 3. PASSWORD_RESET_REQUESTED

비밀번호 재설정 알림

```json
{
  "eventId": "evt-003",
  "eventType": "PASSWORD_RESET_REQUESTED",
  "occurredAt": "2025-12-30T10:00:00Z",
  "userId": "user-789",
  "email": "forgot@example.com",
  "resetToken": "reset-token-abc123",
  "expiresAt": "2025-12-30T11:00:00Z"
}
```

## Idempotency

- Redis 기반 중복 처리 방지
- Key: `idempotency:{eventId}`
- TTL: 24시간

## 빌드 및 실행

```bash
# 빌드
./gradlew :worker:build

# 실행
./gradlew :worker:bootRun
```

## 로컬 테스트 환경 구성

테스트 환경 구성 및 사용법은 `demo/` 폴더를 참고하세요.

```bash
cd demo
./start-local-test.sh
```

자세한 내용은 [demo/TESTING.md](demo/TESTING.md)를 확인하세요.

## 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka 브로커 주소 |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis 호스트 |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis 포트 |
