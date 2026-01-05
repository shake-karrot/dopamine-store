package com.dopaminestore.product.core.port

import com.dopaminestore.product.core.domain.Product
import com.dopaminestore.product.core.domain.value.ProductStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Repository port for Product aggregate persistence.
 *
 * This is a port interface (hexagonal architecture) that defines the contract
 * for product persistence operations. Implementations will be provided in the adapter layer.
 */
interface ProductRepository {

    /**
     * Save a new product or update an existing one.
     *
     * @param product Product to save
     * @return Saved product with updated timestamps
     */
    fun save(product: Product): Mono<Product>

    /**
     * Find a product by its ID.
     *
     * @param id Product ID
     * @return Product if found, empty Mono otherwise
     */
    fun findById(id: UUID): Mono<Product>

    /**
     * Find all products.
     *
     * @return All products in the system
     */
    fun findAll(): Flux<Product>

    /**
     * Find products by status.
     *
     * Note: Status is computed from sale date and stock, so this query
     * uses dynamic filtering based on current time.
     *
     * @param status Product status to filter by
     * @param now Current timestamp (defaults to Instant.now())
     * @return Products matching the status
     */
    fun findByStatus(status: ProductStatus, now: Instant = Instant.now()): Flux<Product>

    /**
     * Find products with sale date in a time range.
     *
     * Useful for listing upcoming sales or active sales.
     *
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return Products with sale date in the range
     */
    fun findBySaleDateBetween(startDate: Instant, endDate: Instant): Flux<Product>

    /**
     * Find products with pagination and status filtering.
     *
     * @param status Optional status filter
     * @param offset Number of items to skip
     * @param limit Maximum number of items to return
     * @return Page of products
     */
    fun findWithPagination(
        status: ProductStatus? = null,
        offset: Long = 0,
        limit: Int = 20
    ): Flux<Product>

    /**
     * Count total products.
     *
     * @return Total number of products
     */
    fun count(): Mono<Long>

    /**
     * Count products by status.
     *
     * @param status Product status
     * @param now Current timestamp (defaults to Instant.now())
     * @return Number of products with the given status
     */
    fun countByStatus(status: ProductStatus, now: Instant = Instant.now()): Mono<Long>

    /**
     * Check if a product exists.
     *
     * @param id Product ID
     * @return true if product exists, false otherwise
     */
    fun existsById(id: UUID): Mono<Boolean>

    /**
     * Delete a product.
     *
     * Note: This should only be called after validating that no active purchase slots exist.
     * Consider using soft delete in production systems.
     *
     * @param id Product ID to delete
     * @return Mono that completes when deletion is done
     */
    fun deleteById(id: UUID): Mono<Void>

    /**
     * Find products created by a specific admin user.
     *
     * @param createdBy Admin user ID
     * @return Products created by the user
     */
    fun findByCreatedBy(createdBy: UUID): Flux<Product>

    /**
     * Update product stock atomically.
     *
     * This is a low-level operation used for stock adjustments.
     * Use domain entity methods (decreaseStock/increaseStock) for business logic validation.
     *
     * @param id Product ID
     * @param newStock New stock value
     * @return Updated product, or empty Mono if product not found
     */
    fun updateStock(id: UUID, newStock: Int): Mono<Product>
}
