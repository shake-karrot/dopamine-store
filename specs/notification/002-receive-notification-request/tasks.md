# Tasks: 알림 요청 수신 및 검증

**Input**: Design documents from `/specs/notification/002-receive-notification-request/`
**Prerequisites**: spec.md (required), Constitution (module structure reference)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Based on Constitution module structure:
- **core**: `notification/core/src/main/kotlin/` - Domain entities, UseCase interfaces, Services
- **worker**: `notification/worker/src/main/kotlin/` - Kafka Consumers
- **adapter**: `notification/adapter/src/main/kotlin/` - External integrations, Configs

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Kafka Consumer 기반 인프라 구성

- [X] T001 Create Kafka configuration class in `notification/adapter/src/main/kotlin/config/KafkaConsumerConfig.kt`
- [X] T002 [P] Create DLQ (Dead Letter Queue) configuration in `notification/adapter/src/main/kotlin/config/DlqConfig.kt`
- [X] T003 [P] Define Kafka topic constants in `notification/core/src/main/kotlin/constant/KafkaTopics.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 User Story가 공유하는 핵심 도메인 모델 및 인프라

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Create NotificationType enum in `notification/core/src/main/kotlin/domain/NotificationType.kt`
- [X] T005 [P] Create SendType enum (IMMEDIATE, SCHEDULED) in `notification/core/src/main/kotlin/domain/SendType.kt`
- [X] T006 [P] Create NotificationRequest domain entity in `notification/core/src/main/kotlin/domain/NotificationRequest.kt`
- [X] T007 [P] Create DomainEvent base class in `notification/core/src/main/kotlin/domain/DomainEvent.kt`
- [X] T008 Create EventValidator interface in `notification/core/src/main/kotlin/port/EventValidator.kt`
- [X] T009 [P] Create NotificationRequestPort interface (output port) in `notification/core/src/main/kotlin/port/NotificationRequestPort.kt`
- [X] T010 Create IdempotencyChecker interface in `notification/core/src/main/kotlin/port/IdempotencyChecker.kt`
- [X] T011 Implement EventValidatorImpl in `notification/adapter/src/main/kotlin/validation/EventValidatorImpl.kt`
- [X] T012 [P] Implement IdempotencyCheckerImpl (Redis-based) in `notification/adapter/src/main/kotlin/idempotency/IdempotencyCheckerImpl.kt`
- [X] T013 Create structured logging utility in `notification/core/src/main/kotlin/logging/NotificationLogger.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 회원가입 완료 알림 요청 수신 (Priority: P1) 🎯 MVP

**Goal**: auth 도메인에서 `NEW_USER_REGISTERED` 이벤트를 수신하여 환영 이메일 알림 요청으로 변환

**Independent Test**: Kafka에 `NEW_USER_REGISTERED` 이벤트를 발행하고, NotificationRequest가 생성되어 로그에 기록되는지 확인

### Implementation for User Story 1

- [X] T014 [US1] Create NewUserRegisteredEvent DTO in `notification/adapter/src/main/kotlin/kafka/event/NewUserRegisteredEvent.kt`
- [X] T015 [US1] Create NewUserRegisteredEventMapper in `notification/adapter/src/main/kotlin/kafka/mapper/NewUserRegisteredEventMapper.kt`
- [X] T016 [US1] Implement ReceiveNotificationUseCase interface in `notification/core/src/main/kotlin/usecase/ReceiveNotificationUseCase.kt`
- [X] T017 [US1] Implement ReceiveNotificationService in `notification/core/src/main/kotlin/service/ReceiveNotificationService.kt`
- [X] T018 [US1] Create NewUserRegisteredConsumer in `notification/worker/src/main/kotlin/consumer/NewUserRegisteredConsumer.kt`
- [X] T019 [US1] Add validation logic for NEW_USER_REGISTERED event (userId, email required) in `notification/adapter/src/main/kotlin/validation/NewUserRegisteredValidator.kt`
- [X] T020 [US1] Add DLQ error handling for validation failures in `notification/worker/src/main/kotlin/consumer/handler/DlqErrorHandler.kt`
- [X] T021 [US1] Add structured logging for NEW_USER_REGISTERED event processing

**Checkpoint**: User Story 1 완료 - 회원가입 이벤트 수신 및 알림 요청 변환 동작

---

## Phase 4: User Story 2 - 구매 권한 획득 알림 요청 수신 (Priority: P1)

**Goal**: purchase 도메인에서 `PURCHASE_SLOT_ACQUIRED` 이벤트를 수신하여 즉시 알림 + 예약 알림(만료 5분 전) 두 개 생성

**Independent Test**: Kafka에 `PURCHASE_SLOT_ACQUIRED` 이벤트를 발행하고, 즉시/예약 두 개의 NotificationRequest가 생성되는지 확인

### Implementation for User Story 2

- [X] T022 [P] [US2] Create PurchaseSlotAcquiredEvent DTO in `notification/adapter/src/main/kotlin/kafka/event/PurchaseSlotAcquiredEvent.kt`
- [X] T023 [US2] Create PurchaseSlotAcquiredEventMapper in `notification/adapter/src/main/kotlin/kafka/mapper/PurchaseSlotAcquiredEventMapper.kt`
- [X] T024 [US2] Add ScheduledNotificationRequest factory method to handle expiresAt - 5min calculation in `notification/core/src/main/kotlin/domain/NotificationRequest.kt`
- [X] T025 [US2] Create PurchaseSlotAcquiredConsumer in `notification/worker/src/main/kotlin/consumer/PurchaseSlotAcquiredConsumer.kt`
- [X] T026 [US2] Add validation logic for PURCHASE_SLOT_ACQUIRED event (userId, email, expiresAt required) in `notification/adapter/src/main/kotlin/validation/PurchaseSlotAcquiredValidator.kt`
- [X] T027 [US2] Integrate scheduled notification creation logic into ReceiveNotificationService
- [X] T028 [US2] Add structured logging for PURCHASE_SLOT_ACQUIRED event processing (both immediate and scheduled)

**Checkpoint**: User Story 2 완료 - 슬롯 획득 이벤트 수신 및 즉시/예약 알림 요청 생성

---

## Phase 5: User Story 3 - 비밀번호 재설정 알림 요청 수신 (Priority: P2)

**Goal**: auth 도메인에서 `PASSWORD_RESET_REQUESTED` 이벤트를 수신하여 재설정 링크 알림 요청 생성

**Independent Test**: Kafka에 `PASSWORD_RESET_REQUESTED` 이벤트를 발행하고, resetToken이 포함된 NotificationRequest가 생성되는지 확인

### Implementation for User Story 3

- [ ] T029 [P] [US3] Create PasswordResetRequestedEvent DTO in `notification/worker/src/main/kotlin/consumer/event/PasswordResetRequestedEvent.kt`
- [ ] T030 [US3] Create PasswordResetRequestedEventMapper in `notification/worker/src/main/kotlin/consumer/mapper/PasswordResetRequestedEventMapper.kt`
- [ ] T031 [US3] Create PasswordResetRequestedConsumer in `notification/worker/src/main/kotlin/consumer/PasswordResetRequestedConsumer.kt`
- [ ] T032 [US3] Add validation logic for PASSWORD_RESET_REQUESTED event (userId, email, resetToken required) in `notification/adapter/src/main/kotlin/validation/PasswordResetRequestedValidator.kt`
- [ ] T033 [US3] Add structured logging for PASSWORD_RESET_REQUESTED event processing

**Checkpoint**: User Story 3 완료 - 비밀번호 재설정 이벤트 수신 및 알림 요청 생성

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Edge Cases 처리 및 전체 품질 향상

- [ ] T034 Implement idempotency check in all consumers using IdempotencyChecker
- [ ] T035 [P] Add metrics for event processing latency (p99 < 50ms target)
- [ ] T036 [P] Add consumer health check endpoint
- [ ] T037 Configure consumer group for parallel partition processing (min 3 partitions)
- [ ] T038 Add integration test for DLQ flow when validation fails
- [ ] T039 Document Kafka topic and consumer configuration in README

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - US1 and US2 are both P1, can proceed in parallel if staffed
  - US3 (P2) can start after Foundational, independent of US1/US2
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - No dependencies on other stories

### Within Each User Story

- Event DTO before Mapper
- Mapper before Consumer
- Validator before Consumer integration
- Consumer before logging/metrics integration

### Parallel Opportunities

- T002, T003 can run in parallel with T001
- T005, T006, T007, T009, T012 can run in parallel (different files)
- US1, US2, US3 can all run in parallel after Foundational phase
- T022, T029 can run in parallel (independent event DTOs)
- T035, T036 can run in parallel (different concerns)

---

## Parallel Example: Foundational Phase

```bash
# Launch all domain models in parallel:
Task: "Create SendType enum in notification/core/.../SendType.kt"
Task: "Create NotificationRequest entity in notification/core/.../NotificationRequest.kt"
Task: "Create DomainEvent base class in notification/core/.../DomainEvent.kt"
Task: "Create NotificationRequestPort interface in notification/core/.../NotificationRequestPort.kt"
```

---

## Parallel Example: User Stories (with multiple developers)

```bash
# Developer A: User Story 1
Task: "Create NewUserRegisteredEvent DTO"
Task: "Create NewUserRegisteredEventMapper"
Task: "Create NewUserRegisteredConsumer"

# Developer B: User Story 2 (in parallel)
Task: "Create PurchaseSlotAcquiredEvent DTO"
Task: "Create PurchaseSlotAcquiredEventMapper"
Task: "Create PurchaseSlotAcquiredConsumer"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (Kafka config)
2. Complete Phase 2: Foundational (Domain models, ports)
3. Complete Phase 3: User Story 1 (NEW_USER_REGISTERED)
4. **STOP and VALIDATE**: Kafka 메시지 발행 → Consumer 수신 → NotificationRequest 생성 확인
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo (슬롯 획득 알림)
4. Add User Story 3 → Test independently → Deploy/Demo (비밀번호 재설정)
5. Add Polish → 전체 품질 향상

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (회원가입)
   - Developer B: User Story 2 (슬롯 획득)
   - Developer C: User Story 3 (비밀번호 재설정)
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Module structure follows Constitution: core → adapter, worker depends on core + adapter
