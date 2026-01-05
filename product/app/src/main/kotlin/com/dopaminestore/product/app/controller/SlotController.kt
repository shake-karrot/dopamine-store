package com.dopaminestore.product.app.controller

import com.dopaminestore.product.app.dto.AcquireSlotRequest
import com.dopaminestore.product.app.dto.AcquireSlotResponse
import com.dopaminestore.product.app.dto.ProblemDetail
import com.dopaminestore.product.app.dto.SlotDetailResponse
import com.dopaminestore.product.core.usecase.DuplicateSlotException
import com.dopaminestore.product.core.usecase.OutOfStockException
import com.dopaminestore.product.core.usecase.ProductNotAvailableException
import com.dopaminestore.product.core.usecase.ProductNotFoundException
import com.dopaminestore.product.core.usecase.SlotAcquisitionUseCase
import com.dopaminestore.product.core.port.PurchaseSlotRepository
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * REST controller for purchase slot operations.
 *
 * **API Endpoints**:
 * - POST /api/v1/slots/acquire - Acquire a new purchase slot
 * - GET /api/v1/slots/{slotId} - Get slot details
 * - GET /api/v1/users/{userId}/slots - List user's slots
 *
 * **Architecture**:
 * - Thin controller layer (no business logic)
 * - Delegates to use case layer (SlotAcquisitionUseCase)
 * - Returns RFC 7807 Problem Details for errors
 * - Captures arrival timestamp for fairness ordering
 *
 * **Performance**:
 * - Fully reactive (WebFlux)
 * - Non-blocking I/O
 * - Target: 100K RPS with p99 < 100ms
 */
@RestController
@RequestMapping("/api/v1/slots")
class SlotController(
    private val slotAcquisitionUseCase: SlotAcquisitionUseCase,
    private val slotRepository: PurchaseSlotRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Acquire a purchase slot for a user and product.
     *
     * **Request Flow**:
     * 1. Capture arrival timestamp (for fairness)
     * 2. Extract trace ID from request headers
     * 3. Call SlotAcquisitionUseCase
     * 4. Map domain entity to response DTO
     *
     * **Success Response**: 201 CREATED
     * **Error Responses**:
     * - 400 BAD REQUEST: Validation error
     * - 404 NOT FOUND: Product not found
     * - 409 CONFLICT: Duplicate slot or out of stock
     * - 500 INTERNAL ERROR: Server error
     *
     * @param request Slot acquisition request
     * @param exchange ServerWebExchange for accessing headers and timestamps
     * @return Acquired slot details
     */
    @PostMapping("/acquire")
    fun acquireSlot(
        @Valid @RequestBody request: AcquireSlotRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<AcquireSlotResponse>> {
        // Extract trace ID from headers (X-Trace-Id or generate new)
        val traceId = exchange.request.headers.getFirst("X-Trace-Id") ?: UUID.randomUUID().toString()

        // Capture arrival timestamp at the earliest possible point for fairness
        val arrivalTimestamp = System.currentTimeMillis()

        logger.info(
            "[SLOT_ACQUISITION_REQUEST] userId={}, productId={}, arrivalTimestamp={}, traceId={}",
            request.userId, request.productId, arrivalTimestamp, traceId
        )

        val command = SlotAcquisitionUseCase.AcquireSlotCommand(
            userId = request.userId,
            productId = request.productId,
            arrivalTimestamp = arrivalTimestamp,
            traceId = traceId
        )

        return slotAcquisitionUseCase.acquireSlot(command)
            .map { slot ->
                val response = AcquireSlotResponse.from(slot)
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .header("X-Trace-Id", traceId)
                    .body(response)
            }
            .onErrorResume { error ->
                handleSlotAcquisitionError(error, traceId).map { problemDetail ->
                    ResponseEntity
                        .status(problemDetail.status)
                        .header("X-Trace-Id", traceId)
                        .body(null)
                }
            }
    }

    /**
     * Get slot details by ID.
     *
     * **Success Response**: 200 OK
     * **Error Responses**:
     * - 404 NOT FOUND: Slot not found
     * - 500 INTERNAL ERROR: Server error
     *
     * @param slotId Slot UUID
     * @param exchange ServerWebExchange for trace ID
     * @return Slot details
     */
    @GetMapping("/{slotId}")
    fun getSlot(
        @PathVariable slotId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<SlotDetailResponse>> {
        val traceId = exchange.request.headers.getFirst("X-Trace-Id") ?: UUID.randomUUID().toString()

        logger.debug("[SLOT_DETAIL_REQUEST] slotId={}, traceId={}", slotId, traceId)

        return slotRepository.findById(slotId)
            .map { slot ->
                val response = SlotDetailResponse.from(slot)
                ResponseEntity
                    .ok()
                    .header("X-Trace-Id", traceId)
                    .body(response)
            }
            .switchIfEmpty(
                Mono.just(
                    ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .header("X-Trace-Id", traceId)
                        .body(null)
                )
            )
            .onErrorResume { error ->
                logger.error("[SLOT_DETAIL_ERROR] slotId={}, traceId={}", slotId, traceId, error)
                Mono.just(
                    ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("X-Trace-Id", traceId)
                        .body(null)
                )
            }
    }

    /**
     * Check if user has an active slot for a product.
     *
     * **Success Response**: 200 OK with boolean
     * **Error Responses**:
     * - 500 INTERNAL ERROR: Server error
     *
     * @param userId User UUID
     * @param productId Product UUID
     * @param exchange ServerWebExchange for trace ID
     * @return Boolean indicating if active slot exists
     */
    @GetMapping("/check")
    fun hasActiveSlot(
        @RequestParam userId: UUID,
        @RequestParam productId: UUID,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Map<String, Boolean>>> {
        val traceId = exchange.request.headers.getFirst("X-Trace-Id") ?: UUID.randomUUID().toString()

        return slotRepository.hasActiveSlot(userId, productId)
            .map { hasSlot ->
                ResponseEntity
                    .ok()
                    .header("X-Trace-Id", traceId)
                    .body(mapOf("hasActiveSlot" to hasSlot))
            }
            .onErrorResume { error ->
                logger.error(
                    "[HAS_ACTIVE_SLOT_ERROR] userId={}, productId={}, traceId={}",
                    userId, productId, traceId, error
                )
                Mono.just(
                    ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("X-Trace-Id", traceId)
                        .body(mapOf("hasActiveSlot" to false))
                )
            }
    }

    /**
     * Handle slot acquisition errors and map to RFC 7807 Problem Details.
     */
    private fun handleSlotAcquisitionError(
        error: Throwable,
        traceId: String
    ): Mono<ProblemDetail> {
        val problemDetail = when (error) {
            is ProductNotFoundException -> {
                logger.warn(
                    "[SLOT_ACQUISITION_FAILED] reason=PRODUCT_NOT_FOUND, productId={}, traceId={}",
                    error.productId, traceId
                )
                ProblemDetail.productNotFound(error.productId, traceId)
            }
            is ProductNotAvailableException -> {
                logger.warn(
                    "[SLOT_ACQUISITION_FAILED] reason=PRODUCT_NOT_AVAILABLE, " +
                            "productId={}, status={}, traceId={}",
                    error.productId, error.status, traceId
                )
                ProblemDetail.productNotAvailable(error.productId, error.status.name, traceId)
            }
            is DuplicateSlotException -> {
                logger.warn(
                    "[SLOT_ACQUISITION_FAILED] reason=DUPLICATE_SLOT, " +
                            "userId={}, productId={}, traceId={}",
                    error.userId, error.productId, traceId
                )
                ProblemDetail.duplicateSlot(error.userId, error.productId, traceId)
            }
            is OutOfStockException -> {
                logger.warn(
                    "[SLOT_ACQUISITION_FAILED] reason=OUT_OF_STOCK, productId={}, traceId={}",
                    error.productId, traceId
                )
                ProblemDetail.outOfStock(error.productId, traceId)
            }
            else -> {
                logger.error(
                    "[SLOT_ACQUISITION_FAILED] reason=INTERNAL_ERROR, traceId={}",
                    traceId, error
                )
                ProblemDetail.internalError(
                    error.message ?: "An unexpected error occurred",
                    traceId
                )
            }
        }

        return Mono.just(problemDetail)
    }
}
