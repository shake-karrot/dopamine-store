# Quickstart Guide: Product Domain

**Feature**: Product Domain API & Business Logic
**Last Updated**: 2026-01-05

## Prerequisites

Before starting local development, ensure you have the following installed:

| Tool | Version | Purpose | Installation |
|------|---------|---------|--------------|
| **JDK** | 21+ | Kotlin runtime | `brew install openjdk@21` (macOS) |
| **Gradle** | 8.5+ | Build tool | Included via Gradle Wrapper (`./gradlew`) |
| **Docker** | 24.0+ | Infrastructure (PostgreSQL, Redis, Kafka) | [Docker Desktop](https://www.docker.com/products/docker-desktop) |
| **Docker Compose** | 2.20+ | Multi-container orchestration | Included with Docker Desktop |
| **IntelliJ IDEA** | 2023.3+ | IDE (recommended for Kotlin) | [Download](https://www.jetbrains.com/idea/download/) |
| **Postman** (optional) | Latest | API testing | [Download](https://www.postman.com/downloads/) |

**Verify installations**:
```bash
java --version   # Should show 21.x.x
./gradlew --version  # Should show Gradle 8.5+
docker --version  # Should show 24.0.x+
docker compose version  # Should show 2.20.x+
```

---

## 1. Clone and Setup

```bash
# Clone repository
git clone https://github.com/dopamine-store/dopamine-store.git
cd dopamine-store

# Checkout product feature branch
git checkout product/002-product-api-spec

# Navigate to product domain
cd product/
```

---

## 2. Start Infrastructure

The Product domain depends on PostgreSQL, Redis, and Kafka. Use Docker Compose to start all dependencies:

```bash
# From product/ directory
docker compose up -d

# Verify services are running
docker compose ps

# Expected output:
# NAME                COMMAND                  SERVICE     STATUS      PORTS
# product-postgres    "docker-entrypoint.s…"   postgres    Up 10s      0.0.0.0:5432->5432/tcp
# product-redis       "redis-server /usr/l…"   redis       Up 10s      0.0.0.0:6379->6379/tcp
# product-kafka       "start-kafka.sh"         kafka       Up 10s      0.0.0.0:9092->9092/tcp
# product-zookeeper   "/docker-entrypoint.…"   zookeeper   Up 15s      2181/tcp
```

**`docker-compose.yml`** (for reference):
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: product-postgres
    environment:
      POSTGRES_DB: product
      POSTGRES_USER: product_service
      POSTGRES_PASSWORD: local_dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    container_name: product-redis
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: product-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: product-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

volumes:
  postgres_data:
  redis_data:
```

**Verify connectivity**:
```bash
# PostgreSQL
docker exec -it product-postgres psql -U product_service -d product -c "SELECT version();"

# Redis
docker exec -it product-redis redis-cli PING
# Expected: PONG

# Kafka (list topics)
docker exec -it product-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

---

## 3. Database Migration

Run Flyway migrations to create schema:

```bash
# From product/ directory
./gradlew flywayMigrate

# Expected output:
# Flyway Community Edition 9.x.x
# Successfully applied 3 migrations to schema "public", now at version v1.2.0
```

**Verify tables created**:
```bash
docker exec -it product-postgres psql -U product_service -d product -c "\dt"

# Expected tables:
# products
# purchase_slots
# purchases
# slot_audit_log
# flyway_schema_history
```

---

## 4. Build the Project

```bash
# Build all modules (core, app, worker, adapter)
./gradlew build

# Expected output:
# > Task :core:build
# > Task :adapter:build
# > Task :app:build
# > Task :worker:build
# BUILD SUCCESSFUL in 15s
```

**Build artifacts**:
- `app/build/libs/app-0.0.1-SNAPSHOT.jar` - REST API server
- `worker/build/libs/worker-0.0.1-SNAPSHOT.jar` - Scheduled jobs

---

## 5. Run the Application

### Option A: Run with Gradle (Development)

```bash
# Terminal 1: Start app module (REST API)
./gradlew :app:bootRun

# Terminal 2: Start worker module (Expiration jobs)
./gradlew :worker:bootRun
```

**Expected logs** (app module):
```
2026-01-05 10:00:00.123  INFO 12345 --- [main] c.d.product.Application : Started Application in 3.456 seconds
2026-01-05 10:00:00.234  INFO 12345 --- [main] o.s.b.w.e.netty.NettyWebServer : Netty started on port 8080
```

**Expected logs** (worker module):
```
2026-01-05 10:00:00.456  INFO 12346 --- [main] c.d.product.WorkerApplication : Started WorkerApplication in 2.345 seconds
2026-01-05 10:01:00.789  INFO 12346 --- [scheduling-1] c.d.product.job.SlotExpirationJob : Processed 0 expired slots
```

### Option B: Run with JAR (Production-like)

```bash
# Build JARs
./gradlew bootJar

# Run app
java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar &

# Run worker
java -jar worker/build/libs/worker-0.0.1-SNAPSHOT.jar &
```

---

## 6. Verify Health

```bash
# Check API health
curl http://localhost:8080/product/v1/health

# Expected response:
{
  "status": "UP",
  "timestamp": "2026-01-05T10:00:00Z",
  "components": {
    "database": {"status": "UP"},
    "redis": {"status": "UP"},
    "kafka": {"status": "UP"}
  }
}
```

---

## 7. Seed Test Data

Create test products and users (requires Auth domain JWT for admin actions):

```bash
# Generate admin JWT (use Auth domain API or mock for local dev)
export ADMIN_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Create a test product
curl -X POST http://localhost:8080/product/v1/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Limited Edition Sneakers",
    "description": "Only 100 pairs available worldwide",
    "stock": 100,
    "saleDate": "2026-01-06T10:00:00Z"
  }'

# Expected response (201 Created):
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Limited Edition Sneakers",
  "description": "Only 100 pairs available worldwide",
  "stock": 100,
  "initialStock": 100,
  "saleDate": "2026-01-06T10:00:00Z",
  "status": "UPCOMING",
  "createdAt": "2026-01-05T10:05:00Z",
  "updatedAt": "2026-01-05T10:05:00Z"
}
```

**Alternative: SQL Seed Script**

```sql
-- Insert test product directly
INSERT INTO products (id, name, description, stock, initial_stock, sale_date, created_by)
VALUES (
  '123e4567-e89b-12d3-a456-426614174000',
  'Limited Edition Sneakers',
  'Only 100 pairs available worldwide',
  100,
  100,
  NOW() + INTERVAL '1 day',
  'admin-user-id'
);
```

---

## 8. Test API Endpoints

### 8.1 List Products (No Auth)

```bash
curl http://localhost:8080/product/v1/products?status=ON_SALE

# Expected response (200 OK):
{
  "products": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "name": "Limited Edition Sneakers",
      "stock": 100,
      "status": "ON_SALE",
      ...
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 8.2 Acquire Purchase Slot (Buyer Auth)

```bash
# Generate buyer JWT
export BUYER_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

curl -X POST http://localhost:8080/product/v1/slots/acquire \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "123e4567-e89b-12d3-a456-426614174000"
  }'

# Expected response (201 Created):
{
  "id": "slot-uuid-1234",
  "productId": "123e4567-e89b-12d3-a456-426614174000",
  "productName": "Limited Edition Sneakers",
  "userId": "buyer-user-id",
  "acquisitionTimestamp": "2026-01-05T10:10:00Z",
  "expirationTimestamp": "2026-01-05T10:40:00Z",
  "status": "ACTIVE",
  "remainingTimeSeconds": 1800,
  "createdAt": "2026-01-05T10:10:00Z"
}
```

### 8.3 Initiate Payment (Buyer Auth)

```bash
curl -X POST http://localhost:8080/product/v1/payments \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "purchaseSlotId": "slot-uuid-1234",
    "idempotencyKey": "payment-idempotency-key-1",
    "amount": 259000.00,
    "currency": "KRW",
    "paymentMethod": "CARD"
  }'

# Expected response (201 Created):
{
  "id": "purchase-uuid-5678",
  "purchaseSlotId": "slot-uuid-1234",
  "paymentId": "gateway-payment-id-abc",
  "paymentStatus": "PENDING",
  "amount": 259000.00,
  "currency": "KRW",
  "paymentMethod": "CARD",
  "createdAt": "2026-01-05T10:12:00Z",
  "updatedAt": "2026-01-05T10:12:00Z"
}
```

---

## 9. Monitor Redis State

```bash
# Check product stock
docker exec -it product-redis redis-cli GET "product:123e4567-e89b-12d3-a456-426614174000:stock"
# Expected: "99" (after 1 slot acquisition)

# Check slot queue (sorted by timestamp)
docker exec -it product-redis redis-cli ZRANGE "product:123e4567-e89b-12d3-a456-426614174000:queue" 0 -1 WITHSCORES
# Expected: "buyer-user-id" "1704456600000"

# Check duplicate guard
docker exec -it product-redis redis-cli EXISTS "user:buyer-user-id:product:123e4567-e89b-12d3-a456-426614174000"
# Expected: "1" (exists)
```

---

## 10. Monitor Kafka Events

```bash
# View all topics
docker exec -it product-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consume SLOT_ACQUIRED events
docker exec -it product-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic product.slot-acquired \
  --from-beginning

# Expected output (Avro serialized, shown as hex):
# {"slotId":"slot-uuid-1234","userId":"buyer-user-id",...}
```

---

## 11. Run Tests

### Unit Tests (Per Module)

```bash
# Test core business logic
./gradlew :core:test

# Test adapter implementations
./gradlew :adapter:test

# Test app controllers
./gradlew :app:test

# Run all tests
./gradlew test
```

### Integration Tests

```bash
# Start test containers (PostgreSQL, Redis)
./gradlew :integration:test

# Expected output:
# SlotAcquisitionFlowTest > acquireSlotSuccessfully() PASSED
# SlotExpirationFlowTest > slotExpiresAfter30Minutes() PASSED
# PaymentFlowTest > processPaymentForActiveSlot() PASSED
```

### Load Test (k6)

```bash
# Install k6 (if not already)
brew install k6  # macOS

# Run 100K RPS slot acquisition test (requires 4 k6 instances - see research.md)
k6 run --vus 1000 --duration 5m tests/load/slot_acquisition_100k_rps.js

# Expected metrics:
# http_req_duration..............: avg=45ms  min=10ms  med=40ms  max=200ms  p(90)=80ms  p(99)=95ms
# http_reqs......................: 100000 25000/s
# errors.........................: 0.05% ✓
```

---

## 12. Troubleshooting

### Issue: `Connection refused` to PostgreSQL

**Cause**: Docker container not started or port conflict

**Solution**:
```bash
# Check if container is running
docker ps | grep product-postgres

# Check logs
docker logs product-postgres

# Restart container
docker compose restart postgres
```

### Issue: Redis `DENIED` errors

**Cause**: Redis requires password but none configured in local dev

**Solution**: Update `application-local.yml`:
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: null  # No password for local dev
```

### Issue: Kafka topic not found

**Cause**: Topics not auto-created

**Solution**:
```bash
# Manually create topics
docker exec -it product-kafka kafka-topics --bootstrap-server localhost:9092 --create \
  --topic product.slot-acquired \
  --partitions 3 \
  --replication-factor 1
```

### Issue: Tests fail with "Port 8080 already in use"

**Cause**: App module still running from previous session

**Solution**:
```bash
# Kill process on port 8080
lsof -ti:8080 | xargs kill -9

# Or use different port for tests
./gradlew test -Dserver.port=0  # Random port
```

---

## 13. IDE Setup (IntelliJ IDEA)

1. **Open Project**: `File > Open > /path/to/dopamine-store/product`
2. **Import Gradle**: IntelliJ should auto-detect `build.gradle.kts` and import
3. **Enable Kotlin**: Install Kotlin plugin if not already installed
4. **Code Style**: `Settings > Editor > Code Style > Kotlin > Set from > Kotlin style guide`
5. **Run Configurations**:
   - **App Module**: Main class = `com.dopaminestore.product.ApplicationKt`, Module = `app.main`
   - **Worker Module**: Main class = `com.dopaminestore.product.WorkerApplicationKt`, Module = `worker.main`

**Recommended Plugins**:
- Kotlin
- Avro and Parquet (for `.avsc` schema viewing)
- OpenAPI (Swagger) Editor (for `openapi.yaml` validation)

---

## 14. Environment Variables

**Local Development** (`application-local.yml`):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/product
    username: product_service
    password: local_dev_password
  redis:
    host: localhost
    port: 6379
  kafka:
    bootstrap-servers: localhost:9092

auth:
  jwt:
    secret: local-dev-secret-key-change-in-production
    issuer: auth-domain-local

logging:
  level:
    com.dopaminestore.product: DEBUG
    org.springframework.data.r2dbc: DEBUG
```

**Production** (environment variables):
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://product-db.prod:5432/product
SPRING_DATASOURCE_USERNAME=product_service
SPRING_DATASOURCE_PASSWORD=${PRODUCT_DB_PASSWORD}  # From secrets manager
SPRING_REDIS_HOST=product-redis.prod
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-broker-1.prod:9092,kafka-broker-2.prod:9092
AUTH_JWT_SECRET=${JWT_SECRET}  # From secrets manager
```

---

## 15. Next Steps

After verifying local setup:

1. **Review Contracts**: See `contracts/openapi.yaml` for full API documentation
2. **Review Data Model**: See `data-model.md` for entity schemas and state machines
3. **Review Research**: See `research.md` for architecture decisions
4. **Implement P1**: Start with slot acquisition (critical path) - see `/speckit.tasks`
5. **Load Test**: Validate 100K RPS target before production deployment

---

## Quick Command Reference

| Task | Command |
|------|---------|
| Start infrastructure | `docker compose up -d` |
| Stop infrastructure | `docker compose down` |
| Build project | `./gradlew build` |
| Run app module | `./gradlew :app:bootRun` |
| Run worker module | `./gradlew :worker:bootRun` |
| Run tests | `./gradlew test` |
| Check health | `curl http://localhost:8080/product/v1/health` |
| View logs (app) | `./gradlew :app:bootRun --console=plain` |
| View Redis state | `docker exec -it product-redis redis-cli` |
| View Kafka topics | `docker exec -it product-kafka kafka-topics --list --bootstrap-server localhost:9092` |
| DB migration | `./gradlew flywayMigrate` |
| Load test | `k6 run tests/load/slot_acquisition_100k_rps.js` |

---

**Document Version**: 1.0
**Last Updated**: 2026-01-05
**Maintainer**: Product Team

For questions or issues, contact: product-team@dopaminestore.com
