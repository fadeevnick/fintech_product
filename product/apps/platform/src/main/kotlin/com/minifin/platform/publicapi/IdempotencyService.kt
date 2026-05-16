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

    fun lookupCached(
        merchantId: UUID,
        idempotencyKey: String,
        method: String,
        route: String,
        requestFingerprint: String,
    ): CachedResponse? {
        val existing = repository.find(merchantId, idempotencyKey) ?: return null
        if (existing.method != method || existing.route != route) {
            throw PublicApiException(
                code = "idempotency_conflict",
                message = "Idempotency-Key was already used with a different request.",
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
        return CachedResponse(existing.responseStatus, existing.responseBody)
    }

    @Transactional
    fun persist(
        merchantId: UUID,
        idempotencyKey: String,
        method: String,
        route: String,
        requestFingerprint: String,
        responseStatus: Int,
        responseBody: String,
    ): CachedResponse {
        val inserted = repository.insert(
            merchantId = merchantId,
            idempotencyKey = idempotencyKey,
            method = method,
            route = route,
            requestFingerprint = requestFingerprint,
            responseStatus = responseStatus,
            responseBody = responseBody,
        )
        if (!inserted) {
            val existing = repository.find(merchantId, idempotencyKey)
                ?: throw PublicApiException(
                    code = "idempotency_state_conflict",
                    message = "Idempotency state changed concurrently.",
                    status = HttpStatus.CONFLICT,
                )
            if (existing.requestFingerprint != requestFingerprint
                || existing.method != method
                || existing.route != route
            ) {
                throw PublicApiException(
                    code = "idempotency_conflict",
                    message = "Idempotency-Key was already used with a different request.",
                    status = HttpStatus.CONFLICT,
                )
            }
            return CachedResponse(existing.responseStatus, existing.responseBody)
        }
        return CachedResponse(responseStatus, responseBody)
    }

    fun writeJson(value: Any): String = objectMapper.writeValueAsString(value)
}
