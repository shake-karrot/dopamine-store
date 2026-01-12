# Notification Service 로컬 테스트 가이드

## 빠른 시작

```bash
# 1. demo 디렉토리로 이동
cd notification/demo

# 2. 인프라 실행 (Kafka, Redis)
./start-local-test.sh

# 3. Worker 실행 (새 터미널, notification 디렉토리에서)
cd notification
./gradlew :worker:bootRun

# 4. 테스트 이벤트 발행 (새 터미널, demo 디렉토리에서)
cd notification/demo
./setup-producer.sh
source venv/bin/activate
python test-producer.py
```

## 상세 가이드

### 1. 인프라 실행

```bash
cd notification
docker-compose up -d
```

실행 확인:
```bash
docker-compose ps
```

예상 출력:
```
notification-kafka      Up
notification-redis      Up
notification-kafka-ui   Up
```

Kafka는 KRaft 모드로 실행되어 Zookeeper가 필요 없습니다.

### 2. Worker 실행

```bash
./gradlew :worker:bootRun
```

정상 실행 시 로그:
```
Started NotificationWorkerApplication in X seconds
Subscribing to topics: [notification.requests]
```

### 3. 테스트 이벤트 발행

#### 방법 1: Python Producer (권장)

```bash
# Virtual environment 활성화 (최초 1회 설정 필요)
# python3 -m venv venv
source venv/bin/activate

# Producer 실행
python test-producer.py
```

대화형 메뉴에서 이벤트 타입 선택:
- `1`: NEW_USER_REGISTERED (회원가입)
- `2`: PASSWORD_RESET_REQUESTED (비밀번호 재설정)
- `3`: PURCHASE_SLOT_ACQUIRED (슬롯 획득)
- `4`: 모든 이벤트 한 번에 발행

#### 방법 2: Kafka UI 사용

1. http://localhost:8080 접속
2. Topics → `notification.requests` 선택
3. "Produce Message" 클릭
4. 아래 예제 JSON 붙여넣기

**NEW_USER_REGISTERED 예제:**
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

**PASSWORD_RESET_REQUESTED 예제:**
```json
{
  "eventId": "evt-002",
  "eventType": "PASSWORD_RESET_REQUESTED",
  "occurredAt": "2025-12-30T10:00:00Z",
  "userId": "user-456",
  "email": "forgot@example.com",
  "resetToken": "reset-token-abc123",
  "expiresAt": "2025-12-30T11:00:00Z"
}
```

**PURCHASE_SLOT_ACQUIRED 예제:**
```json
{
  "eventId": "evt-003",
  "eventType": "PURCHASE_SLOT_ACQUIRED",
  "occurredAt": "2025-12-30T10:00:00Z",
  "userId": "user-789",
  "email": "buyer@example.com",
  "slotId": "slot-123",
  "productId": "prod-001",
  "productName": "한정판 스니커즈",
  "expiresAt": "2025-12-30T10:30:00Z"
}
```

### 4. 결과 확인

#### Worker 로그 확인

정상 처리 시:
```
Received event: NEW_USER_REGISTERED (eventId=evt-001)
Validated event successfully
Created NotificationRequest (id=req-xyz789)
Successfully processed notification request
```

검증 실패 시:
```
Event validation failed: Missing required field 'email'
Moving to DLQ: evt-001
```

#### Kafka UI에서 확인

1. http://localhost:8080 접속
2. Topics → `notification.requests`
   - Offset 증가 확인 (메시지가 consume됨)
3. Topics → `notification.requests.DLQ`
   - 실패한 메시지가 여기로 이동

#### Redis에서 Idempotency 확인

```bash
# Redis CLI 접속
docker exec -it notification-redis redis-cli

# Idempotency 키 확인
KEYS idempotency:*

# 특정 키 조회
GET idempotency:evt-001

# TTL 확인 (24시간 = 86400초)
TTL idempotency:evt-001
```

## 트러블슈팅

### Kafka 연결 실패

```
Error: Connection refused (localhost:9092)
```

해결:
```bash
# Docker Compose가 실행 중인지 확인
docker-compose ps

# Kafka가 준비되지 않았을 수 있음 (20초 대기)
sleep 20

# 재시작
docker-compose restart kafka
```

### Worker가 메시지를 수신하지 않음

1. Worker가 실행 중인지 확인
2. Kafka UI에서 Consumer Group 확인:
   - http://localhost:8080 → Consumers → `notification-consumer-group`
   - Lag이 증가하는지 확인
3. Worker 로그에서 에러 확인

### 중복 이벤트 테스트

같은 `eventId`로 두 번 발행:
```bash
source venv/bin/activate
python test-producer.py
# 1번 선택, 같은 이메일 입력
# 다시 실행해서 1번 선택, 같은 이메일 입력
```

Worker 로그에서 확인:
```
Event already processed: evt-001 (idempotency check)
```

## 정리

```bash
# Worker 중지: Ctrl+C

# Docker Compose 중지
docker-compose down

# 데이터까지 삭제
docker-compose down -v
```

## FAQ

### Q: Producer만 실행하면 테스트되나요?

A: 아니요. Producer는 Kafka에 이벤트를 발행만 하고, Worker가 실제로 이벤트를 수신하고 처리합니다.

테스트 플로우:
1. Docker Compose (인프라)
2. Worker (Consumer)
3. Producer (이벤트 발행)
4. Worker 로그 확인 (처리 결과)

### Q: Kafka UI는 필수인가요?

A: 아니요. 디버깅과 모니터링을 위한 선택사항입니다. Producer 스크립트만으로도 테스트 가능합니다.

### Q: 실제 이메일이 발송되나요?

A: 현재 브랜치(002)에서는 이벤트 수신과 변환만 구현되어 있습니다. 003번 브랜치에서 Gmail SMTP 연동이 추가될 예정입니다.

### Q: DLQ에 메시지가 쌓이면 어떻게 처리하나요?

A: Kafka UI에서 DLQ 토픽을 확인하고, 필요시 메시지를 복사해서 메인 토픽으로 재발행할 수 있습니다.
