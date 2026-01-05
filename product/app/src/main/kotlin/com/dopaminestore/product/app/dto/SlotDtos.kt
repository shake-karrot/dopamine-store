package com.dopaminestore.product.app.dto

import com.dopaminestore.product.core.domain.PurchaseSlot
import com.dopaminestore.product.core.domain.value.SlotStatus
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/**
 * Request DTO for acquiring a purchase slot.
 *
 * **API Contract**: POST /api/v1/slots/acquire
 *
 * **Validation**:
 * - productId: Required, must be valid UUID
 * - userId: Required, must be valid UUID (from JWT token)
 *
 * **Business Rules**:
 * - Product must exist and be ON_SALE
 * - User cannot have duplicate active slot for same product
 * - Stock must be available
 * - Fairness ordering enforced by arrival timestamp
 */
data class AcquireSlotRequest(
    @field:NotNull(message = "Product ID is required")
    val productId: UUID,

    @field:NotNull(message = "User ID is required")
    val userId: UUID
) {
    /**
     * Arrival timestamp is set on the server-side at the earliest possible point
     * (e.g., in a WebFilter or interceptor) to ensure fairness.
     *
     * This field is NOT part of the request body - it's added by the server.
     */
    var arrivalTimestamp: Long = System.currentTimeMillis()
}

/**
 * Response DTO for successful slot acquisition.
 *
 * **API Contract**: POST /api/v1/slots/acquire
 *
 * **Response Codes**:
 * - 201 CREATED: Slot successfully acquired
 * - 400 BAD REQUEST: Invalid request data
 * - 404 NOT FOUND: Product not found
 * - 409 CONFLICT: Duplicate slot or out of stock
 * - 500 INTERNAL ERROR: Server error
 */
data class AcquireSlotResponse(
    val slotId: UUID,
    val productId: UUID,
    val userId: UUID,
    val acquisitionTimestamp: Instant,
    val expirationTimestamp: Instant,
    val status: SlotStatus,
    val remainingSeconds: Long,
    val message: String = "Slot acquired successfully"
) {
    companion object {
        /**
         * Map domain entity to response DTO.
         */
        fun from(slot: PurchaseSlot): AcquireSlotResponse {
            return AcquireSlotResponse(
                slotId = slot.id,
                productId = slot.productId,
                userId = slot.userId,
                acquisitionTimestamp = slot.acquisitionTimestamp,
                expirationTimestamp = slot.expirationTimestamp,
                status = slot.status,
                remainingSeconds = slot.remainingSeconds()
            )
        }
    }
}

/**
 * Response DTO for slot details (GET /api/v1/slots/{slotId}).
 */
data class SlotDetailResponse(
    val slotId: UUID,
    val productId: UUID,
    val userId: UUID,
    val acquisitionTimestamp: Instant,
    val expirationTimestamp: Instant,
    val status: SlotStatus,
    val reclaimStatus: String? = null,
    val remainingSeconds: Long,
    val canProcessPayment: Boolean,
    val traceId: String
) {
    companion object {
        fun from(slot: PurchaseSlot): SlotDetailResponse {
            return SlotDetailResponse(
                slotId = slot.id,
                productId = slot.productId,
                userId = slot.userId,
                acquisitionTimestamp = slot.acquisitionTimestamp,
                expirationTimestamp = slot.expirationTimestamp,
                status = slot.status,
                reclaimStatus = slot.reclaimStatus?.name,
                remainingSeconds = slot.remainingSeconds(),
                canProcessPayment = slot.canProcessPayment(),
                traceId = slot.traceId
            )
        }
    }
}

/**
 * Response DTO for listing user slots (GET /api/v1/users/{userId}/slots).
 */
data class UserSlotsResponse(
    val slots: List<SlotSummary>,
    val totalCount: Int,
    val activeCount: Int,
    val expiredCount: Int,
    val completedCount: Int
) {
    data class SlotSummary(
        val slotId: UUID,
        val productId: UUID,
        val productName: String?,
        val status: SlotStatus,
        val acquisitionTimestamp: Instant,
        val expirationTimestamp: Instant,
        val remainingSeconds: Long
    ) {
        companion object {
            fun from(slot: PurchaseSlot, productName: String? = null): SlotSummary {
                return SlotSummary(
                    slotId = slot.id,
                    productId = slot.productId,
                    productName = productName,
                    status = slot.status,
                    acquisitionTimestamp = slot.acquisitionTimestamp,
                    expirationTimestamp = slot.expirationTimestamp,
                    remainingSeconds = slot.remainingSeconds()
                )
            }
        }
    }
}

/**
 * RFC 7807 Problem Details for API errors.
 *
 * Standard error response format following RFC 7807 specification.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a>
 */
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val traceId: String? = null,
    val timestamp: Instant = Instant.now(),
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        // Problem type URIs
        const val TYPE_PRODUCT_NOT_FOUND = "/problems/product-not-found"
        const val TYPE_PRODUCT_NOT_AVAILABLE = "/problems/product-not-available"
        const val TYPE_DUPLICATE_SLOT = "/problems/duplicate-slot"
        const val TYPE_OUT_OF_STOCK = "/problems/out-of-stock"
        const val TYPE_SLOT_NOT_FOUND = "/problems/slot-not-found"
        const val TYPE_SLOT_EXPIRED = "/problems/slot-expired"
        const val TYPE_VALIDATION_ERROR = "/problems/validation-error"
        const val TYPE_INTERNAL_ERROR = "/problems/internal-error"

        /**
         * Create a problem detail for product not found.
         */
        fun productNotFound(productId: UUID, traceId: String): ProblemDetail {
            return ProblemDetail(
                type = TYPE_PRODUCT_NOT_FOUND,
                title = "Product Not Found",
                status = 404,
                detail = "The requested product does not exist",
                instance = "/api/v1/products/$productId",
                traceId = traceId,
                additionalProperties = mapOf("productId" to productId.toString())
            )
        }

        /**
         * Create a problem detail for product not available.
         */
        fun productNotAvailable(productId: UUID, status: String, traceId: String): ProblemDetail {
            return ProblemDetail(
                type = TYPE_PRODUCT_NOT_AVAILABLE,
                title = "Product Not Available",
                status = 409,
                detail = "The product is not available for purchase (status: $status)",
                instance = "/api/v1/products/$productId",
                traceId = traceId,
                additionalProperties = mapOf(
                    "productId" to productId.toString(),
                    "productStatus" to status
                )
            )
        }

        /**
         * Create a problem detail for duplicate slot.
         */
        fun duplicateSlot(userId: UUID, productId: UUID, traceId: String): ProblemDetail {
            return ProblemDetail(
                type = TYPE_DUPLICATE_SLOT,
                title = "Duplicate Slot",
                status = 409,
                detail = "You already have an active slot for this product",
                instance = "/api/v1/slots/acquire",
                traceId = traceId,
                additionalProperties = mapOf(
                    "userId" to userId.toString(),
                    "productId" to productId.toString()
                )
            )
        }

        /**
         * Create a problem detail for out of stock.
         */
        fun outOfStock(productId: UUID, traceId: String): ProblemDetail {
            return ProblemDetail(
                type = TYPE_OUT_OF_STOCK,
                title = "Out of Stock",
                status = 409,
                detail = "This product is sold out",
                instance = "/api/v1/products/$productId",
                traceId = traceId,
                additionalProperties = mapOf("productId" to productId.toString())
            )
        }

        /**
         * Create a problem detail for slot not found.
         */
        fun slotNotFound(slotId: UUID, traceId: String): ProblemDetail {
            return ProblemDetail(
                type = TYPE_SLOT_NOT_FOUND,
                title = "Slot Not Found",
                status = 404,
                detail = "The requested slot does not exist",
                instance = "/api/v1/slots/$slotId",
                traceId = traceId,
                additionalProperties = mapOf("slotId" to slotId.toString())
            )
        }

        /**
         * Create a problem detail for validation errors.
         */
        fun validationError(errors: Map<String, String>, traceId: String): ProblemDetail {
            return ProblemDetail(
                type = TYPE_VALIDATION_ERROR,
                title = "Validation Error",
                status = 400,
                detail = "Request validation failed",
                instance = "/api/v1",
                traceId = traceId,
                additionalProperties = mapOf("errors" to errors)
            )
        }

        /**
         * Create a problem detail for internal errors.
         */
        fun internalError(message: String, traceId: String): ProblemDetail {
            return ProblemDetail(
                type = TYPE_INTERNAL_ERROR,
                title = "Internal Server Error",
                status = 500,
                detail = message,
                instance = "/api/v1",
                traceId = traceId
            )
        }
    }
}
