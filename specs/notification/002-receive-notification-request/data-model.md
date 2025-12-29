# Data Model: 알림 요청 수신 및 검증

**Feature**: notification/002-receive-notification-request
**Date**: 2025-12-30

---

## Domain Entities

### 1. NotificationRequest

알림 발송을 위한 내부 도메인 객체. Kafka 이벤트로부터 변환되어 생성된다.

```kotlin
data class NotificationRequest(
    val id: UUID,                           // Idempotency Key (from eventId)
    val userId: String,                     // 수신자 사용자 ID
    val email: String,                      // 수신자 이메일 주소
    val notificationType: NotificationType, // 알림 유형
    val payload: Map<String, Any>,          // 템플릿 변수들
    val sendType: SendType,                 // IMMEDIATE or SCHEDULED
    val scheduledAt: Instant?,              // 예약 발송 시간 (SCHEDULED인 경우)
    val createdAt: Instant                  // 생성 시간
)
```

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| id | UUID | No | 이벤트 ID로부터 파생된 고유 식별자 (idempotency key) |
| userId | String | No | 알림 수신 대상 사용자 ID |
| email | String | No | 알림 수신 이메일 주소 |
| notificationType | NotificationType | No | 알림 유형 (enum) |
| payload | Map<String, Any> | No | 이메일 템플릿에 사용될 변수들 |
| sendType | SendType | No | 즉시/예약 발송 구분 |
| scheduledAt | Instant | Yes | SCHEDULED인 경우 발송 예정 시간 |
| createdAt | Instant | No | NotificationRequest 생성 시각 |

---

### 2. NotificationType (Enum)

알림 유형을 정의하는 열거형.

```kotlin
enum class NotificationType {
    NEW_USER_REGISTERED,        // 회원가입 완료
    PASSWORD_RESET_REQUESTED,   // 비밀번호 재설정 요청
    PURCHASE_SLOT_ACQUIRED,     // 구매 슬롯 획득
    PURCHASE_SLOT_EXPIRING      // 구매 슬롯 만료 예정
}
```

---

### 3. SendType (Enum)

발송 유형을 정의하는 열거형.

```kotlin
enum class SendType {
    IMMEDIATE,  // 즉시 발송
    SCHEDULED   // 예약 발송
}
```

---

### 4. DomainEvent

Kafka로부터 수신되는 외부 도메인 이벤트의 공통 구조.

```kotlin
interface DomainEvent {
    val eventId: String         // 이벤트 고유 ID
    val eventType: String       // 이벤트 타입 문자열
    val occurredAt: Instant     // 이벤트 발생 시간
}
```

---

## Event DTOs (Kafka Message)

### 1. NewUserRegisteredEvent

```kotlin
data class NewUserRegisteredEvent(
    override val eventId: String,
    override val eventType: String = "NEW_USER_REGISTERED",
    override val occurredAt: Instant,
    val userId: String,
    val email: String,
    val userName: String?       // optional: 이메일 템플릿용
) : DomainEvent
```

**Source**: auth 도메인
**Required Fields**: eventId, userId, email, occurredAt

---

### 2. PasswordResetRequestedEvent

```kotlin
data class PasswordResetRequestedEvent(
    override val eventId: String,
    override val eventType: String = "PASSWORD_RESET_REQUESTED",
    override val occurredAt: Instant,
    val userId: String,
    val email: String,
    val resetToken: String,     // 비밀번호 재설정 토큰
    val expiresAt: Instant      // 토큰 만료 시간
) : DomainEvent
```

**Source**: auth 도메인
**Required Fields**: eventId, userId, email, resetToken, occurredAt

---

### 3. PurchaseSlotAcquiredEvent

```kotlin
data class PurchaseSlotAcquiredEvent(
    override val eventId: String,
    override val eventType: String = "PURCHASE_SLOT_ACQUIRED",
    override val occurredAt: Instant,
    val userId: String,
    val email: String,
    val slotId: String,         // 획득한 슬롯 ID
    val productId: String,      // 상품 ID
    val productName: String,    // 상품명
    val expiresAt: Instant      // 슬롯 만료 시간
) : DomainEvent
```

**Source**: purchase 도메인
**Required Fields**: eventId, userId, email, slotId, expiresAt, occurredAt

---

## Entity Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                        Kafka Topics                              │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ notification.requests                                     │    │
│  │   - NewUserRegisteredEvent                               │    │
│  │   - PasswordResetRequestedEvent                          │    │
│  │   - PurchaseSlotAcquiredEvent                            │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Kafka Consumer                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ NewUserRegistered│  │PasswordReset   │  │ PurchaseSlot   │  │
│  │ Consumer         │  │ Consumer       │  │ Consumer       │  │
│  └────────┬─────────┘  └────────┬───────┘  └────────┬───────┘  │
└───────────┼─────────────────────┼───────────────────┼───────────┘
            │                     │                   │
            ▼                     ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                     EventMapper                                  │
│  DomainEvent → NotificationRequest                              │
│  (1 event → 1 or 2 NotificationRequests)                        │
└─────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   NotificationRequest                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ id: UUID                                                  │   │
│  │ userId: String                                            │   │
│  │ email: String                                             │   │
│  │ notificationType: NotificationType                        │   │
│  │ payload: Map<String, Any>                                 │   │
│  │ sendType: SendType (IMMEDIATE | SCHEDULED)                │   │
│  │ scheduledAt: Instant?                                     │   │
│  │ createdAt: Instant                                        │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
            │
            ├── IMMEDIATE ──▶ 즉시 발송 파이프라인 (003)
            │
            └── SCHEDULED ──▶ 예약 발송 저장소 (006)
```

---

## Event → NotificationRequest Mapping

| Event Type | NotificationRequest Count | sendType | scheduledAt |
|------------|---------------------------|----------|-------------|
| NEW_USER_REGISTERED | 1 | IMMEDIATE | null |
| PASSWORD_RESET_REQUESTED | 1 | IMMEDIATE | null |
| PURCHASE_SLOT_ACQUIRED | 2 | IMMEDIATE, SCHEDULED | null, (expiresAt - 5min) |

### Payload Mapping

| Event Type | Payload Fields |
|------------|----------------|
| NEW_USER_REGISTERED | userName |
| PASSWORD_RESET_REQUESTED | resetToken, resetLink (generated), expiresAt |
| PURCHASE_SLOT_ACQUIRED (IMMEDIATE) | productName, slotId, expiresAt, paymentLink (generated) |
| PURCHASE_SLOT_ACQUIRED (SCHEDULED) | productName, slotId, expiresAt, remainingMinutes |

---

## Validation Rules

### Common Rules (All Events)
- `eventId`: Required, non-empty string
- `userId`: Required, non-empty string
- `email`: Required, valid email format
- `occurredAt`: Required, valid timestamp

### Event-Specific Rules

| Event | Field | Rule |
|-------|-------|------|
| PASSWORD_RESET_REQUESTED | resetToken | Required, non-empty |
| PASSWORD_RESET_REQUESTED | expiresAt | Required, must be in future |
| PURCHASE_SLOT_ACQUIRED | slotId | Required, non-empty |
| PURCHASE_SLOT_ACQUIRED | expiresAt | Required, must be in future |
| PURCHASE_SLOT_ACQUIRED | productName | Required, non-empty |

---

## Idempotency Key Strategy

- **Key Format**: `idempotency:{eventId}`
- **Storage**: Redis
- **TTL**: 24 hours (86400 seconds)
- **Behavior**: If key exists, skip processing (log as duplicate)

```
Check: EXISTS idempotency:evt-123
  └── If exists → Log "duplicate", skip processing
  └── If not exists → Process event, SET idempotency:evt-123 EX 86400
```
