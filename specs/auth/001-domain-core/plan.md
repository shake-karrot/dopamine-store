# Phase 1: 도메인 및 코어 설계 (Domain & Core)

## 목표
- `auth/core` 모듈에 핵심 비즈니스 로직과 도메인 모델을 정의합니다.
- 외부 의존성 없이 순수 Java/Kotlin 코드로 도메인 규칙을 작성합니다.

## 할 일 목록 (Todo List)

### 1. 도메인 모델 정의
- [ ] **User Aggregate Root 설계**
  - 속성:
    - `UserId`: 고유 식별자 (Long or UUID)
    - `Username`: 사용자 ID (Unique)
    - `Email`: 이메일 (Unique, 알림 전송용)
    - `Password`: 암호화된 비밀번호
    - `Role`: 사용자 역할 (Enum: `BUYER`, `ADMIN`) - FR-005
    - `CreatedAt`, `UpdatedAt`: 감사 정보
  - 행위:
    - `changePassword(newPassword)`: 비밀번호 변경
    - `resetPassword(tempPassword)`: 비밀번호 초기화
    - `updateProfile(...)`: 정보 수정
- [ ] **Value Objects 정의**
  - `Email`: 이메일 형식 검증 로직 포함
  - `Password`: 암호화된 비밀번호 래퍼
  - `Username`: 아이디 형식 검증 (길이, 특수문자 등)
  - `Role`: `BUYER`, `ADMIN` 정의

### 2. 포트(Port) 인터페이스 정의
- [ ] **Repository Port (Output Port)**
  - `LoadUserPort`: 사용자 조회 (ById, ByUsername, ByEmail)
  - `SaveUserPort`: 사용자 저장 및 수정
  - `ExistUserPort`: 중복 가입 체크 (Username, Email)
- [ ] **Utility Port (Output Port)**
  - `PasswordEncoderPort`: 비밀번호 암호화 및 매칭 인터페이스
  - `TokenProviderPort`: 인증 토큰 생성 및 파싱 인터페이스
  - `NotificationPort`: 알림 발송 인터페이스 (회원가입 환영, 비번 초기화 등)

### 3. 도메인 이벤트
- [ ] `UserRegisteredEvent`: 회원 가입 완료 시 발생 -> 알림 시스템 연동
- [ ] `PasswordResetEvent`: 비밀번호 초기화 시 발생 -> 이메일 발송 연동

## 산출물
- `auth/core/domain/**/*.java` (or `.kt`)
- `auth/core/port/**/*.java` (or `.kt`)
