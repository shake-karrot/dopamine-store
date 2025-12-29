<!--
  ============================================================================
  SYNC IMPACT REPORT
  ============================================================================
  Version Change: 1.1.0 → 1.2.0 (MINOR - module rename: purchase → product)

  Modified Principles: None

  Added Sections: None

  Removed Sections: None

  Modified Sections:
    - Multi-Module Structure: purchase → product 모듈명 변경
    - Module Dependencies Rules: purchase → product
    - Branch Naming Convention: purchase → product 예시 변경
    - Module Responsibilities: purchase → product
    - Critical Paths by Module: purchase → product
    - Required Events to Log: purchase → product
    - Performance Targets: purchase → product
    - Branch Strategy: purchase → product

  Rationale:
    - purchase 모듈이 Product, PurchaseSlot, Purchase 도메인을 담당하므로
    - 상품(Product) 중심의 도메인 명명이 더 직관적

  Templates Verification:
    - .specify/templates/plan-template.md: ✅ Compatible
    - .specify/templates/spec-template.md: ✅ Compatible
    - .specify/templates/tasks-template.md: ✅ Compatible
    - .specify/templates/checklist-template.md: ✅ Compatible

  Follow-up TODOs: None
  ============================================================================
-->

# Dopamine Store Constitution

> 이 Constitution은 모든 모듈(notification, product, auth)이 따라야 할 공통 원칙을 정의합니다.

## Multi-Module Structure

### Repository Overview

dopamine-store는 하나의 레포지토리 아래 3개의 독립적인 멀티모듈 프로젝트로 구성된다.
각 프로젝트는 MSA처럼 취급되며, 서로 의존성이 없고 독립적으로 배포 가능하다.

```
dopamine-store/
├── .specify/                    # 공통 Constitution 및 템플릿
│   ├── memory/
│   │   └── constitution.md      # 이 파일 (전체 공통 원칙)
│   └── templates/               # 공통 템플릿
│
├── specs/                       # 모든 팀의 specification 저장소
│   ├── notification/
│   ├── product/
│   └── auth/
│
├── notification/                # 알림 도메인 (독립 프로젝트)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── core/
│   ├── app/
│   ├── worker/
│   └── adapter/
│
├── product/                     # 상품/구매 도메인 (독립 프로젝트)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── core/
│   ├── app/
│   ├── worker/
│   └── adapter/
│
├── auth/                        # 인증 도메인 (독립 프로젝트)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── core/
│   ├── app/
│   ├── worker/
│   └── adapter/
│
└── shared/                      # 공유 코드 (이벤트 스키마 등)
    └── events/
```

### Internal Module Architecture

각 도메인 프로젝트(notification, product, auth)는 동일한 내부 모듈 구조를 따른다:

| Module | Responsibility | Dependencies |
|--------|----------------|--------------|
| **core** | 순수 비즈니스 로직. UseCase interface 정의, Service 구현 | Spring Framework까지만 허용 |
| **app** | REST API, gRPC 엔드포인트. Controller만 포함 | core, adapter |
| **worker** | Kafka Consumer, 비동기 작업 처리 | core, adapter |
| **adapter** | DB, 외부 서비스 연동. Repository 구현, External API Client, Config | core |

```
{domain}/
├── core/                        # 순수 비즈니스 로직
│   └── src/main/kotlin/
│       ├── usecase/             # UseCase interfaces
│       ├── service/             # Business logic implementation
│       ├── domain/              # Domain entities
│       └── port/                # Port interfaces (for adapter)
│
├── app/                         # REST API, gRPC
│   └── src/main/kotlin/
│       ├── controller/          # REST controllers
│       ├── grpc/                # gRPC services
│       └── dto/                 # Request/Response DTOs
│
├── worker/                      # Consumer 처리
│   └── src/main/kotlin/
│       ├── consumer/            # Kafka consumers
│       └── job/                 # Scheduled jobs
│
└── adapter/                     # 외부 연동
    └── src/main/kotlin/
        ├── persistence/         # DB repositories
        ├── external/            # External API clients
        └── config/              # External service configs
```

### Module Dependencies Rules

**프로젝트 간 의존성 (MUST NOT)**:
- notification, product, auth 프로젝트 간 직접 의존성 금지
- 모든 프로젝트 간 통신은 Kafka 이벤트를 통해서만 수행

**내부 모듈 간 의존성 (build.gradle.kts)**:
```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":core"))
    implementation(project(":adapter"))
}

// worker/build.gradle.kts
dependencies {
    implementation(project(":core"))
    implementation(project(":adapter"))
}

// adapter/build.gradle.kts
dependencies {
    implementation(project(":core"))
}

// core/build.gradle.kts
dependencies {
    // Spring Framework까지만 허용
    // 외부 라이브러리 의존성 금지
}
```

### Branch Naming Convention

```
{module}/{number}-{feature-name}

Examples:
- notification/001-alert-service
- product/001-slot-acquisition
- product/002-payment-flow
- auth/001-user-signup
```

**SpecKit 명령어 사용 시:**
```bash
# notification 팀
/speckit.specify 알림 발송 서비스 구현 --short-name notification/alert-service

# product 팀
/speckit.specify 선착순 슬롯 획득 API --short-name product/slot-acquisition

# auth 팀
/speckit.specify 회원가입 및 로그인 --short-name auth/user-signup
```

### Module Responsibilities

| Module | Domains | Responsibility |
|--------|---------|----------------|
| **notification** | Notification | 알림 요청 수신, 스케줄링, 전송, 상태 로깅 |
| **product** | Product, PurchaseSlot, Purchase | 상품 관리, 슬롯 획득/만료, 결제 처리 |
| **auth** | User, Auth | 회원가입, 로그인, 토큰 발급, 인증/인가 |

### Inter-Module Communication

- 프로젝트 간 직접 호출 금지 (MSA 원칙)
- 모든 통신은 Kafka 이벤트를 통해 비동기로 처리
- 공유 이벤트 스키마는 `shared/events/` 디렉토리에서 관리

## Core Principles

### I. Concurrency-First Architecture

모든 설계 결정은 동시성 처리 능력을 최우선으로 고려해야 한다.

**Non-Negotiable Rules**:
- 모든 API 엔드포인트는 100,000 RPS 처리를 목표로 설계해야 한다
- 공유 상태(Shared State)는 반드시 Redis, Kafka 등 분산 시스템을 통해 관리해야 한다
- Database 직접 쓰기는 Critical Path에서 금지하며, 큐를 통해 비동기 처리해야 한다
- 모든 외부 호출은 Circuit Breaker 패턴을 적용해야 한다
- Connection Pool, Thread Pool 설정은 반드시 문서화하고 부하 테스트로 검증해야 한다

**Rationale**: 10만 동시 사용자의 선착순 구매 요청을 안정적으로 처리하기 위해 동시성은 핵심 설계 원칙이다.

### II. Domain Ownership

각 프로젝트는 자신의 도메인에 대한 완전한 소유권을 가진다.

**Non-Negotiable Rules**:
- 각 프로젝트는 자체 데이터 저장소를 가지며, 다른 프로젝트의 DB 직접 접근은 금지한다
- 프로젝트 간 데이터 필요 시 Kafka 이벤트를 통해서만 통신한다
- 각 프로젝트의 `specs/{module}/`은 해당 팀만 수정할 수 있다
- Aggregate Root를 통해서만 도메인 상태를 변경할 수 있다
- 공유 코드는 `shared/` 디렉토리에서 관리하며, 변경 시 모든 팀 합의가 필요하다

**Rationale**: 3개 팀이 독립적으로 개발하면서도 전체 시스템의 일관성을 유지하기 위함이다.

### III. Prototype-Validate-Harden

빠른 검증 후 점진적으로 강화하는 개발 방식을 따른다.

**Non-Negotiable Rules**:
- 새 기능은 먼저 동작하는 프로토타입으로 핵심 흐름을 검증해야 한다
- 프로토타입 검증 후 반드시 부하 테스트를 수행해야 한다
- 부하 테스트 통과 후에야 프로덕션 배포가 허용된다
- 테스트 코드는 검증된 기능에 대해 작성하며, Critical Path는 반드시 테스트를 포함해야 한다
- "완벽한 설계 후 구현"보다 "동작하는 코드 후 개선"을 우선한다

**Critical Paths by Module**:
- **product**: 슬롯 획득, 슬롯 만료/회수, 결제 확정
- **auth**: 로그인, 토큰 검증
- **notification**: 알림 발송 (재시도 포함)

**Rationale**: 100K RPS 시스템에서 이론적 설계만으로는 실제 병목을 예측하기 어렵다.

### IV. Observability by Default

모든 시스템 동작은 관측 가능해야 한다.

**Non-Negotiable Rules**:
- 모든 API 요청은 Trace ID를 발급하고 프로젝트 간 전파해야 한다
- 핵심 비즈니스 이벤트는 반드시 구조화된 로그로 기록해야 한다
- 메트릭(Latency, Throughput, Error Rate)은 Prometheus 형식으로 노출해야 한다
- 장애 발생 시 5분 이내에 원인 파악이 가능한 수준의 로깅을 유지해야 한다
- 각 프로젝트는 자체 Health Check 엔드포인트를 제공해야 한다

**Required Events to Log**:
- **product**: SLOT_REQUESTED, SLOT_ACQUIRED, SLOT_EXPIRED, PAYMENT_COMPLETED
- **auth**: LOGIN_SUCCESS, LOGIN_FAILED, TOKEN_ISSUED, TOKEN_REVOKED
- **notification**: NOTIFICATION_SENT, NOTIFICATION_FAILED, NOTIFICATION_RETRIED

**Rationale**: 10시 정각 트래픽 폭증 시 실시간으로 시스템 상태를 파악하고 빠르게 대응하기 위함이다.

### V. Fairness Guarantees

선착순 처리의 공정성을 시스템 레벨에서 보장해야 한다.

**Non-Negotiable Rules**:
- 슬롯 획득 순서는 반드시 요청 도착 시간 기준으로 결정해야 한다
- 동일 사용자의 중복 요청은 시스템 레벨에서 차단해야 한다
- 정확히 N개 재고에 대해 N명에게만 슬롯을 발급해야 한다 (초과 발급 금지)
- 슬롯 만료 및 회수 로직은 원자적(Atomic)으로 처리해야 한다
- 모든 슬롯 상태 변경은 감사 로그(Audit Log)로 기록해야 한다

**Rationale**: "획득의 짜릿함"이라는 서비스 가치는 공정한 경쟁에서 비롯된다.

## Technical Standards

### Technology Stack

| Layer | Technology | Version | Note |
|-------|------------|---------|------|
| Language | Kotlin | 1.9.25 | Coroutines for async |
| Framework | Spring Boot | 3.5.8 | WebFlux (Reactive) |
| Messaging | Apache Kafka | - | 프로젝트 간 통신 |
| Cache | Redis | - | 슬롯 관리, Rate Limiting |
| Database | 팀 자율 선택 | - | 각 프로젝트별 적합한 DB 선택 |
| Testing | JUnit 5, Kotest, k6 | - | 부하 테스트 포함 |

### Performance Targets

| Metric | Target | Module |
|--------|--------|--------|
| RPS | 100,000 | product (슬롯 획득) |
| Latency p99 | < 100ms | product (슬롯 획득) |
| Latency p99 | < 500ms | product (결제) |
| Latency p99 | < 200ms | auth (토큰 검증) |
| Error Rate | < 0.1% | 전체 시스템 |
| 동시 접속자 | 100,000 | 10시 정각 기준 |

### API Standards

- RESTful API with JSON response
- 모든 응답에 `X-Trace-ID` 헤더 포함
- 에러 응답은 RFC 7807 Problem Details 형식 준수
- Rate Limiting: 사용자별 초당 10회

## Development Workflow

### Feature Development Cycle

1. **Prototype**: 핵심 흐름 구현 (Happy Path)
2. **Validate**: 기능 검증 및 부하 테스트
3. **Harden**: 엣지 케이스 처리, 테스트 코드 추가
4. **Review**: 코드 리뷰 및 성능 메트릭 확인
5. **Deploy**: 카나리 배포 후 점진적 롤아웃

### Branch Strategy

```
main
├── notification/001-alert-service
├── notification/002-scheduled-notify
├── product/001-slot-acquisition
├── product/002-payment-flow
├── auth/001-user-signup
└── auth/002-jwt-auth
```

- 각 팀은 `{module}/{number}-{feature-name}` 형식의 브랜치 사용
- main 브랜치 머지는 해당 프로젝트 팀 리드 승인 필요

### Code Review Checklist

- [ ] 동시성 처리 패턴 적용 여부
- [ ] 도메인 경계 준수 여부 (다른 프로젝트 DB 직접 접근 금지)
- [ ] 내부 모듈 의존성 규칙 준수 여부 (core → adapter → app/worker)
- [ ] 관측 가능성 요소(로깅, 메트릭) 포함 여부
- [ ] 부하 테스트 결과 첨부 (Critical Path)
- [ ] 이벤트 스키마 변경 시 하위 호환성 확인

## Governance

### Amendment Process

1. Constitution 변경 제안은 문서화된 이유와 함께 제출
2. **모든 프로젝트 팀 리드 합의** 필요 (공통 Constitution)
3. 버전 업데이트 및 변경 이력 기록

### Versioning Policy

- **MAJOR**: 원칙 삭제 또는 근본적 재정의
- **MINOR**: 새 원칙/섹션 추가 또는 기존 내용 확장
- **PATCH**: 문구 수정, 오타 교정, 명확화

### Compliance

- 모든 PR은 Constitution Check 항목을 포함해야 한다
- 원칙 위반은 반드시 문서화된 사유와 함께 예외 승인을 받아야 한다
- 월간 Constitution 준수 현황 리뷰 수행

**Version**: 1.2.0 | **Ratified**: 2025-12-23 | **Last Amended**: 2025-12-30
