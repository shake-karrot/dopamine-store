# 사용자 인증 모듈 구현 마스터 플랜

## 개요
본 문서는 `auth` 디렉토리 내에 사용자 인증 시스템을 구축하기 위한 전체 계획을 기술합니다. 프로젝트의 구조(adapter, app, core, worker)를 고려하여 헥사고날 아키텍처(Hexagonal Architecture) 기반으로 설계 및 구현을 진행합니다.

## 목표
- 안전하고 확장 가능한 사용자 인증/인가 시스템 구축
- 도메인 중심 설계를 통한 비즈니스 로직 격리
- JWT 기반의 무상태(Stateless) 인증 처리 (대규모 트래픽 고려)
- 사용자 역할(Buyer, Admin) 구분 및 권한 관리
- 이메일 기반 비밀번호 초기화 및 알림 시스템 연동

## 개발 단계 (Phases)

### Phase 1: 도메인 및 코어 설계 (Domain & Core)
- **목표**: 비즈니스 핵심 로직과 규칙 정의
- **내용**:
  - `User` 엔티티 (Buyer, Admin 역할 구분)
  - `Email`, `Password`, `Username` Value Object
  - 도메인 이벤트 (`UserRegistered`, `PasswordReset`)
  - 입출력 포트(Port) 인터페이스 정의 (Repository, Service, Notification)

### Phase 2: 애플리케이션 계층 구현 (Application Layer)
- **목표**: 사용자 요청을 처리하는 유스케이스 구현 (FR-001 ~ FR-005)
- **내용**:
  - 회원가입 (SignUp) 유스케이스 (ID/이메일 중복 체크)
  - 로그인 (Login) 유스케이스 (토큰 발급)
  - 비밀번호 초기화 (ResetPassword) 유스케이스
  - 도메인 서비스 오케스트레이션

### Phase 3: 인프라 및 어댑터 구현 (Infrastructure & Adapters)
- **목표**: 외부 시스템 및 입출력 처리 구현
- **내용**:
  - Web Adapter: REST API 컨트롤러, DTO
  - Persistence Adapter: DB 저장소 구현 (JPA/MyBatis 등)
  - Security Adapter: JWT 생성/검증, 비밀번호 암호화
  - Worker: 비동기 작업 처리 (예: 가입 환영 이메일)

## 디렉토리 구조 예상
```text
auth/
├── core/           # Phase 1
│   ├── domain/
│   └── port/
├── app/            # Phase 2
│   └── usecase/
├── adapter/        # Phase 3
│   ├── web/
│   ├── persistence/
│   └── infrastructure/
└── worker/         # Phase 3 (Optional)
```
