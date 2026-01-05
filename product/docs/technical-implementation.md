# Product Domain: Technical Implementation Guide

**Version**: 1.0
**Last Updated**: 2026-01-06
**Status**: Phase 3 In Progress (61% complete)

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Design](#architecture-design)
3. [Phase 1: Infrastructure Layer](#phase-1-infrastructure-layer)
4. [Phase 2: Domain Core Layer](#phase-2-domain-core-layer)
5. [Phase 3: Slot Acquisition Feature](#phase-3-slot-acquisition-feature)
6. [Key Technical Decisions](#key-technical-decisions)
7. [Performance Optimization](#performance-optimization)
8. [Testing Strategy](#testing-strategy)
9. [Future Improvements](#future-improvements)

---

## Overview

### Goals

Product 도메인은 선착순 구매권 획득 시스템을 구현하며, 다음 목표를 달성합니다:

- **High Performance**: 100K RPS 처리 능력
- **Fairness Guarantee**: 도착 시간 기반 선착순 보장
- **Zero Overselling**: N개 재고에 정확히 N개 구매권만 발급
- **Low Latency**: p99 latency < 100ms
- **Event-Driven**: 도메인 간 느슨한 결합

### Tech Stack

```
┌─────────────────────────────────────────────────┐
│ Application Layer (Spring Boot WebFlux)        │
│ - REST API (Reactive Router)                   │
│ - Error Handling (RFC 7807)                    │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│ Domain Core Layer (Clean Architecture)         │
│ - Entities (Product, PurchaseSlot, Purchase)   │
│ - Value Objects (Money, Status types)          │
│ - Use Cases & Services                          │
│ - Port Interfaces (Hexagonal)                   │
└─────────────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────┐
│ Adapter Layer (Infrastructure Implementations) │
│ - PostgreSQL (R2DBC)                            │
│ - Redis (Lettuce Reactive)                      │
│ - Kafka (Event Publishing)                      │
└─────────────────────────────────────────────────┘
```

**Core Technologies**:
- Kotlin 1.9.25 (Coroutines support)
- Spring Boot 3.5.8 (WebFlux for reactive)
- PostgreSQL 16 + R2DBC (reactive DB access)
- Redis 7 + Lettuce (reactive cache)
- Kafka 3.x + Avro (event streaming)

---

## Architecture Design

### Hexagonal Architecture (Ports & Adapters)

```
product/
├── core/              # Domain Core (비즈니스 로직, 순수 Kotlin)
│   ├── domain/        # Entities, Value Objects
│   ├── port/          # Port Interfaces (repository, cache, event)
│   ├── usecase/       # Use Case Interfaces
│   └── service/       # Business Logic Implementation
│
├── adapter/           # Infrastructure Adapters (외부 시스템 연동)
│   ├── persistence/   # R2DBC Repository Implementations
│   ├── redis/         # Redis Cache Implementations
│   ├── kafka/         # Kafka Event Publisher
│   └── config/        # Infrastructure Configuration
│
├── app/               # Application Entry Point (REST API)
│   ├── controller/    # WebFlux Controllers
│   ├── dto/           # Request/Response DTOs
│   └── config/        # Application Configuration
│
└── worker/            # Background Workers (Kafka Consumers)
    ├── consumer/      # Event Consumers
    └── scheduler/     # Scheduled Tasks (slot expiration)
```

### Design Principles

1. **Dependency Rule**: 의존성은 항상 안쪽(core)으로만 향함
   - `core`는 외부 모듈을 의존하지 않음 (순수 Kotlin + Reactor)
   - `adapter`와 `app`은 `core`의 port 인터페이스를 구현
   - `core`는 테스트 가능하고 프레임워크 독립적

2. **Port-Adapter Pattern**:
   - **Port**: `core/port/` - 인터페이스 정의 (what)
   - **Adapter**: `adapter/` - 구현체 (how)
   - 예: `ProductRepository` (port) ← `ProductRepositoryImpl` (adapter)

3. **Reactive Programming**:
   - 모든 I/O는 non-blocking (Mono/Flux)
   - Backpressure 지원으로 안정적인 고부하 처리

---

## Phase 1: Infrastructure Layer

**Branch**: `product/002-phase-1-infrastructure`
**Status**: ✅ Complete (16 tasks)
**Goal**: 데이터베이스, 캐시, 메시징 인프라 구축

### 1.1 Database Design

#### PostgreSQL Schema

**핵심 테이블 4개**:

```sql
-- V001: products 테이블
CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    stock INTEGER NOT NULL CHECK (stock >= 0),
    initial_stock INTEGER NOT NULL CHECK (initial_stock >= 0),
    sale_date TIMESTAMP NOT NULL,
    price DECIMAL(19, 2) NOT NULL CHECK (price >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255)
);

CREATE INDEX idx_products_sale_date ON products(sale_date);
CREATE INDEX idx_products_stock ON products(stock) WHERE stock > 0;
```

**기술적 고민**:
- **Partial Index**: `WHERE stock > 0` - 재고 있는 상품만 인덱싱하여 인덱스 크기 최소화
- **Check Constraints**: `stock >= 0`, `price >= 0` - 데이터베이스 레벨에서 불변 조건 강제
- **UUID Primary Key**: 분산 환경에서 ID 충돌 방지, 순차적이지 않아 보안 강화

```sql
-- V002: purchase_slots 테이블 (구매권)
CREATE TABLE purchase_slots (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    acquired_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    queue_position BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE CASCADE
);

-- 복합 인덱스: 사용자별 활성 구매권 조회 최적화
CREATE UNIQUE INDEX idx_slots_user_product_active
    ON purchase_slots(user_id, product_id)
    WHERE status = 'ACTIVE';

-- 만료 처리용 인덱스
CREATE INDEX idx_slots_expires_at
    ON purchase_slots(expires_at)
    WHERE status = 'ACTIVE';
```

**기술적 고민**:
- **Unique Partial Index**: `WHERE status = 'ACTIVE'` - 사용자가 동일 상품에 대해 활성 구매권을 1개만 가지도록 DB 레벨에서 강제
- **Expiration Index**: 만료된 슬롯을 빠르게 찾아 배치 처리 (Worker 성능 최적화)
- **Cascade Delete**: 상품 삭제 시 관련 구매권도 자동 삭제 (referential integrity)

```sql
-- V003: purchases 테이블 (실제 구매)
CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    price_amount DECIMAL(19, 2) NOT NULL,
    price_currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    payment_status VARCHAR(50) NOT NULL,
    purchased_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_slot FOREIGN KEY (slot_id)
        REFERENCES purchase_slots(id) ON DELETE RESTRICT
);

CREATE INDEX idx_purchases_user_id ON purchases(user_id);
CREATE INDEX idx_purchases_payment_status ON purchases(payment_status);
```

**기술적 고민**:
- **ON DELETE RESTRICT**: 구매권이 삭제되면 구매도 삭제되는 것을 방지 (데이터 무결성)
- **Denormalization**: `product_name`, `price_amount` 등을 저장 - 상품 정보 변경에도 과거 구매 이력 보존
- **Payment Status Index**: 결제 상태별 조회 최적화 (관리자 대시보드, 정산 등)

```sql
-- V004: slot_audit_log 테이블 (감사 로그)
CREATE TABLE slot_audit_log (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    trace_id VARCHAR(100),
    metadata JSONB,

    CONSTRAINT fk_slot_audit FOREIGN KEY (slot_id)
        REFERENCES purchase_slots(id) ON DELETE CASCADE
);

CREATE INDEX idx_audit_slot_id ON slot_audit_log(slot_id);
CREATE INDEX idx_audit_timestamp ON slot_audit_log(timestamp DESC);
CREATE INDEX idx_audit_trace_id ON slot_audit_log(trace_id);
```

**기술적 고민**:
- **JSONB Column**: 유연한 메타데이터 저장 + GIN 인덱스 가능
- **Trace ID Index**: 분산 추적을 위한 전체 플로우 추적
- **Timestamp DESC Index**: 최근 이벤트 조회 최적화

#### Flyway Migration Strategy

```kotlin
// product/adapter/build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:postgresql") // JDBC for Flyway
}
```

**기술적 고민**:
- **R2DBC + Flyway 조합**: Flyway는 JDBC만 지원하므로 JDBC 드라이버 추가 필요
- **Migration Execution**: 애플리케이션 시작 시 동기적으로 실행 후 R2DBC로 전환
- **Version Naming**: `V001__`, `V002__` - 순차적 실행 보장

### 1.2 Redis Infrastructure

#### Redis Configuration

```kotlin
@Configuration
class RedisConfig {
    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        val config = RedisStandaloneConfiguration().apply {
            hostName = "localhost"
            port = 6379
        }

        return LettuceConnectionFactory(config).apply {
            // Connection Pool Settings
            clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .build()
        }
    }

    @Bean
    fun reactiveRedisTemplate(
        factory: ReactiveRedisConnectionFactory
    ): ReactiveRedisTemplate<String, String> {
        return ReactiveRedisTemplate(
            factory,
            RedisSerializationContext.string()
        )
    }
}
```

**기술적 고민**:
- **Lettuce vs Jedis**: Lettuce 선택 - Netty 기반 완전 비동기, Project Reactor 네이티브 지원
- **Connection Timeout**: 500ms - 빠른 실패로 cascading failure 방지
- **Serialization**: String-based - 단순하고 디버깅 용이, JSON 직렬화는 애플리케이션 레벨에서 처리

#### Redis Key Design

```kotlin
object RedisKeyHelper {
    // Stock management: String
    fun productStock(productId: UUID): String =
        "product:$productId:stock"

    // Fairness queue: Sorted Set (ZSET)
    // Score = arrival timestamp (milliseconds)
    fun slotQueue(productId: UUID): String =
        "product:$productId:queue"

    // Duplicate prevention: Set with TTL
    fun slotDuplicateCheck(userId: UUID, productId: UUID): String =
        "slot:$userId:$productId:acquired"

    // Rate limiting (optional): String with TTL
    fun userRateLimit(userId: UUID): String =
        "ratelimit:user:$userId"
}
```

**기술적 고민**:
- **Key Naming Convention**: `domain:entity:attribute` 패턴 - 가독성, 패턴 매칭 용이
- **ZSET for Queue**: Score를 timestamp로 사용하여 자동 정렬, O(log N) 삽입/조회
- **TTL 전략**: 중복 체크 키는 15분 TTL - 메모리 절약, 자동 정리

#### Lua Script for Atomic Operations

```lua
-- redis/slot-acquisition.lua
-- 원자적 슬롯 획득: 재고 확인 + 큐 추가 + 중복 체크
local stock_key = KEYS[1]      -- product:{productId}:stock
local queue_key = KEYS[2]      -- product:{productId}:queue
local duplicate_key = KEYS[3]  -- slot:{userId}:{productId}:acquired

local user_id = ARGV[1]
local arrival_timestamp = tonumber(ARGV[2])

-- 1. Check if user already has a slot (duplicate prevention)
if redis.call('EXISTS', duplicate_key) == 1 then
    return {
        success = false,
        reason = 'DUPLICATE',
        queue_position = 0,
        remaining_stock = 0
    }
end

-- 2. Check remaining stock
local stock = tonumber(redis.call('GET', stock_key) or '0')
if stock <= 0 then
    return {
        success = false,
        reason = 'SOLD_OUT',
        queue_position = 0,
        remaining_stock = 0
    }
end

-- 3. Decrement stock atomically
redis.call('DECR', stock_key)

-- 4. Add to fairness queue (ZSET with timestamp as score)
redis.call('ZADD', queue_key, arrival_timestamp, user_id)

-- 5. Mark as acquired to prevent duplicates (TTL 15 minutes)
redis.call('SETEX', duplicate_key, 900, '1')

-- 6. Get queue position
local queue_position = redis.call('ZRANK', queue_key, user_id) + 1

-- 7. Get updated stock
local remaining_stock = tonumber(redis.call('GET', stock_key) or '0')

return {
    success = true,
    queue_position = queue_position,
    remaining_stock = remaining_stock
}
```

**기술적 고민**:
- **Why Lua?**: Redis는 단일 스레드이므로 Lua 스크립트는 원자적으로 실행됨 - race condition 방지
- **All-or-Nothing**: 중복 체크 → 재고 확인 → 차감 → 큐 추가를 한 트랜잭션으로 처리
- **Return Value**: JSON-like table 반환 - 성공/실패 이유, 큐 위치, 남은 재고를 한 번의 호출로 획득
- **Performance**: Network round-trip 1회로 4개 Redis 명령 실행 - 지연 시간 최소화

### 1.3 Kafka Event Streaming

#### Kafka Producer Configuration

```kotlin
@Configuration
class KafkaProducerConfig {
    @Bean
    fun kafkaProducerFactory(): ProducerFactory<String, ByteArray> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "1",  // Leader ack only
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(factory: ProducerFactory<String, ByteArray>): KafkaTemplate<String, ByteArray> {
        return KafkaTemplate(factory)
    }
}
```

**기술적 고민**:
- **ACKS=1**: Leader만 확인 - 성능과 내구성 균형 (all=3은 지연 시간 증가)
- **Idempotence**: 중복 전송 방지 - 네트워크 재시도로 인한 중복 이벤트 제거
- **Max In Flight=5**: 순서 보장하면서 처리량 최적화 (기본값보다 높음)

#### Avro Schema Design

```json
// shared/events/product/slot-acquired.avsc
{
  "namespace": "com.dopaminestore.events.product",
  "type": "record",
  "name": "SlotAcquiredEvent",
  "fields": [
    {"name": "slotId", "type": "string"},
    {"name": "userId", "type": "string"},
    {"name": "productId", "type": "string"},
    {"name": "productName", "type": "string"},
    {"name": "queuePosition", "type": "long"},
    {"name": "expiresAt", "type": "long"},
    {"name": "acquiredAt", "type": "long"},
    {"name": "traceId", "type": "string"}
  ]
}
```

**기술적 고민**:
- **Avro vs JSON**: Avro 선택 - 스키마 진화 지원, 작은 payload 크기, 강타입
- **Schema Registry**: (향후) 스키마 버전 관리, 하위 호환성 보장
- **Trace ID**: 분산 추적을 위해 모든 이벤트에 포함 - 전체 플로우 디버깅 가능

#### Event Topics

```
product.slot.acquired      # 구매권 획득 성공
product.slot.expired       # 구매권 만료 (Worker 발행)
product.payment.completed  # 결제 완료 (Payment 도메인에서 수신)
product.purchase.completed # 구매 완료
product.stock.depleted     # 재고 소진 알림
```

**기술적 고민**:
- **Topic Naming**: `{domain}.{entity}.{event}` 패턴 - 명확한 이벤트 소유권
- **Fire-and-Forget**: 이벤트 발행 실패가 구매권 획득을 막지 않음 - 가용성 우선
- **Event Sourcing 준비**: 향후 이벤트를 데이터 소스로 활용 가능

### 1.4 Distributed Tracing

```kotlin
@Configuration
class TracingConfig {
    @Bean
    fun traceIdFilter(): WebFilter {
        return WebFilter { exchange, chain ->
            val traceId = exchange.request.headers.getFirst("X-Trace-Id")
                ?: UUID.randomUUID().toString()

            exchange.response.headers.add("X-Trace-Id", traceId)

            chain.filter(exchange)
                .contextWrite { ctx -> ctx.put("traceId", traceId) }
        }
    }
}
```

**기술적 고민**:
- **Trace ID Propagation**: HTTP Header → Reactor Context → DB/Redis/Kafka
- **Correlation**: 단일 요청의 모든 로그/이벤트를 trace ID로 연결
- **Observability**: (향후) OpenTelemetry 통합 준비

---

## Phase 2: Domain Core Layer

**Branch**: `product/002-phase-2-domain-core`
**Status**: ✅ Complete (18 tasks, 119 tests)
**Goal**: DDD 기반 도메인 모델 설계 및 비즈니스 로직 구현

### 2.1 Domain-Driven Design

#### Aggregate Roots

**1. Product Aggregate**

```kotlin
data class Product(
    val id: UUID,
    val name: String,
    val description: String,
    val stock: Int,
    val initialStock: Int,
    val saleDate: Instant,
    val price: Money,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val createdBy: String
) {
    init {
        require(name.isNotBlank()) { "Product name cannot be blank" }
        require(stock >= 0) { "Stock cannot be negative" }
        require(initialStock >= stock) { "Initial stock must be >= current stock" }
        require(price.amount >= BigDecimal.ZERO) { "Price cannot be negative" }
    }

    fun computeStatus(): ProductStatus {
        val now = Instant.now()
        return when {
            saleDate.isAfter(now) -> ProductStatus.UPCOMING
            stock <= 0 -> ProductStatus.SOLD_OUT
            else -> ProductStatus.AVAILABLE
        }
    }

    fun decreaseStock(quantity: Int = 1): Product {
        require(quantity > 0) { "Quantity must be positive" }
        require(stock >= quantity) { "Insufficient stock" }
        return copy(stock = stock - quantity, updatedAt = Instant.now())
    }
}
```

**기술적 고민**:
- **Immutability**: `data class`로 불변성 보장 - 스레드 안전, 예측 가능한 동작
- **Constructor Validation**: `init` 블록에서 불변 조건 검증 - 잘못된 상태의 객체 생성 불가
- **Business Logic in Entity**: `computeStatus()`, `decreaseStock()` - 비즈니스 규칙을 도메인 객체에 캡슐화
- **Copy for Updates**: `copy()`로 새 객체 반환 - 이벤트 소싱 준비

**2. PurchaseSlot Aggregate**

```kotlin
data class PurchaseSlot(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val productId: UUID,
    val productName: String,
    val status: SlotStatus,
    val acquiredAt: Instant = Instant.now(),
    val expiresAt: Instant,
    val queuePosition: Long,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    companion object {
        const val SLOT_TTL_MINUTES = 15L

        fun create(
            userId: UUID,
            productId: UUID,
            productName: String,
            queuePosition: Long
        ): PurchaseSlot {
            val now = Instant.now()
            return PurchaseSlot(
                userId = userId,
                productId = productId,
                productName = productName,
                status = SlotStatus.ACTIVE,
                acquiredAt = now,
                expiresAt = now.plusSeconds(SLOT_TTL_MINUTES * 60),
                queuePosition = queuePosition
            )
        }
    }

    fun isExpired(): Boolean {
        return Instant.now().isAfter(expiresAt) && status == SlotStatus.ACTIVE
    }

    fun expire(): PurchaseSlot {
        require(status == SlotStatus.ACTIVE) { "Only active slots can be expired" }
        return copy(
            status = SlotStatus.EXPIRED,
            updatedAt = Instant.now()
        )
    }

    fun use(): PurchaseSlot {
        require(status == SlotStatus.ACTIVE) { "Only active slots can be used" }
        require(!isExpired()) { "Expired slot cannot be used" }
        return copy(
            status = SlotStatus.USED,
            updatedAt = Instant.now()
        )
    }

    fun cancel(): PurchaseSlot {
        require(status == SlotStatus.ACTIVE) { "Only active slots can be cancelled" }
        return copy(
            status = SlotStatus.CANCELLED,
            updatedAt = Instant.now()
        )
    }

    fun remainingSeconds(): Long {
        return Duration.between(Instant.now(), expiresAt).seconds.coerceAtLeast(0)
    }
}
```

**기술적 고민**:
- **Factory Method**: `create()` - 일관된 초기 상태 보장 (15분 TTL, ACTIVE 상태)
- **State Transitions**: `expire()`, `use()`, `cancel()` - 허용된 상태 전환만 가능
- **Preconditions**: `require()` - 잘못된 상태 전환 시 예외 발생
- **Behavior-Rich Model**: `isExpired()`, `remainingSeconds()` - 데이터뿐만 아니라 행위도 캡슐화

**3. Purchase Aggregate**

```kotlin
data class Purchase(
    val id: UUID = UUID.randomUUID(),
    val slotId: UUID,
    val userId: UUID,
    val productId: UUID,
    val productName: String,
    val price: Money,
    val paymentStatus: PaymentStatus,
    val purchasedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    companion object {
        fun fromSlot(slot: PurchaseSlot, price: Money): Purchase {
            require(slot.status == SlotStatus.USED) { "Slot must be used" }
            return Purchase(
                slotId = slot.id,
                userId = slot.userId,
                productId = slot.productId,
                productName = slot.productName,
                price = price,
                paymentStatus = PaymentStatus.PENDING
            )
        }
    }

    fun completePayment(): Purchase {
        require(paymentStatus == PaymentStatus.PENDING) { "Only pending payments can be completed" }
        return copy(
            paymentStatus = PaymentStatus.COMPLETED,
            purchasedAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    fun failPayment(): Purchase {
        require(paymentStatus == PaymentStatus.PENDING) { "Only pending payments can fail" }
        return copy(
            paymentStatus = PaymentStatus.FAILED,
            updatedAt = Instant.now()
        )
    }
}
```

**기술적 고민**:
- **Aggregate Boundary**: Purchase는 PurchaseSlot과 별도 Aggregate - 독립적 라이프사이클
- **Factory from Slot**: `fromSlot()` - 슬롯 사용 후 구매 생성 플로우 명확화
- **Payment State Machine**: PENDING → COMPLETED/FAILED - 명확한 결제 상태 전환

### 2.2 Value Objects

**1. Money Value Object**

```kotlin
data class Money(
    val amount: BigDecimal,
    val currency: Currency = Currency.getInstance("KRW")
) {
    init {
        require(amount.scale() <= 2) { "Money amount cannot have more than 2 decimal places" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.subtract(other.amount), currency)
    }

    operator fun times(multiplier: Int): Money {
        return Money(amount.multiply(BigDecimal(multiplier)), currency)
    }

    operator fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot operate on different currencies: $currency vs ${other.currency}"
        }
    }
}
```

**기술적 고민**:
- **Type Safety**: BigDecimal로 정확한 금액 계산 (Float/Double의 부동소수점 오차 방지)
- **Operator Overloading**: `+`, `-`, `*`, `compareTo` - 자연스러운 수식 표현
- **Currency Validation**: 서로 다른 통화 연산 방지
- **Immutable**: 모든 연산이 새 객체 반환

**2. Status Enums with Logic**

```kotlin
enum class SlotStatus {
    ACTIVE,    // 구매 가능
    USED,      // 구매 완료
    EXPIRED,   // 시간 만료
    CANCELLED; // 사용자 취소

    fun canTransitionTo(newStatus: SlotStatus): Boolean {
        return when (this) {
            ACTIVE -> newStatus in setOf(USED, EXPIRED, CANCELLED)
            USED, EXPIRED, CANCELLED -> false  // 종료 상태
        }
    }
}

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED;

    fun isFinal(): Boolean = this in setOf(COMPLETED, FAILED, REFUNDED)
}
```

**기술적 고민**:
- **State Machine in Enum**: `canTransitionTo()` - 허용된 상태 전환 명시
- **Query Methods**: `isFinal()` - 상태 속성을 도메인 언어로 표현
- **Type Safety**: Enum으로 잘못된 문자열 값 방지

### 2.3 Port Interfaces (Hexagonal)

**Repository Ports**

```kotlin
interface ProductRepository {
    fun findById(id: UUID): Mono<Product>
    fun findBySaleDate(from: Instant, to: Instant): Flux<Product>
    fun save(product: Product): Mono<Product>
    fun decreaseStock(productId: UUID, quantity: Int): Mono<Product>
}

interface PurchaseSlotRepository {
    fun findById(id: UUID): Mono<PurchaseSlot>
    fun findByUserId(userId: UUID): Flux<PurchaseSlot>
    fun findActiveSlots(productId: UUID): Flux<PurchaseSlot>
    fun hasActiveSlot(userId: UUID, productId: UUID): Mono<Boolean>
    fun save(slot: PurchaseSlot): Mono<PurchaseSlot>
    fun findExpiredSlots(): Flux<PurchaseSlot>
}
```

**기술적 고민**:
- **Reactive Types**: Mono/Flux - 비동기 non-blocking I/O
- **Domain Language**: 메서드 이름이 비즈니스 의도를 명확히 표현
- **Aggregate-Oriented**: Repository는 Aggregate Root 단위로만 정의

**External Service Ports**

```kotlin
interface RedisSlotCache {
    data class AcquisitionResult(
        val success: Boolean,
        val reason: String? = null,  // DUPLICATE, SOLD_OUT
        val queuePosition: Long,
        val remainingStock: Int
    )

    // Lua script execution
    fun acquireSlot(
        productId: UUID,
        userId: UUID,
        arrivalTimestamp: Long
    ): Mono<AcquisitionResult>

    // Queue management
    fun getQueuePosition(productId: UUID, userId: UUID): Mono<Long>
    fun getQueueSize(productId: UUID): Mono<Long>

    // Stock management
    fun getStock(productId: UUID): Mono<Int>
    fun setStock(productId: UUID, stock: Int): Mono<Void>
    fun incrementStock(productId: UUID, quantity: Int): Mono<Int>
}

interface EventPublisher {
    data class SlotAcquiredEvent(
        val slotId: UUID,
        val userId: UUID,
        val productId: UUID,
        val productName: String,
        val queuePosition: Long,
        val expiresAt: Instant,
        val acquiredAt: Instant,
        val traceId: String
    )

    fun publishSlotAcquired(event: SlotAcquiredEvent): Mono<Void>
    fun publishSlotExpired(slotId: UUID, userId: UUID, productId: UUID, traceId: String): Mono<Void>
    // ... more event methods

    companion object {
        const val TOPIC_SLOT_ACQUIRED = "product.slot.acquired"
        const val TOPIC_SLOT_EXPIRED = "product.slot.expired"
    }
}
```

**기술적 고민**:
- **Result Objects**: `AcquisitionResult` - 복잡한 결과를 타입 안전하게 반환
- **Topic Constants**: Companion object에 정의 - 토픽 이름 중앙 관리
- **Fire-and-Forget**: `Mono<Void>` - 이벤트 발행 실패가 비즈니스 로직을 막지 않음

### 2.4 Testing Strategy (119 Tests)

**Value Object Tests (61 tests)**

```kotlin
class MoneyTest {
    @Test
    fun `should add money with same currency`() {
        val money1 = Money(BigDecimal("100.00"), Currency.getInstance("KRW"))
        val money2 = Money(BigDecimal("50.00"), Currency.getInstance("KRW"))

        val result = money1 + money2

        assertEquals(BigDecimal("150.00"), result.amount)
    }

    @Test
    fun `should throw exception when adding different currencies`() {
        val krw = Money(BigDecimal("100.00"), Currency.getInstance("KRW"))
        val usd = Money(BigDecimal("100.00"), Currency.getInstance("USD"))

        assertThrows<IllegalArgumentException> {
            krw + usd
        }
    }

    // 31 total tests: arithmetic, comparison, validation, edge cases
}
```

**Entity Tests (58 tests)**

```kotlin
class PurchaseSlotTest {
    @Test
    fun `should create active slot with 15 minute TTL`() {
        val slot = PurchaseSlot.create(
            userId = UUID.randomUUID(),
            productId = UUID.randomUUID(),
            productName = "Test Product",
            queuePosition = 1L
        )

        assertEquals(SlotStatus.ACTIVE, slot.status)
        assertTrue(slot.remainingSeconds() > 0)
        assertTrue(slot.remainingSeconds() <= 15 * 60)
    }

    @Test
    fun `should expire active slot`() {
        val slot = createActiveSlot()

        val expired = slot.expire()

        assertEquals(SlotStatus.EXPIRED, expired.status)
    }

    @Test
    fun `should not expire already used slot`() {
        val usedSlot = createActiveSlot().use()

        assertThrows<IllegalArgumentException> {
            usedSlot.expire()
        }
    }

    // 21 total tests: state transitions, validation, expiration logic
}
```

**기술적 고민**:
- **Test Coverage**: 도메인 불변 조건, 상태 전환, 경계값, 예외 케이스 모두 검증
- **Parameterized Tests**: 여러 입력 조합 테스트 (JUnit 5 `@ParameterizedTest`)
- **Test Naming**: `should {action} when {condition}` - 의도 명확화

---

## Phase 3: Slot Acquisition Feature

**Branch**: `product/002-phase-3-slot-acquisition`
**Status**: 🟡 In Progress (11/18 tasks, 61%)
**Goal**: 100K RPS 선착순 구매권 획득 기능 구현

### 3.1 Use Case Implementation

#### SlotAcquisitionService (6-Step Orchestration)

```kotlin
@Service
class SlotAcquisitionService(
    private val productRepository: ProductRepository,
    private val slotRepository: PurchaseSlotRepository,
    private val slotCache: RedisSlotCache,
    private val auditRepository: SlotAuditRepository,
    private val eventPublisher: EventPublisher
) : SlotAcquisitionUseCase {

    override fun acquireSlot(command: AcquireSlotCommand): Mono<PurchaseSlot> {
        return validateProduct(command.productId, command.traceId)
            .flatMap { product ->
                checkDuplicateSlot(command.userId, command.productId, command.traceId)
                    .then(acquireSlotAtomically(
                        command.userId,
                        command.productId,
                        command.arrivalTimestamp,
                        command.traceId
                    ))
                    .flatMap { cacheResult ->
                        persistSlot(command, product.name)
                            .flatMap { slot ->
                                logAcquisition(slot, cacheResult, command.traceId)
                                    .then(publishAcquisitionEvent(slot, command.traceId))
                                    .thenReturn(slot)
                            }
                    }
            }
    }

    private fun validateProduct(productId: UUID, traceId: String): Mono<Product> {
        return productRepository.findById(productId)
            .switchIfEmpty(Mono.error(ProductNotFoundException(productId)))
            .flatMap { product ->
                when {
                    product.computeStatus() == ProductStatus.UPCOMING ->
                        Mono.error(SaleNotStartedException(product.saleDate))
                    product.stock <= 0 ->
                        Mono.error(ProductSoldOutException(productId))
                    else -> Mono.just(product)
                }
            }
    }

    private fun checkDuplicateSlot(
        userId: UUID,
        productId: UUID,
        traceId: String
    ): Mono<Void> {
        return slotRepository.hasActiveSlot(userId, productId)
            .flatMap { hasSlot ->
                if (hasSlot) Mono.error(DuplicateSlotException(userId, productId))
                else Mono.empty()
            }
    }

    private fun acquireSlotAtomically(
        userId: UUID,
        productId: UUID,
        arrivalTimestamp: Long,
        traceId: String
    ): Mono<RedisSlotCache.AcquisitionResult> {
        return slotCache.acquireSlot(productId, userId, arrivalTimestamp)
            .flatMap { result ->
                if (result.success) Mono.just(result)
                else when (result.reason) {
                    "DUPLICATE" -> Mono.error(DuplicateSlotException(userId, productId))
                    "SOLD_OUT" -> Mono.error(ProductSoldOutException(productId))
                    else -> Mono.error(SlotAcquisitionFailedException(result.reason ?: "UNKNOWN"))
                }
            }
    }

    private fun persistSlot(
        command: AcquireSlotCommand,
        productName: String
    ): Mono<PurchaseSlot> {
        val slot = PurchaseSlot.create(
            userId = command.userId,
            productId = command.productId,
            productName = productName,
            queuePosition = 0  // Will be updated from cache result
        )
        return slotRepository.save(slot)
    }

    private fun logAcquisition(
        slot: PurchaseSlot,
        cacheResult: RedisSlotCache.AcquisitionResult,
        traceId: String
    ): Mono<SlotAuditRepository.AuditLogEntry> {
        val logEntry = SlotAuditRepository.AuditLogEntry(
            id = UUID.randomUUID(),
            slotId = slot.id,
            eventType = "SLOT_ACQUIRED",
            oldStatus = null,
            newStatus = SlotStatus.ACTIVE,
            timestamp = Instant.now(),
            traceId = traceId
        )
        return auditRepository.save(logEntry)
    }

    private fun publishAcquisitionEvent(
        slot: PurchaseSlot,
        traceId: String
    ): Mono<Void> {
        val event = EventPublisher.SlotAcquiredEvent(
            slotId = slot.id,
            userId = slot.userId,
            productId = slot.productId,
            productName = slot.productName,
            queuePosition = slot.queuePosition,
            expiresAt = slot.expiresAt,
            acquiredAt = slot.acquiredAt,
            traceId = traceId
        )
        return eventPublisher.publishSlotAcquired(event)
            .doOnError { error ->
                log.error("Failed to publish slot acquired event: slotId=${slot.id}, error=${error.message}")
            }
            .onErrorResume { Mono.empty() }  // Fire-and-forget
    }
}
```

**기술적 고민**:

1. **Orchestration Pattern**: 6단계를 명확히 분리 - 각 단계의 책임 명확, 테스트 용이
2. **Fail-Fast Validation**: 상품 검증 → 중복 체크 순서로 빠른 실패
3. **Atomic Cache Operation**: Redis Lua 스크립트로 race condition 방지
4. **Database as Source of Truth**: Redis 성공 후 DB 저장 - 최종 일관성
5. **Fire-and-Forget Event**: 이벤트 발행 실패가 슬롯 획득을 막지 않음 - 가용성 우선
6. **Reactive Chain**: `flatMap` 체인으로 비동기 플로우 구성 - non-blocking

### 3.2 Fairness Guarantee

#### Arrival Time Capture

```kotlin
@RestController
@RequestMapping("/api/v1/slots")
class SlotController(
    private val slotAcquisitionUseCase: SlotAcquisitionUseCase
) {
    @PostMapping("/acquire")
    fun acquireSlot(
        @Valid @RequestBody request: AcquireSlotRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<AcquireSlotResponse>> {
        // Critical: Capture arrival time ASAP
        val arrivalTimestamp = System.currentTimeMillis()

        val traceId = exchange.request.headers.getFirst("X-Trace-Id")
            ?: UUID.randomUUID().toString()

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = request.userId,
            productId = request.productId,
            arrivalTimestamp = arrivalTimestamp,  // Fairness guarantee
            traceId = traceId
        )

        return slotAcquisitionUseCase.acquireSlot(command)
            .map { slot ->
                ResponseEntity.status(HttpStatus.CREATED)
                    .body(AcquireSlotResponse.from(slot))
            }
            .onErrorResume { error -> handleSlotAcquisitionError(error, traceId) }
    }
}
```

**기술적 고민**:
- **Arrival Time = Wall Clock**: 컨트롤러 진입 시점에 즉시 캡처 - 네트워크 지연 후 처리 순서 영향 최소화
- **Millisecond Precision**: `currentTimeMillis()` - 마이크로초는 오버킬, 밀리초면 충분
- **Clock Skew 문제**: 단일 서버 가정, 분산 환경에서는 NTP 동기화 필요

#### Redis ZSET Queue

```kotlin
@Component
class RedisSlotCacheImpl(
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val acquisitionScript: RedisScript<Map<*, *>>
) : RedisSlotCache {

    override fun getQueuePosition(productId: UUID, userId: UUID): Mono<Long> {
        val queueKey = RedisKeyHelper.slotQueue(productId)

        return redisTemplate.opsForZSet()
            .rank(queueKey, userId.toString())  // ZRANK: O(log N)
            .map { rank -> rank + 1 }  // 1-based position
            .defaultIfEmpty(0L)
    }

    override fun getQueueSize(productId: UUID): Mono<Long> {
        val queueKey = RedisKeyHelper.slotQueue(productId)

        return redisTemplate.opsForZSet()
            .size(queueKey)  // ZCARD: O(1)
            .defaultIfEmpty(0L)
    }
}
```

**기술적 고민**:
- **ZSET Score = Timestamp**: 자동 정렬로 O(log N) 삽입, O(log N) 순위 조회
- **Idempotent**: 동일 사용자 재요청 시 기존 점수 유지 (업데이트 안 됨)
- **Queue Size Monitoring**: ZCARD로 대기열 길이 실시간 모니터링

### 3.3 Adapter Implementations

#### R2DBC Repository

```kotlin
@Repository
class PurchaseSlotRepositoryImpl(
    private val databaseClient: DatabaseClient
) : PurchaseSlotRepository {

    override fun hasActiveSlot(userId: UUID, productId: UUID): Mono<Boolean> {
        val sql = """
            SELECT EXISTS(
                SELECT 1 FROM purchase_slots
                WHERE user_id = :user_id
                  AND product_id = :product_id
                  AND status = 'ACTIVE'
            )
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("user_id", userId)
            .bind("product_id", productId)
            .map { row -> row.get(0, Boolean::class.javaObjectType) ?: false }
            .one()
    }

    override fun save(slot: PurchaseSlot): Mono<PurchaseSlot> {
        val sql = """
            INSERT INTO purchase_slots (
                id, user_id, product_id, status, acquired_at,
                expires_at, queue_position, created_at, updated_at
            ) VALUES (
                :id, :user_id, :product_id, :status, :acquired_at,
                :expires_at, :queue_position, :created_at, :updated_at
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", slot.id)
            .bind("user_id", slot.userId)
            .bind("product_id", slot.productId)
            .bind("status", slot.status.name)
            .bind("acquired_at", slot.acquiredAt)
            .bind("expires_at", slot.expiresAt)
            .bind("queue_position", slot.queuePosition)
            .bind("created_at", slot.createdAt)
            .bind("updated_at", slot.updatedAt)
            .fetch()
            .rowsUpdated()
            .thenReturn(slot)
    }
}
```

**기술적 고민**:
- **EXISTS vs COUNT**: `EXISTS`가 빠름 - 첫 번째 매치에서 즉시 반환
- **UPSERT**: `ON CONFLICT DO UPDATE` - 재시도 시 멱등성 보장
- **Partial Index 활용**: `WHERE status = 'ACTIVE'` - 인덱스 히트율 높음

#### Kafka Event Publisher

```kotlin
@Component
class EventPublisherImpl(
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>
) : EventPublisher {

    override fun publishSlotAcquired(event: EventPublisher.SlotAcquiredEvent): Mono<Void> {
        val topic = EventPublisher.TOPIC_SLOT_ACQUIRED
        val key = event.userId.toString()  // Partition by user

        val avroEvent = mapOf(
            "slotId" to event.slotId.toString(),
            "userId" to event.userId.toString(),
            "productId" to event.productId.toString(),
            "productName" to event.productName,
            "queuePosition" to event.queuePosition,
            "expiresAt" to event.expiresAt.toEpochMilli(),
            "acquiredAt" to event.acquiredAt.toEpochMilli(),
            "traceId" to event.traceId
        )

        return publishEvent(topic, key, avroEvent, event.traceId)
    }

    private fun publishEvent(
        topic: String,
        key: String,
        payload: Map<String, Any>,
        traceId: String
    ): Mono<Void> {
        return Mono.fromFuture {
            kafkaTemplate.send(topic, key, serializeAvro(payload))
        }
        .doOnSuccess {
            log.info("Event published: topic=$topic, key=$key, traceId=$traceId")
        }
        .doOnError { error ->
            log.error("Failed to publish event: topic=$topic, key=$key, traceId=$traceId, error=${error.message}")
        }
        .then()
    }
}
```

**기술적 고민**:
- **Partition Key = User ID**: 동일 사용자 이벤트는 순서 보장
- **Async Publishing**: `Mono.fromFuture()` - Kafka 전송을 비동기로 처리
- **Error Logging Only**: 이벤트 실패가 비즈니스 플로우를 막지 않음

### 3.4 Error Handling (RFC 7807)

```kotlin
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val traceId: String? = null,
    val timestamp: Instant = Instant.now(),
    val additionalProperties: Map<String, Any>? = null
)

private fun handleSlotAcquisitionError(
    error: Throwable,
    traceId: String
): Mono<ResponseEntity<ProblemDetail>> {
    val problem = when (error) {
        is ProductNotFoundException -> ProblemDetail(
            type = "https://api.dopaminestore.com/errors/product-not-found",
            title = "Product Not Found",
            status = 404,
            detail = "Product with ID ${error.productId} not found",
            instance = "/api/v1/slots/acquire",
            traceId = traceId
        )

        is ProductSoldOutException -> ProblemDetail(
            type = "https://api.dopaminestore.com/errors/product-sold-out",
            title = "Product Sold Out",
            status = 409,
            detail = "Product with ID ${error.productId} is sold out",
            instance = "/api/v1/slots/acquire",
            traceId = traceId,
            additionalProperties = mapOf("productId" to error.productId.toString())
        )

        is DuplicateSlotException -> ProblemDetail(
            type = "https://api.dopaminestore.com/errors/duplicate-slot",
            title = "Duplicate Slot Acquisition",
            status = 409,
            detail = "User ${error.userId} already has an active slot for product ${error.productId}",
            instance = "/api/v1/slots/acquire",
            traceId = traceId,
            additionalProperties = mapOf(
                "userId" to error.userId.toString(),
                "productId" to error.productId.toString()
            )
        )

        else -> ProblemDetail(
            type = "https://api.dopaminestore.com/errors/internal-server-error",
            title = "Internal Server Error",
            status = 500,
            detail = error.message ?: "An unexpected error occurred",
            instance = "/api/v1/slots/acquire",
            traceId = traceId
        )
    }

    return Mono.just(ResponseEntity.status(problem.status).body(problem))
}
```

**기술적 고민**:
- **RFC 7807 Standard**: API 클라이언트가 예측 가능한 에러 구조
- **Type URL**: 에러 유형별 문서 링크 - 클라이언트 개발자 가이드
- **Additional Properties**: 에러 컨텍스트 정보 추가 (디버깅 용이)
- **Trace ID 포함**: 에러 발생 시 전체 플로우 추적 가능

---

## Key Technical Decisions

### 1. Reactive Programming (Spring WebFlux + R2DBC)

**Decision**: 전체 스택을 Reactive로 구성

**Rationale**:
- 100K RPS 목표 달성을 위해 non-blocking I/O 필수
- Thread-per-request 모델은 높은 동시성에서 메모리 부족 (1K threads = ~1GB)
- Reactive: 수천~수만 동시 연결을 소수 스레드로 처리

**Trade-offs**:
- ✅ **Pro**: 높은 처리량, 낮은 메모리 사용, 백프레셔 지원
- ❌ **Con**: 학습 곡선, 디버깅 어려움, 스택 트레이스 복잡

### 2. Redis Lua Script for Atomicity

**Decision**: Lua 스크립트로 재고 확인 + 차감 + 큐 추가를 원자적 처리

**Rationale**:
- Redis는 단일 스레드이므로 Lua 스크립트는 원자적으로 실행됨
- 4개 Redis 명령을 1번의 네트워크 호출로 처리 (latency 감소)
- Race condition 완전 방지: 동시 요청 시 정확히 N개만 성공

**Alternative Considered**:
- **Redis Transaction (MULTI/EXEC)**: Watch 조건이 복잡하고 재시도 필요
- **Distributed Lock**: 추가 복잡도, 락 타임아웃 관리 필요

**Trade-offs**:
- ✅ **Pro**: 완벽한 원자성, 높은 성능, 간단한 코드
- ❌ **Con**: Lua 스크립트 디버깅 어려움, Redis 버전 의존성

### 3. Database as Source of Truth (Redis는 Cache)

**Decision**: Redis 성공 후 PostgreSQL에 저장, DB를 최종 데이터 소스로 사용

**Rationale**:
- Redis는 휘발성 - 재시작 시 데이터 손실 가능
- DB는 내구성 보장 - ACID 트랜잭션, 백업/복구
- Redis는 hot path 성능 최적화용, DB는 감사/분석용

**Alternative Considered**:
- **Redis만 사용**: 성능 최고지만 데이터 손실 위험
- **DB만 사용**: 안정적이지만 100K RPS 불가능

**Trade-offs**:
- ✅ **Pro**: 데이터 내구성, 감사 로그, OLAP 분석 가능
- ❌ **Con**: Redis-DB 간 일시적 불일치 (최종 일관성)

### 4. Fire-and-Forget Event Publishing

**Decision**: 이벤트 발행 실패가 비즈니스 로직을 막지 않음

**Rationale**:
- 가용성 우선: 구매권 획득은 성공했는데 이벤트 실패로 전체 실패는 불합리
- 이벤트는 알림/통계용 - 비즈니스 크리티컬 경로 아님
- Kafka 장애가 전체 서비스 장애로 전파되지 않음

**Alternative Considered**:
- **Blocking Event Publish**: 이벤트 성공까지 대기 - 지연 시간 증가, Kafka 장애 시 서비스 다운

**Trade-offs**:
- ✅ **Pro**: 높은 가용성, Kafka 장애 격리, 낮은 latency
- ❌ **Con**: 이벤트 손실 가능성 (로그로 추적, 재발행 메커니즘 필요)

### 5. Hexagonal Architecture

**Decision**: Core 모듈을 외부 의존성 없이 순수 비즈니스 로직으로 구성

**Rationale**:
- 테스트 용이: Mock 없이 도메인 로직 테스트 가능
- 프레임워크 독립: Spring 교체 가능 (Ktor, Micronaut 등)
- 비즈니스 로직 집중: 인프라 관심사 분리

**Trade-offs**:
- ✅ **Pro**: 높은 테스트 커버리지, 유지보수성, 명확한 책임 분리
- ❌ **Con**: 초기 설정 복잡, 코드량 증가 (인터페이스 + 구현체)

---

## Performance Optimization

### 1. Connection Pooling

**R2DBC Pool Settings**:
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
      max-acquire-time: 3s
      max-idle-time: 30m
```

**Rationale**:
- Initial 10: 애플리케이션 시작 시 워밍업
- Max 20: 100K RPS ÷ 5000 instances = 20 req/s per instance (충분함)
- Max Acquire 3s: DB 장애 시 빠른 실패

### 2. Redis Pipelining (향후)

**Current**: 명령별 개별 호출
**Future**: 여러 Redis 명령을 파이프라인으로 일괄 처리

```kotlin
// Future optimization
redisTemplate.executePipelined { connection ->
    connection.get(stockKey)
    connection.zcard(queueKey)
    connection.exists(duplicateKey)
}
```

**Expected Improvement**: Latency 60% 감소 (3 RTT → 1 RTT)

### 3. Database Indexing Strategy

**Created Indexes**:
- `products(sale_date)` - 판매 일정 조회
- `products(stock) WHERE stock > 0` - 재고 있는 상품만
- `purchase_slots(user_id, product_id) WHERE status = 'ACTIVE'` - 중복 체크 최적화
- `purchase_slots(expires_at) WHERE status = 'ACTIVE'` - 만료 배치 처리

**Avoided Anti-patterns**:
- ❌ UUID에 B-Tree 인덱스만: Partial Index로 크기 축소
- ❌ 모든 컬럼 인덱싱: Write 성능 저하 방지

### 4. Caching Strategy

**3-Tier Cache**:
1. **Redis**: Hot path (slot acquisition, stock check)
2. **Application Cache**: 상품 메타데이터 (Caffeine)
3. **CDN**: 정적 자원 (이미지, CSS)

**Cache Invalidation**:
- Write-Through: DB 업데이트 후 Redis 갱신
- TTL: 중복 체크 키 15분, 재고 키 영구 (명시적 삭제)

---

## Testing Strategy

### 1. Unit Tests (127 tests)

**Phase 2**: 119 tests (domain core)
**Phase 3**: 8 tests (service layer)

**Coverage**:
- Value Objects: 61 tests (arithmetic, validation, edge cases)
- Entities: 58 tests (state transitions, business rules)
- Services: 8 tests (use case orchestration, error handling)

**Tools**:
- JUnit 5 (test framework)
- Mockito-Kotlin (mocking repositories)
- StepVerifier (reactive testing)

### 2. Integration Tests (TODO)

**T038d-f**: End-to-end flow, concurrency, fairness

```kotlin
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class SlotAcquisitionIntegrationTest {
    @Container
    val postgres = PostgreSQLContainer("postgres:16")

    @Container
    val redis = GenericContainer("redis:7")

    @Test
    fun `should prevent overselling with 1000 concurrent requests for 100 stock`() {
        // Given: Product with 100 stock
        // When: 1000 users try to acquire slots concurrently
        // Then: Exactly 100 slots acquired, 900 sold-out errors
    }
}
```

### 3. Load Testing (TODO)

**T039-043**: k6 scripts for 100K RPS

```javascript
// k6 test script
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  scenarios: {
    slot_acquisition: {
      executor: 'constant-arrival-rate',
      rate: 100000,  // 100K RPS
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 5000,
      maxVUs: 10000,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<100'],  // p99 < 100ms
  },
};

export default function () {
  const payload = JSON.stringify({
    userId: `user-${__VU}-${__ITER}`,
    productId: 'test-product-id',
  });

  const res = http.post('http://api/slots/acquire', payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
}
```

**Distributed Setup**: 4 k6 instances × 25K RPS each

---

## Future Improvements

### 1. Read Replicas for Scaling

**Current**: 단일 PostgreSQL master
**Future**: 1 master + 2 read replicas

- **Write**: Master로 전송 (slot 생성)
- **Read**: Replicas로 분산 (슬롯 조회, 감사 로그)

**Expected**: Write throughput 유지, Read throughput 3배 증가

### 2. Redis Cluster for High Availability

**Current**: 단일 Redis 인스턴스
**Future**: Redis Cluster (3 master + 3 replica)

- **Sharding**: productId로 자동 샤딩
- **Failover**: Sentinel 자동 장애 조치
- **Capacity**: 메모리 용량 수평 확장

### 3. Event Sourcing for Audit Trail

**Current**: 상태 기반 저장 (latest state only)
**Future**: 이벤트 소싱 (모든 상태 변화 이벤트 저장)

```kotlin
// Event store
sealed class SlotEvent {
    data class SlotAcquired(...)
    data class SlotUsed(...)
    data class SlotExpired(...)
}

// Rebuild state from events
fun rebuildSlot(events: List<SlotEvent>): PurchaseSlot {
    return events.fold(initialSlot) { slot, event ->
        slot.apply(event)
    }
}
```

**Benefits**: 완전한 감사 로그, 시간 여행 디버깅, 이벤트 재처리

### 4. CQRS for Read Optimization

**Current**: 단일 모델 (Command + Query)
**Future**: Command Model (PostgreSQL) + Query Model (Elasticsearch)

- **Command**: 구매권 생성/수정 → PostgreSQL
- **Query**: 구매권 조회/검색 → Elasticsearch (비정규화, 풀텍스트 검색)

**Benefits**: Read 성능 최적화, 복잡한 검색 쿼리 지원

### 5. Circuit Breaker for Resilience

**Current**: 단순 재시도 + 타임아웃
**Future**: Resilience4j Circuit Breaker

```kotlin
@CircuitBreaker(name = "redis", fallbackMethod = "fallbackAcquireSlot")
fun acquireSlot(...): Mono<PurchaseSlot> {
    // Redis 호출
}

fun fallbackAcquireSlot(...): Mono<PurchaseSlot> {
    // Redis 장애 시 DB만으로 처리 (degraded mode)
}
```

**Benefits**: Cascading failure 방지, 빠른 실패, 자동 복구

---

## Appendix: Commit History

### Phase 1: Infrastructure
- `05c9fc4` - ✨ Feat: Product 도메인 Phase 1 인프라 구축 완료
- `cafe1f2` - 📝 Docs: Add Phase 1 Infrastructure spec file

### Phase 2: Domain Core
- `b69fba2` - ✨ Feat: Product 도메인 Phase 2 Core 구현 완료
- `5902628` - ✅ Test: Phase 2 단위 테스트 완료 (119 test cases)
- `ab18877` - 📝 Docs: Add Phase 2 Domain Core spec file

### Phase 3: Slot Acquisition
- `b4405ce` - ✨ Feat: Phase 3 US1 - Slot Acquisition 구현 완료 (T028-T038)
- `cb6e3f4` - ✅ Test: SlotAcquisitionService unit tests (T038a)
- `80d6c6c` - 📝 Docs: Phase별 specs 폴더 구조 생성

---

**Document Maintainers**: Product Domain Team
**Last Review**: 2026-01-06
**Next Review**: After Phase 3 completion (100% tasks)
