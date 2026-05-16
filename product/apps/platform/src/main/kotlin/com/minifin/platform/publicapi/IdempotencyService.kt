package com.minifin.platform.publicapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.merchant.apikeys.sha256Hex
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CachedResponse(
    val httpStatus: Int,
    val body: String,
)

/**
 * Result of an idempotent operation.
 *
 * The [cachedBody] is what subsequent replays observe and what we persist into
 * `idempotency.idempotency_keys`. [firstCallBody] is returned only to the
 * caller that actually executed the operation. By default they are identical.
 *
 * They differ for routes whose first-call response contains a one-time-visible
 * secret that must not be persisted (e.g. merchant API key creation).
 */
data class IdempotentOutcome(
    val httpStatus: Int,
    val cachedBody: String,
    val firstCallBody: String = cachedBody,
) {
    companion object {
        fun of(httpStatus: Int, body: String): IdempotentOutcome =
            IdempotentOutcome(httpStatus = httpStatus, cachedBody = body)
    }
}

/**
 * Idempotency primitive scoped per (merchant, route, key).
 *
 * The contract per planning/03_functional_requirements.md is:
 * - same merchant + same endpoint + same key + same body → cached response;
 * - same merchant + same endpoint + same key + different body → 409 conflict;
 * - same key reused by a different merchant or against a different endpoint is
 *   independent.
 *
 * Atomicity contract: the business operation and the cache entry are written in
 * the SAME database transaction by inserting a placeholder row first and
 * finalizing it after the business write succeeds. Concurrent callers that race
 * on the same (merchant, route, key) block on the unique constraint and, once
 * the winning transaction commits, observe the cached response.
 */
@Service
class IdempotencyService(
    private val repository: IdempotencyRepository,
    private val objectMapper: ObjectMapper,
) {
    fun validateKey(idempotencyKey: String?): String {
        val trimmed = idempotencyKey?.trim()
        if (trimmed.isNullOrEmpty()) {
            throw PublicApiException(
                code = "idempotency_key_required",
                message = "Idempotency-Key header is required for this route.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        if (trimmed.length > 200) {
            throw PublicApiException(
                code = "invalid_idempotency_key",
                message = "Idempotency-Key header is invalid.",
                status = HttpStatus.BAD_REQUEST,
            )
        }
        return trimmed
    }

    fun fingerprint(method: String, route: String, rawBody: String): String =
        sha256Hex("$method|$route|$rawBody")

    fun writeJson(value: Any): String = objectMapper.writeValueAsString(value)

    /**
     * Run [operation] exactly once for the (merchant, route, key) scope and
     * cache the response in the same transaction. Returns either the cached
     * response from a previous run or the freshly computed one.
     *
     * The whole flow runs under a single Spring transaction. The placeholder
     * INSERT serializes concurrent callers via the unique constraint; only the
     * winning caller executes [operation].
     */
    @Transactional
    fun runWriteOnce(
        merchantId: UUID,
        idempotencyKey: String,
        method: String,
        route: String,
        requestFingerprint: String,
        operation: () -> IdempotentOutcome,
    ): CachedResponse {
        repository.find(merchantId, route, idempotencyKey)?.let { existing ->
            return validateAndReturn(existing, method, requestFingerprint)
        }

        val inserted = repository.insertPlaceholder(
            merchantId = merchantId,
            idempotencyKey = idempotencyKey,
            method = method,
            route = route,
            requestFingerprint = requestFingerprint,
        )
        if (!inserted) {
            val existing = repository.find(merchantId, route, idempotencyKey)
                ?: throw PublicApiException(
                    code = "idempotency_state_conflict",
                    message = "Idempotency state changed concurrently.",
                    status = HttpStatus.CONFLICT,
                )
            return validateAndReturn(existing, method, requestFingerprint)
        }

        val outcome = operation()
        val updated = repository.finalize(
            merchantId = merchantId,
            route = route,
            idempotencyKey = idempotencyKey,
            responseStatus = outcome.httpStatus,
            responseBody = outcome.cachedBody,
        )
        if (updated == 0) {
            throw PublicApiException(
                code = "idempotency_state_conflict",
                message = "Idempotency state changed concurrently.",
                status = HttpStatus.CONFLICT,
            )
        }
        return CachedResponse(outcome.httpStatus, outcome.firstCallBody)
    }

    private fun validateAndReturn(
        existing: IdempotencyRecord,
        method: String,
        requestFingerprint: String,
    ): CachedResponse {
        if (existing.method != method) {
            throw PublicApiException(
                code = "idempotency_conflict",
                message = "Idempotency-Key was already used with a different HTTP method.",
                status = HttpStatus.CONFLICT,
            )
        }
        if (existing.requestFingerprint != requestFingerprint) {
            throw PublicApiException(
                code = "idempotency_conflict",
                message = "Idempotency-Key was already used with a different request body.",
                status = HttpStatus.CONFLICT,
            )
        }
        if (existing.responseStatus == 0) {
            // Another transaction is in-flight and has reserved the key but
            // not committed yet. We should not observe this under READ COMMITTED
            // because the placeholder INSERT blocks until the winning transaction
            // commits. If we do, surface a transient conflict.
            throw PublicApiException(
                code = "idempotency_in_progress",
                message = "Idempotency-Key is being processed; retry shortly.",
                status = HttpStatus.CONFLICT,
            )
        }
        return CachedResponse(existing.responseStatus, existing.responseBody)
    }
}
