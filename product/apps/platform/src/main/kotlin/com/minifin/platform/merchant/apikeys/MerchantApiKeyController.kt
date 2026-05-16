package com.minifin.platform.merchant.apikeys

import com.fasterxml.jackson.databind.ObjectMapper
import com.minifin.platform.identity.ApiError
import com.minifin.platform.identity.ApiResponse
import com.minifin.platform.identity.IdentityException
import com.minifin.platform.identity.IdentityService
import com.minifin.platform.identity.MerchantEmployeeRecord
import com.minifin.platform.publicapi.IdempotencyService
import com.minifin.platform.publicapi.IdempotentOutcome
import com.minifin.platform.publicapi.PublicApiException
import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

private const val SESSION_COOKIE = "MFP_SESSION"
private const val API_KEY_CREATE_ROUTE = "/api/v1/merchant/api-keys"

@RestController
class MerchantApiKeyController(
    private val apiKeyService: ApiKeyService,
    private val identityService: IdentityService,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping(API_KEY_CREATE_ROUTE, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun create(
        request: HttpServletRequest,
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<String> {
        val employee = identityService.currentMerchant(sessionToken)
        val rawBody = request.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val parsed = parseBody(rawBody)

        if (idempotencyKey.isNullOrBlank()) {
            val response = apiKeyService.create(employee, parsed)
            val body = objectMapper.writeValueAsString(ApiResponse(data = response))
            return jsonResponse(HttpStatus.CREATED.value(), body)
        }
        val validatedKey = idempotencyService.validateKey(idempotencyKey)
        val fingerprint = idempotencyService.fingerprint("POST", API_KEY_CREATE_ROUTE, rawBody)
        val cached = idempotencyService.runWriteOnce(
            merchantId = employee.merchantId,
            idempotencyKey = validatedKey,
            method = "POST",
            route = API_KEY_CREATE_ROUTE,
            requestFingerprint = fingerprint,
        ) {
            val response = apiKeyService.create(employee, parsed)
            // The raw key is one-time visible: store a redacted copy in the
            // idempotency cache so replays cannot exfiltrate it from the DB.
            val redacted = response.copy(key = REDACTED_KEY_PLACEHOLDER)
            IdempotentOutcome(
                httpStatus = HttpStatus.CREATED.value(),
                cachedBody = objectMapper.writeValueAsString(ApiResponse(data = redacted)),
                firstCallBody = objectMapper.writeValueAsString(ApiResponse(data = response)),
            )
        }
        return jsonResponse(cached.httpStatus, cached.body)
    }

    @GetMapping(API_KEY_CREATE_ROUTE)
    fun list(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
    ): ApiResponse<List<ApiKeySummary>> {
        val employee = identityService.currentMerchant(sessionToken)
        return ApiResponse(data = apiKeyService.list(employee))
    }

    @PostMapping("/api/v1/merchant/api-keys/{apiKeyId}/revoke")
    fun revoke(
        @CookieValue(name = SESSION_COOKIE, required = false) sessionToken: String?,
        @PathVariable apiKeyId: String,
    ): ApiResponse<RevokeApiKeyResponse> {
        val employee: MerchantEmployeeRecord = identityService.currentMerchant(sessionToken)
        val keyUuid = runCatching { UUID.fromString(apiKeyId) }
            .getOrElse {
                throw ApiKeyException(
                    code = "invalid_uuid",
                    message = "Invalid API key id.",
                    status = HttpStatus.BAD_REQUEST,
                )
            }
        return ApiResponse(data = apiKeyService.revoke(employee, keyUuid))
    }

    @ExceptionHandler(ApiKeyException::class)
    fun handleApiKeyException(exception: ApiKeyException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                            field = exception.field,
                        ),
                    ),
                ),
            )

    @ExceptionHandler(IdentityException::class)
    fun handleIdentityException(exception: IdentityException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                            field = exception.field,
                        ),
                    ),
                ),
            )

    @ExceptionHandler(PublicApiException::class)
    fun handlePublicApiException(exception: PublicApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(exception.status)
            .body(
                ApiResponse(
                    errors = listOf(
                        ApiError(
                            code = exception.code,
                            message = exception.message,
                            field = exception.field,
                        ),
                    ),
                ),
            )

    private fun parseBody(rawBody: String): CreateApiKeyRequest {
        if (rawBody.isBlank()) return CreateApiKeyRequest()
        return runCatching { objectMapper.readValue(rawBody, CreateApiKeyRequest::class.java) }
            .getOrElse {
                throw ApiKeyException(
                    code = "invalid_request_body",
                    message = "Request body is invalid JSON.",
                    status = HttpStatus.BAD_REQUEST,
                )
            }
    }

    private fun jsonResponse(status: Int, body: String): ResponseEntity<String> =
        ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)

    companion object {
        const val REDACTED_KEY_PLACEHOLDER = "REDACTED_ON_REPLAY"
    }
}
