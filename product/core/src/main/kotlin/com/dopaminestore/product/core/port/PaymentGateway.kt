package com.dopaminestore.product.core.port

import com.dopaminestore.product.core.domain.value.Money
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Port interface for external payment gateway integration.
 *
 * This port defines the contract for payment processing operations with external gateways.
 * Critical for handling payment transactions and ensuring idempotency.
 *
 * **Supported Gateways** (for future extension):
 * - Toss Payments (primary)
 * - Stripe (international)
 * - PayPal (alternative)
 *
 * **Payment Flow**:
 * 1. **Initiate Payment**: Create payment intent with gateway
 * 2. **User Authorization**: User completes payment on gateway's UI (redirect/iframe)
 * 3. **Webhook Callback**: Gateway sends payment result via webhook
 * 4. **Verify Payment**: Verify webhook signature and payment status
 * 5. **Complete/Fail**: Update purchase record based on payment result
 *
 * **Idempotency**:
 * - Every payment request includes an idempotency key (UUID)
 * - Duplicate requests with same key return the original payment result
 * - Prevents double-charging on network failures or retries
 *
 * **Security**:
 * - All webhook payloads must be verified using gateway's signature
 * - Payment IDs are validated against expected format
 * - Amount and currency are validated before processing
 *
 * **Timeout Policy**:
 * - Payment intent expires after 5 minutes (default)
 * - Background job marks pending payments as FAILED after timeout
 * - Expired payments trigger slot reclamation
 */
interface PaymentGateway {

    /**
     * Initiate a payment transaction.
     *
     * Creates a payment intent with the external gateway and returns a URL
     * for user to complete payment authorization.
     *
     * **Gateway Operations**:
     * - Create payment intent with amount, currency, metadata
     * - Generate secure payment URL with embedded user/order info
     * - Set success/failure callback URLs
     * - Configure timeout (5 minutes)
     *
     * **Idempotency**: If a payment with the same idempotencyKey exists,
     * returns the existing payment result instead of creating a new one.
     *
     * @param request Payment initiation request
     * @return Payment initiation result with payment URL and gateway reference
     */
    fun initiatePayment(request: PaymentInitiationRequest): Mono<PaymentInitiationResult>

    /**
     * Payment initiation request.
     *
     * @param idempotencyKey Unique key for idempotency (prevents duplicate charges)
     * @param userId User making the payment
     * @param productId Product being purchased
     * @param purchaseSlotId Purchase slot ID (for callback reference)
     * @param amount Payment amount
     * @param orderName Order name/description (shown to user)
     * @param successCallbackUrl URL to redirect on successful payment
     * @param failureCallbackUrl URL to redirect on payment failure
     * @param traceId Distributed trace ID
     */
    data class PaymentInitiationRequest(
        val idempotencyKey: UUID,
        val userId: UUID,
        val productId: UUID,
        val purchaseSlotId: UUID,
        val amount: Money,
        val orderName: String,
        val successCallbackUrl: String,
        val failureCallbackUrl: String,
        val traceId: String
    )

    /**
     * Payment initiation result.
     *
     * @param success Whether payment initiation succeeded
     * @param paymentId Gateway's payment reference ID (null if failed)
     * @param paymentUrl URL for user to complete payment (null if failed)
     * @param errorCode Error code if initiation failed
     * @param errorMessage Error message if initiation failed
     * @param expiresAt When payment intent expires (null if failed)
     */
    data class PaymentInitiationResult(
        val success: Boolean,
        val paymentId: String? = null,
        val paymentUrl: String? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val expiresAt: Instant? = null
    )

    /**
     * Verify webhook signature from payment gateway.
     *
     * Validates that webhook payload is authentic and not tampered with.
     * Critical for preventing payment fraud.
     *
     * **Verification Methods** (gateway-specific):
     * - HMAC-SHA256 signature in header
     * - JWT token validation
     * - IP whitelist check
     *
     * @param payload Raw webhook payload (JSON string)
     * @param signature Signature from webhook header
     * @param secret Gateway webhook secret (from configuration)
     * @return true if signature is valid, false otherwise
     */
    fun verifyWebhookSignature(
        payload: String,
        signature: String,
        secret: String
    ): Mono<Boolean>

    /**
     * Parse webhook payload and extract payment result.
     *
     * Converts gateway-specific webhook format to normalized PaymentResult.
     *
     * @param payload Raw webhook payload (JSON string)
     * @return Parsed payment result
     */
    fun parseWebhookPayload(payload: String): Mono<PaymentResult>

    /**
     * Payment result from webhook.
     *
     * @param paymentId Gateway's payment reference ID
     * @param status Payment status (SUCCESS, FAILED, PENDING, CANCELLED)
     * @param amount Payment amount (for validation)
     * @param currency Currency code (for validation)
     * @param paidAt When payment was completed (null if not successful)
     * @param failureReason Failure reason if status is FAILED
     * @param metadata Additional metadata from gateway
     */
    data class PaymentResult(
        val paymentId: String,
        val status: PaymentStatus,
        val amount: Money,
        val paidAt: Instant? = null,
        val failureReason: String? = null,
        val metadata: Map<String, Any>? = null
    )

    /**
     * Payment status from gateway.
     */
    enum class PaymentStatus {
        /** Payment completed successfully */
        SUCCESS,

        /** Payment failed (insufficient funds, card declined, etc.) */
        FAILED,

        /** Payment still pending user authorization */
        PENDING,

        /** Payment cancelled by user */
        CANCELLED
    }

    /**
     * Query payment status from gateway.
     *
     * Used for:
     * - Manual verification of webhook results
     * - Recovery from webhook delivery failures
     * - Admin troubleshooting
     *
     * @param paymentId Gateway's payment reference ID
     * @return Current payment status
     */
    fun queryPaymentStatus(paymentId: String): Mono<PaymentResult>

    /**
     * Cancel a pending payment.
     *
     * Used when:
     * - User cancels checkout manually
     * - Slot expires before payment completes
     * - Admin intervention required
     *
     * Note: Can only cancel PENDING payments. SUCCESS/FAILED payments cannot be cancelled.
     * For successful payments, use refund() instead.
     *
     * @param paymentId Gateway's payment reference ID
     * @param reason Cancellation reason (for audit trail)
     * @param traceId Distributed trace ID
     * @return true if cancellation succeeded, false otherwise
     */
    fun cancelPayment(
        paymentId: String,
        reason: String,
        traceId: String
    ): Mono<Boolean>

    /**
     * Refund a completed payment.
     *
     * Used for:
     * - Customer service refund requests
     * - Product defects or issues
     * - Admin corrections
     *
     * Note: Can only refund SUCCESS payments.
     * Partial refunds are supported by specifying amount < original payment amount.
     *
     * @param paymentId Gateway's payment reference ID
     * @param amount Refund amount (null for full refund)
     * @param reason Refund reason (for audit trail)
     * @param traceId Distributed trace ID
     * @return Refund result with refund ID
     */
    fun refundPayment(
        paymentId: String,
        amount: Money? = null,
        reason: String,
        traceId: String
    ): Mono<RefundResult>

    /**
     * Refund result.
     *
     * @param success Whether refund succeeded
     * @param refundId Gateway's refund reference ID (null if failed)
     * @param refundedAmount Actual refunded amount (null if failed)
     * @param refundedAt When refund was processed (null if failed)
     * @param errorCode Error code if refund failed
     * @param errorMessage Error message if refund failed
     */
    data class RefundResult(
        val success: Boolean,
        val refundId: String? = null,
        val refundedAmount: Money? = null,
        val refundedAt: Instant? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Validate that a payment ID belongs to this system.
     *
     * Prevents attacks where attacker provides fake payment IDs.
     * Checks gateway records to ensure payment ID exists and matches expected metadata.
     *
     * @param paymentId Gateway's payment reference ID
     * @param expectedAmount Expected payment amount
     * @param expectedUserId Expected user ID
     * @return true if payment ID is valid and matches expectations
     */
    fun validatePaymentId(
        paymentId: String,
        expectedAmount: Money,
        expectedUserId: UUID
    ): Mono<Boolean>

    /**
     * Get payment transaction details for audit purposes.
     *
     * Returns complete payment history including:
     * - Creation timestamp
     * - Authorization timestamp
     * - Completion/failure timestamp
     * - All status transitions
     * - Gateway metadata
     *
     * @param paymentId Gateway's payment reference ID
     * @return Payment transaction details
     */
    fun getPaymentDetails(paymentId: String): Mono<PaymentDetails>

    /**
     * Payment transaction details.
     *
     * @param paymentId Gateway's payment reference ID
     * @param status Current payment status
     * @param amount Payment amount
     * @param createdAt When payment was initiated
     * @param authorizedAt When user authorized payment (null if not authorized)
     * @param completedAt When payment completed (null if not completed)
     * @param failedAt When payment failed (null if not failed)
     * @param cancelledAt When payment was cancelled (null if not cancelled)
     * @param method Payment method (CARD, BANK_TRANSFER, etc.)
     * @param metadata Additional gateway metadata
     */
    data class PaymentDetails(
        val paymentId: String,
        val status: PaymentStatus,
        val amount: Money,
        val createdAt: Instant,
        val authorizedAt: Instant? = null,
        val completedAt: Instant? = null,
        val failedAt: Instant? = null,
        val cancelledAt: Instant? = null,
        val method: String? = null,
        val metadata: Map<String, Any>? = null
    )

    companion object {
        /**
         * Default payment timeout (5 minutes).
         */
        const val DEFAULT_TIMEOUT_MILLIS = 300_000L

        /**
         * Standard error codes.
         */
        const val ERROR_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS"
        const val ERROR_CARD_DECLINED = "CARD_DECLINED"
        const val ERROR_GATEWAY_ERROR = "GATEWAY_ERROR"
        const val ERROR_TIMEOUT = "PAYMENT_TIMEOUT"
        const val ERROR_INVALID_AMOUNT = "INVALID_AMOUNT"
        const val ERROR_DUPLICATE_REQUEST = "DUPLICATE_REQUEST"
    }
}
