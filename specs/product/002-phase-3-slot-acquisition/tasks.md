# Tasks: Phase 3 - Slot Acquisition (US1)

**Feature Branch**: `product/002-phase-3-slot-acquisition`
**Status**: 🟡 In Progress
**Created**: 2026-01-06
**Last Updated**: 2026-01-06

## Overview

Phase 3 implements the first-come-first-served slot acquisition feature (US1) with 100K RPS capability and strict fairness guarantees.

**Tech Stack**: Spring Boot WebFlux, R2DBC, Redis (Lettuce), Kafka
**Dependencies**: Phase 1 (Infrastructure), Phase 2 (Domain Core)
**Target**: 100K RPS with p99 < 100ms

---

## Core Business Logic ✅ COMPLETE

- [X] [T028] [US1] Create SlotAcquisitionUseCase interface in `product/core/src/main/kotlin/com/dopaminestore/product/core/usecase/SlotAcquisitionUseCase.kt`
- [X] [T029] [US1] Implement SlotAcquisitionService in `product/core/src/main/kotlin/com/dopaminestore/product/core/service/SlotAcquisitionService.kt` with duplicate check and fairness guarantee
- [X] [T030] [US1] Implement arrival-time ordering logic using Redis ZSET scores in SlotAcquisitionService

## Adapter Implementations ✅ COMPLETE

- [X] [T031] [US1] Implement RedisSlotCacheImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/redis/RedisSlotCacheImpl.kt` with Lua script execution
- [X] [T032] [US1] Implement ProductRepositoryImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/persistence/ProductRepositoryImpl.kt` using R2DBC (added V005 migration for price column)
- [X] [T033] [US1] Implement PurchaseSlotRepositoryImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/persistence/PurchaseSlotRepositoryImpl.kt`
- [X] [T034] [US1] Implement EventPublisherImpl in `product/adapter/src/main/kotlin/com/dopaminestore/product/adapter/kafka/EventPublisherImpl.kt` with trace ID propagation

## REST API Endpoint ✅ COMPLETE

- [X] [T035] [US1] Create SlotController in `product/app/src/main/kotlin/com/dopaminestore/product/app/controller/SlotController.kt` with POST /slots/acquire endpoint
- [X] [T036] [US1] Create SlotAcquireRequest DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/SlotDtos.kt` (AcquireSlotRequest)
- [X] [T037] [US1] Create SlotResponse DTO in `product/app/src/main/kotlin/com/dopaminestore/product/app/dto/SlotDtos.kt` (AcquireSlotResponse with remainingSeconds)
- [X] [T038] [US1] Implement error handling for sold-out and duplicate requests with RFC 7807 Problem Details format

## Unit Tests ✅ COMPLETE (Partial)

- [X] [T038a] [US1] Create SlotAcquisitionServiceTest in `product/core/src/test/kotlin/com/dopaminestore/product/core/service/SlotAcquisitionServiceTest.kt` with mocked repositories (8 test cases)
- [ ] [T038b] [US1] Create RedisSlotCacheImplTest in `product/adapter/src/test/kotlin/com/dopaminestore/product/adapter/redis/RedisSlotCacheImplTest.kt` with embedded Redis
- [ ] [T038c] [US1] Create ProductRepositoryImplTest in `product/adapter/src/test/kotlin/com/dopaminestore/product/adapter/persistence/ProductRepositoryImplTest.kt` with Testcontainers PostgreSQL

## Integration Tests 🔴 TODO

- [ ] [T038d] [US1] Create SlotAcquisitionIntegrationTest in `product/tests/src/test/kotlin/com/dopaminestore/product/integration/SlotAcquisitionIntegrationTest.kt` with end-to-end flow
- [ ] [T038e] [US1] Test duplicate prevention with concurrent requests
- [ ] [T038f] [US1] Test fairness guarantee with arrival-time ordering verification

## Load Testing 🔴 TODO

- [ ] [T039] [US1] Create k6 test script in `product/tests/load/slot_acquisition_100k_rps.js` with 100K RPS target
- [ ] [T040] [US1] Configure k6 distributed execution setup for 4 instances (25K RPS each)
- [ ] [T041] [US1] Execute load test and verify p99 latency < 100ms requirement (SC-002)
- [ ] [T042] [US1] Execute load test and verify exactly N slots granted for N stock items (SC-003)
- [ ] [T043] [US1] Document bottlenecks and optimization results in `product/docs/load-test-results.md`

---

## Summary

✅ **11/18 tasks completed** (61%)
✅ **8 unit tests** - All passing (127 total with Phase 2)

### Completed
- Core business logic with 6-step orchestration
- All adapter implementations (Redis, R2DBC, Kafka)
- REST API with RFC 7807 error handling
- Unit tests for service layer

### Remaining
- Adapter unit tests (Redis, R2DBC)
- Integration tests (end-to-end, concurrency, fairness)
- Load testing (100K RPS validation)

**Next Steps**: Complete adapter tests (T038b-T038c), then integration and load testing
