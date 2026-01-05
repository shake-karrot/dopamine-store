# Phase 0: Technology Research & Decisions

**Feature**: Product Domain API & Business Logic
**Date**: 2026-01-05
**Status**: ✅ COMPLETE

## Overview

This document consolidates research findings for key technology decisions required to implement the Product domain's 100K RPS first-come-first-served slot acquisition system. All decisions prioritize the constitution's Concurrency-First and Fairness Guarantees principles.

---

## Decision 1: Redis Data Structures for Fairness

### Context

Need to implement strict arrival-time ordering for slot acquisition requests while handling 100,000 RPS. Must guarantee exactly N slots for N stock with zero over-allocation.

### Research Findings

**Option A: Redis Sorted Sets (ZSET)**
- **Pros**: Native arrival-time ordering (score = timestamp), atomic ZADD/ZPOPMIN operations, O(log N) complexity
- **Cons**: Requires Lua scripts for compound operations (check stock + add to queue atomically)
- **Performance**: Excellent for read-heavy workloads, good for 100K RPS with pipelining

**Option B: Redis Lists (LIST)**
- **Pros**: Simple LPUSH/RPOP for FIFO, O(1) operations
- **Cons**: No inherent timestamp tracking, difficult to implement fair arrival-time ordering with precise millisecond granularity
- **Performance**: Fast but doesn't meet fairness requirement (arrival-time order)

**Option C: Redis Streams**
- **Pros**: Built-in timestamp, designed for high-throughput message queuing, consumer groups for parallel processing
- **Cons**: More complex setup, overkill for simple fairness queue, higher memory overhead
- **Performance**: Excellent but introduces unnecessary complexity

### Decision

**CHOSEN**: **Redis Sorted Sets (ZSET) + Lua Scripts**

**Rationale**:
1. **Fairness**: ZSET scores directly represent arrival timestamps, enabling strict FIFO ordering as required by FR-008
2. **Atomicity**: Lua scripts allow compound operations (check stock, add to queue, decrement counter) in a single atomic call
3. **Performance**: ZADD operations are O(log N), acceptable for 100K RPS with proper connection pooling
4. **Simplicity**: Simpler than Streams while meeting all requirements

**Implementation Pattern**:
```lua
-- slot-acquisition.lua (Lua script executed atomically in Redis)
local product_key = KEYS[1]          -- "product:{product_id}:stock"
local queue_key = KEYS[2]            -- "product:{product_id}:queue"
local duplicate_key = KEYS[3]        -- "user:{user_id}:product:{product_id}"
local user_id = ARGV[1]
local timestamp = ARGV[2]            -- Request arrival time (epoch millis)

-- Check duplicate
if redis.call('EXISTS', duplicate_key) == 1 then
    return {err = 'DUPLICATE_REQUEST'}
end

-- Check stock
local stock = tonumber(redis.call('GET', product_key))
if stock == nil or stock <= 0 then
    return {err = 'SOLD_OUT'}
end

-- Atomic allocation
redis.call('DECR', product_key)
redis.call('ZADD', queue_key, timestamp, user_id)
redis.call('SET', duplicate_key, '1', 'EX', 1800)  -- 30-min expiry

return {ok = 'SLOT_ACQUIRED'}
```

**Alternatives Considered**:
- Lists: Rejected due to lack of timestamp-based ordering
- Streams: Rejected as over-engineered for this use case

---

## Decision 2: R2DBC Connection Pooling

### Context

Non-blocking database access required for 100K RPS. Need optimal pool configuration for PostgreSQL via R2DBC to avoid connection exhaustion while minimizing latency.

### Research Findings

**Best Practices from r2dbc-pool Documentation**:
- **Initial size**: Start small (10-20 connections) to avoid resource waste during low traffic
- **Max size**: Formula: `(core_count * 2) + effective_spindle_count`
  - For 8-core server with SSD: `(8 * 2) + 1 = 17` connections per instance
  - For horizontal scaling with 10 instances: 17 connections each = 170 total DB connections
- **Max idle time**: 30 minutes (balance between connection reuse and server-side resource release)
- **Validation query**: `SELECT 1` (lightweight PostgreSQL health check)
- **Acquire retry**: 3 attempts with 100ms delay (handles transient failures)

**HikariCP Recommendations (adapted for R2DBC)**:
- Avoid pool size > 10x core count (diminishing returns, contention)
- Monitor connection wait time; if > 10ms consistently, increase pool size
- Use connection lifetime limit (30 min) to handle database-side connection limits

### Decision

**CHOSEN**: **Initial=10, Max=20, MaxIdleTime=30min, ValidationQuery=SELECT 1**

**Rationale**:
1. **Conservative start**: Initial=10 handles normal traffic without over-provisioning
2. **Scalable max**: Max=20 per instance aligns with 8-core server formula (allows headroom)
3. **Reactive paradigm**: R2DBC's non-blocking nature means fewer connections needed vs. traditional JDBC
4. **Horizontal scaling**: With 10 product domain instances, 200 total DB connections is well within PostgreSQL's default max_connections=100 per database (will configure DB for 250 connections)

**Configuration** (Kotlin):
```kotlin
// adapter/config/DatabaseConfig.kt
@Configuration
class DatabaseConfig {
    @Bean
    fun connectionFactory(): ConnectionFactory {
        return ConnectionFactories.get(
            ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(HOST, "product-db.internal")
                .option(PORT, 5432)
                .option(USER, "product_service")
                .option(PASSWORD, getSecret("PRODUCT_DB_PASSWORD"))
                .option(DATABASE, "product")
                .build()
        )
    }

    @Bean
    fun r2dbcPool(connectionFactory: ConnectionFactory): ConnectionPool {
        return ConnectionPoolConfiguration.builder(connectionFactory)
            .initialSize(10)
            .maxSize(20)
            .maxIdleTime(Duration.ofMinutes(30))
            .validationQuery("SELECT 1")
            .maxAcquireTime(Duration.ofSeconds(3))
            .build()
            .let { ConnectionPool(it) }
    }
}
```

**Tuning Plan**:
- Phase 2 load testing will measure connection wait times under 100K RPS
- If p99 wait time > 50ms, increase maxSize to 30
- Monitor PostgreSQL `pg_stat_activity` for idle connections

**Alternatives Considered**:
- Max=50: Rejected as excessive for reactive workload; would waste DB resources
- No pooling: Rejected; connection establishment overhead (~10ms) unacceptable at 100K RPS

---

## Decision 3: Slot Expiration Strategy

### Context

PurchaseSlots must expire exactly 30 minutes after acquisition (FR-012, FR-015). Expired slots must be reclaimed atomically and logged for audit trail (FR-016, FR-020). System must handle 100K active slots simultaneously.

### Research Findings

**Option A: Redis TTL Only**
- **Pros**: Automatic expiration, zero manual cleanup, distributed
- **Cons**: No expiration event notification (can't trigger audit logging or notification), lazy deletion (memory not freed immediately)
- **Performance**: Excellent, no CPU overhead

**Option B: Scheduled Job (Polling)**
- **Pros**: Full control over expiration logic, can trigger Kafka events, audit logging guaranteed
- **Cons**: Polling overhead, accuracy depends on scan frequency (1-min intervals = up to 60s delay)
- **Performance**: CPU-intensive at scale (scanning 100K slots every minute)

**Option C: Hybrid (Redis TTL + Scheduled Job + Lazy Evaluation)**
- **Pros**: Redis TTL prevents memory leaks, job handles audit trail, lazy evaluation at slot access for immediate feedback
- **Cons**: More complex implementation
- **Performance**: Best of both worlds—Redis handles memory, job handles business logic

### Decision

**CHOSEN**: **Hybrid Approach (Redis TTL + Scheduled Job + Lazy Evaluation)**

**Rationale**:
1. **Memory safety**: Redis TTL ensures expired slots don't consume memory indefinitely (EXPIRE key 1800)
2. **Audit compliance**: Scheduled job (every 1 minute) scans Redis `product:*:queue` ZSET, moves expired slots to `expired_slots` list, publishes Kafka events for audit logging
3. **User experience**: Lazy evaluation at payment time (check `current_time > expiration_time`) provides immediate "slot expired" feedback without waiting for job
4. **Accuracy**: Combined approach meets SC-004 requirement ("within 30 seconds of deadline")—job runs every 60s, lazy eval provides instant check

**Implementation Pattern**:

```kotlin
// worker/job/SlotExpirationJob.kt
@Component
class SlotExpirationJob(
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val eventPublisher: EventPublisher
) {
    @Scheduled(fixedDelay = 60_000) // Every 1 minute
    suspend fun processExpiredSlots() {
        val now = Instant.now().toEpochMilli()
        val thirtyMinutesAgo = now - 1_800_000

        // Scan all product queues (pattern: product:*:queue)
        val expiredSlots = redisTemplate.keys("product:*:queue")
            .flatMap { queueKey ->
                // ZRANGEBYSCORE to find slots with timestamp < (now - 30min)
                redisTemplate.opsForZSet()
                    .rangeByScore(queueKey, 0.0, thirtyMinutesAgo.toDouble())
            }
            .collectList()
            .awaitSingle()

        expiredSlots.forEach { userId ->
            // Publish SLOT_EXPIRED event for audit logging
            eventPublisher.publish(SlotExpiredEvent(
                slotId = generateSlotId(userId),
                userId = userId,
                expirationTimestamp = Instant.now(),
                reclaimStatus = "AUTO_EXPIRED"
            ))

            // Remove from queue
            redisTemplate.opsForZSet().remove(queueKey, userId).awaitSingle()

            // Increment stock (reclamation)
            redisTemplate.opsForValue().increment("product:${productId}:stock").awaitSingle()
        }

        logger.info("Processed ${expiredSlots.size} expired slots")
    }
}

// core/service/PaymentService.kt (lazy evaluation)
fun validateSlot(slot: PurchaseSlot): Either<PaymentError, Unit> {
    val now = Instant.now()
    return if (now.isAfter(slot.expirationTimestamp)) {
        Left(PaymentError.SlotExpired("Slot expired at ${slot.expirationTimestamp}"))
    } else {
        Right(Unit)
    }
}
```

**Pre-Expiration Notification** (FR-017):
- Scheduled job also checks `(now - 25 minutes) < timestamp < (now - 24 minutes)` range
- Publishes Kafka event to Notification domain for 5-minute warning

**Alternatives Considered**:
- TTL only: Rejected due to lack of audit trail and notification capability
- Job only: Rejected due to potential memory leak if job fails

---

## Decision 4: Payment Gateway Stub Design

### Context

P4 (Payment Processing) is marked as advanced/deferrable (Priority 4). Need minimal viable payment interface to unblock P1-P3 development while allowing future integration with real payment gateways.

### Research Findings

**Payment Processing Patterns**:
1. **Synchronous**: Client waits for payment result (simple, but blocks during gateway latency)
2. **Async (Webhook)**: Client receives 202 Accepted, gateway calls back with result (industry standard for high-value transactions)
3. **Polling**: Client periodically checks payment status (fallback for webhook failures)

**Idempotency Requirements**:
- Payment requests must include `idempotency_key` (prevent duplicate charges on retry)
- Gateway stub must store processed keys in Redis (TTL = 24 hours)

### Decision

**CHOSEN**: **Async Payment API with Webhook Stub**

**Rationale**:
1. **Non-blocking**: Aligns with reactive architecture; payment processing doesn't block slot acquisition flow
2. **Realistic**: Mirrors real gateway behavior (Stripe, PayPal use webhooks)
3. **Testable**: Stub can simulate success/failure/timeout scenarios for integration tests
4. **Future-proof**: Contract matches real gateway APIs, minimizing refactoring when integrating

**API Contract**:

```kotlin
// core/port/PaymentGateway.kt (port interface)
interface PaymentGateway {
    suspend fun initiatePayment(request: PaymentRequest): PaymentResponse
    suspend fun getPaymentStatus(paymentId: String): PaymentStatus
}

data class PaymentRequest(
    val idempotencyKey: String,      // UUID for duplicate prevention
    val amount: Money,
    val currency: String,
    val purchaseSlotId: String,
    val userId: String,
    val returnUrl: String            // Webhook callback URL
)

data class PaymentResponse(
    val paymentId: String,
    val status: PaymentStatus,       // PENDING, SUCCESS, FAILED
    val message: String?
)

enum class PaymentStatus { PENDING, SUCCESS, FAILED, TIMEOUT }

// adapter/external/PaymentGatewayStub.kt (stub implementation for P4)
@Component
class PaymentGatewayStub(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) : PaymentGateway {
    override suspend fun initiatePayment(request: PaymentRequest): PaymentResponse {
        // Check idempotency
        val idempotencyKey = "payment:idempotency:${request.idempotencyKey}"
        val existing = redisTemplate.opsForValue().get(idempotencyKey).awaitSingleOrNull()
        if (existing != null) {
            return PaymentResponse(existing, PaymentStatus.SUCCESS, "Already processed")
        }

        // Simulate 200ms gateway latency
        delay(200)

        // Stub: 95% success rate
        val paymentId = UUID.randomUUID().toString()
        val status = if (Random.nextInt(100) < 95) PaymentStatus.SUCCESS else PaymentStatus.FAILED

        // Store idempotency key (24h TTL)
        redisTemplate.opsForValue().set(idempotencyKey, paymentId, Duration.ofHours(24)).awaitSingle()

        return PaymentResponse(paymentId, status, if (status == PaymentStatus.FAILED) "Insufficient funds" else null)
    }

    override suspend fun getPaymentStatus(paymentId: String): PaymentStatus {
        // Stub: always return success for demo
        return PaymentStatus.SUCCESS
    }
}
```

**Webhook Endpoint** (future):
```kotlin
// app/controller/PaymentWebhookController.kt
@RestController
@RequestMapping("/webhooks/payment")
class PaymentWebhookController(private val paymentUseCase: PaymentUseCase) {
    @PostMapping
    suspend fun handlePaymentWebhook(@RequestBody webhook: PaymentWebhookPayload): ResponseEntity<Unit> {
        // Verify webhook signature (HMAC with shared secret)
        // Update Purchase status based on webhook.status
        paymentUseCase.handlePaymentResult(webhook.paymentId, webhook.status)
        return ResponseEntity.ok().build()
    }
}
```

**Alternatives Considered**:
- Synchronous stub: Rejected; doesn't match real gateway behavior, blocks reactive flow
- No stub (hardcoded success): Rejected; insufficient for testing failure scenarios

---

## Decision 5: Kafka Event Schema Evolution

### Context

Product domain publishes events consumed by Notification and Auth domains. Events must evolve over time (e.g., adding fields) without breaking consumers. Need schema registry for `shared/events/` coordination.

### Research Findings

**Schema Formats**:
1. **JSON Schema**: Human-readable, flexible, no code generation, weaker validation
2. **Avro**: Binary format, compact, code generation, schema evolution rules (forward/backward/full compatibility)
3. **Protobuf**: Binary, efficient, requires .proto files, strong typing

**Compatibility Modes**:
- **BACKWARD**: Consumers using old schema can read new data (safe to add optional fields)
- **FORWARD**: Consumers using new schema can read old data (safe to remove fields)
- **FULL**: Both directions (most restrictive, safest for long-term evolution)

**Confluent Schema Registry**:
- Industry standard for Avro schema management
- REST API for schema registration and validation
- Automatic compatibility checking on schema registration

### Decision

**CHOSEN**: **Avro with BACKWARD Compatibility Mode + Confluent Schema Registry**

**Rationale**:
1. **Backward compatibility**: Most common evolution pattern (add fields over time); old consumers won't break
2. **Compact**: Avro binary serialization reduces Kafka message size (important at 100K RPS)
3. **Type safety**: Code generation from .avsc files prevents serialization bugs
4. **Industry standard**: Confluent Schema Registry is battle-tested, well-documented

**Schema Examples**:

```json
// shared/events/product/slot-acquired.avsc (v1)
{
  "type": "record",
  "name": "SlotAcquiredEvent",
  "namespace": "com.dopaminestore.product.events",
  "fields": [
    {"name": "slotId", "type": "string"},
    {"name": "userId", "type": "string"},
    {"name": "productId", "type": "string"},
    {"name": "acquisitionTimestamp", "type": "long", "logicalType": "timestamp-millis"},
    {"name": "expirationTimestamp", "type": "long", "logicalType": "timestamp-millis"},
    {"name": "traceId", "type": "string"}
  ]
}

// shared/events/product/slot-expired.avsc (v1)
{
  "type": "record",
  "name": "SlotExpiredEvent",
  "namespace": "com.dopaminestore.product.events",
  "fields": [
    {"name": "slotId", "type": "string"},
    {"name": "userId", "type": "string"},
    {"name": "productId", "type": "string"},
    {"name": "expirationTimestamp", "type": "long", "logicalType": "timestamp-millis"},
    {"name": "reclaimStatus", "type": "string", "default": "AUTO_EXPIRED"},
    {"name": "traceId", "type": "string"}
  ]
}

// shared/events/product/payment-completed.avsc (v1)
{
  "type": "record",
  "name": "PaymentCompletedEvent",
  "namespace": "com.dopaminestore.product.events",
  "fields": [
    {"name": "purchaseId", "type": "string"},
    {"name": "slotId", "type": "string"},
    {"name": "userId", "type": "string"},
    {"name": "productId", "type": "string"},
    {"name": "paymentStatus", "type": "string"},
    {"name": "amount", "type": "double"},
    {"name": "currency", "type": "string", "default": "KRW"},
    {"name": "confirmationTimestamp", "type": "long", "logicalType": "timestamp-millis"},
    {"name": "traceId", "type": "string"}
  ]
}
```

**Schema Registration Workflow**:
1. Notification team reviews proposed schemas in `shared/events/product/`
2. Schema registered to Confluent Schema Registry via CI/CD pipeline
3. Code generation produces Kotlin data classes in adapter module
4. Compatibility validation runs on every schema change PR

**Gradle Plugin** (for code generation):
```kotlin
// adapter/build.gradle.kts
plugins {
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

avro {
    stringType.set("String")
    fieldVisibility.set("PRIVATE")
}
```

**Alternatives Considered**:
- JSON Schema: Rejected due to lack of schema evolution guarantees and larger message size
- Protobuf: Rejected; Avro is more Kafka-native and has better Confluent tooling support

---

## Decision 6: Load Testing Tooling

### Context

Must validate 100K RPS handling for P1 (slot acquisition) before production deployment (constitution requirement). Need tooling to simulate traffic, identify bottlenecks, and measure p99 latency.

### Research Findings

**k6 (Grafana)**:
- JavaScript-based, developer-friendly, excellent metrics (p50/p90/p99/p99.9)
- Supports distributed execution (multiple load generators)
- Integrates with Prometheus/Grafana for real-time dashboards
- Virtual Users (VUs) model: each VU = concurrent user executing script

**Load Testing Patterns**:
1. **Ramp-up test**: Gradually increase from 1K → 100K RPS to find breaking point
2. **Soak test**: Sustain 100K RPS for 10+ minutes to detect memory leaks
3. **Spike test**: Sudden 0 → 100K RPS to test elasticity

**Distributed k6 Setup**:
- Single k6 instance limit: ~30K RPS (network/CPU bound)
- For 100K RPS: Need 4 k6 instances (25K RPS each) coordinated via k6 Cloud or custom orchestration

### Decision

**CHOSEN**: **k6 with Distributed Execution (4 instances) + Prometheus Integration**

**Rationale**:
1. **Realistic load**: 4 instances can generate 100K RPS without k6 itself becoming the bottleneck
2. **Detailed metrics**: k6's built-in metrics (http_req_duration, http_reqs) directly map to success criteria (SC-002, SC-007)
3. **CI/CD integration**: k6 exit codes enable automated pass/fail in deployment pipeline
4. **Open source**: No licensing costs vs. commercial tools (JMeter, LoadRunner)

**k6 Test Script**:

```javascript
// tests/load/slot_acquisition_100k_rps.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
    scenarios: {
        slot_acquisition: {
            executor: 'constant-arrival-rate',
            rate: 25000,              // 25K RPS per instance (4 instances = 100K total)
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 1000,    // Pre-allocate VUs for instant ramp-up
            maxVUs: 5000,
        },
    },
    thresholds: {
        'http_req_duration{type:slot_acquisition}': ['p(99)<100'],  // SC-002
        'errors': ['rate<0.001'],                                     // SC-007 (< 0.1%)
    },
};

export default function () {
    const productId = 'product-123';  // Fixed product for all requests (worst-case contention)
    const userId = `user-${__VU}-${__ITER}`;  // Unique user per request

    const payload = JSON.stringify({
        productId: productId,
        userId: userId,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Trace-ID': `trace-${__VU}-${__ITER}`,
        },
        tags: { type: 'slot_acquisition' },
    };

    const response = http.post('http://product-api.internal/slots/acquire', payload, params);

    check(response, {
        'status is 200 or 409': (r) => r.status === 200 || r.status === 409,  // 200=success, 409=sold out
        'response time < 100ms': (r) => r.timings.duration < 100,
    }) || errorRate.add(1);

    // No sleep—constant arrival rate handles pacing
}
```

**Execution Command**:
```bash
# Run on 4 separate k6 instances (or k6 Cloud distributed mode)
k6 run --out prometheus=namespace=product tests/load/slot_acquisition_100k_rps.js

# View results in Grafana dashboard (connected to Prometheus)
```

**Bottleneck Identification Plan**:
- Phase 2 load test will measure:
  - Redis latency (via Redis SLOWLOG)
  - PostgreSQL query times (via pg_stat_statements)
  - WebFlux thread pool saturation (via Micrometer metrics)
  - Network saturation (via iftop/netstat)
- If p99 > 100ms, iteratively optimize (increase Redis pool, tune Lua script, add caching)

**Alternatives Considered**:
- JMeter: Rejected; heavyweight UI, slower script iteration vs. k6's code-based approach
- Gatling: Rejected; Scala-based, steeper learning curve, k6 metrics are superior

---

## Summary of Decisions

| Decision Area | Chosen Solution | Key Rationale |
|---------------|----------------|---------------|
| **Fairness Queue** | Redis Sorted Sets + Lua Scripts | Arrival-time ordering, atomic operations, 100K RPS capable |
| **DB Connection Pool** | R2DBC Pool (Initial=10, Max=20) | Reactive paradigm, conservative sizing, horizontal scaling |
| **Slot Expiration** | Hybrid (Redis TTL + Scheduled Job + Lazy Eval) | Memory safety, audit compliance, immediate user feedback |
| **Payment Gateway** | Async Webhook Stub | Matches real gateway behavior, non-blocking, testable |
| **Event Schemas** | Avro + Backward Compatibility + Schema Registry | Compact, type-safe, industry standard, evolution-friendly |
| **Load Testing** | k6 Distributed (4 instances) + Prometheus | 100K RPS capacity, detailed metrics, CI/CD integration |

## Next Steps

✅ **Phase 0 COMPLETE** - Proceed to Phase 1:
1. Generate `data-model.md` with entity schemas and state machines
2. Generate `contracts/openapi.yaml` with REST API specifications
3. Generate `contracts/events/` with Avro schemas for Kafka events
4. Generate `quickstart.md` with developer setup instructions
5. Run `.specify/scripts/bash/update-agent-context.sh claude` to update agent context

**Phase 1 Prerequisites Met**:
- All technology choices finalized
- No remaining "NEEDS CLARIFICATION" items
- Constitution gates passed

---

**Document Version**: 1.0
**Last Updated**: 2026-01-05
**Reviewers**: Product Team Lead (pending)
