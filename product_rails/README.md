# Product Rails - FCFS Order Processing System

도파민 스토어의 상품 관리 및 선착순 주문 처리를 위한 Rails API 서비스입니다.

## 프로젝트 개요

이 프로젝트는 Kafka를 활용한 마이크로서비스 아키텍처 기반의 선착순(FCFS) 주문 처리 시스템입니다.

### 주요 기능
- 상품 관리 API
- 선착순 주문 처리 (Kafka 기반)
- 실시간 재고 관리
- 주문 상태 추적

### 기술 스택
- **Backend**: Ruby on Rails 8.0.2 (API 모드)
- **Database**: PostgreSQL 16
- **Message Queue**: Apache Kafka (KRaft 모드)
- **Cache**: Redis 7
- **Consumer Framework**: Karafka 2.4
- **Containerization**: Docker & Docker Compose

## 빠른 시작

### 사전 요구사항
- Docker Desktop 또는 Docker Engine
- Docker Compose v2

### 설치 및 실행

1. **서비스 시작**
   ```bash
   docker compose up -d
   ```

2. **데이터베이스 초기화**
   ```bash
   docker compose exec rails_api bundle exec rails db:create
   docker compose exec rails_api bundle exec rails db:migrate
   ```

3. **헬스체크 확인**
   ```bash
   curl http://localhost:3000/health
   ```

상세한 설정 및 문제 해결 방법은 [SETUP.md](SETUP.md)를 참고하세요.

## 아키텍처

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│             │      │              │      │             │
│  Rails API  │─────▶│    Kafka     │─────▶│    FCFS     │
│   (REST)    │      │  (KRaft)     │      │  Processor  │
│             │      │              │      │  (Karafka)  │
└─────────────┘      └──────────────┘      └─────────────┘
      │                                           │
      │              ┌──────────────┐             │
      └─────────────▶│              │◀────────────┘
                     │  PostgreSQL  │
                     │              │
                     └──────────────┘
```

## 서비스 구성

| 서비스 | 포트 | 설명 |
|--------|------|------|
| Rails API | 3000 | REST API 서버 |
| Kafka | 9092, 9093 | 메시지 브로커 (KRaft 모드) |
| PostgreSQL | 5432 | 데이터베이스 |
| Redis | 6379 | 캐시 및 세션 스토어 |
| FCFS Processor | - | Kafka consumer |

## API 엔드포인트

### 헬스체크
```bash
GET /health
```

응답 예시:
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

## 개발 환경

### 로그 확인
```bash
# 모든 서비스 로그
docker compose logs -f

# 특정 서비스 로그
docker compose logs -f rails_api
docker compose logs -f fcfs_processor
```

### 서비스 재시작
```bash
docker compose restart rails_api
docker compose restart fcfs_processor
```

### 데이터베이스 콘솔
```bash
docker compose exec rails_api bundle exec rails db
```

### Rails 콘솔
```bash
docker compose exec rails_api bundle exec rails console
```

## Kafka KRaft 모드

이 프로젝트는 최신 Kafka KRaft 모드를 사용합니다:
- ✨ ZooKeeper 의존성 제거
- 🚀 더 빠른 시작 시간
- 🔧 간소화된 아키텍처
- 💪 향상된 메타데이터 관리

자세한 내용은 [SETUP.md의 Kafka KRaft 모드 섹션](SETUP.md#kafka-kraft-모드)을 참고하세요.

## 프로젝트 구조

```
product_rails/
├── app/
│   ├── consumers/          # Karafka consumers
│   ├── controllers/        # API controllers
│   ├── models/             # ActiveRecord models
│   └── services/           # Business logic
├── config/
│   ├── database.yml        # DB 설정
│   ├── karafka.rb          # Kafka consumer 설정
│   └── routes.rb           # API routes
├── db/
│   └── migrate/            # Database migrations
├── scripts/
│   └── create_kafka_topics.sh  # Kafka topic 생성 스크립트
├── docker-compose.yml      # Docker 서비스 정의
├── Dockerfile              # Rails 컨테이너 이미지
└── README.md               # 이 파일
```

## 환경 변수

주요 환경 변수는 `docker-compose.yml`에 정의되어 있습니다:
- `DATABASE_HOST`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `REDIS_URL`
- `KAFKA_BROKERS`
- `RAILS_ENV`

## 테스트

```bash
docker compose exec rails_api bundle exec rails test
```

## 문제 해결

일반적인 문제와 해결 방법은 [SETUP.md의 문제 해결 섹션](SETUP.md#문제-해결)을 참고하세요.

## 개발 단계

- [x] **Phase 1**: 프로젝트 초기화 및 Docker 설정
- [ ] **Phase 2**: 데이터베이스 모델 및 마이그레이션
- [ ] **Phase 3**: REST API 엔드포인트
- [ ] **Phase 4**: Kafka 프로듀서/컨슈머 구현
- [ ] **Phase 5**: FCFS 로직 구현
- [ ] **Phase 6**: 테스트 및 최적화

## 문서

- [설치 가이드](SETUP.md) - 상세한 설치 및 설정 방법
- [Phase 1 문서](docs/github_issues/phase_1.md) - 초기 설정 작업 목록

## 라이선스

Copyright (c) 2026 Dopamine Store

## 기여

이슈나 풀 리퀘스트를 통해 기여해주세요.
