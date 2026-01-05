package com.dopaminestore.product.adapter.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Distributed tracing configuration for trace ID propagation.
 *
 * Responsibilities:
 * - Generate trace ID for incoming requests (if not present)
 * - Propagate trace ID via X-Trace-ID header in responses
 * - Store trace ID in Reactor context for logging
 * - Ensure trace IDs are included in Kafka events and Redis operations
 */
@Configuration
class TracingConfig {

    companion object {
        const val TRACE_ID_HEADER = "X-Trace-ID"
        const val TRACE_ID_CONTEXT_KEY = "traceId"
    }

    /**
     * Web filter to inject trace ID into request context.
     *
     * Flow:
     * 1. Check if request has X-Trace-ID header
     * 2. If not, generate new UUID as trace ID
     * 3. Add trace ID to Reactor context
     * 4. Add X-Trace-ID header to response
     */
    @Bean
    fun traceIdFilter(): WebFilter {
        return WebFilter { exchange, chain ->
            val request = exchange.request
            val response = exchange.response

            // Get or generate trace ID
            val traceId = request.headers.getFirst(TRACE_ID_HEADER)
                ?: UUID.randomUUID().toString()

            // Add trace ID to response header
            response.headers.add(TRACE_ID_HEADER, traceId)

            // Propagate trace ID through Reactor context
            chain.filter(exchange)
                .contextWrite { context ->
                    context.put(TRACE_ID_CONTEXT_KEY, traceId)
                }
        }
    }
}

/**
 * Extension function to get trace ID from Reactor context.
 *
 * Usage:
 * ```
 * Mono.deferContextual { ctx ->
 *     val traceId = ctx.getTraceId()
 *     // Use trace ID...
 * }
 * ```
 */
fun reactor.util.context.ContextView.getTraceId(): String {
    return getOrDefault(TracingConfig.TRACE_ID_CONTEXT_KEY, "unknown") as? String ?: "unknown"
}

/**
 * Extension function to add trace ID to Reactor context.
 *
 * Usage:
 * ```
 * someOperation()
 *     .withTraceId(traceId)
 * ```
 */
fun <T> Mono<T>.withTraceId(traceId: String): Mono<T> {
    return this.contextWrite { context ->
        context.put(TracingConfig.TRACE_ID_CONTEXT_KEY, traceId)
    }
}
