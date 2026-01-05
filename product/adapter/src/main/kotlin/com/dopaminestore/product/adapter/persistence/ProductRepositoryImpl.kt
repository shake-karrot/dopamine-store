package com.dopaminestore.product.adapter.persistence

import com.dopaminestore.product.core.domain.Product
import com.dopaminestore.product.core.domain.value.ProductStatus
import com.dopaminestore.product.core.port.ProductRepository
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * R2DBC implementation of ProductRepository.
 *
 * Maps between Product domain entity and products database table.
 * Uses Spring Data R2DBC for reactive database operations.
 *
 * **Performance Optimizations**:
 * - Indexed queries on sale_date and stock
 * - Batch operations support
 * - Connection pooling (configured in R2DBC config)
 */
@Repository
class ProductRepositoryImpl(
    private val databaseClient: DatabaseClient
) : ProductRepository {

    override fun save(product: Product): Mono<Product> {
        return existsById(product.id)
            .flatMap { exists ->
                if (exists) {
                    updateProduct(product)
                } else {
                    insertProduct(product)
                }
            }
    }

    private fun insertProduct(product: Product): Mono<Product> {
        val sql = """
            INSERT INTO products (
                id, name, description, stock, initial_stock,
                sale_date, price, created_at, updated_at, created_by
            ) VALUES (
                :id, :name, :description, :stock, :initial_stock,
                :sale_date, :price, :created_at, :updated_at, :created_by
            )
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", product.id)
            .bind("name", product.name)
            .bind("description", product.description)
            .bind("stock", product.stock)
            .bind("initial_stock", product.initialStock)
            .bind("sale_date", product.saleDate)
            .bind("price", product.price)
            .bind("created_at", product.createdAt)
            .bind("updated_at", product.updatedAt)
            .bind("created_by", product.createdBy)
            .then()
            .thenReturn(product)
    }

    private fun updateProduct(product: Product): Mono<Product> {
        val sql = """
            UPDATE products SET
                name = :name,
                description = :description,
                stock = :stock,
                price = :price,
                updated_at = :updated_at
            WHERE id = :id
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", product.id)
            .bind("name", product.name)
            .bind("description", product.description)
            .bind("stock", product.stock)
            .bind("price", product.price)
            .bind("updated_at", Instant.now())
            .fetch()
            .rowsUpdated()
            .map { rowsUpdated ->
                if (rowsUpdated > 0) {
                    product.copy(updatedAt = Instant.now())
                } else {
                    product
                }
            }
    }

    override fun findById(id: UUID): Mono<Product> {
        val sql = """
            SELECT id, name, description, stock, initial_stock,
                   sale_date, price, created_at, updated_at, created_by
            FROM products
            WHERE id = :id
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", id)
            .map { row ->
                Product(
                    id = row.get("id", UUID::class.java)!!,
                    name = row.get("name", String::class.java)!!,
                    description = row.get("description", String::class.java)!!,
                    stock = row.get("stock", Integer::class.java)!!.toInt(),
                    initialStock = row.get("initial_stock", Integer::class.java)!!.toInt(),
                    saleDate = row.get("sale_date", Instant::class.java)!!,
                    price = row.get("price", BigDecimal::class.java) ?: BigDecimal.ZERO,
                    createdAt = row.get("created_at", Instant::class.java)!!,
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    createdBy = row.get("created_by", UUID::class.java)!!
                )
            }
            .one()
    }

    override fun findAll(): Flux<Product> {
        val sql = """
            SELECT id, name, description, stock, initial_stock,
                   sale_date, price, created_at, updated_at, created_by
            FROM products
            ORDER BY created_at DESC
        """.trimIndent()

        return databaseClient.sql(sql)
            .map { row ->
                Product(
                    id = row.get("id", UUID::class.java)!!,
                    name = row.get("name", String::class.java)!!,
                    description = row.get("description", String::class.java)!!,
                    stock = row.get("stock", Integer::class.java)!!.toInt(),
                    initialStock = row.get("initial_stock", Integer::class.java)!!.toInt(),
                    saleDate = row.get("sale_date", Instant::class.java)!!,
                    price = row.get("price", BigDecimal::class.java) ?: BigDecimal.ZERO,
                    createdAt = row.get("created_at", Instant::class.java)!!,
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    createdBy = row.get("created_by", UUID::class.java)!!
                )
            }
            .all()
    }

    override fun findByStatus(status: ProductStatus, now: Instant): Flux<Product> {
        val sql = when (status) {
            ProductStatus.UPCOMING -> """
                SELECT id, name, description, stock, initial_stock,
                       sale_date, price, created_at, updated_at, created_by
                FROM products
                WHERE sale_date > :now
                ORDER BY sale_date ASC
            """.trimIndent()
            ProductStatus.ON_SALE -> """
                SELECT id, name, description, stock, initial_stock,
                       sale_date, price, created_at, updated_at, created_by
                FROM products
                WHERE sale_date <= :now AND stock > 0
                ORDER BY sale_date DESC
            """.trimIndent()
            ProductStatus.SOLD_OUT -> """
                SELECT id, name, description, stock, initial_stock,
                       sale_date, price, created_at, updated_at, created_by
                FROM products
                WHERE stock = 0
                ORDER BY sale_date DESC
            """.trimIndent()
        }

        return databaseClient.sql(sql)
            .bind("now", now)
            .map { row ->
                Product(
                    id = row.get("id", UUID::class.java)!!,
                    name = row.get("name", String::class.java)!!,
                    description = row.get("description", String::class.java)!!,
                    stock = row.get("stock", Integer::class.java)!!.toInt(),
                    initialStock = row.get("initial_stock", Integer::class.java)!!.toInt(),
                    saleDate = row.get("sale_date", Instant::class.java)!!,
                    price = row.get("price", BigDecimal::class.java) ?: BigDecimal.ZERO,
                    createdAt = row.get("created_at", Instant::class.java)!!,
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    createdBy = row.get("created_by", UUID::class.java)!!
                )
            }
            .all()
    }

    override fun findBySaleDateBetween(startDate: Instant, endDate: Instant): Flux<Product> {
        val sql = """
            SELECT id, name, description, stock, initial_stock,
                   sale_date, price, created_at, updated_at, created_by
            FROM products
            WHERE sale_date BETWEEN :start_date AND :end_date
            ORDER BY sale_date ASC
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("start_date", startDate)
            .bind("end_date", endDate)
            .map { row ->
                Product(
                    id = row.get("id", UUID::class.java)!!,
                    name = row.get("name", String::class.java)!!,
                    description = row.get("description", String::class.java)!!,
                    stock = row.get("stock", Integer::class.java)!!.toInt(),
                    initialStock = row.get("initial_stock", Integer::class.java)!!.toInt(),
                    saleDate = row.get("sale_date", Instant::class.java)!!,
                    price = row.get("price", BigDecimal::class.java) ?: BigDecimal.ZERO,
                    createdAt = row.get("created_at", Instant::class.java)!!,
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    createdBy = row.get("created_by", UUID::class.java)!!
                )
            }
            .all()
    }

    override fun findWithPagination(
        status: ProductStatus?,
        offset: Long,
        limit: Int
    ): Flux<Product> {
        val sql = if (status != null) {
            val statusCondition = when (status) {
                ProductStatus.UPCOMING -> "sale_date > NOW()"
                ProductStatus.ON_SALE -> "sale_date <= NOW() AND stock > 0"
                ProductStatus.SOLD_OUT -> "stock = 0"
            }
            """
                SELECT id, name, description, stock, initial_stock,
                       sale_date, price, created_at, updated_at, created_by
                FROM products
                WHERE $statusCondition
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
            """.trimIndent()
        } else {
            """
                SELECT id, name, description, stock, initial_stock,
                       sale_date, price, created_at, updated_at, created_by
                FROM products
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
            """.trimIndent()
        }

        return databaseClient.sql(sql)
            .bind("limit", limit)
            .bind("offset", offset)
            .map { row ->
                Product(
                    id = row.get("id", UUID::class.java)!!,
                    name = row.get("name", String::class.java)!!,
                    description = row.get("description", String::class.java)!!,
                    stock = row.get("stock", Integer::class.java)!!.toInt(),
                    initialStock = row.get("initial_stock", Integer::class.java)!!.toInt(),
                    saleDate = row.get("sale_date", Instant::class.java)!!,
                    price = row.get("price", BigDecimal::class.java) ?: BigDecimal.ZERO,
                    createdAt = row.get("created_at", Instant::class.java)!!,
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    createdBy = row.get("created_by", UUID::class.java)!!
                )
            }
            .all()
    }

    override fun count(): Mono<Long> {
        val sql = "SELECT COUNT(*) FROM products"
        return databaseClient.sql(sql)
            .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
            .one()
    }

    override fun countByStatus(status: ProductStatus, now: Instant): Mono<Long> {
        val sql = when (status) {
            ProductStatus.UPCOMING -> """
                SELECT COUNT(*) FROM products WHERE sale_date > :now
            """.trimIndent()
            ProductStatus.ON_SALE -> """
                SELECT COUNT(*) FROM products WHERE sale_date <= :now AND stock > 0
            """.trimIndent()
            ProductStatus.SOLD_OUT -> """
                SELECT COUNT(*) FROM products WHERE stock = 0
            """.trimIndent()
        }

        return databaseClient.sql(sql)
            .bind("now", now)
            .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
            .one()
    }

    override fun existsById(id: UUID): Mono<Boolean> {
        val sql = "SELECT EXISTS(SELECT 1 FROM products WHERE id = :id)"
        return databaseClient.sql(sql)
            .bind("id", id)
            .map { row -> row.get(0, Boolean::class.javaObjectType) ?: false }
            .one()
    }

    override fun deleteById(id: UUID): Mono<Void> {
        val sql = "DELETE FROM products WHERE id = :id"
        return databaseClient.sql(sql)
            .bind("id", id)
            .then()
    }

    override fun findByCreatedBy(createdBy: UUID): Flux<Product> {
        val sql = """
            SELECT id, name, description, stock, initial_stock,
                   sale_date, price, created_at, updated_at, created_by
            FROM products
            WHERE created_by = :created_by
            ORDER BY created_at DESC
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("created_by", createdBy)
            .map { row ->
                Product(
                    id = row.get("id", UUID::class.java)!!,
                    name = row.get("name", String::class.java)!!,
                    description = row.get("description", String::class.java)!!,
                    stock = row.get("stock", Integer::class.java)!!.toInt(),
                    initialStock = row.get("initial_stock", Integer::class.java)!!.toInt(),
                    saleDate = row.get("sale_date", Instant::class.java)!!,
                    price = row.get("price", BigDecimal::class.java) ?: BigDecimal.ZERO,
                    createdAt = row.get("created_at", Instant::class.java)!!,
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    createdBy = row.get("created_by", UUID::class.java)!!
                )
            }
            .all()
    }

    override fun updateStock(id: UUID, newStock: Int): Mono<Product> {
        require(newStock >= 0) { "Stock cannot be negative: $newStock" }

        val sql = """
            UPDATE products SET
                stock = :stock,
                updated_at = NOW()
            WHERE id = :id
            RETURNING id, name, description, stock, initial_stock,
                      sale_date, price, created_at, updated_at, created_by
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", id)
            .bind("stock", newStock)
            .map { row ->
                Product(
                    id = row.get("id", UUID::class.java)!!,
                    name = row.get("name", String::class.java)!!,
                    description = row.get("description", String::class.java)!!,
                    stock = row.get("stock", Integer::class.java)!!.toInt(),
                    initialStock = row.get("initial_stock", Integer::class.java)!!.toInt(),
                    saleDate = row.get("sale_date", Instant::class.java)!!,
                    price = row.get("price", BigDecimal::class.java) ?: BigDecimal.ZERO,
                    createdAt = row.get("created_at", Instant::class.java)!!,
                    updatedAt = row.get("updated_at", Instant::class.java)!!,
                    createdBy = row.get("created_by", UUID::class.java)!!
                )
            }
            .one()
    }
}
