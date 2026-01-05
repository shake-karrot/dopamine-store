package com.dopaminestore.product.adapter.persistence

import com.dopaminestore.product.core.domain.PurchaseSlot
import com.dopaminestore.product.core.domain.value.ReclaimStatus
import com.dopaminestore.product.core.domain.value.SlotStatus
import com.dopaminestore.product.core.port.PurchaseSlotRepository
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * R2DBC implementation of PurchaseSlotRepository.
 *
 * Maps between PurchaseSlot domain entity and purchase_slots database table.
 * Uses Spring Data R2DBC for reactive database operations.
 *
 * **Performance Optimizations**:
 * - Unique partial index on (user_id, product_id) for ACTIVE slots (duplicate prevention)
 * - Index on expiration_timestamp for scheduled expiration jobs
 * - Index on (product_id, status) for active slot counting
 */
@Repository
class PurchaseSlotRepositoryImpl(
    private val databaseClient: DatabaseClient
) : PurchaseSlotRepository {

    override fun save(slot: PurchaseSlot): Mono<PurchaseSlot> {
        return existsById(slot.id)
            .flatMap { exists ->
                if (exists) {
                    updateSlot(slot)
                } else {
                    insertSlot(slot)
                }
            }
    }

    private fun insertSlot(slot: PurchaseSlot): Mono<PurchaseSlot> {
        val sql = """
            INSERT INTO purchase_slots (
                id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                status, reclaim_status, trace_id, created_at, updated_at
            ) VALUES (
                :id, :product_id, :user_id, :acquisition_timestamp, :expiration_timestamp,
                :status, :reclaim_status, :trace_id, :created_at, :updated_at
            )
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", slot.id)
            .bind("product_id", slot.productId)
            .bind("user_id", slot.userId)
            .bind("acquisition_timestamp", slot.acquisitionTimestamp)
            .bind("expiration_timestamp", slot.expirationTimestamp)
            .bind("status", slot.status.name)
            .bindNullable("reclaim_status", slot.reclaimStatus?.name)
            .bind("trace_id", slot.traceId)
            .bind("created_at", slot.createdAt)
            .bind("updated_at", slot.updatedAt)
            .then()
            .thenReturn(slot)
    }

    private fun updateSlot(slot: PurchaseSlot): Mono<PurchaseSlot> {
        val sql = """
            UPDATE purchase_slots SET
                status = :status,
                reclaim_status = :reclaim_status,
                updated_at = :updated_at
            WHERE id = :id
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", slot.id)
            .bind("status", slot.status.name)
            .bindNullable("reclaim_status", slot.reclaimStatus?.name)
            .bind("updated_at", Instant.now())
            .fetch()
            .rowsUpdated()
            .map { rowsUpdated ->
                if (rowsUpdated > 0) {
                    slot.copy(updatedAt = Instant.now())
                } else {
                    slot
                }
            }
    }

    private fun existsById(id: UUID): Mono<Boolean> {
        val sql = "SELECT EXISTS(SELECT 1 FROM purchase_slots WHERE id = :id)"
        return databaseClient.sql(sql)
            .bind("id", id)
            .map { row -> row.get(0, Boolean::class.javaObjectType) ?: false }
            .one()
    }

    override fun findById(id: UUID): Mono<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE id = :id
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", id)
            .map { row -> mapRowToSlot(row) }
            .one()
    }

    override fun findActiveSlotByUserAndProduct(userId: UUID, productId: UUID): Mono<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE user_id = :user_id
              AND product_id = :product_id
              AND status = 'ACTIVE'
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("user_id", userId)
            .bind("product_id", productId)
            .map { row -> mapRowToSlot(row) }
            .one()
    }

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

    override fun findByUserId(userId: UUID): Flux<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE user_id = :user_id
            ORDER BY acquisition_timestamp DESC
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("user_id", userId)
            .map { row -> mapRowToSlot(row) }
            .all()
    }

    override fun findByUserIdAndStatus(userId: UUID, status: SlotStatus): Flux<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE user_id = :user_id AND status = :status
            ORDER BY acquisition_timestamp DESC
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("user_id", userId)
            .bind("status", status.name)
            .map { row -> mapRowToSlot(row) }
            .all()
    }

    override fun findActiveSlotsByProduct(productId: UUID): Flux<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE product_id = :product_id AND status = 'ACTIVE'
            ORDER BY acquisition_timestamp ASC
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("product_id", productId)
            .map { row -> mapRowToSlot(row) }
            .all()
    }

    override fun countActiveSlotsByProduct(productId: UUID): Mono<Long> {
        val sql = """
            SELECT COUNT(*) FROM purchase_slots
            WHERE product_id = :product_id AND status = 'ACTIVE'
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("product_id", productId)
            .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
            .one()
    }

    override fun findExpiredSlots(now: Instant, limit: Int): Flux<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE status = 'ACTIVE'
              AND expiration_timestamp <= :now
            ORDER BY expiration_timestamp ASC
            LIMIT :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("now", now)
            .bind("limit", limit)
            .map { row -> mapRowToSlot(row) }
            .all()
    }

    override fun findSlotsExpiringSoon(
        minMinutes: Long,
        maxMinutes: Long,
        now: Instant
    ): Flux<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE status = 'ACTIVE'
              AND expiration_timestamp BETWEEN :min_time AND :max_time
            ORDER BY expiration_timestamp ASC
        """.trimIndent()

        val minTime = now.plusSeconds(minMinutes * 60)
        val maxTime = now.plusSeconds(maxMinutes * 60)

        return databaseClient.sql(sql)
            .bind("min_time", minTime)
            .bind("max_time", maxTime)
            .map { row -> mapRowToSlot(row) }
            .all()
    }

    override fun findByStatusWithPagination(
        status: SlotStatus,
        offset: Long,
        limit: Int
    ): Flux<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE status = :status
            ORDER BY acquisition_timestamp DESC
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("status", status.name)
            .bind("limit", limit)
            .bind("offset", offset)
            .map { row -> mapRowToSlot(row) }
            .all()
    }

    override fun countByStatus(status: SlotStatus): Mono<Long> {
        val sql = "SELECT COUNT(*) FROM purchase_slots WHERE status = :status"

        return databaseClient.sql(sql)
            .bind("status", status.name)
            .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
            .one()
    }

    override fun findByTraceId(traceId: String): Flux<PurchaseSlot> {
        val sql = """
            SELECT id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                   status, reclaim_status, trace_id, created_at, updated_at
            FROM purchase_slots
            WHERE trace_id = :trace_id
            ORDER BY acquisition_timestamp DESC
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("trace_id", traceId)
            .map { row -> mapRowToSlot(row) }
            .all()
    }

    override fun deleteById(id: UUID): Mono<Void> {
        val sql = "DELETE FROM purchase_slots WHERE id = :id"
        return databaseClient.sql(sql)
            .bind("id", id)
            .then()
    }

    override fun updateStatus(id: UUID, newStatus: SlotStatus, updatedAt: Instant): Mono<PurchaseSlot> {
        val sql = """
            UPDATE purchase_slots SET
                status = :status,
                updated_at = :updated_at
            WHERE id = :id
            RETURNING id, product_id, user_id, acquisition_timestamp, expiration_timestamp,
                      status, reclaim_status, trace_id, created_at, updated_at
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", id)
            .bind("status", newStatus.name)
            .bind("updated_at", updatedAt)
            .map { row -> mapRowToSlot(row) }
            .one()
    }

    /**
     * Map database row to PurchaseSlot domain entity.
     */
    private fun mapRowToSlot(row: io.r2dbc.spi.Readable): PurchaseSlot {
        val status = SlotStatus.valueOf(row.get("status", String::class.java)!!)
        val reclaimStatusStr = row.get("reclaim_status", String::class.java)
        val reclaimStatus = reclaimStatusStr?.let { ReclaimStatus.valueOf(it) }

        return PurchaseSlot(
            id = row.get("id", UUID::class.java)!!,
            productId = row.get("product_id", UUID::class.java)!!,
            userId = row.get("user_id", UUID::class.java)!!,
            acquisitionTimestamp = row.get("acquisition_timestamp", Instant::class.java)!!,
            expirationTimestamp = row.get("expiration_timestamp", Instant::class.java)!!,
            status = status,
            reclaimStatus = reclaimStatus,
            traceId = row.get("trace_id", String::class.java)!!,
            createdAt = row.get("created_at", Instant::class.java)!!,
            updatedAt = row.get("updated_at", Instant::class.java)!!
        )
    }

    /**
     * Extension function to bind nullable values.
     */
    private fun DatabaseClient.GenericExecuteSpec.bindNullable(name: String, value: Any?): DatabaseClient.GenericExecuteSpec {
        return if (value != null) {
            this.bind(name, value)
        } else {
            this.bindNull(name, String::class.java)
        }
    }
}
