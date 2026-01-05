# Feature Specification: Product Domain API & Business Logic

**Feature Branch**: `product/002-product-api-spec`
**Created**: 2026-01-05
**Status**: Draft
**Input**: User description: "product/docs/ProjectSpec.md 을 읽고 product 프로젝트에 필요한 api spec을 정의하고 비즈니스 로직을 먼저 정의하고 싶어."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - First-Come-First-Served Slot Acquisition (Priority: P1)

A buyer visits the product page at 10:00 AM when a limited-stock item goes on sale. They click "Purchase" and the system attempts to secure a PurchaseSlot for them. If successful, they receive immediate confirmation and have 30 minutes to complete payment.

**Why this priority**: This is the core value proposition of Dopamine Store - delivering the "thrill of acquisition" through fair first-come-first-served processing. Without this, the entire business model fails. Must handle 100,000 RPS at peak.

**Independent Test**: Can be fully tested by simulating concurrent purchase requests (load test with 100K requests) and verifying exactly N slots are granted for N stock items, with requests processed in arrival-time order.

**Acceptance Scenarios**:

1. **Given** a product has 100 stock and 100,000 users request simultaneously, **When** the system processes requests, **Then** exactly 100 users receive slots in arrival-time order and 99,900 receive "sold out" response
2. **Given** a user successfully obtains a slot, **When** they check their slot status, **Then** they see slot details with 30-minute expiration countdown
3. **Given** a product has 0 stock, **When** a user attempts to obtain a slot, **Then** they receive immediate "sold out" response without queuing

---

### User Story 2 - Slot Expiration and Reclamation (Priority: P2)

A buyer who obtained a PurchaseSlot but doesn't complete payment within 30 minutes sees their slot automatically expire. The system reclaims the slot and the stock becomes available for other waiting buyers (if any).

**Why this priority**: Prevents stock hoarding and ensures fairness. Critical for maintaining the "first-come-first-served" promise to waiting users. Must work reliably at scale.

**Independent Test**: Can be tested by obtaining slots, waiting 30+ minutes, verifying automatic expiration, and confirming reclaimed slots return to available inventory.

**Acceptance Scenarios**:

1. **Given** a user obtained a slot 30 minutes ago without payment, **When** the expiration time passes, **Then** the slot status changes to "expired" and stock count increases by 1
2. **Given** a slot is about to expire in 5 minutes, **When** the system checks expiration status, **Then** a pre-expiration notification is sent to the user
3. **Given** multiple slots expire simultaneously, **When** reclamation runs, **Then** all expired slots are processed atomically without race conditions

---

### User Story 3 - Product Management by Admin (Priority: P3)

An admin logs into the system and creates a new product for tomorrow's 10:00 AM sale, specifying name, description, stock quantity, and sale date. The product appears in the upcoming sales list but cannot be purchased until the sale date.

**Why this priority**: Required for content management but can be initially handled with basic CRUD. Does not face the same concurrency challenges as P1/P2. Can be enhanced later with bulk operations.

**Independent Test**: Can be tested by admin creating/updating/deleting products and verifying they appear correctly in buyer views with correct sale schedules.

**Acceptance Scenarios**:

1. **Given** an admin creates a product with sale_date of tomorrow 10:00 AM, **When** a buyer views the product list, **Then** the product appears with "upcoming" status and countdown timer
2. **Given** a product exists with 100 stock, **When** admin updates stock to 150, **Then** 50 additional PurchaseSlots become available
3. **Given** a product has active PurchaseSlots, **When** admin attempts to delete it, **Then** the system prevents deletion and shows error message

---

### User Story 4 - Payment Processing (Priority: P4)

A buyer with a valid PurchaseSlot navigates to the payment page, enters payment information, and submits. Upon successful payment, the purchase is confirmed, the slot is consumed, and the buyer receives a confirmation notification.

**Why this priority**: Advanced feature. Core value is in slot acquisition; payment can initially be stubbed or simplified. Full payment integration can be deferred without breaking the core experience.

**Independent Test**: Can be tested by obtaining a slot, submitting payment, and verifying purchase confirmation with slot consumption.

**Acceptance Scenarios**:

1. **Given** a user has a valid unexpired slot, **When** they complete payment, **Then** purchase status is "confirmed" and slot is marked as consumed
2. **Given** a user's slot expired, **When** they attempt payment, **Then** payment is rejected with "slot expired" error
3. **Given** payment processing fails, **When** system retries, **Then** user's slot expiration timer is paused during retry window

---

### User Story 5 - My Purchase Slots View (Priority: P5)

A buyer logs in and views all their current PurchaseSlots, seeing which ones are active (with time remaining), which expired, and which were completed with payment.

**Why this priority**: Important for user experience but not critical path. Users can manage slots through product detail pages initially. Centralized view is a convenience enhancement.

**Independent Test**: Can be tested by user obtaining multiple slots across different products and verifying all appear in the list with correct statuses.

**Acceptance Scenarios**:

1. **Given** a user has 3 active slots and 2 expired slots, **When** they view "My Slots" page, **Then** they see all 5 slots grouped by status with time remaining for active ones
2. **Given** a slot is expiring in 1 minute, **When** user views the list, **Then** the slot is highlighted with urgent status indicator
3. **Given** a user has no slots, **When** they view "My Slots" page, **Then** they see empty state with link to browse products

---

### Edge Cases

- What happens when a user obtains a slot but the product is deleted by admin before payment?
- How does the system handle clock skew across distributed servers for fair first-come processing?
- What happens if two users click "Purchase" at the exact same millisecond for the last slot?
- How does the system handle slot reclamation when database connection fails during expiration processing?
- What happens when payment is processing at the exact moment a slot expires?
- How does the system prevent duplicate slot acquisition by the same user for the same product?
- What happens when admin changes sale_date after users have already set reminders?
- How does the system handle partial stock updates (e.g., admin decreases stock below already-granted slots)?

## Requirements *(mandatory)*

### Functional Requirements

#### Product Management

- **FR-001**: Admin users MUST be able to create products with required fields: name, description, stock quantity, and sale_date
- **FR-002**: Admin users MUST be able to update product details (name, description) without affecting existing PurchaseSlots
- **FR-003**: Admin users MUST be able to increase product stock, which creates additional available PurchaseSlots
- **FR-004**: System MUST prevent product deletion if any active (non-expired, non-completed) PurchaseSlots exist for that product
- **FR-005**: System MUST prevent sale_date changes once a product has any PurchaseSlots (active, expired, or completed)
- **FR-006**: Buyers MUST be able to view all products with their current status (upcoming, on-sale, sold-out)
- **FR-007**: Product detail page MUST show current stock availability and sale timing

#### PurchaseSlot Acquisition

- **FR-008**: System MUST process slot acquisition requests in strict arrival-time order to ensure fairness
- **FR-009**: System MUST grant exactly N slots for a product with N stock, never exceeding this limit
- **FR-010**: System MUST prevent the same user from obtaining multiple slots for the same product
- **FR-011**: Users MUST receive immediate response indicating success or failure (sold-out) when requesting a slot
- **FR-012**: Each PurchaseSlot MUST be valid for exactly 30 minutes from acquisition time
- **FR-013**: System MUST handle 100,000 concurrent slot requests without service degradation
- **FR-014**: Slot acquisition response time p99 MUST be under 100ms

#### PurchaseSlot Lifecycle

- **FR-015**: System MUST automatically expire PurchaseSlots after 30 minutes if payment is not completed
- **FR-016**: System MUST reclaim expired slots and return stock to available inventory atomically
- **FR-017**: System MUST send notification to users 5 minutes before their slot expires
- **FR-018**: Users MUST be able to view all their PurchaseSlots with status (active, expired, completed)
- **FR-019**: Users MUST be able to view time remaining for active slots
- **FR-020**: System MUST log all slot state transitions (acquired, expired, completed) for audit purposes

#### Payment Processing

- **FR-021**: System MUST only allow payment for valid (unexpired, not-yet-completed) PurchaseSlots
- **FR-022**: System MUST prevent payment for expired PurchaseSlots with clear error message
- **FR-023**: Payment completion MUST atomically change purchase status to "confirmed" and mark slot as consumed
- **FR-024**: System MUST support payment timeout/failure scenarios with appropriate error handling
- **FR-025**: Payment processing p99 latency MUST be under 500ms
- **FR-026**: System MUST send purchase confirmation notification after successful payment

#### Concurrency & Reliability

- **FR-027**: All slot acquisition operations MUST be atomic to prevent race conditions
- **FR-028**: All slot expiration and reclamation operations MUST be atomic
- **FR-029**: System MUST use distributed state management (not in-memory state) for slot availability
- **FR-030**: System MUST log all critical business events (slot acquisition, expiration, payment) with trace IDs
- **FR-031**: System MUST provide health check endpoints for monitoring
- **FR-032**: System error rate MUST be below 0.1% under peak load

### Key Entities

- **Product**: Represents an item available for purchase. Attributes: unique ID, name, description, stock quantity (total available), sale_date (when purchase becomes available), created/updated timestamps. Relationships: has many PurchaseSlots.

- **PurchaseSlot**: Represents a buyer's temporary right to purchase a specific product. Attributes: unique ID, product reference, user reference, acquisition timestamp, expiration timestamp (30 min from acquisition), status (active/expired/completed). Relationships: belongs to one Product, belongs to one User, may have one Purchase.

- **Purchase**: Represents a completed payment transaction. Attributes: unique ID, PurchaseSlot reference, user reference, payment amount, payment method, payment status (pending/success/failed), confirmation timestamp. Relationships: belongs to one PurchaseSlot.

- **User**: Buyer account (defined in Auth domain). Product domain references User ID for slot ownership and purchase tracking.

- **Admin**: Administrative account (defined in Auth domain) with elevated permissions for product management.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: System successfully processes 100,000 concurrent slot acquisition requests at 10:00 AM daily peak without service failure
- **SC-002**: 99% of slot acquisition requests receive response in under 100ms
- **SC-003**: Exactly N users obtain slots for N stock items with zero over-allocation or under-allocation
- **SC-004**: All slots expire automatically within 30 seconds of their 30-minute deadline
- **SC-005**: 95% of users who obtain slots receive pre-expiration notification at 5-minute mark
- **SC-006**: Payment processing completes in under 500ms for 99% of transactions
- **SC-007**: System maintains less than 0.1% error rate during peak load
- **SC-008**: Zero duplicate slot allocations occur for the same user-product combination
- **SC-009**: Admin can create and publish new products in under 2 minutes
- **SC-010**: Users can view their slot status and time remaining in under 1 second

### Business Success Criteria

- **SC-011**: Fair first-come-first-served processing verified through audit logs showing correlation between request arrival time and slot acquisition success
- **SC-012**: All critical business events (slot acquisition, expiration, payment) are traceable through distributed trace IDs for troubleshooting

## Assumptions

- User authentication and authorization are handled by the Auth domain and are assumed to be working correctly
- Notification delivery is handled by the Notification domain via Kafka events
- Payment processing details (payment gateway integration, PCI compliance) will be defined in payment implementation phase
- Admin user management and role assignment are handled by the Auth domain
- All inter-domain communication happens asynchronously via Kafka events (no synchronous HTTP calls between domains)
- Database selection for Product domain persistence will be determined during implementation planning
- Time synchronization across distributed servers is handled by infrastructure (NTP)
- Rate limiting (10 requests per second per user) is enforced at API gateway level, not in Product domain logic

## Out of Scope

- Payment gateway integration details (deferred to payment implementation)
- Email template design for notifications (handled by Notification domain)
- Admin user interface design (focusing on API contracts only)
- Analytics and reporting dashboards
- Product recommendation or search features
- Multi-currency or internationalization support
- Refund or cancellation workflows
- Product reviews or ratings
- Inventory forecasting or reordering
