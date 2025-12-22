# Feature Specification: Notification 모듈 프로젝트 설정

**Feature Branch**: `notification/001-project-setup`
**Created**: 2025-12-23
**Status**: Draft
**Input**: User description: "Notification 모듈 설정"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 멀티모듈 프로젝트 구조 생성 (Priority: P1)

개발자로서, Notification 도메인의 멀티모듈 Gradle 프로젝트(core, app, worker, adapter)를 생성하여 독립적으로 빌드/배포 가능한 프로젝트 기반을 마련한다.

**Why this priority**: 모든 후속 개발의 기반이 되는 프로젝트 구조가 없으면 어떤 기능도 구현할 수 없다.

**Independent Test**: `./gradlew build` 명령이 성공하고, 4개 서브모듈이 모두 빌드된다.

**Acceptance Scenarios**:

1. **Given** notification 디렉토리, **When** 프로젝트 설정 완료, **Then** `./gradlew build` 명령이 성공한다
2. **Given** settings.gradle.kts, **When** 모듈 목록 확인, **Then** core, app, worker, adapter 4개 모듈이 포함되어 있다
3. **Given** 각 서브모듈, **When** 독립 빌드 실행, **Then** `./gradlew :core:build` 등 개별 빌드가 성공한다

---

### User Story 2 - 모듈 간 의존성 규칙 적용 (Priority: P2)

개발자로서, Constitution에서 정의한 모듈 의존성 규칙(core ← adapter ← app/worker)을 build.gradle.kts에 적용하여 아키텍처 일관성을 보장한다.

**Why this priority**: 잘못된 의존성은 아키텍처 붕괴로 이어지며, 초기에 올바르게 설정해야 한다.

**Independent Test**: 각 모듈의 build.gradle.kts를 확인하여 의존성 방향이 올바른지 검증한다.

**Acceptance Scenarios**:

1. **Given** core 모듈, **When** 의존성 확인, **Then** Spring Framework 외 외부 라이브러리 의존성이 없다
2. **Given** adapter 모듈, **When** 의존성 확인, **Then** `implementation(project(":core"))`만 존재한다
3. **Given** app/worker 모듈, **When** 의존성 확인, **Then** core와 adapter에 대한 의존성만 존재한다

---

### User Story 3 - 모듈 규칙 검증용 Mock 클래스 구성 (Priority: P3)

개발자로서, 각 모듈의 역할과 의존성 규칙을 검증할 수 있는 샘플 클래스를 생성하여 아키텍처 규칙이 실제로 동작함을 증명한다.

**Why this priority**: Mock 클래스를 통해 모듈 간 의존성 규칙이 컴파일 타임에 검증됨을 확인할 수 있다.

**Independent Test**: Mock 클래스들이 올바른 모듈에 위치하고, 의존성 방향이 규칙을 따르는지 확인한다.

**Acceptance Scenarios**:

1. **Given** core 모듈, **When** UseCase 인터페이스와 Service 클래스 생성, **Then** Spring 외 의존성 없이 컴파일된다
2. **Given** adapter 모듈, **When** Port 구현체 생성, **Then** core의 인터페이스를 구현하고 외부 라이브러리 사용 가능하다
3. **Given** app 모듈, **When** Controller 생성, **Then** core의 UseCase를 주입받아 사용한다
4. **Given** worker 모듈, **When** Consumer 생성, **Then** core의 Service를 주입받아 사용한다

---

### Edge Cases

- 순환 의존성: core가 adapter나 app을 참조하려 할 때 컴파일 에러 발생 확인
- JDK 버전: Spring Boot 3.x는 JDK 17+ 필요 - Gradle 빌드 시 검증
- Gradle 버전: Kotlin 1.9.25와 Spring Boot 3.5.8을 지원하는 Gradle 8.x 사용

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: notification 디렉토리에 독립적인 Gradle 멀티모듈 프로젝트를 구성해야 한다
- **FR-002**: 프로젝트는 core, app, worker, adapter 4개 서브모듈을 포함해야 한다
- **FR-003**: Kotlin 1.9.25와 Spring Boot 3.5.8 버전을 사용해야 한다
- **FR-004**: 모듈 간 의존성은 build.gradle.kts를 통해 관리해야 한다
- **FR-005**: core 모듈은 순수 비즈니스 로직만 포함하며, Spring Framework까지만 의존 가능하다
- **FR-006**: core 모듈은 UseCase 인터페이스와 비즈니스 Service를 포함해야 한다
- **FR-007**: adapter 모듈은 DB, 외부 서비스 연동과 Config를 담당해야 한다
- **FR-008**: worker 모듈은 Consumer를 주로 담당해야 한다
- **FR-009**: app 모듈은 REST API, gRPC를 담당하며 Controller만 포함해야 한다
- **FR-010**: 각 모듈에 역할을 증명하는 Mock 클래스가 존재해야 한다

### Key Entities

- **NotificationModule**: notification 프로젝트 루트, 4개 서브모듈 포함
- **CoreModule**: UseCase 인터페이스, Service 클래스, Domain 엔티티 포함
- **AdapterModule**: Repository 구현체, External Client, Config 포함
- **AppModule**: REST Controller, gRPC Service, DTO 포함
- **WorkerModule**: Kafka Consumer, Scheduled Job 포함

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `./gradlew build` 명령이 성공적으로 완료된다
- **SC-002**: 4개 서브모듈이 모두 독립적으로 빌드 가능하다
- **SC-003**: 각 모듈에 최소 1개 이상의 Mock 클래스가 존재한다
- **SC-004**: 모듈 간 의존성이 규칙(core ← adapter ← app/worker)을 따른다
- **SC-005**: core 모듈이 Spring Framework 외 외부 의존성 없이 빌드된다

## Assumptions

- JDK 17 이상이 개발 환경에 설치되어 있다
- Gradle 8.x 버전을 사용한다
- notification, purchase, auth 프로젝트는 서로 독립적이며 의존성이 없다
- 이 spec은 notification 프로젝트만을 대상으로 한다
