# Notification Service 데모 환경

로컬에서 Notification Service를 테스트하기 위한 데모 환경입니다.

## 빠른 시작

```bash
# 1. 인프라 실행 (Kafka, Redis)
./start-local-test.sh

# 2. Worker 실행 (새 터미널, notification 디렉토리에서)
cd ..
./gradlew :worker:bootRun

# 3. 테스트 이벤트 발행 (새 터미널, demo 디렉토리에서)
./setup-producer.sh
source venv/bin/activate
python test-producer.py
```

## 파일 구성

| 파일 | 설명 |
|------|------|
| `docker-compose.yml` | Kafka, Redis, Kafka UI 인프라 |
| `test-producer.py` | Kafka 이벤트 발행 Python 스크립트 |
| `requirements.txt` | Python 의존성 |
| `setup-producer.sh` | Producer 환경 자동 설정 스크립트 |
| `start-local-test.sh` | 인프라 실행 스크립트 |
| `TESTING.md` | 상세 테스트 가이드 |

## 인프라 서비스

### Kafka (포트 9092)
- KRaft 모드로 실행 (Zookeeper 불필요)
- 토픽: `notification.requests`, `notification.requests.DLQ`

### Redis (포트 6379)
- Idempotency 체크용

### Kafka UI (포트 8080)
- http://localhost:8080
- 메시지 확인 및 디버깅용 (선택사항)

## 테스트 시나리오

### 1. 회원가입 환영 이메일
```bash
python test-producer.py
# 메뉴에서 1 선택
```

### 2. 비밀번호 재설정
```bash
python test-producer.py
# 메뉴에서 2 선택
```

### 3. 슬롯 획득 알림
```bash
python test-producer.py
# 메뉴에서 3 선택
```

### 4. 모든 이벤트 한번에
```bash
python test-producer.py
# 메뉴에서 4 선택
```

## 정리

```bash
# Worker 중지: Ctrl+C

# 인프라 중지
docker-compose down

# 데이터까지 삭제
docker-compose down -v

# Virtual environment 비활성화
deactivate
```

## 상세 가이드

더 자세한 테스트 방법은 [TESTING.md](TESTING.md)를 참고하세요.
