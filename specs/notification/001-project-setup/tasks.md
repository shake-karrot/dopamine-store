# Tasks: Notification 모듈 프로젝트 설정

**Input**: Design documents from `specs/notification/001-project-setup/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: 테스트 작업은 spec.md에서 요청되지 않음 - 생략

**Organization**: 3개 User Story (P1, P2, P3)를 기반으로 구성

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능 (다른 파일, 의존성 없음)
- **[Story]**: 해당 작업이 속한 User Story (US1, US2, US3)
- 파일 경로 포함

## Path Conventions

- **Project root**: `notification/`
- **Modules**: `notification/{core,app,worker,adapter}/`
- **Source**: `notification/{module}/src/main/kotlin/com/dopaminestore/notification/{module}/`

---

## Phase 1: Setup (프로젝트 초기화)

**Purpose**: Gradle 멀티모듈 프로젝트 기본 구조 생성

- [ ] T001 Create notification directory at repository root `notification/`
- [ ] T002 Create `notification/settings.gradle.kts` with submodule includes (core, app, worker, adapter)
- [ ] T003 Create `notification/gradle.properties` with version properties (kotlin=1.9.25, springBoot=3.5.8)
- [ ] T004 [P] Create `notification/build.gradle.kts` with shared plugins and dependencies
- [ ] T005 [P] Initialize Gradle Wrapper (8.10+) in `notification/` directory

---

## Phase 2: Foundational (모듈 기본 구조)

**Purpose**: 4개 서브모듈 디렉토리 및 build.gradle.kts 생성

**⚠️ CRITICAL**: User Story 작업 전 모든 모듈 구조 완료 필요

- [ ] T006 [P] Create `notification/core/build.gradle.kts` (Spring Framework only, no Boot)
- [ ] T007 [P] Create `notification/adapter/build.gradle.kts` with `implementation(project(":core"))`
- [ ] T008 [P] Create `notification/app/build.gradle.kts` with Spring Boot plugin, depends on core and adapter
- [ ] T009 [P] Create `notification/worker/build.gradle.kts` with Spring Boot plugin, depends on core and adapter
- [ ] T010 [P] Create core source directory `notification/core/src/main/kotlin/com/dopaminestore/notification/core/`
- [ ] T011 [P] Create adapter source directory `notification/adapter/src/main/kotlin/com/dopaminestore/notification/adapter/`
- [ ] T012 [P] Create app source directory `notification/app/src/main/kotlin/com/dopaminestore/notification/app/`
- [ ] T013 [P] Create worker source directory `notification/worker/src/main/kotlin/com/dopaminestore/notification/worker/`

**Checkpoint**: `./gradlew projects` 명령으로 4개 모듈 확인 가능

---

## Phase 3: User Story 1 - 멀티모듈 프로젝트 구조 생성 (Priority: P1) 🎯 MVP

**Goal**: 4개 모듈이 독립적으로 빌드되는 프로젝트 구조 완성

**Independent Test**: `./gradlew build` 성공, `./gradlew :core:build` 등 개별 빌드 성공

### Implementation for User Story 1

- [ ] T014 [P] [US1] Create package directories in core: `domain/`, `usecase/`, `service/`, `port/`
- [ ] T015 [P] [US1] Create package directories in adapter: `persistence/`, `external/`, `config/`
- [ ] T016 [P] [US1] Create package directories in app: `controller/`, `grpc/`, `dto/`
- [ ] T017 [P] [US1] Create package directories in worker: `consumer/`, `job/`
- [ ] T018 [US1] Create placeholder `.gitkeep` files in each package directory
- [ ] T019 [US1] Verify `./gradlew build` succeeds for all modules

**Checkpoint**: 전체 빌드 성공 - User Story 1 완료

---

## Phase 4: User Story 2 - 모듈 간 의존성 규칙 적용 (Priority: P2)

**Goal**: Constitution 의존성 규칙이 build.gradle.kts에 올바르게 적용됨

**Independent Test**: 각 모듈 의존성 확인 - `./gradlew :module:dependencies`

### Implementation for User Story 2

- [ ] T020 [US2] Verify core/build.gradle.kts has only Spring Framework dependencies (no Spring Boot Starter)
- [ ] T021 [US2] Verify adapter/build.gradle.kts has `implementation(project(":core"))` only
- [ ] T022 [US2] Verify app/build.gradle.kts has `implementation(project(":core"))` and `implementation(project(":adapter"))`
- [ ] T023 [US2] Verify worker/build.gradle.kts has `implementation(project(":core"))` and `implementation(project(":adapter"))`
- [ ] T024 [US2] Run `./gradlew :core:dependencies --configuration compileClasspath` and verify no external dependencies
- [ ] T025 [US2] Run `./gradlew :adapter:dependencies --configuration compileClasspath` and verify core dependency

**Checkpoint**: 의존성 규칙 검증 완료 - User Story 2 완료

---

## Phase 5: User Story 3 - 모듈 규칙 검증용 Mock 클래스 구성 (Priority: P3)

**Goal**: 각 모듈의 역할을 증명하는 Mock 클래스 생성

**Independent Test**: Mock 클래스들이 컴파일되고, 의존성 방향이 올바른지 확인

### Core Module Mock Classes

- [ ] T026 [P] [US3] Create `MockNotification.kt` in `notification/core/src/main/kotlin/com/dopaminestore/notification/core/domain/`
- [ ] T027 [P] [US3] Create `MockUseCase.kt` interface in `notification/core/src/main/kotlin/com/dopaminestore/notification/core/usecase/`
- [ ] T028 [P] [US3] Create `MockService.kt` in `notification/core/src/main/kotlin/com/dopaminestore/notification/core/service/`
- [ ] T029 [P] [US3] Create `MockPort.kt` interface in `notification/core/src/main/kotlin/com/dopaminestore/notification/core/port/`

### Adapter Module Mock Classes

- [ ] T030 [US3] Create `MockRepositoryImpl.kt` implementing MockPort in `notification/adapter/src/main/kotlin/com/dopaminestore/notification/adapter/persistence/`
- [ ] T031 [US3] Verify adapter compiles with core dependency - `./gradlew :adapter:compileKotlin`

### App Module Mock Classes

- [ ] T032 [US3] Create `NotificationAppApplication.kt` Spring Boot main class in `notification/app/src/main/kotlin/com/dopaminestore/notification/app/`
- [ ] T033 [US3] Create `MockController.kt` injecting MockUseCase in `notification/app/src/main/kotlin/com/dopaminestore/notification/app/controller/`
- [ ] T034 [US3] Verify app compiles with core and adapter dependencies - `./gradlew :app:compileKotlin`

### Worker Module Mock Classes

- [ ] T035 [US3] Create `NotificationWorkerApplication.kt` Spring Boot main class in `notification/worker/src/main/kotlin/com/dopaminestore/notification/worker/`
- [ ] T036 [US3] Create `MockConsumer.kt` injecting MockService in `notification/worker/src/main/kotlin/com/dopaminestore/notification/worker/consumer/`
- [ ] T037 [US3] Verify worker compiles with core and adapter dependencies - `./gradlew :worker:compileKotlin`

**Checkpoint**: 모든 Mock 클래스 컴파일 성공 - User Story 3 완료

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 최종 검증 및 정리

- [ ] T038 Run full build `./gradlew clean build` and verify success
- [ ] T039 Run `./gradlew projects` and verify 4 submodules listed
- [ ] T040 Verify quickstart.md commands work correctly
- [ ] T041 Remove `.gitkeep` placeholder files (now have actual source files)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존성 없음 - 즉시 시작 가능
- **Foundational (Phase 2)**: Setup 완료 필요 - 모든 User Story 블로킹
- **User Story 1 (Phase 3)**: Foundational 완료 필요
- **User Story 2 (Phase 4)**: User Story 1 완료 필요 (의존성 검증은 모듈 존재 후 가능)
- **User Story 3 (Phase 5)**: User Story 2 완료 필요 (Mock 클래스는 의존성 규칙 적용 후 검증)
- **Polish (Phase 6)**: 모든 User Story 완료 필요

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 완료 후 시작 - 다른 Story 의존성 없음
- **User Story 2 (P2)**: US1 완료 후 시작 - 모듈 구조가 필요
- **User Story 3 (P3)**: US2 완료 후 시작 - 의존성 규칙이 올바르게 설정되어야 Mock 검증 가능

### Within Each Phase

- [P] 표시된 작업은 병렬 실행 가능
- 디렉토리 생성 후 파일 생성
- build.gradle.kts 생성 후 컴파일 검증

### Parallel Opportunities

**Phase 2 (모두 병렬 가능):**
```
T006, T007, T008, T009 - 모든 build.gradle.kts 동시 생성
T010, T011, T012, T013 - 모든 source directory 동시 생성
```

**Phase 3 (모두 병렬 가능):**
```
T014, T015, T016, T017 - 모든 package directory 동시 생성
```

**Phase 5 Core Mock (병렬 가능):**
```
T026, T027, T028, T029 - Core 모듈 Mock 클래스 동시 생성
```

---

## Parallel Example: Phase 2

```bash
# Launch all build.gradle.kts creation tasks together:
Task: "Create notification/core/build.gradle.kts"
Task: "Create notification/adapter/build.gradle.kts"
Task: "Create notification/app/build.gradle.kts"
Task: "Create notification/worker/build.gradle.kts"

# Launch all source directory creation tasks together:
Task: "Create core source directory"
Task: "Create adapter source directory"
Task: "Create app source directory"
Task: "Create worker source directory"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: `./gradlew build` 성공 확인
5. 빌드 가능한 프로젝트 구조 완성

### Incremental Delivery

1. Setup + Foundational → 프로젝트 기반 완성
2. Add User Story 1 → `./gradlew build` 성공 (MVP!)
3. Add User Story 2 → 의존성 규칙 검증 완료
4. Add User Story 3 → Mock 클래스로 아키텍처 검증 완료
5. 각 Story가 이전 Story를 깨지 않고 가치를 추가

---

## Summary

| Metric | Value |
|--------|-------|
| Total Tasks | 41 |
| Phase 1 (Setup) | 5 tasks |
| Phase 2 (Foundational) | 8 tasks |
| Phase 3 (US1) | 6 tasks |
| Phase 4 (US2) | 6 tasks |
| Phase 5 (US3) | 12 tasks |
| Phase 6 (Polish) | 4 tasks |
| Parallel Opportunities | 25 tasks (61%) |
| MVP Scope | Phase 1-3 (19 tasks) |

---

## Notes

- [P] 작업 = 다른 파일, 의존성 없음
- [Story] 라벨은 특정 User Story에 매핑
- 각 User Story는 독립적으로 완료 및 테스트 가능
- 작업 또는 논리 그룹 완료 후 커밋
- 각 Checkpoint에서 Story 독립 검증 가능
