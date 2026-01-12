# Phase 2: 애플리케이션 계층 구현 (Application Layer)

## 목표
- `auth/app` 모듈에 사용자 유스케이스(Use Case)를 구현합니다.
- `auth/core`의 도메인 로직과 포트를 사용하여 실제 비즈니스 흐름을 제어합니다.

## 할 일 목록 (Todo List)

### 1. 입력 포트 (Input Port / UseCase Interface) 정의
- [ ] `SignUpUseCase`: 회원가입 기능 (FR-001)
- [ ] `LoginUseCase`: 로그인 및 토큰 발급 기능 (FR-003)
- [ ] `ResetPasswordUseCase`: 비밀번호 초기화 기능 (FR-004)
- [ ] `TokenRefreshUseCase`: 토큰 갱신 기능

### 2. 유스케이스 구현 (Service)
- [ ] **SignUpService 구현**
  - 입력: `SignUpCommand` (Username, Password, Email, Role)
  - 로직:
    1. ID/Email 중복 확인 (`ExistUserPort`) - FR-002
    2. 비밀번호 암호화 (`PasswordEncoderPort`)
    3. User 엔티티 생성 (기본 Role: BUYER)
    4. 저장 (`SaveUserPort`)
    5. 가입 환영 이벤트 발행 (`NotificationPort` or `EventPublisher`) - FR-022
  - 출력: `UserResult` (식별자, 가입일시)

- [ ] **LoginService 구현**
  - 입력: `LoginCommand` (Username, Password)
  - 로직:
    1. 사용자 조회 (`LoadUserPort.findByUsername`)
    2. 비밀번호 일치 확인 (`PasswordEncoderPort`)
    3. Access/Refresh 토큰 생성 (`TokenProviderPort`)
  - 출력: `AuthTokenResult`

- [ ] **ResetPasswordService 구현** (FR-004)
  - 입력: `ResetPasswordCommand` (Username, Email)
  - 로직:
    1. 사용자 조회 및 이메일 일치 확인
    2. 임시 비밀번호 생성 또는 리셋 토큰 생성
    3. 비밀번호 업데이트 (`SaveUserPort`)
    4. 이메일 발송 (`NotificationPort`)
  - 출력: void (성공 시)

### 3. 단위 테스트 작성
- [ ] UseCase 별 단위 테스트 (Mocking Ports)

## 산출물
- `auth/app/usecase/**/*.java` (or `.kt`)
- `auth/app/service/**/*.java` (or `.kt`)
