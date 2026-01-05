# Phase 3: 인프라 및 어댑터 구현 (Infrastructure & Adapters)

## 목표
- `auth/adapter` 모듈에 실제 기술 스택(Spring Web, JPA, Security 등)을 적용한 어댑터를 구현합니다.
- 외부 요청을 받아 애플리케이션 계층으로 전달하고, 데이터를 영구 저장소에 저장합니다.

## 할 일 목록 (Todo List)

### 1. Web Adapter (Inbound Adapter)
- [ ] **API 명세 작성 및 DTO 정의**
  - Request: `SignUpRequest`, `LoginRequest`, `ResetPasswordRequest`
  - Response: `TokenResponse`, `UserResponse`
- [ ] **AuthController 구현**
  - REST Endpoint 정의
    - `POST /api/v1/auth/signup`
    - `POST /api/v1/auth/login`
    - `POST /api/v1/auth/reset-password` (FR-004)
  - 입력값 검증 (Validation - Length, Regex)
  - UseCase 호출 및 응답 변환

### 2. Persistence Adapter (Outbound Adapter)
- [ ] **Entity 정의 (JPA)**
  - `UserJpaEntity`: DB 테이블 매핑 (`@Table(uniqueConstraints = ...)`)
  - `username`, `email` 인덱스 및 유니크 제약 조건 설정
- [ ] **Repository 구현**
  - `UserRepositoryAdapter`: `LoadUserPort`, `SaveUserPort`, `ExistUserPort` 구현체
  - Spring Data JPA Repository 활용 (`existsByUsername`, `existsByEmail`)

### 3. Infrastructure Adapter
- [ ] **Security 구현**
  - `BCryptPasswordEncoder` 기반의 `PasswordEncoderPort` 구현
  - JWT 라이브러리(jjwt 등)를 이용한 `TokenProviderPort` 구현
- [ ] **Notification 구현**
  - `EmailNotificationAdapter`: `NotificationPort` 구현 구현 (JavaMailSender 등 활용)
- [ ] **Configuration**
  - Spring Security 설정 (CSRF, CORS, Filter Chain)

## 산출물
- `auth/adapter/web/**/*.java`
- `auth/adapter/persistence/**/*.java`
- `auth/adapter/infrastructure/**/*.java`
