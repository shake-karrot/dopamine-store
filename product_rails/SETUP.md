# Product Rails - Setup Guide

## Phase 1 구현 완료

Rails API 프로젝트 초기화 및 Docker 인프라 구성이 완료되었습니다.

## 구성 완료된 항목

### 1. Rails API 프로젝트
- Rails 8.0.2 API 모드로 초기화
- PostgreSQL 데이터베이스 설정
- 필요한 gem 의존성 설치:
  - karafka (~> 2.4) - Kafka 소비자 프레임워크
  - waterdrop (~> 2.7) - Kafka 프로듀서
  - redis (~> 5.0) - 캐싱 및 백그라운드 작업
  - oj (~> 3.16) - JSON 파싱
  - prometheus_exporter (~> 2.1) - 메트릭 수집

### 2. Docker 구성
- `docker-compose.yml`: 전체 인프라 오케스트레이션
  - **Kafka (KRaft 모드)** - 포트 9092, 9093
  - PostgreSQL - 포트 5432
  - Redis - 포트 6379
  - Rails API - 포트 3000
  - FCFS Processor (Karafka consumer)
- `Dockerfile`: Rails 앱 컨테이너 이미지

**중요**: Kafka는 최신 KRaft 모드로 구성되어 있어 ZooKeeper가 필요하지 않습니다.

### 3. Kafka 설정
- **KRaft 모드**: ZooKeeper 없이 독립적으로 실행
- Karafka 설치 및 기본 설정
- 환경 변수 기반 Kafka 브로커 연결
- 토픽 생성 스크립트: `scripts/create_kafka_topics.sh`

### 4. 헬스체크 엔드포인트
- `GET /health`: 전체 서비스 상태 확인
  - Database 연결 상태
  - Redis 연결 상태
  - Kafka 설정 상태

## 실행 방법

### 1. Docker 환경 시작

```bash
docker compose up -d
```

이 명령은 다음 서비스를 시작합니다:
- **Kafka (KRaft 모드)** - 메시지 브로커
- PostgreSQL - 데이터베이스
- Redis - 캐시 및 세션 스토어
- Rails API 서버 - REST API
- FCFS Processor - Karafka consumer

### 2. 데이터베이스 초기화

첫 실행 시 데이터베이스를 생성해야 합니다:

```bash
docker compose exec rails_api bundle exec rails db:create
docker compose exec rails_api bundle exec rails db:migrate
```

### 3. 헬스체크 확인

Rails 서버가 시작되면 헬스체크 엔드포인트로 상태를 확인할 수 있습니다:

```bash
curl http://localhost:3000/health
```

예상 응답:
```json
{
  "status": "ok",
  "timestamp": "2026-01-12T01:30:00Z",
  "services": {
    "database": { "status": "ok" },
    "redis": { "status": "ok" },
    "kafka": { "status": "ok" }
  }
}
```

### 4. Kafka 토픽 확인

Kafka 토픽이 생성되었는지 확인:

```bash
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 5. 로그 확인

특정 서비스의 로그를 확인:

```bash
docker compose logs -f rails_api
docker compose logs -f fcfs_processor
docker compose logs -f kafka
```

### 6. 서비스 중지

```bash
docker compose down
```

데이터를 포함하여 완전히 삭제:

```bash
docker compose down -v
```

## 파일 구조

```
product_rails/
├── app/
│   └── controllers/
│       └── health_controller.rb       # 헬스체크 엔드포인트
├── config/
│   ├── database.yml                   # Docker 환경 변수 설정
│   ├── karafka.rb                     # Karafka 설정
│   └── routes.rb                      # API 라우트
├── scripts/
│   └── create_kafka_topics.sh         # Kafka 토픽 생성 스크립트
├── docker-compose.yml                 # Docker 서비스 정의
├── Dockerfile                         # Rails 앱 컨테이너 이미지
└── Gemfile                           # Ruby 의존성
```

## 환경 변수

Docker Compose에서 사용되는 환경 변수:

- `DATABASE_HOST`: PostgreSQL 호스트 (기본: postgres)
- `DATABASE_USERNAME`: DB 사용자명 (기본: postgres)
- `DATABASE_PASSWORD`: DB 비밀번호 (기본: postgres)
- `DATABASE_NAME`: 데이터베이스 이름 (기본: product_rails_development)
- `REDIS_URL`: Redis 연결 URL (기본: redis://redis:6379/0)
- `KAFKA_BROKERS`: Kafka 브로커 주소 (기본: kafka:9093)
- `RAILS_ENV`: Rails 환경 (기본: development)

## Kafka KRaft 모드

이 프로젝트는 Kafka 3.x 이상의 KRaft 모드를 사용합니다:

### KRaft 모드의 장점
- ✨ **간소화된 아키텍처**: ZooKeeper 의존성 제거
- 🚀 **빠른 시작**: 하나의 컨테이너만 필요
- 🔧 **쉬운 관리**: 메타데이터가 Kafka 자체에 저장
- 💪 **향상된 확장성**: 더 나은 메타데이터 관리

### KRaft vs ZooKeeper
- **이전 방식**: Kafka + ZooKeeper (deprecated)
- **현재 방식**: Kafka (KRaft 모드) - ZooKeeper 없이 독립 실행

Kafka 4.0부터는 ZooKeeper 지원이 완전히 제거될 예정이므로, KRaft 모드가 표준입니다.

## 검증 완료

Phase 1의 모든 검증 기준이 충족되었습니다:
- ✅ docker-compose.yml 구성 완료 (KRaft 모드)
- ✅ Dockerfile 작성 완료
- ✅ Rails 서버 http://localhost:3000 접근 가능
- ✅ Kafka 토픽 생성 스크립트 준비
- ✅ 헬스체크 엔드포인트 구현
- ✅ 모든 서비스 정상 작동 확인

## 다음 단계

Phase 2: 데이터베이스 모델 및 마이그레이션
- Product 모델 생성
- Order 모델 생성
- 관계 설정 및 마이그레이션

## 문제 해결

### Docker credential 에러
이미지 pull 중 credential 에러가 발생하면 이미지를 수동으로 다운로드:

```bash
docker pull confluentinc/cp-kafka:7.5.0
docker pull postgres:16-alpine
docker pull redis:7-alpine
docker pull ruby:3.4.1-slim
```

### 포트 충돌
이미 실행 중인 서비스와 포트가 충돌하는 경우, docker-compose.yml에서 포트를 변경하세요.

### 데이터베이스 연결 오류
PostgreSQL 컨테이너가 완전히 시작될 때까지 기다려야 합니다. `docker compose logs postgres`로 상태를 확인하세요.

### Kafka 연결 오류
Kafka는 시작하는 데 10-15초 정도 걸립니다. 헬스체크가 통과할 때까지 기다리세요:

```bash
docker compose ps  # 모든 서비스가 'healthy' 상태인지 확인
```

### 기존 컨테이너 충돌
기존 컨테이너와 이름이 충돌하는 경우:

```bash
docker compose down
docker ps -a | grep -E 'kafka|postgres|redis' | awk '{print $1}' | xargs docker rm -f
docker compose up -d
```
