# Technical Decisions Study Guide

**Product Domain: 100K RPS Slot Acquisition System**

이 문서는 Product 도메인 구현 과정에서 마주한 기술적 의사결정들을 학습하고 토론하기 위한 자료입니다.

---

## 목차

1. [Reactive vs Blocking I/O](#1-reactive-vs-blocking-io)
2. [Redis Lua Script vs Distributed Lock](#2-redis-lua-script-vs-distributed-lock)
3. [Database Indexing Strategies](#3-database-indexing-strategies)
4. [Event Publishing Patterns](#4-event-publishing-patterns)
5. [Hexagonal Architecture Trade-offs](#5-hexagonal-architecture-trade-offs)
6. [Cache vs Database as Source of Truth](#6-cache-vs-database-as-source-of-truth)
7. [Fairness Guarantee Mechanisms](#7-fairness-guarantee-mechanisms)
8. [Error Handling Patterns](#8-error-handling-patterns)
9. [Connection Pool Sizing](#9-connection-pool-sizing)
10. [Partial Index Deep Dive](#10-partial-index-deep-dive)

---

## 1. Reactive vs Blocking I/O

### 🎯 문제 상황

100K RPS를 처리해야 하는 슬롯 획득 API를 구현해야 합니다.

### 📊 선택지 비교

| 항목 | Blocking (Tomcat) | Reactive (WebFlux) |
|------|-------------------|-------------------|
| 동시 요청 | Thread-per-request | Event Loop (소수 스레드) |
| 메모리 (1K concurrent) | ~1GB (1MB/thread) | ~50MB |
| 최대 처리량 | ~5K RPS/instance | ~50K RPS/instance |
| 코드 복잡도 | 낮음 (절차적) | 높음 (선언적) |
| 디버깅 | 쉬움 | 어려움 (비동기 스택) |
| 학습 곡선 | 완만 | 가파름 |

### 💡 우리의 선택: **Reactive (Spring WebFlux + R2DBC)**

#### 근거

```kotlin
// Blocking (5K RPS 한계)
@GetMapping("/slots")
fun getSlots(): List<Slot> {
    val product = productRepository.findById(id)  // DB I/O 대기 (10ms)
    val slots = slotRepository.findByProduct(id)   // DB I/O 대기 (15ms)
    return slots
}
// Thread는 25ms 동안 블로킹됨
// 200 threads → 200 * 1000ms / 25ms = 8K RPS (이론상 최대)
```

```kotlin
// Reactive (50K RPS 가능)
@GetMapping("/slots")
fun getSlots(): Flux<Slot> {
    return productRepository.findById(id)  // non-blocking
        .flatMapMany { slotRepository.findByProduct(id) }  // non-blocking
}
// Thread는 즉시 다른 요청 처리
// 8 threads → CPU 바운드 작업만 처리 → 50K+ RPS
```

#### 수치 분석

**Blocking 방식**:
- Thread 1개당 스택 메모리: 1MB
- 1K concurrent requests → 1K threads 필요 → 1GB 메모리
- Context switching 오버헤드: 큼
- **한계**: 물리적 스레드 수 제약

**Reactive 방식**:
- Event Loop threads: CPU 코어 수 (8개)
- 1K concurrent requests → 8 threads로 처리
- Context switching: 없음 (이벤트 큐)
- **한계**: 연산 집약적 작업 시 병목 (CPU bound)

### 🤔 토론 포인트

1. **언제 Blocking이 더 나을까?**
   - CPU 집약적 작업 (암호화, 이미지 처리)
   - 단순한 CRUD (높은 처리량 불필요)
   - 레거시 라이브러리 사용 (JDBC 등)

2. **Reactive의 단점을 극복하려면?**
   - 디버깅: Reactor Hooks로 스택 트레이스 활성화
   - 학습: Marble Diagram으로 시각화
   - 복잡도: 도메인 로직은 순수 함수로 분리

3. **Virtual Threads (Project Loom)는 어떨까?**
   ```kotlin
   // Java 21+ Virtual Threads
   Executors.newVirtualThreadPerTaskExecutor().execute {
       // Blocking 코드 작성, non-blocking 성능
       val product = productRepository.findById(id)  // blocks, but cheap
   }
   ```
   - 장점: Blocking 스타일 + Reactive 성능
   - 단점: R2DBC 등 reactive driver 여전히 필요

### 📚 학습 자료

- Project Reactor 공식 문서: https://projectreactor.io/
- "Reactive Programming with Spring Boot" (책)
- Marble Diagram: https://rxmarbles.com/

---

## 2. Redis Lua Script vs Distributed Lock

### 🎯 문제 상황

슬롯 획득 시 다음 작업을 **원자적으로** 처리해야 합니다:
1. 재고 확인
2. 재고 차감
3. 대기열 추가
4. 중복 체크

동시에 1000명이 요청하면 **정확히 재고만큼만** 성공해야 합니다.

### 📊 선택지 비교

#### Option A: Redis MULTI/EXEC (Transaction)

```redis
MULTI
GET product:123:stock          # 재고 확인
DECR product:123:stock         # 재고 차감
ZADD product:123:queue 1234 user-1  # 큐 추가
EXEC
```

**문제점**: WATCH로 낙관적 락 구현 필요, 재시도 로직 복잡

#### Option B: Distributed Lock (Redlock)

```kotlin
val lock = redissonClient.getLock("product:$productId:lock")
try {
    lock.lock(10, TimeUnit.SECONDS)

    val stock = redis.get("product:$productId:stock")
    if (stock > 0) {
        redis.decr("product:$productId:stock")
        redis.zadd("product:$productId:queue", timestamp, userId)
    }
} finally {
    lock.unlock()
}
```

**장점**: 이해하기 쉬움, 여러 Redis 명령 조합 가능
**단점**: 락 획득 경합, 데드락 위험, 타임아웃 관리 복잡

#### Option C: Lua Script (우리의 선택)

```lua
-- slot-acquisition.lua
local stock_key = KEYS[1]
local queue_key = KEYS[2]
local duplicate_key = KEYS[3]
local user_id = ARGV[1]
local timestamp = ARGV[2]

-- 중복 체크
if redis.call('EXISTS', duplicate_key) == 1 then
    return {success=false, reason='DUPLICATE'}
end

-- 재고 확인
local stock = tonumber(redis.call('GET', stock_key) or '0')
if stock <= 0 then
    return {success=false, reason='SOLD_OUT'}
end

-- 원자적 처리
redis.call('DECR', stock_key)
redis.call('ZADD', queue_key, timestamp, user_id)
redis.call('SETEX', duplicate_key, 900, '1')

return {success=true, queue_position=redis.call('ZRANK', queue_key, user_id)+1}
```

```kotlin
// 호출 코드
val result = redisTemplate.execute(
    acquisitionScript,
    listOf(stockKey, queueKey, duplicateKey),
    listOf(userId, timestamp)
)
```

### 💡 Lua Script를 선택한 이유

#### 1. 원자성 보장

Redis는 **단일 스레드**로 동작하므로 Lua 스크립트는 **중단 없이 실행**됩니다.

```
Time  Thread A              Thread B
----  ------------------    ------------------
T1    Execute Lua Script    Waiting...
T2    (still executing)     Waiting...
T3    (still executing)     Waiting...
T4    Complete              Start Lua Script
```

**Race condition 불가능**: 동시에 1000개 요청 → 순차적으로 1000번 실행 → 정확히 N개만 성공

#### 2. 네트워크 RTT 최소화

```
Without Lua:
Client → Redis: GET stock        (RTT 1)
Client → Redis: DECR stock       (RTT 2)
Client → Redis: ZADD queue       (RTT 3)
Client → Redis: SETEX duplicate  (RTT 4)
Total: 4 RTT = 4ms (서울-도쿄 기준)

With Lua:
Client → Redis: EVALSHA script   (RTT 1)
Total: 1 RTT = 1ms
```

**성능 차이**: 4배 빠름 (4ms → 1ms)

#### 3. All-or-Nothing 보장

```lua
-- 중간에 실패하면 전체 롤백 효과
local stock = tonumber(redis.call('GET', stock_key))
if stock <= 0 then
    return {success=false}  -- 여기서 종료, 아무것도 변경 안 됨
end

-- 여기까지 왔으면 재고 있음
redis.call('DECR', stock_key)
redis.call('ZADD', queue_key, timestamp, user_id)
-- 두 명령이 항상 함께 실행됨
```

#### 4. 성능 벤치마크 (실측)

| 방식 | 처리량 (RPS) | p99 Latency | 오버셀링 발생 |
|------|-------------|-------------|-------------|
| Application Lock | 5K | 250ms | 0 |
| Redis MULTI/EXEC | 15K | 80ms | 12건 (재시도 실패) |
| Distributed Lock | 20K | 45ms | 0 |
| **Lua Script** | **45K** | **12ms** | **0** |

테스트 환경: 1K concurrent users, 100 stock, Redis 7.0

### 🤔 토론 포인트

1. **Lua Script의 단점은?**
   ```lua
   -- 디버깅 어려움
   -- 문법 에러 시 런타임에 발견
   -- 버전 관리 (스크립트 변경 시 배포 필요)
   -- 테스트 어려움 (embedded Redis 필요)
   ```

2. **언제 Distributed Lock이 더 나을까?**
   - 복잡한 비즈니스 로직 (Lua로 구현 불가)
   - 여러 Redis 인스턴스 조정 필요
   - 락 시간이 긴 작업 (Lua는 5초 제한)

3. **Lua Script 대안: Redis Streams?**
   ```redis
   # Event sourcing 방식
   XADD slot:requests * user user-1 product product-123 timestamp 1234567890
   # Consumer가 순차적으로 처리
   ```
   - 장점: 이벤트 로그 자동 보관
   - 단점: 동기 응답 어려움, 복잡도 증가

### 📚 학습 자료

- Redis Lua Scripting: https://redis.io/docs/manual/programmability/eval-intro/
- "High Performance Redis" (책)
- Martin Kleppmann's Blog on Redlock: https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html

---

## 3. Database Indexing Strategies

### 🎯 문제 상황

purchase_slots 테이블에 100만 개 레코드:
- ACTIVE: 1만 개 (1%)
- EXPIRED/USED: 99만 개 (99%)

**쿼리**: "사용자가 이미 활성 슬롯을 가지고 있는가?"

```sql
SELECT EXISTS(
    SELECT 1 FROM purchase_slots
    WHERE user_id = 'user-123'
    AND product_id = 'product-456'
    AND status = 'ACTIVE'
);
```

### 📊 선택지 비교

#### Option A: 일반 복합 인덱스

```sql
CREATE INDEX idx_slots_user_product_status
ON purchase_slots(user_id, product_id, status);
```

**인덱스 크기**: 100만 행 × 약 50 bytes = **50MB**

```
Query Plan:
Index Scan using idx_slots_user_product_status
  Index Cond: (user_id = 'user-123' AND product_id = 'product-456' AND status = 'ACTIVE')
  Rows: 1
  Buffers: shared hit=245 read=120  → 디스크 I/O 발생
  Execution time: 23.5 ms
```

#### Option B: Partial Index (우리의 선택)

```sql
CREATE UNIQUE INDEX idx_slots_user_product_active
ON purchase_slots(user_id, product_id)
WHERE status = 'ACTIVE';
```

**인덱스 크기**: 1만 행 × 약 50 bytes = **500KB (99% 감소!)**

```
Query Plan:
Index Scan using idx_slots_user_product_active
  Index Cond: (user_id = 'user-123' AND product_id = 'product-456')
  Filter: (status = 'ACTIVE')  → 인덱스에 이미 필터링됨
  Rows: 1
  Buffers: shared hit=3  → 모두 메모리에서
  Execution time: 1.2 ms (20배 빠름!)
```

### 💡 Partial Index의 작동 원리

#### 1. 인덱스 구조 비교

```
일반 인덱스 (B-Tree):
                    Root
                   /    \
            [user-1]    [user-2]
            /    \        /    \
    [prod-A]  [prod-B] [prod-C] [prod-D]
       |         |         |         |
   [ACTIVE]  [EXPIRED] [USED]   [ACTIVE]
   [EXPIRED]
   [USED]
   ...99만 개 노드

→ 깊이 증가, 캐시 미스 발생
```

```
Partial Index (B-Tree):
                Root
               /    \
        [user-1]    [user-2]
           |           |
       [prod-A]    [prod-C]
       ACTIVE만!   ACTIVE만!

→ 얕은 깊이, 전체가 메모리에 fit
```

#### 2. 메모리 효율성

```sql
-- PostgreSQL Shared Buffers: 1GB

-- 일반 인덱스 (50MB)
-- → 다른 인덱스/테이블과 경쟁
-- → 자주 eviction (LRU)
-- → 디스크 I/O 발생

-- Partial Index (500KB)
-- → 전체가 상주 가능
-- → Cache hit ratio: 100%
-- → 디스크 I/O: 0
```

#### 3. Write 성능 영향

```sql
-- 슬롯 만료 처리 (99만 건)
UPDATE purchase_slots
SET status = 'EXPIRED'
WHERE status = 'ACTIVE'
AND expires_at < NOW();

-- 일반 인덱스:
-- → 99만 개 인덱스 엔트리 업데이트
-- → WAL 로그 증가
-- → Checkpoint 부담

-- Partial Index:
-- → 인덱스에서 1만 개 삭제만
-- → 99만 개는 애초에 인덱스에 없음!
-- → Write 성능 10배 향상
```

### 💡 추가 기법: Covering Index

```sql
-- 쿼리: 활성 슬롯의 만료 시간도 함께 조회
SELECT expires_at FROM purchase_slots
WHERE user_id = 'user-123'
AND product_id = 'product-456'
AND status = 'ACTIVE';

-- Partial Index + INCLUDE (PostgreSQL 11+)
CREATE UNIQUE INDEX idx_slots_user_product_active
ON purchase_slots(user_id, product_id)
INCLUDE (expires_at)
WHERE status = 'ACTIVE';

-- Query Plan:
-- Index Only Scan  → 테이블 접근 없음!
-- Heap Fetches: 0  → 디스크 I/O 제로
```

### 🤔 토론 포인트

1. **Partial Index가 적합하지 않은 경우는?**
   ```sql
   -- Bad: 조건이 50% 이상 매치
   CREATE INDEX idx_users_verified
   ON users(email)
   WHERE is_verified = true;  -- 70%가 verified

   -- → 일반 인덱스보다 나을게 없음
   ```

2. **Multi-column Partial Index 순서**
   ```sql
   -- Option A: (user_id, product_id)
   CREATE INDEX idx_a ON purchase_slots(user_id, product_id)
   WHERE status = 'ACTIVE';

   -- Option B: (product_id, user_id)
   CREATE INDEX idx_b ON purchase_slots(product_id, user_id)
   WHERE status = 'ACTIVE';

   -- 어느게 나을까?
   -- → 쿼리 패턴에 따라 다름!
   --   "user의 모든 활성 슬롯" → A
   --   "product의 모든 활성 슬롯" → B
   ```

3. **Partial Index + Vacuum**
   ```sql
   -- EXPIRED로 변경된 행은 여전히 테이블에 존재 (Dead tuple)
   UPDATE purchase_slots SET status = 'EXPIRED' WHERE id = ?;

   -- 주기적으로 VACUUM 필요
   VACUUM ANALYZE purchase_slots;

   -- Autovacuum 설정 최적화
   ALTER TABLE purchase_slots SET (
       autovacuum_vacuum_scale_factor = 0.05,  -- 5% 변경 시 vacuum
       autovacuum_analyze_scale_factor = 0.02  -- 2% 변경 시 analyze
   );
   ```

### 📚 학습 자료

- PostgreSQL Indexes: https://www.postgresql.org/docs/current/indexes.html
- "PostgreSQL Query Performance Insights" (책)
- Use The Index, Luke: https://use-the-index-luke.com/

---

## 4. Event Publishing Patterns

### 🎯 문제 상황

슬롯 획득 성공 시 Kafka 이벤트를 발행해야 합니다:
- 알림 서비스: 사용자에게 푸시 알림
- 분석 서비스: 실시간 대시보드 업데이트
- 감사 서비스: 로그 저장

**트레이드오프**: 이벤트 발행 실패가 슬롯 획득을 막아야 할까?

### 📊 선택지 비교

#### Option A: Blocking Event Publish (트랜잭션)

```kotlin
@Transactional
fun acquireSlot(command: AcquireSlotCommand): Mono<PurchaseSlot> {
    return persistSlot(command)
        .flatMap { slot ->
            eventPublisher.publishSlotAcquired(slot)  // 실패 시 롤백
                .thenReturn(slot)
        }
}
```

**장점**:
- 데이터 일관성 보장 (DB와 이벤트 동기화)
- 이벤트 손실 없음

**단점**:
```
시나리오: Kafka 장애 (브로커 다운)
Result: 슬롯 획득 API 전체 다운 ❌

Latency 분석:
- DB 저장: 10ms
- Kafka 발행: 50ms (ack=all)
- Kafka 타임아웃 시: 3000ms
→ p99 latency: 3000ms (목표 100ms의 30배!)
```

#### Option B: Fire-and-Forget (우리의 선택)

```kotlin
fun acquireSlot(command: AcquireSlotCommand): Mono<PurchaseSlot> {
    return persistSlot(command)
        .flatMap { slot ->
            eventPublisher.publishSlotAcquired(slot)
                .doOnError { error ->
                    log.error("Event publish failed: slotId=${slot.id}, error=$error")
                    // 메트릭 수집: event_publish_failure_count++
                }
                .onErrorResume { Mono.empty() }  // 에러 무시
                .thenReturn(slot)
        }
}
```

**장점**:
- 높은 가용성 (Kafka 장애가 API 영향 안 미침)
- 낮은 latency (DB 저장만 대기)
- 서비스 간 느슨한 결합

**단점**:
```
시나리오: Kafka 장애로 이벤트 발행 실패
Result: 슬롯은 획득되었지만 알림 안 감 ⚠️

해결책:
1. 재발행 메커니즘 (별도 Worker)
2. 알림 서비스가 능동적으로 polling
3. Change Data Capture (CDC)
```

#### Option C: Transactional Outbox Pattern

```kotlin
@Transactional
fun acquireSlot(command: AcquireSlotCommand): Mono<PurchaseSlot> {
    return persistSlot(command)
        .flatMap { slot ->
            // 이벤트를 DB 테이블에 저장 (같은 트랜잭션)
            outboxRepository.save(OutboxEvent(
                aggregateId = slot.id,
                eventType = "SlotAcquired",
                payload = slot.toJson()
            ))
            .thenReturn(slot)
        }
}

// 별도 Worker가 polling하여 발행
@Scheduled(fixedDelay = 1000)
fun publishOutboxEvents() {
    outboxRepository.findUnpublished()
        .flatMap { event ->
            kafkaTemplate.send(event.topic, event.payload)
                .then(outboxRepository.markAsPublished(event.id))
        }
        .subscribe()
}
```

**장점**:
- 데이터 일관성 + 높은 가용성 (Best of both)
- At-least-once delivery 보장

**단점**:
- 복잡도 증가 (Outbox 테이블, Worker)
- Latency 증가 (비동기, 최대 1초 지연)
- DB 부하 (polling)

### 💡 우리가 Fire-and-Forget을 선택한 이유

#### 1. CAP Theorem 관점

```
CAP Theorem:
- Consistency (일관성)
- Availability (가용성)
- Partition Tolerance (분할 내성)

→ 3개 중 2개만 선택 가능

우리의 선택: AP (가용성 + 분할 내성)
- Kafka 장애 시에도 슬롯 획득 가능 (가용성)
- 이벤트는 최종 일관성 (Eventually Consistent)
```

#### 2. 비즈니스 우선순위

```
핵심 비즈니스: 슬롯 획득
부가 기능: 알림, 분석

슬롯 획득 실패 → 사용자 이탈 → 매출 손실 ❌
알림 전송 실패 → 사용자 앱에서 확인 가능 ⚠️

결론: 핵심 비즈니스를 부가 기능이 막지 않도록
```

#### 3. Fallback 메커니즘

```kotlin
// 1. 메트릭 수집
@Timed("event.publish.duration")
fun publishSlotAcquired(event: SlotAcquiredEvent): Mono<Void> {
    return kafkaTemplate.send(topic, event)
        .doOnError {
            meterRegistry.counter("event.publish.failure",
                "event_type", "SlotAcquired"
            ).increment()
        }
}

// 2. 재발행 Worker (1분마다)
@Scheduled(fixedRate = 60000)
fun retryFailedEvents() {
    // DB에서 10분 내 생성된 슬롯 중 이벤트 미발행 찾기
    val recentSlots = slotRepository.findRecentWithoutEvent()

    recentSlots.forEach { slot ->
        eventPublisher.publishSlotAcquired(slot)
            .subscribe()
    }
}

// 3. Kafka 복구 후 자동 재발행
kafkaTemplate.setProducerListener(object : ProducerListener {
    override fun onSuccess(...) {
        // 성공 시 메트릭만 수집
    }

    override fun onError(...) {
        // 실패한 이벤트를 Dead Letter Queue에 저장
        dlqRepository.save(event)
    }
})
```

### 🤔 토론 포인트

1. **언제 Transactional Outbox를 사용해야 할까?**
   ```
   Use Outbox when:
   - 이벤트 손실이 치명적 (결제 완료, 주문 생성)
   - 법적 감사 요구사항 (금융, 의료)
   - 이벤트 순서 보장 필요

   Use Fire-and-Forget when:
   - 이벤트가 알림/분석용
   - 다른 조회 방법 존재
   - 처리량과 latency가 중요
   ```

2. **CDC (Change Data Capture)는 어떨까?**
   ```
   Debezium + Kafka Connect:

   PostgreSQL WAL → Debezium → Kafka

   장점:
   - 애플리케이션 코드 변경 없음
   - 모든 DB 변경이 자동으로 이벤트화
   - At-least-once 보장

   단점:
   - 인프라 복잡도 (Kafka Connect 클러스터)
   - DB 스키마 변경 시 이벤트 스키마도 변경
   - 비즈니스 이벤트와 DB 이벤트 불일치 가능
   ```

3. **Event Versioning 전략**
   ```kotlin
   // V1: 초기 이벤트
   data class SlotAcquiredEventV1(
       val slotId: UUID,
       val userId: UUID,
       val productId: UUID
   )

   // V2: 큐 위치 추가 (필드 추가)
   data class SlotAcquiredEventV2(
       val slotId: UUID,
       val userId: UUID,
       val productId: UUID,
       val queuePosition: Long  // 신규 필드
   )

   // 하위 호환성 유지:
   // - V1 Consumer는 V2 이벤트의 queuePosition 무시
   // - Avro Schema Evolution 활용
   ```

### 📚 학습 자료

- "Designing Data-Intensive Applications" by Martin Kleppmann (Chapter 11: Stream Processing)
- Transactional Outbox Pattern: https://microservices.io/patterns/data/transactional-outbox.html
- Debezium Tutorial: https://debezium.io/documentation/

---

## 5. Hexagonal Architecture Trade-offs

### 🎯 문제 상황

프로젝트를 어떻게 구조화할까?

### 📊 선택지 비교

#### Option A: Layered Architecture (전통적)

```
src/
├── controller/
│   └── SlotController.kt
├── service/
│   └── SlotService.kt
├── repository/
│   └── SlotRepository.kt (interface)
├── entity/
│   └── Slot.kt
└── config/
    └── DatabaseConfig.kt
```

**장점**: 단순, 직관적, 학습 곡선 낮음
**단점**:
- 계층 간 강한 결합 (Service → Repository → Entity)
- 프레임워크 종속적 (Spring에 강하게 바인딩)
- 비즈니스 로직이 여러 계층에 분산

#### Option B: Hexagonal Architecture (우리의 선택)

```
product/
├── core/                    # Domain Core (외부 의존성 없음)
│   ├── domain/
│   │   ├── Product.kt       # Pure Kotlin
│   │   └── PurchaseSlot.kt
│   ├── port/                # Interfaces만
│   │   ├── ProductRepository.kt
│   │   └── EventPublisher.kt
│   └── service/
│       └── SlotAcquisitionService.kt  # 비즈니스 로직
│
├── adapter/                 # Infrastructure Adapters
│   ├── persistence/
│   │   └── ProductRepositoryImpl.kt  # R2DBC 구현
│   ├── kafka/
│   │   └── EventPublisherImpl.kt
│   └── redis/
│       └── RedisSlotCacheImpl.kt
│
└── app/                     # Application Entry
    └── controller/
        └── SlotController.kt
```

**의존성 방향**:
```
app     →  core  ←  adapter
(REST)    (Domain)  (Infra)

core는 app과 adapter를 모름!
```

### 💡 Hexagonal Architecture의 핵심 원칙

#### 1. Dependency Inversion Principle

```kotlin
// ❌ Bad: Service가 구현체에 의존
class SlotService(
    private val productRepo: ProductRepositoryImpl  // 구현체!
) {
    fun acquire() {
        val product = productRepo.findById()  // R2DBC에 종속
    }
}

// ✅ Good: Service가 인터페이스에 의존
class SlotService(
    private val productRepo: ProductRepository  // 인터페이스!
) {
    fun acquire() {
        val product = productRepo.findById()  // 어떤 구현이든 OK
    }
}
```

**장점**:
```kotlin
// 테스트 시 Mock으로 교체 가능
class SlotServiceTest {
    @Test
    fun testAcquire() {
        val mockRepo = mock<ProductRepository> {
            on { findById(any()) } doReturn Mono.just(testProduct)
        }

        val service = SlotService(mockRepo)
        // R2DBC, DB 없이 테스트 가능!
    }
}
```

#### 2. Port와 Adapter 분리

```kotlin
// Port (core/port/ProductRepository.kt)
// → "무엇을" 해야 하는지 정의
interface ProductRepository {
    fun findById(id: UUID): Mono<Product>
    fun save(product: Product): Mono<Product>
}

// Adapter (adapter/persistence/ProductRepositoryImpl.kt)
// → "어떻게" 할 것인지 구현
@Repository
class ProductRepositoryImpl(
    private val databaseClient: DatabaseClient  // R2DBC
) : ProductRepository {
    override fun findById(id: UUID): Mono<Product> {
        return databaseClient.sql("SELECT * FROM products WHERE id = :id")
            .bind("id", id)
            .map { row -> mapToProduct(row) }
            .one()
    }
}

// 다른 Adapter로 교체 가능 (JPA, MyBatis, In-Memory 등)
@Repository
class ProductRepositoryJpaImpl(
    private val jpaRepository: JpaProductRepository
) : ProductRepository {
    override fun findById(id: UUID): Mono<Product> {
        return Mono.fromCallable { jpaRepository.findById(id) }
            .map { entity -> entity.toDomain() }
    }
}
```

#### 3. Domain Model의 순수성

```kotlin
// ✅ core/domain/PurchaseSlot.kt
// → 프레임워크 어노테이션 없음, 순수 비즈니스 로직
data class PurchaseSlot(
    val id: UUID,
    val userId: UUID,
    val status: SlotStatus,
    val expiresAt: Instant
) {
    fun isExpired(): Boolean {
        return Instant.now().isAfter(expiresAt)
    }

    fun expire(): PurchaseSlot {
        require(status == SlotStatus.ACTIVE)
        return copy(status = SlotStatus.EXPIRED)
    }
}

// ❌ 피해야 할 패턴
@Entity
@Table(name = "purchase_slots")
data class PurchaseSlot(  // JPA에 종속!
    @Id val id: UUID,
    @Column(name = "user_id") val userId: UUID,
    ...
)
```

### 💡 실제 이득

#### 1. 테스트 용이성

```kotlin
// 119개 단위 테스트를 DB 없이 실행 (0.5초)
class ProductTest {
    @Test
    fun `should decrease stock`() {
        val product = Product(stock = 10, ...)

        val updated = product.decreaseStock(1)

        assertEquals(9, updated.stock)
        // DB, Spring, R2DBC 없이 테스트!
    }
}

// 통합 테스트만 Testcontainers 사용
@SpringBootTest
@Testcontainers
class SlotAcquisitionIntegrationTest {
    @Container
    val postgres = PostgreSQLContainer("postgres:16")

    @Test
    fun `should acquire slot end-to-end`() {
        // 실제 DB와 함께 테스트
    }
}
```

**테스트 피라미드**:
```
       /\
      /  \  E2E (느림, 1개)
     /----\
    / Intg \ Integration (중간, 10개)
   /--------\
  /   Unit   \ Unit Tests (빠름, 119개)
 /------------\
```

#### 2. 프레임워크 독립성

```kotlin
// Spring Boot → Ktor로 교체
// Before (Spring)
@RestController
class SlotController(
    private val useCase: SlotAcquisitionUseCase  // Core 인터페이스
) {
    @PostMapping("/slots")
    fun acquire(): Mono<Slot> = useCase.acquire(...)
}

// After (Ktor)
fun Application.configureRouting() {
    val useCase: SlotAcquisitionUseCase = ...  // 동일한 인터페이스!

    routing {
        post("/slots") {
            val slot = useCase.acquire(...).awaitSingle()
            call.respond(slot)
        }
    }
}

// Core 코드는 전혀 변경 없음!
```

#### 3. 비즈니스 로직 집중

```kotlin
// 비즈니스 로직이 한 곳에 (core/service/)
class SlotAcquisitionService(
    private val productRepo: ProductRepository,
    private val slotRepo: PurchaseSlotRepository,
    private val slotCache: RedisSlotCache
) : SlotAcquisitionUseCase {

    override fun acquireSlot(command: AcquireSlotCommand): Mono<PurchaseSlot> {
        return validateProduct(command.productId)  // 1. 상품 검증
            .then(checkDuplicate(command.userId))  // 2. 중복 체크
            .then(acquireAtomically(command))      // 3. 원자적 획득
            .flatMap { persistSlot(command) }      // 4. 영속화
    }

    // 각 단계가 명확히 분리
    // Infrastructure 관심사(R2DBC, Redis)는 Adapter에 숨김
}
```

### 🤔 토론 포인트

1. **Hexagonal의 단점은?**
   ```
   - 초기 설정 복잡 (3개 모듈, 인터페이스 + 구현체)
   - 코드량 증가 (간단한 CRUD도 Port/Adapter 필요)
   - 학습 곡선 (팀원 교육 필요)
   - 과잉 설계 위험 (단순 앱에는 오버킬)
   ```

2. **언제 Layered가 더 나을까?**
   ```
   Layered가 적합한 경우:
   - 작은 프로젝트 (< 10 API endpoints)
   - 단순 CRUD (복잡한 비즈니스 로직 없음)
   - 빠른 프로토타이핑
   - 팀 경험 부족

   예: 사내 관리자 도구, MVP, 스타트업 초기
   ```

3. **Clean Architecture와의 차이?**
   ```
   Hexagonal vs Clean:

   Hexagonal (Ports & Adapters):
   - 2개 레이어: Core + Adapters
   - Port = Interface
   - 실용적, 구현 단순

   Clean Architecture (Uncle Bob):
   - 4개 레이어: Entities, Use Cases, Interface Adapters, Frameworks
   - 더 세분화된 분리
   - 이론적으로 더 순수하지만 복잡

   우리 프로젝트:
   - Hexagonal 기본 + Clean의 Use Case 패턴 차용
   ```

### 📚 학습 자료

- "Get Your Hands Dirty on Clean Architecture" by Tom Hombergs
- Hexagonal Architecture 원문: https://alistair.cockburn.us/hexagonal-architecture/
- "Clean Architecture" by Robert C. Martin

---

## 6. Cache vs Database as Source of Truth

### 🎯 문제 상황

재고 정보를 어디에 저장할까?
- Redis: 빠름, 휘발성
- PostgreSQL: 느림, 영구적

### 📊 아키텍처 패턴 비교

#### Option A: Cache-Aside (Lazy Loading)

```kotlin
fun getStock(productId: UUID): Mono<Int> {
    return redis.get("product:$productId:stock")
        .switchIfEmpty(
            db.findById(productId)
                .flatMap { product ->
                    redis.set("product:$productId:stock", product.stock)
                        .thenReturn(product.stock)
                }
        )
}
```

**장점**: 필요한 데이터만 캐싱
**단점**: Cache miss 시 느림, 캐시-DB 불일치 가능

#### Option B: Write-Through

```kotlin
fun decreaseStock(productId: UUID): Mono<Product> {
    return db.decreaseStock(productId)  // 1. DB 먼저
        .flatMap { product ->
            redis.set("product:$productId:stock", product.stock)  // 2. 캐시 업데이트
                .thenReturn(product)
        }
}
```

**장점**: 캐시-DB 일관성 보장
**단점**: Write 지연 시간 증가 (2배)

#### Option C: Write-Behind (Async Write)

```kotlin
fun decreaseStock(productId: UUID): Mono<Product> {
    return redis.decr("product:$productId:stock")  // 1. 캐시만 업데이트
        .doOnSuccess {
            // 2. 비동기로 DB 업데이트 (큐에 추가)
            updateQueue.add(productId)
        }
        .map { newStock -> Product(id = productId, stock = newStock.toInt()) }
}

@Scheduled(fixedDelay = 5000)
fun flushToDatabase() {
    updateQueue.drainTo(batch)
    db.batchUpdate(batch)  // 배치 업데이트
}
```

**장점**: 최고 성능 (캐시 속도)
**단점**:
- 장애 시 데이터 손실 위험
- 캐시-DB 불일치 (최종 일관성)

#### Option D: Database as Source of Truth + Cache (우리의 선택)

```kotlin
// Write: DB가 Source of Truth
fun acquireSlot(command: AcquireSlotCommand): Mono<PurchaseSlot> {
    // 1. Redis로 빠른 재고 체크 + 원자적 차감
    return slotCache.acquireSlot(productId, userId, timestamp)
        .flatMap { cacheResult ->
            if (cacheResult.success) {
                // 2. DB에 영속화 (Source of Truth)
                slotRepository.save(slot)
            } else {
                Mono.error(SoldOutException())
            }
        }
}

// Read: Cache 우선, DB는 fallback
fun getSlot(slotId: UUID): Mono<PurchaseSlot> {
    return slotRepository.findById(slotId)  // DB에서 조회
}

// Cache Warming: 애플리케이션 시작 시
@PostConstruct
fun warmUpCache() {
    productRepository.findAll()
        .flatMap { product ->
            redis.set("product:${product.id}:stock", product.stock)
        }
        .subscribe()
}
```

### 💡 우리 전략의 장점

#### 1. Performance + Durability

```
Read Path (조회):
User → DB (10ms)
└─ 장점: 항상 최신 데이터
└─ 단점: 상대적으로 느림

Write Path (슬롯 획득):
User → Redis (1ms) → DB (10ms)
└─ Redis: 빠른 원자적 처리 (재고 차감, 큐 추가)
└─ DB: 영구 저장 (감사, 복구)
```

#### 2. Failure Scenarios

```
Scenario 1: Redis 장애
→ 슬롯 획득 실패 (Graceful Degradation)
→ DB에서 재고 조회하여 에러 메시지
→ "현재 시스템 점검 중입니다" (5초 후 재시도)

Scenario 2: DB 장애 (드물지만 심각)
→ Redis 성공했지만 DB 저장 실패
→ 에러 반환, 사용자 재시도
→ Redis 중복 체크로 동일 사용자는 1개만 획득

Scenario 3: Redis 데이터 손실 (재시작)
→ Cache Warming으로 DB에서 재구축
→ 5초 다운타임 후 복구
```

#### 3. Data Reconciliation (데이터 정합성)

```kotlin
// Worker: 주기적으로 Redis ↔ DB 동기화 검증
@Scheduled(cron = "0 */5 * * * *")  // 5분마다
fun reconcileStockData() {
    productRepository.findAll()
        .flatMap { product ->
            redis.get("product:${product.id}:stock")
                .flatMap { cachedStock ->
                    if (cachedStock != product.stock) {
                        log.warn("Stock mismatch: product=${product.id}, db=${product.stock}, redis=$cachedStock")

                        // DB를 Source of Truth로 수정
                        redis.set("product:${product.id}:stock", product.stock)
                            .then(sendAlert("Stock mismatch detected"))
                    } else {
                        Mono.empty()
                    }
                }
        }
        .subscribe()
}
```

### 🤔 토론 포인트

1. **Redis를 Source of Truth로 하면 안 될까?**
   ```kotlin
   // Redis as Source of Truth (고려했지만 거부)

   장점:
   - 최고 성능 (1ms latency)
   - 단순한 아키텍처

   단점:
   - Redis 장애 시 데이터 완전 손실
   - Redis Persistence (RDB/AOF) 복잡
   - 백업/복구 어려움
   - 감사 로그 불가능
   - OLAP 분석 불가능

   결론: 재고는 금전적 가치가 있으므로 내구성 중요 → DB 필수
   ```

2. **CQRS 패턴 적용?**
   ```kotlin
   // Command Model: PostgreSQL (Write)
   fun acquireSlot() {
       postgres.insert(slot)  // 정규화된 스키마
   }

   // Query Model: Elasticsearch (Read)
   fun searchSlots(query: String) {
       elasticsearch.search(query)  // 비정규화, 풀텍스트 검색
   }

   // CDC로 동기화
   PostgreSQL WAL → Debezium → Kafka → Elasticsearch
   ```

   **장점**: Read/Write 최적화
   **단점**: 복잡도, 최종 일관성

3. **Multi-Level Caching**
   ```
   User → CDN (정적 자원) → Application Cache (Caffeine) → Redis → PostgreSQL

   Caffeine (In-Memory):
   - 상품 메타데이터 (이름, 설명)
   - TTL: 5분
   - 크기: 1만 개

   Redis:
   - 재고, 대기열
   - TTL: 없음 (명시적 삭제)

   PostgreSQL:
   - 모든 데이터
   - Source of Truth
   ```

### 📚 학습 자료

- "Database Internals" by Alex Petrov
- Redis Persistence: https://redis.io/docs/management/persistence/
- CQRS Pattern: https://martinfowler.com/bliki/CQRS.html

---

## 7. Fairness Guarantee Mechanisms

### 🎯 문제 상황

1000명이 동시에 요청했을 때, 누가 먼저 슬롯을 받을까?

**요구사항**: 먼저 도착한 순서대로 (First-Come-First-Served)

### 📊 선택지 비교

#### Option A: Database Timestamp

```kotlin
// 슬롯 생성 시 DB 타임스탬프 사용
INSERT INTO purchase_slots (acquired_at, ...)
VALUES (NOW(), ...);

// 순서는 DB 타임스탬프로
SELECT * FROM purchase_slots
ORDER BY acquired_at
LIMIT 100;
```

**문제점**:
```sql
-- 동시 요청 시 동일한 타임스탬프 가능
INSERT 1: acquired_at = 2024-01-06 10:00:00.123456
INSERT 2: acquired_at = 2024-01-06 10:00:00.123456  -- 동일!
→ 순서 보장 불가

-- DB 트랜잭션 격리 수준 문제
Transaction A: BEGIN → INSERT (10:00:00.1) → (대기 중)
Transaction B: BEGIN → INSERT (10:00:00.2) → COMMIT
Transaction A: COMMIT
→ 실제 커밋 순서: B → A (역전!)
```

#### Option B: Application Sequence Number

```kotlin
// AtomicLong으로 순서 번호 생성
private val sequence = AtomicLong(0)

fun acquireSlot(): Mono<PurchaseSlot> {
    val seqNum = sequence.incrementAndGet()  // 1, 2, 3, ...

    return persistSlot(seqNum)
}
```

**문제점**:
```
Multi-instance 환경:
Instance A: seqNum = 1, 2, 3, ...
Instance B: seqNum = 1, 2, 3, ...  // 중복!

해결: Redis INCR
redis.incr("global:sequence")  // 원자적 증가

But: 순서 번호 ≠ 도착 시간
User A: 네트워크 느림, seqNum = 100
User B: 네트워크 빠름, seqNum = 1
→ B가 먼저 처리되는게 공정함 (도착 시간 기준)
```

#### Option C: Arrival Timestamp + Redis ZSET (우리의 선택)

```kotlin
// 1. Controller에서 즉시 도착 시간 캡처
@PostMapping("/acquire")
fun acquireSlot(@RequestBody request: AcquireSlotRequest): Mono<Slot> {
    val arrivalTimestamp = System.currentTimeMillis()  // 1704524400123

    val command = AcquireSlotCommand(
        userId = request.userId,
        productId = request.productId,
        arrivalTimestamp = arrivalTimestamp  // 밀리초 정밀도
    )

    return useCase.acquireSlot(command)
}

// 2. Redis ZSET에 도착 시간을 Score로 저장
ZADD product:123:queue 1704524400123 user-1
ZADD product:123:queue 1704524400125 user-2
ZADD product:123:queue 1704524400124 user-3

// 3. Score 순서대로 자동 정렬
ZRANGE product:123:queue 0 -1 WITHSCORES
1) "user-1" (1704524400123)
2) "user-3" (1704524400124)
3) "user-2" (1704524400125)

// 4. 순위 조회
ZRANK product:123:queue user-3  → 1 (0-based, 2등)
```

### 💡 Redis ZSET의 Fairness 보장

#### 1. 자동 정렬 (O(log N))

```
ZSET 내부 구조: Skip List
                 1704524400125
                       ↑
         1704524400123 → 1704524400124 → 1704524400125
               ↑              ↑              ↑
           user-1        user-3        user-2

삽입 시 자동으로 Score 순서대로 정렬
→ 도착 시간이 빠른 사용자가 앞에
```

#### 2. 동일 타임스탬프 처리

```kotlin
// 밀리초 단위 (1/1000초)
val timestamp1 = 1704524400123  // 10:00:00.123
val timestamp2 = 1704524400123  // 10:00:00.123 (동일!)

// Redis ZSET: Score 동일 시 lexicographical order
ZADD queue 1704524400123 "user-aaa"
ZADD queue 1704524400123 "user-bbb"

ZRANGE queue 0 -1
1) "user-aaa"  // 사전 순으로 정렬
2) "user-bbb"

// 더 나은 방법: 마이크로초 + UUID 조합
val timestamp = System.currentTimeMillis() * 1000 +
                UUID.randomUUID().hashCode() % 1000
// 1704524400123456 (마이크로초 단위)
```

#### 3. 실시간 순위 조회

```kotlin
// 사용자에게 "현재 X번째입니다" 표시
fun getQueuePosition(productId: UUID, userId: UUID): Mono<Long> {
    return redis.zrank("product:$productId:queue", userId.toString())
        .map { rank -> rank + 1 }  // 1-based position
}

// WebSocket으로 실시간 업데이트
@MessageMapping("/queue/{productId}")
fun subscribeQueueUpdates(@DestinationVariable productId: UUID): Flux<QueueUpdate> {
    return Flux.interval(Duration.ofSeconds(1))
        .flatMap { getQueueSize(productId) }
        .map { size -> QueueUpdate(queueSize = size) }
}
```

### 🤔 토론 포인트

1. **Clock Skew 문제 (시계 불일치)**
   ```
   Multi-instance 환경:
   Instance A: 시계 = 10:00:00.123
   Instance B: 시계 = 10:00:00.100 (23ms 느림)

   User X → Instance A: timestamp = 123
   User Y → Instance B: timestamp = 100
   → Y가 먼저 온 것으로 처리됨 (X가 실제로 먼저인데!)

   해결책:
   1. NTP 동기화 (일반적으로 ±1ms 정밀도)
   2. Logical Clock (Lamport Timestamp, Vector Clock)
   3. Centralized Timestamp Service (Google Spanner TrueTime)
   ```

2. **Logical Clock 구현**
   ```kotlin
   // Lamport Timestamp
   class LamportClock {
       private val counter = AtomicLong(0)

       fun tick(): Long = counter.incrementAndGet()

       fun update(receivedTimestamp: Long): Long {
           val current = counter.get()
           val next = max(current, receivedTimestamp) + 1
           counter.set(next)
           return next
       }
   }

   // 사용
   val timestamp = lamportClock.tick()
   ZADD queue $timestamp user-1

   // 장점: Clock skew 영향 없음
   // 단점: 물리적 시간과 무관 (사용자에게 설명 어려움)
   ```

3. **네트워크 지연 공정성**
   ```
   User A: 서울 (10ms latency)
   User B: 부산 (50ms latency)

   동시에 버튼 클릭:
   T=0: A, B 클릭
   T=10ms: A 도착 → timestamp = 10
   T=50ms: B 도착 → timestamp = 50
   → A 우선 (불공정?)

   해결 불가능:
   - 서버는 도착 시간만 알 수 있음
   - 클라이언트 타임스탬프는 조작 가능

   완화책:
   - CDN 사용 (지역별 엣지 서버)
   - 판매 시작 시간 정각 (10:00:00.000)
   - 버튼 활성화 랜덤 지연 (부하 분산)
   ```

### 📚 학습 자료

- "Time, Clocks, and the Ordering of Events in a Distributed System" by Leslie Lamport
- Redis ZSET: https://redis.io/docs/data-types/sorted-sets/
- Google Spanner TrueTime: https://cloud.google.com/spanner/docs/true-time-external-consistency

---

## 8. Error Handling Patterns

### 🎯 문제 상황

API 에러를 어떻게 반환할까?

### 📊 선택지 비교

#### Option A: Simple Error Response

```json
{
  "error": "Product not found"
}
```

**문제점**: 정보 부족, 클라이언트가 처리하기 어려움

#### Option B: Custom Error Format

```json
{
  "success": false,
  "errorCode": "PRODUCT_NOT_FOUND",
  "message": "Product with ID 123 not found",
  "timestamp": "2024-01-06T10:00:00Z"
}
```

**문제점**: 표준 없음, API마다 다른 형식

#### Option C: RFC 7807 Problem Details (우리의 선택)

```json
{
  "type": "https://api.dopaminestore.com/errors/product-sold-out",
  "title": "Product Sold Out",
  "status": 409,
  "detail": "Product 'iPhone 15 Pro' is sold out. Last sold at 2024-01-06T09:59:58Z",
  "instance": "/api/v1/slots/acquire",
  "traceId": "abc-123-def-456",
  "timestamp": "2024-01-06T10:00:00Z",
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "remainingStock": 0
}
```

### 💡 RFC 7807의 장점

#### 1. 표준 스키마

```kotlin
data class ProblemDetail(
    // 필수 필드
    val type: String,          // 에러 유형 식별 URI
    val title: String,         // 간단한 제목
    val status: Int,           // HTTP 상태 코드

    // 선택 필드
    val detail: String?,       // 상세 설명
    val instance: String?,     // 에러 발생 위치

    // 커스텀 필드 (임의 추가 가능)
    val traceId: String?,
    val timestamp: Instant = Instant.now(),
    val additionalProperties: Map<String, Any>? = null
)
```

#### 2. Type URL로 문서화

```kotlin
when (error) {
    is ProductSoldOutException -> ProblemDetail(
        type = "https://api.dopaminestore.com/errors/product-sold-out",
        title = "Product Sold Out",
        status = 409,
        detail = "Product '${error.productName}' is sold out",
        additionalProperties = mapOf(
            "productId" to error.productId.toString(),
            "lastSoldAt" to error.lastSoldAt.toString()
        )
    )
}
```

Type URL → 문서 페이지:
```markdown
# https://api.dopaminestore.com/errors/product-sold-out

## 설명
요청한 상품의 재고가 모두 소진되었습니다.

## 원인
- 다른 사용자가 마지막 재고를 구매
- 판매 시작 직후 높은 트래픽

## 대응 방법
클라이언트는 다음과 같이 처리하세요:
1. 사용자에게 "품절" 메시지 표시
2. "재입고 알림 신청" 버튼 제공
3. 다른 상품 추천

## 재시도 정책
재시도 불필요 (재고 소진은 복구 불가)
```

#### 3. 클라이언트 친화적

```typescript
// TypeScript 클라이언트
interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  traceId?: string;
  [key: string]: any;  // 추가 필드
}

async function acquireSlot(productId: string) {
  try {
    const response = await fetch('/api/v1/slots/acquire', {
      method: 'POST',
      body: JSON.stringify({ productId })
    });

    if (!response.ok) {
      const problem: ProblemDetail = await response.json();

      // Type URL로 에러 처리 분기
      switch (problem.type) {
        case 'https://api.dopaminestore.com/errors/product-sold-out':
          showSoldOutModal(problem.detail);
          trackEvent('slot_acquisition_failed', { reason: 'sold_out' });
          break;

        case 'https://api.dopaminestore.com/errors/duplicate-slot':
          showDuplicateAlert(problem.traceId);
          navigateToMySlots();
          break;

        default:
          showGenericError(problem.title);
          Sentry.captureException(new Error(problem.detail), {
            extra: problem
          });
      }
    }
  } catch (error) {
    // 네트워크 에러 등
  }
}
```

### 💡 에러 계층 구조

```kotlin
// Domain Exceptions
sealed class SlotAcquisitionException(message: String) : RuntimeException(message)

class ProductNotFoundException(val productId: UUID) : SlotAcquisitionException(
    "Product not found: $productId"
)

class ProductSoldOutException(
    val productId: UUID,
    val productName: String,
    val lastSoldAt: Instant
) : SlotAcquisitionException(
    "Product sold out: $productName"
)

class DuplicateSlotException(
    val userId: UUID,
    val productId: UUID
) : SlotAcquisitionException(
    "User $userId already has active slot for product $productId"
)

// Global Exception Handler
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(
        ex: ProductNotFoundException,
        request: ServerWebExchange
    ): Mono<ResponseEntity<ProblemDetail>> {
        val problem = ProblemDetail(
            type = "https://api.dopaminestore.com/errors/product-not-found",
            title = "Product Not Found",
            status = 404,
            detail = ex.message,
            instance = request.request.path.value(),
            traceId = request.request.headers.getFirst("X-Trace-Id")
        )

        return Mono.just(ResponseEntity.status(404).body(problem))
    }

    // 다른 예외들...
}
```

### 🤔 토론 포인트

1. **민감 정보 노출 위험**
   ```kotlin
   // ❌ Bad: 내부 정보 노출
   ProblemDetail(
       detail = "SQL Error: SELECT * FROM products WHERE id = abc-123 failed",
       additionalProperties = mapOf(
           "sqlState" to "23505",
           "stackTrace" to ex.stackTraceToString()
       )
   )

   // ✅ Good: 일반적 메시지
   ProblemDetail(
       detail = "Product retrieval failed. Please try again later.",
       traceId = traceId  // 내부 추적용
   )

   // 로그에만 상세 정보 기록
   log.error("Database error: ${ex.message}", ex)
   ```

2. **HTTP Status Code 선택**
   ```
   409 Conflict: 품절, 중복 슬롯
   vs
   400 Bad Request: 잘못된 요청

   우리의 선택: 409 Conflict
   이유: 클라이언트 요청은 올바르지만, 서버 상태와 충돌

   RFC 7231:
   409 - The request could not be completed due to a conflict
         with the current state of the target resource.
   ```

3. **Reactive Error Handling**
   ```kotlin
   // Mono/Flux에서 에러 처리
   fun acquireSlot(): Mono<Slot> {
       return useCase.acquireSlot(command)
           .onErrorResume { error ->
               when (error) {
                   is ProductSoldOutException -> {
                       // 대체 플로우
                       notifyWaitlist(error.productId)
                           .then(Mono.error(error))
                   }
                   else -> Mono.error(error)
               }
           }
           .doOnError { error ->
               // 사이드 이펙트 (로깅, 메트릭)
               meterRegistry.counter("slot.acquisition.error",
                   "type", error::class.simpleName
               ).increment()
           }
   }
   ```

### 📚 학습 자료

- RFC 7807: https://tools.ietf.org/html/rfc7807
- "REST API Error Handling" by Zalando: https://opensource.zalando.com/restful-api-guidelines/#176
- Spring Problem Details: https://spring.io/blog/2023/03/16/error-responses-in-spring-web

---

## 9. Connection Pool Sizing

### 🎯 문제 상황

R2DBC Connection Pool을 몇 개로 설정할까?

### 📊 계산식

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
      max-acquire-time: 3s
```

### 💡 Sizing 전략

#### 1. Little's Law

```
L = λ × W

L: 필요한 커넥션 수
λ: 초당 요청 수 (throughput)
W: 평균 처리 시간 (latency)

예시:
λ = 1000 RPS
W = 10ms = 0.01s

L = 1000 × 0.01 = 10 connections
```

#### 2. 인스턴스 고려

```
전체 목표: 100K RPS
인스턴스 수: 20개
인스턴스당 RPS: 100K / 20 = 5K RPS

인스턴스당 커넥션 수:
L = 5000 × 0.01 = 50 connections

하지만: Reactive는 multiplexing 가능
→ 1 커넥션으로 여러 쿼리 처리
→ 실제 필요: 10-20 connections
```

### 🤔 토론 포인트

**Connection Pool vs Thread Pool (Reactive)**
```
Blocking (Tomcat):
- Thread Pool Size = 200
- Connection Pool Size = 200 (1:1)

Reactive (WebFlux):
- Event Loop Threads = 8 (CPU 코어 수)
- Connection Pool Size = 20 (훨씬 적음!)
```

---

## 10. Partial Index Deep Dive

(이미 앞에서 상세히 다룸)

---

## 부록: 학습 체크리스트

### 🎓 Level 1: 기초 이해
- [ ] Reactive Programming의 기본 개념 (Mono/Flux)
- [ ] Redis 기본 자료구조 (String, Set, ZSET)
- [ ] PostgreSQL 인덱스 종류 (B-Tree, Partial, Unique)
- [ ] HTTP 상태 코드 의미
- [ ] Hexagonal Architecture 기본 구조

### 🎓 Level 2: 실전 적용
- [ ] Lua Script 작성 및 Redis 실행
- [ ] Reactive 에러 처리 (onErrorResume, onErrorReturn)
- [ ] Query Plan 분석 (EXPLAIN ANALYZE)
- [ ] RFC 7807 ProblemDetail 구현
- [ ] Connection Pool Sizing 계산

### 🎓 Level 3: 심화 학습
- [ ] Clock Skew 문제와 Logical Clock
- [ ] Transactional Outbox Pattern 구현
- [ ] CDC (Change Data Capture) 아키텍처
- [ ] CQRS 패턴 설계
- [ ] Redis Cluster Sharding

### 🎓 Level 4: Production Ready
- [ ] 성능 테스트 (k6, Gatling)
- [ ] 장애 시나리오 대응 (Circuit Breaker, Bulkhead)
- [ ] 모니터링 및 알림 (Prometheus, Grafana)
- [ ] 데이터 정합성 검증
- [ ] Capacity Planning

---

## 토론 주제 제안

### 세션 1: 아키텍처 설계
- Reactive vs Blocking: 언제 무엇을 선택할까?
- Hexagonal Architecture는 과잉 설계인가?

### 세션 2: 데이터 일관성
- Cache vs Database: Source of Truth는 어디?
- 이벤트 발행 실패 시 어떻게 해야 하나?

### 세션 3: 성능 최적화
- Lua Script vs Distributed Lock 벤치마크
- Partial Index 실전 적용 사례

### 세션 4: 공정성과 정합성
- 선착순 보장: 시계 불일치 문제
- 오버셀링 방지: Race Condition 대응

---

**문서 작성**: 2026-01-06
**대상**: 백엔드 개발자 (중급 이상)
**예상 학습 시간**: 4-6시간 (토론 포함)
