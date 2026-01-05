# Tasks: Product Domain API & Business Logic

**Feature Branch**: `product/002-product-api-spec`
**Status**: 🟡 In Progress
**Created**: 2026-01-05
**Last Updated**: 2026-01-06

## Overview

This task list implements the Product Domain API & Business Logic feature, delivering first-come-first-served slot acquisition capable of handling 100,000 RPS at peak load. Tasks are organized by implementation phase, with each user story independently testable.

**Tech Stack**: Kotlin 1.9.25, Spring Boot 3.5.8 WebFlux, PostgreSQL, Redis, Kafka
**Multi-Module Structure**: `core/`, `app/`, `worker/`, `adapter/` (per constitution)

---

## Phase 1: Setup & Infrastructure (Foundational) ✅ COMPLETE

### Database Setup

- [X] [T001] [P] Create PostgreSQL schema migration files in `product/adapter/src/main/resources/db/migration/V001__create_products_table.sql`
- [X] [T002] [P] Create migration for purchase_slots table in `product/adapter/src/main/resources/db/migration/V002__create_purchase_slots_table.sql`
- [X] [T003] [P] Create migration for purchases table in `product/adapter/src/main/resources/db/migration/V003__create_purchases_table.sql`
- [X] [T004] [P] Create migration for slot_audit_log table in `product/adapter/src/main/resources/db/migration/V004__create_slot_audit_log_table.sql`
- [X] [T005] Configure Flyway in `product/adapter/build.gradle.kts` with PostgreSQL R2DBC driver

### Redis Infrastructure

- [X] [T006] [P] Create Redis configuration in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/config/RedisConfig.kt` with connection pool settings
- [X] [T007] Create Lua script for slot acquisition in `product/adapter/src/main/resources/redis/slot-acquisition.lua` with atomic stock check and queue management
- [X] [T008] Create Redis key helper utilities in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/redis/RedisKeyHelper.kt` for consistent key naming

### Kafka Infrastructure

- [X] [T009] [P] Create Kafka producer configuration in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/config/KafkaProducerConfig.kt`
- [X] [T010] [P] Create Avro schema for SlotAcquiredEvent in `shared/events/product/slot-acquired.avsc`
- [X] [T011] [P] Create Avro schema for SlotExpiredEvent in `shared/events/product/slot-expired.avsc`
- [X] [T012] [P] Create Avro schema for PaymentCompletedEvent in `shared/events/product/payment-completed.avsc`
- [X] [T013] Configure Avro code generation plugin in `product/adapter/build.gradle.kts`

### Application Configuration

- [X] [T014] [P] Create application configuration in `product/app/src/main/resources/application.yml` with R2DBC pool settings (initial=10, max=20)
- [X] [T015] [P] Create worker application configuration in `product/worker/src/main/resources/application.yml` with Kafka consumer settings
- [X] [T016] Configure distributed tracing with trace ID propagation in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/config/TracingConfig.kt`

---

## Phase 2: Domain Core (US3 Dependencies) ✅ COMPLETE

### Domain Entities ✅ COMPLETE

- [X] [T017] [P] Create Product entity in `product/core/src/main/kotlin/com/dopaminestore/product/core/domain/Product.kt` with status computation
- [X] [T018] [P] Create PurchaseSlot entity in `product/core/src/main/kotlin/com/dopaminestore/product/core/domain/PurchaseSlot.kt` with state transitions
- [X] [T019] [P] Create Purchase entity in `product/core/src/main/kotlin/com/dopaminestore/product/core/domain/Purchase.kt` with payment status
- [X] [T020] [P] Create domain value objects (Money, ProductStatus, SlotStatus, PaymentStatus) in `product/core/src/main/kotlin/com/dopaminestore/product/core/domain/value/`

### Repository Ports (Interfaces) ✅ COMPLETE

- [X] [T021] [P] Create ProductRepository port interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/port/ProductRepository.kt`
- [X] [T022] [P] Create PurchaseSlotRepository port interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/port/PurchaseSlotRepository.kt`
- [X] [T023] [P] Create PurchaseRepository port interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/port/PurchaseRepository.kt`
- [X] [T024] [P] Create SlotAuditRepository port interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/port/SlotAuditRepository.kt`

### External Service Ports ✅ COMPLETE

- [X] [T025] [P] Create RedisSlotCache port interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/port/RedisSlotCache.kt` for fairness queue operations
- [X] [T026] [P] Create EventPublisher port interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/port/EventPublisher.kt` for Kafka events
- [X] [T027] [P] Create PaymentGateway port interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/port/PaymentGateway.kt` for payment processing

### Unit Tests (Domain Layer) ✅ COMPLETE

- [X] [T027a] [P] Create Money value object tests in `product/core/src/test/kotlin/com/dopaminestore/product/core/domain/value/MoneyTest.kt` with arithmetic operations and validation (31 test cases)
- [X] [T027b] [P] Create ProductStatus tests in `product/core/src/test/kotlin/com/dopaminestore/product/core/domain/value/ProductStatusTest.kt` with status computation (10 test cases)
- [X] [T027c] [P] Create SlotStatus tests in `product/core/src/test/kotlin/com/dopaminestore/product/core/domain/value/SlotStatusTest.kt` with state transitions (10 test cases)
- [X] [T027d] [P] Create PaymentStatus tests in `product/core/src/test/kotlin/com/dopaminestore/product/core/domain/value/PaymentStatusTest.kt` with validation (10 test cases)
- [X] [T027e] [P] Create Product entity tests in `product/core/src/test/kotlin/com/dopaminestore/product/core/domain/ProductTest.kt` with stock management and status computation (18 test cases)
- [X] [T027f] [P] Create PurchaseSlot entity tests in `product/core/src/test/kotlin/com/dopaminestore/product/core/domain/PurchaseSlotTest.kt` with expiration and state transitions (21 test cases)
- [X] [T027g] [P] Create Purchase entity tests in `product/core/src/test/kotlin/com/dopaminestore/product/core/domain/PurchaseTest.kt` with payment status transitions (19 test cases)

---

## Phase 3: US1 - Slot Acquisition (P1 - MVP) ✅ COMPLETE

**User Story**: First-Come-First-Served Slot Acquisition (100K RPS)

### Core Business Logic ✅ COMPLETE

- [X] [T028] [US1] Create SlotAcquisitionUseCase interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/usecase/SlotAcquisitionUseCase.kt`
- [X] [T029] [US1] Implement SlotAcquisitionService in `product/core/src/main/kotlin/com/dopaminestore/product/core/service/SlotAcquisitionService.kt` with duplicate check and fairness guarantee
- [X] [T030] [US1] Implement arrival-time ordering logic using Redis ZSET scores in SlotAcquisitionService

### Adapter Implementations ✅ COMPLETE

- [X] [T031] [US1] Implement RedisSlotCacheImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/redis/RedisSlotCacheImpl.kt` with Lua script execution
- [X] [T032] [US1] Implement ProductRepositoryImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/persistence/ProductRepositoryImpl.kt` using R2DBC (added V005 migration for price column)
- [X] [T033] [US1] Implement PurchaseSlotRepositoryImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/persistence/PurchaseSlotRepositoryImpl.kt`
- [X] [T034] [US1] Implement EventPublisherImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/kafka/EventPublisherImpl.kt` with trace ID propagation

### REST API Endpoint ✅ COMPLETE

- [X] [T035] [US1] Create SlotController in `product/app/src/main/kotlin/com/dopaminestore/product/app/controller/SlotController.kt` with POST /slots/acquire endpoint
- [X] [T036] [US1] Create SlotAcquireRequest DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/SlotDtos.kt` (AcquireSlotRequest)
- [X] [T037] [US1] Create SlotResponse DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/SlotDtos.kt` (AcquireSlotResponse with remainingSeconds)
- [X] [T038] [US1] Implement error handling for sold-out and duplicate requests with RFC 7807 Problem Details format

### Unit & Integration Tests

- [X] [T038a] [US1] Create SlotAcquisitionServiceTest in `product/core/src/test/kotlin/com/dopaminestore/product/core/service/SlotAcquisitionServiceTest.kt` with mocked repositories (8 test cases)
- [ ] [T038b] [US1] Create RedisSlotCacheImplTest in `product/adapter/src/test/kotlin/com/dopaminestore/product/adapter/redis/RedisSlotCacheImplTest.kt` with embedded Redis
- [ ] [T038c] [US1] Create ProductRepositoryImplTest in `product/adapter/src/test/kotlin/com/dopaminestore/product/adapter/persistence/ProductRepositoryImplTest.kt` with Testcontainers PostgreSQL
- [ ] [T038d] [US1] Create SlotAcquisitionIntegrationTest in `product/tests/src/test/kotlin/com/dopaminestore/product/integration/SlotAcquisitionIntegrationTest.kt` with end-to-end flow
- [ ] [T038e] [US1] Test duplicate prevention with concurrent requests
- [ ] [T038f] [US1] Test fairness guarantee with arrival-time ordering verification

### Load Testing

- [ ] [T039] [US1] Create k6 test script in `product/tests/load/slot_acquisition_100k_rps.js` with 100K RPS target
- [ ] [T040] [US1] Configure k6 distributed execution setup for 4 instances (25K RPS each)
- [ ] [T041] [US1] Execute load test and verify p99 latency < 100ms requirement (SC-002)
- [ ] [T042] [US1] Execute load test and verify exactly N slots granted for N stock items (SC-003)
- [ ] [T043] [US1] Document bottlenecks and optimization results in `product/docs/load-test-results.md`

---

## Phase 4: US2 - Slot Expiration (P2)

**User Story**: Slot Expiration and Reclamation (30-minute auto-expiration)

### Core Business Logic

- [ ] [T044] [US2] Create SlotExpirationUseCase interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/usecase/SlotExpirationUseCase.kt`
- [ ] [T045] [US2] Implement SlotExpirationService in `product/core/src/main/kotlin/com/dopaminestore/product/core/service/SlotExpirationService.kt` with atomic reclamation logic
- [ ] [T046] [US2] Implement hybrid expiration strategy (Redis TTL + scheduled job + lazy evaluation)

### Worker Module

- [ ] [T047] [US2] Create SlotExpirationJob in `product/worker/src/main/kotlin/com/dopaminestore/product/worker/job/SlotExpirationJob.kt` with @Scheduled(fixedDelay = 60000)
- [ ] [T048] [US2] Implement Redis ZRANGEBYSCORE query for expired slots in SlotExpirationJob
- [ ] [T049] [US2] Implement atomic stock reclamation (INCR product:{id}:stock) in SlotExpirationJob
- [ ] [T050] [US2] Publish SLOT_EXPIRED Kafka events with reclaimStatus in SlotExpirationJob

### Pre-Expiration Notification

- [ ] [T051] [US2] Create PreExpirationNotificationJob in `product/worker/src/main/kotlin/com/dopaminestore/product/worker/job/PreExpirationNotificationJob.kt` for 5-minute warning
- [ ] [T052] [US2] Query slots expiring in 5 minutes and publish notification events

### Audit Logging

- [ ] [T053] [US2] Implement SlotAuditRepositoryImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/persistence/SlotAuditRepositoryImpl.kt`
- [ ] [T054] [US2] Log all slot state transitions (ACTIVE → EXPIRED) with trace IDs

### Unit & Integration Tests

- [ ] [T054a] [US2] [P] Create SlotExpirationServiceTest in `product/core/src/test/kotlin/com/dopaminestore/product/core/service/SlotExpirationServiceTest.kt` with mocked repositories
- [ ] [T054b] [US2] [P] Create SlotExpirationJobTest in `product/worker/src/test/kotlin/com/dopaminestore/product/worker/job/SlotExpirationJobTest.kt` with scheduled execution
- [ ] [T054c] [US2] [P] Create PreExpirationNotificationJobTest in `product/worker/src/test/kotlin/com/dopaminestore/product/worker/job/PreExpirationNotificationJobTest.kt`
- [ ] [T054d] [US2] Create SlotExpirationIntegrationTest in `product/tests/src/test/kotlin/com/dopaminestore/product/integration/SlotExpirationIntegrationTest.kt` with 30-minute timeout verification
- [ ] [T054e] [US2] Test atomic stock reclamation with concurrent expirations
- [ ] [T054f] [US2] Test audit log completeness for all state transitions

---

## Phase 5: US3 - Product Management (P3)

**User Story**: Product Management by Admin (CRUD operations)

### Core Business Logic

- [ ] [T055] [US3] Create ProductManagementUseCase interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/usecase/ProductManagementUseCase.kt`
- [ ] [T056] [US3] Implement ProductManagementService in `product/core/src/main/kotlin/com/dopaminestore/product/core/service/ProductManagementService.kt`
- [ ] [T057] [US3] Implement product creation validation (stock > 0, saleDate in future) in ProductManagementService
- [ ] [T058] [US3] Implement product update validation (stock increase only, saleDate immutable if slots exist)
- [ ] [T059] [US3] Implement product deletion validation (block if active slots exist)

### REST API Endpoints

- [ ] [T060] [US3] Create ProductController in `product/app/src/main/kotlin/com/dopaminestore/product/app/controller/ProductController.kt`
- [ ] [T061] [US3] Implement GET /products endpoint with pagination and status filtering
- [ ] [T062] [US3] Implement POST /products endpoint with admin authorization
- [ ] [T063] [US3] Implement GET /products/{id} endpoint
- [ ] [T064] [US3] Implement PUT /products/{id} endpoint with conflict handling (409)
- [ ] [T065] [US3] Implement DELETE /products/{id} endpoint with conflict handling

### DTOs

- [ ] [T066] [US3] [P] Create CreateProductRequest DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/CreateProductRequest.kt`
- [ ] [T067] [US3] [P] Create UpdateProductRequest DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/UpdateProductRequest.kt`
- [ ] [T068] [US3] [P] Create ProductResponse DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/ProductResponse.kt` with computed status
- [ ] [T069] [US3] [P] Create ProductListResponse DTO with pagination metadata

### Unit & Integration Tests

- [ ] [T069a] [US3] [P] Create ProductManagementServiceTest in `product/core/src/test/kotlin/com/dopaminestore/product/core/service/ProductManagementServiceTest.kt` with validation rules
- [ ] [T069b] [US3] [P] Create ProductControllerTest in `product/app/src/test/kotlin/com/dopaminestore/product/app/controller/ProductControllerTest.kt` with WebTestClient
- [ ] [T069c] [US3] Test admin authorization for POST/PUT/DELETE endpoints
- [ ] [T069d] [US3] Test pagination with large product catalogs (1000+ products)
- [ ] [T069e] [US3] Test conflict handling when deleting product with active slots
- [ ] [T069f] [US3] Test stock update validation (increase only, no decrease)

---

## Phase 6: US4 - Payment Processing (P4)

**User Story**: Payment Processing (Async webhook pattern with stub)

### Core Business Logic

- [ ] [T070] [US4] Create PaymentUseCase interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/usecase/PaymentUseCase.kt`
- [ ] [T071] [US4] Implement PaymentService in `product/core/src/main/kotlin/com/dopaminestore/product/core/service/PaymentService.kt` with slot validation
- [ ] [T072] [US4] Implement lazy evaluation for slot expiration check at payment time
- [ ] [T073] [US4] Implement idempotency key validation using Redis

### Payment Gateway Stub

- [ ] [T074] [US4] Implement PaymentGatewayStub in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/external/PaymentGatewayStub.kt` with 95% success rate simulation
- [ ] [T075] [US4] Implement 200ms latency simulation in PaymentGatewayStub
- [ ] [T076] [US4] Implement idempotency key storage in Redis with 24-hour TTL

### REST API Endpoints

- [ ] [T077] [US4] Create PaymentController in `product/app/src/main/kotlin/com/dopaminestore/product/app/controller/PaymentController.kt`
- [ ] [T078] [US4] Implement POST /payments endpoint with idempotency key validation
- [ ] [T079] [US4] Implement GET /payments/{id} endpoint
- [ ] [T080] [US4] Implement error handling for expired slots (409 Conflict)

### DTOs

- [ ] [T081] [US4] [P] Create ProcessPaymentRequest DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/ProcessPaymentRequest.kt`
- [ ] [T082] [US4] [P] Create PaymentResponse DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/PaymentResponse.kt`

### Payment Completion Flow

- [ ] [T083] [US4] Implement payment webhook consumer in `product/worker/src/main/kotlin/com/dopaminestore/product/worker/consumer/PaymentWebhookConsumer.kt`
- [ ] [T084] [US4] Implement atomic slot status update (ACTIVE → COMPLETED) on payment success
- [ ] [T085] [US4] Publish PAYMENT_COMPLETED Kafka event with trace ID

### Unit & Integration Tests

- [ ] [T085a] [US4] [P] Create PaymentServiceTest in `product/core/src/test/kotlin/com/dopaminestore/product/core/service/PaymentServiceTest.kt` with slot validation
- [ ] [T085b] [US4] [P] Create PaymentGatewayStubTest in `product/adapter/src/test/kotlin/com/dopaminestore/product/adapter/external/PaymentGatewayStubTest.kt` with latency verification
- [ ] [T085c] [US4] [P] Create PaymentControllerTest in `product/app/src/test/kotlin/com/dopaminestore/product/app/controller/PaymentControllerTest.kt` with WebTestClient
- [ ] [T085d] [US4] Test idempotency key validation with duplicate payment requests
- [ ] [T085e] [US4] Test expired slot rejection (409 Conflict) during payment
- [ ] [T085f] [US4] Create PaymentWebhookConsumerTest in `product/worker/src/test/kotlin/com/dopaminestore/product/worker/consumer/PaymentWebhookConsumerTest.kt`
- [ ] [T085g] [US4] Create PaymentIntegrationTest in `product/tests/src/test/kotlin/com/dopaminestore/product/integration/PaymentIntegrationTest.kt` with end-to-end webhook flow

---

## Phase 7: US5 - My Purchase Slots View (P5)

**User Story**: My Purchase Slots View (User slot history)

### Core Business Logic

- [ ] [T086] [US5] Create SlotQueryUseCase interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/usecase/SlotQueryUseCase.kt`
- [ ] [T087] [US5] Implement SlotQueryService in `product/core/src/main/kotlin/com/dopaminestore/product/core/service/SlotQueryService.kt` with user filtering

### REST API Endpoints

- [ ] [T088] [US5] Implement GET /slots/my-slots endpoint in SlotController with status filtering
- [ ] [T089] [US5] Implement GET /slots/{id} endpoint with user ownership validation (403 Forbidden)
- [ ] [T090] [US5] Add productName denormalization to SlotResponse for convenience

### DTOs

- [ ] [T091] [US5] [P] Create MySlotListResponse DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/MySlotListResponse.kt` with totalCount

### Unit & Integration Tests

- [ ] [T091a] [US5] [P] Create SlotQueryServiceTest in `product/core/src/test/kotlin/com/dopaminestore/product/core/service/SlotQueryServiceTest.kt` with user filtering
- [ ] [T091b] [US5] [P] Create SlotControllerTest in `product/app/src/test/kotlin/com/dopaminestore/product/app/controller/SlotControllerTest.kt` for GET endpoints
- [ ] [T091c] [US5] Test user ownership validation (403 Forbidden for other user's slots)
- [ ] [T091d] [US5] Test status filtering (ACTIVE, EXPIRED, COMPLETED)
- [ ] [T091e] [US5] Test pagination with large slot history (100+ slots per user)

---

## Phase 8: Observability & Polish

### Health Check

- [ ] [T092] [P] Create HealthController in `product/app/src/main/kotlin/com/dopaminestore/product/app/controller/HealthController.kt`
- [ ] [T093] [P] Implement GET /health endpoint with PostgreSQL, Redis, and Kafka component checks
- [ ] [T094] [P] Return 503 Service Unavailable if any component is DOWN

### Distributed Tracing

- [ ] [T095] Implement trace ID generation middleware in `product/app/src/main/kotlin/com/dopaminestore/product/app/filter/TraceIdFilter.kt`
- [ ] [T096] Add X-Trace-ID header to all responses
- [ ] [T097] Propagate trace IDs to Kafka events and Redis operations

### Error Handling

- [ ] [T098] Create global exception handler in `product/app/src/main/kotlin/com/dopaminestore/product/app/exception/GlobalExceptionHandler.kt`
- [ ] [T099] Implement RFC 7807 Problem Details response format for all errors
- [ ] [T100] Implement rate limit exceeded handling (429 Too Many Requests)

### Metrics & Monitoring

- [ ] [T101] [P] Configure Micrometer Prometheus endpoint in `product/app/src/main/resources/application.yml`
- [ ] [T102] [P] Add custom metrics for slot acquisition rate, payment success rate, and expiration rate
- [ ] [T103] Create Grafana dashboard configuration in `product/docs/grafana-dashboard.json`

### Logging

- [ ] [T104] Configure structured logging (JSON format) in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/config/LoggingConfig.kt`
- [ ] [T105] Add business event logging for SLOT_REQUESTED, SLOT_ACQUIRED, SLOT_EXPIRED, PAYMENT_COMPLETED
- [ ] [T106] Ensure all critical paths log with trace IDs and latency metrics

### Documentation

- [ ] [T107] Update OpenAPI spec in `specs/product/002-product-api-spec/contracts/openapi.yaml` with actual implementation details
- [ ] [T108] Create API usage examples in `product/docs/api-examples.md`
- [ ] [T109] Document Redis key patterns and TTL policies in `product/docs/redis-architecture.md`
- [ ] [T110] Document Kafka event schemas and consumption patterns in `product/docs/event-architecture.md`

### Unit & Integration Tests

- [ ] [T110a] [P] Create HealthControllerTest in `product/app/src/test/kotlin/com/dopaminestore/product/app/controller/HealthControllerTest.kt` with component health checks
- [ ] [T110b] [P] Create TraceIdFilterTest in `product/app/src/test/kotlin/com/dopaminestore/product/app/filter/TraceIdFilterTest.kt` with header propagation
- [ ] [T110c] [P] Create GlobalExceptionHandlerTest in `product/app/src/test/kotlin/com/dopaminestore/product/app/exception/GlobalExceptionHandlerTest.kt` with RFC 7807 format
- [ ] [T110d] [P] Test metrics collection for slot acquisition, payment success, and expiration rates
- [ ] [T110e] [P] Test structured logging output with JSON format validation
- [ ] [T110f] [P] Test trace ID propagation through Kafka events and Redis operations

---

## Task Summary

**Total Tasks**: 152 (110 implementation + 42 test tasks)
**Parallelizable Tasks**: 59 (marked with [P])
**Test Tasks**: 42 (unit tests, integration tests, load tests)

### By Phase
- Phase 1 (Setup): 16 tasks (T001-T016)
- Phase 2 (Domain Core): 18 tasks (T017-T027g) - includes 7 unit test tasks
- Phase 3 (US1 - Slot Acquisition): 21 tasks (T028-T043) - includes 6 unit/integration test tasks
- Phase 4 (US2 - Slot Expiration): 17 tasks (T044-T054f) - includes 6 unit/integration test tasks
- Phase 5 (US3 - Product Management): 21 tasks (T055-T069f) - includes 6 unit/integration test tasks
- Phase 6 (US4 - Payment): 23 tasks (T070-T085g) - includes 7 unit/integration test tasks
- Phase 7 (US5 - My Slots): 11 tasks (T086-T091e) - includes 5 unit/integration test tasks
- Phase 8 (Observability): 25 tasks (T092-T110f) - includes 6 unit/integration test tasks

### By User Story
- US1 (Slot Acquisition): 15 tasks
- US2 (Slot Expiration): 11 tasks
- US3 (Product Management): 15 tasks
- US4 (Payment Processing): 16 tasks
- US5 (My Purchase Slots): 6 tasks

### Critical Path (MVP)
For minimal viable deployment, complete these tasks in order:
1. Phase 1 (all 16 tasks)
2. Phase 2 (all 11 tasks)
3. Phase 3 (T028-T043 for US1)
4. T092-T094 (health check)
5. T095-T097 (tracing)
6. T041-T042 (load testing validation)

---

## Dependencies

### External Dependencies
- PostgreSQL database instance (configured in application.yml)
- Redis cluster (configured in RedisConfig.kt)
- Kafka cluster with Schema Registry (configured in KafkaProducerConfig.kt)
- Auth domain (for user authentication/authorization via JWT)

### Inter-Task Dependencies
- T031 depends on T007 (Lua script)
- T034 depends on T010-T012 (Avro schemas)
- T041-T042 depend on T035 (API endpoint implementation)
- T047-T050 depend on T045 (expiration service)
- T078 depends on T074 (payment gateway stub)

### Blocking Tasks (must complete before others)
- T001-T005 (database migrations) must complete before any repository implementations
- T006-T008 (Redis setup) must complete before T031 (Redis cache implementation)
- T009-T013 (Kafka setup) must complete before T034 (event publisher)
- T017-T020 (domain entities) must complete before any service implementations

---

## Success Criteria Mapping

| Success Criteria | Related Tasks |
|------------------|---------------|
| SC-001 (100K RPS) | T028-T043 (US1 implementation + load testing) |
| SC-002 (p99 < 100ms) | T041, T101-T103 (load testing + metrics) |
| SC-003 (Exact N slots) | T029-T030 (fairness logic) |
| SC-004 (30s expiration) | T044-T050 (expiration job) |
| SC-005 (95% pre-expiration) | T051-T052 (notification job) |
| SC-006 (Payment p99 < 500ms) | T070-T085 (payment implementation) |
| SC-007 (< 0.1% error rate) | T098-T100 (error handling) |
| SC-008 (Zero duplicates) | T029, T031 (duplicate prevention) |

---

**Document Version**: 1.0
**Last Updated**: 2026-01-05
**Ready for Implementation**: ✅ YES
